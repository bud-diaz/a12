package com.paperweight.os.admin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.paperweight.os.MainActivity
import com.paperweight.os.R

class PaperweightDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)

        val devicePolicyManager = context.getSystemService(DevicePolicyManager::class.java)
        val adminComponent = ComponentName(context, PaperweightDeviceAdminReceiver::class.java)

        if (!devicePolicyManager.isDeviceOwnerApp(context.packageName)) return

        devicePolicyManager.setLockTaskPackages(adminComponent, arrayOf(context.packageName))

        val homeFilter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        devicePolicyManager.addPersistentPreferredActivity(
            adminComponent,
            homeFilter,
            ComponentName(context, MainActivity::class.java)
        )
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return context.getString(R.string.device_admin_disable_warning)
    }

    override fun onLockTaskModeEntering(context: Context, intent: Intent, pkg: String) {
        super.onLockTaskModeEntering(context, intent, pkg)
    }

    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        super.onLockTaskModeExiting(context, intent)
    }
}
