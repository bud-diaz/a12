package com.paperweight.os.admin

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.paperweight.os.MainActivity

object DeviceOwnerPolicy {
    fun apply(context: Context) {
        val devicePolicyManager = context.getSystemService(DevicePolicyManager::class.java)
        if (!devicePolicyManager.isDeviceOwnerApp(context.packageName)) return

        val adminComponent = ComponentName(context, PaperweightDeviceAdminReceiver::class.java)
        val homeActivity = ComponentName(context, MainActivity::class.java)

        devicePolicyManager.setLockTaskPackages(adminComponent, arrayOf(context.packageName))
        devicePolicyManager.clearPackagePersistentPreferredActivities(
            adminComponent,
            context.packageName
        )

        val homeFilter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        devicePolicyManager.addPersistentPreferredActivity(
            adminComponent,
            homeFilter,
            homeActivity
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            devicePolicyManager.setKeyguardDisabled(adminComponent, true)
            devicePolicyManager.setPermissionGrantState(
                adminComponent,
                context.packageName,
                Manifest.permission.CAMERA,
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED,
            )
        }
    }
}
