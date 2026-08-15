package com.paperweight.os

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.paperweight.os.admin.DeviceOwnerPolicy
import com.paperweight.os.broadcast.BroadcastService
import com.paperweight.os.backup.RestoreManager
import com.paperweight.os.data.db.AppDatabase
import com.paperweight.os.data.prefs.AppPreferences
import com.paperweight.os.provisioning.SetupActivity
import com.paperweight.os.storage.SdCardDetector
import com.paperweight.os.storage.SdCardMountState
import com.paperweight.os.ui.nav.DashboardApp
import com.paperweight.os.ui.setup.RestoreGateScreen
import com.paperweight.os.ui.setup.RestoreGateState
import com.paperweight.os.ui.setup.SdCardRequiredScreen
import com.paperweight.os.ui.theme.PaperweightOSTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val devicePolicyManager by lazy {
        getSystemService(DevicePolicyManager::class.java)
    }
    private val appPreferences by lazy { AppPreferences.create(this) }
    private val restoreManager by lazy { RestoreManager(this, appPreferences) }
    private var restoreGateState by mutableStateOf<RestoreGateState>(RestoreGateState.NeedsFolderGrant)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!devicePolicyManager.isDeviceOwnerApp(packageName)) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        DeviceOwnerPolicy.apply(this)
        markRestoreDecisionMadeForExistingInstall()

        setContent {
            PaperweightOSTheme {
                // Boot chain: Device Owner (above) -> SD card present/sized ->
                // one-time SAF Paperweight folder grant + restore/start-fresh
                // decision -> dashboard. No pairing step anymore — the app is
                // its own backend. hasSdCard is a live Flow so pulling the card
                // mid-session drops back to the gate instead of crashing.
                val appContext = applicationContext
                val hasSdCard by remember { SdCardMountState.observe(appContext) }
                    .collectAsState(initial = SdCardDetector.hasValidCard(appContext))
                val restoreDecisionMade by remember { appPreferences.initialRestoreDecisionMade }
                    .collectAsState(initial = false)
                val vaultTreeUri by remember { appPreferences.vaultTreeUri }
                    .collectAsState(initial = null)
                val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                    handleTreeGrant(uri)
                }

                if (!hasSdCard) {
                    SdCardRequiredScreen()
                } else if (!restoreDecisionMade) {
                    LaunchedEffect(vaultTreeUri) {
                        if (vaultTreeUri == null) {
                            restoreGateState = RestoreGateState.NeedsFolderGrant
                        } else if (restoreGateState == RestoreGateState.NeedsFolderGrant) {
                            inspectBackups(Uri.parse(vaultTreeUri))
                        }
                    }
                    RestoreGateScreen(
                        state = restoreGateState,
                        onChooseFolder = { treeLauncher.launch(null) },
                        onRestore = ::restoreLatestBackupAndContinue,
                        onStartFresh = ::startFreshAndContinue,
                        onRetry = { vaultTreeUri?.let { inspectBackups(Uri.parse(it)) } ?: run { restoreGateState = RestoreGateState.NeedsFolderGrant } },
                    )
                } else {
                    LaunchedEffect(Unit) { startBroadcastService() }
                    DashboardApp()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        window.decorView.post { enterLockTaskWhenForeground() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterLockTaskWhenForeground()
    }

    private fun handleTreeGrant(uri: Uri?) {
        if (uri == null) {
            restoreGateState = RestoreGateState.Error("Choose the SD-card folder named Paperweight before continuing.")
            return
        }
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        contentResolver.takePersistableUriPermission(uri, flags)
        appPreferences.setVaultTreeUri(uri)
        inspectBackups(uri)
    }

    private fun inspectBackups(treeUri: Uri) {
        restoreGateState = RestoreGateState.Checking
        lifecycleScope.launch {
            val result = runCatching {
                val root = DocumentFile.fromTreeUri(this@MainActivity, treeUri)
                    ?: error("The selected Paperweight folder is no longer readable.")
                restoreManager.findLatestSnapshot(root)
            }
            restoreGateState = result.fold(
                onSuccess = { snapshot -> snapshot?.let { RestoreGateState.OfferRestore(it) } ?: RestoreGateState.NoBackupFound },
                onFailure = { error -> RestoreGateState.Error(error.message ?: "Unable to check backups on the SD card.") },
            )
        }
    }

    private fun restoreLatestBackupAndContinue() {
        restoreGateState = RestoreGateState.Restoring
        lifecycleScope.launch {
            val result = runCatching {
                val treeUri = appPreferences.vaultTreeUri.first() ?: error("Choose the SD-card Paperweight folder before restoring.")
                val root = DocumentFile.fromTreeUri(this@MainActivity, Uri.parse(treeUri))
                    ?: error("The selected Paperweight folder is no longer readable.")
                restoreManager.restoreLatest(root) ?: error("No valid backup was found in Paperweight/backups/.")
            }
            result.fold(
                onSuccess = { appPreferences.setInitialRestoreDecisionMade(true) },
                onFailure = { error -> restoreGateState = RestoreGateState.Error(error.message ?: "Restore failed.") },
            )
        }
    }

    private fun startFreshAndContinue() {
        appPreferences.setInitialRestoreDecisionMade(true)
    }

    private fun startBroadcastService() {
        val intent = Intent(this, BroadcastService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun markRestoreDecisionMadeForExistingInstall() {
        if (getDatabasePath(AppDatabase.DATABASE_NAME).exists()) {
            appPreferences.setInitialRestoreDecisionMade(true)
        }
    }

    private fun enterLockTaskWhenForeground() {
        if (!devicePolicyManager.isDeviceOwnerApp(packageName) || isInLockTaskMode()) return
        if (!hasWindowFocus()) return

        try {
            startLockTask()
        } catch (_: IllegalArgumentException) {
            window.decorView.postDelayed({ enterLockTaskWhenForeground() }, LOCK_TASK_RETRY_DELAY_MS)
        }
    }

    private fun isInLockTaskMode(): Boolean {
        val activityManager = getSystemService(ActivityManager::class.java)
        return activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    private companion object {
        const val LOCK_TASK_RETRY_DELAY_MS = 500L
    }
}
