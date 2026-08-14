package com.paperweight.os.admin

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

        devicePolicyManager.setLockTaskPackages(
            adminComponent,
            arrayOf(
                context.packageName,
                SETTINGS_PACKAGE,
            )
        )
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
            // RECORD_AUDIO/POST_NOTIFICATIONS silent grants land alongside the
            // broadcast engine (mic capture) and its foreground service in
            // later phases, once those permissions are actually declared in
            // the manifest — mirroring how the removed CAMERA grant used to
            // work here for the QR pairing flow.
        }
    }

    private const val SETTINGS_PACKAGE = "com.android.settings"
}
