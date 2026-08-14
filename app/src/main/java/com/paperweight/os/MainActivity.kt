package com.paperweight.os

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.paperweight.os.admin.DeviceOwnerPolicy
import com.paperweight.os.provisioning.SetupActivity
import com.paperweight.os.storage.SdCardDetector
import com.paperweight.os.storage.SdCardMountState
import com.paperweight.os.ui.nav.DashboardApp
import com.paperweight.os.ui.setup.SdCardRequiredScreen
import com.paperweight.os.ui.theme.PaperweightOSTheme

class MainActivity : ComponentActivity() {

    private val devicePolicyManager by lazy {
        getSystemService(DevicePolicyManager::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!devicePolicyManager.isDeviceOwnerApp(packageName)) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        DeviceOwnerPolicy.apply(this)

        setContent {
            PaperweightOSTheme {
                // Boot chain: Device Owner (above) -> SD card present/sized
                // -> dashboard. No pairing step anymore — the app is its own
                // backend. hasSdCard is a live Flow so pulling the card mid-
                // session drops back to the gate instead of crashing.
                val appContext = applicationContext
                val hasSdCard by remember { SdCardMountState.observe(appContext) }
                    .collectAsState(initial = SdCardDetector.hasValidCard(appContext))

                if (hasSdCard) {
                    DashboardApp()
                } else {
                    SdCardRequiredScreen()
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
