package com.paperweight.os.pairing

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paperweight.os.MainActivity
import com.paperweight.os.admin.DeviceOwnerPolicy
import com.paperweight.os.ui.theme.PaperweightOSTheme

class PairingActivity : ComponentActivity() {

    private val devicePolicyManager by lazy {
        getSystemService(DevicePolicyManager::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PaperweightOSTheme {
                val viewModel: PairingViewModel = viewModel()
                PairingScreen(
                    viewModel = viewModel,
                    onPaired = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        DeviceOwnerPolicy.apply(this)
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
