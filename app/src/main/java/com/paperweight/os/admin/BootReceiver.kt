package com.paperweight.os.admin

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.paperweight.os.MainActivity

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in BOOT_ACTIONS) return

        val devicePolicyManager = context.getSystemService(DevicePolicyManager::class.java)
        if (!devicePolicyManager.isDeviceOwnerApp(context.packageName)) return

        DeviceOwnerPolicy.apply(context)

        Handler(Looper.getMainLooper()).postDelayed({
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(launchIntent)
        }, BOOT_LAUNCH_DELAY_MS)
    }

    private companion object {
        const val BOOT_LAUNCH_DELAY_MS = 8_000L
        val BOOT_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
    }
}
