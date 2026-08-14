package com.paperweight.os

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.paperweight.os.network.SessionStore
import com.paperweight.os.pairing.PairingActivity
import com.paperweight.os.provisioning.SetupActivity
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

        if (!SessionStore(applicationContext).isPaired) {
            startActivity(Intent(this, PairingActivity::class.java))
            finish()
            return
        }

        setContent {
            PaperweightOSTheme {
                DashboardPlaceholder()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (devicePolicyManager.isDeviceOwnerApp(packageName) && !isInLockTaskMode()) {
            startLockTask()
        }
    }

    private fun isInLockTaskMode(): Boolean {
        val activityManager = getSystemService(ActivityManager::class.java)
        return activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }
}

// Stands in for the nav shell + 9 dashboard screens (Milestones 3-4).
@Composable
private fun DashboardPlaceholder() {
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.dashboard_placeholder),
            modifier = Modifier.align(Alignment.Center)
        )
    }
}
