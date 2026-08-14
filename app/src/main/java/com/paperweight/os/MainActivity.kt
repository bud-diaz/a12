package com.paperweight.os

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.paperweight.os.network.SessionStore
import com.paperweight.os.pairing.PairingActivity
import com.paperweight.os.provisioning.SetupActivity
import com.paperweight.os.ui.nav.DashboardApp
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
                DashboardApp()
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
