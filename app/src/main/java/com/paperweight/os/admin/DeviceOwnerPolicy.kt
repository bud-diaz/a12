package com.paperweight.os.admin

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.paperweight.os.MainActivity

object DeviceOwnerPolicy {
    fun apply(context: Context) {
        val devicePolicyManager = context.getSystemService(DevicePolicyManager::class.java)
        if (!devicePolicyManager.isDeviceOwnerApp(context.packageName)) return

        val adminComponent = ComponentName(context, PaperweightDeviceAdminReceiver::class.java)
        val homeActivity = ComponentName(context, MainActivity::class.java)

        devicePolicyManager.setLockTaskPackages(adminComponent, lockTaskPackages(context))
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
                Manifest.permission.RECORD_AUDIO,
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED,
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            devicePolicyManager.setPermissionGrantState(
                adminComponent,
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED,
            )
        }
    }

    // Android Settings is an explicit kiosk exception for Wi-Fi/provisioning
    // recovery. SAF/content pickers used by "Add to vault" (ACTION_OPEN_DOCUMENT_TREE,
    // ACTION_OPEN_DOCUMENT) and the legacy artwork-upload flow
    // (ACTION_GET_CONTENT) resolve to a different package than this app —
    // which one varies by OEM/OS version (AOSP's com.android.documentsui,
    // Play-Store-updated com.google.android.documentsui, or Samsung's own
    // My Files) and can change across a system update. Resolving it via
    // PackageManager instead of hardcoding a guess means this stays correct
    // regardless of which picker the device actually ships, and re-resolves
    // every time this runs (every MainActivity.onCreate).
    private fun lockTaskPackages(context: Context): Array<String> {
        val packages = linkedSetOf(context.packageName, SETTINGS_PACKAGE)
        packages += resolveSettingsPackages(context)
        val resolved = resolvePickerPackages(context)
        if (resolved.isEmpty()) {
            Log.w(
                TAG,
                "No package resolved ACTION_OPEN_DOCUMENT_TREE/OPEN_DOCUMENT/GET_CONTENT; " +
                    "SAF pickers will likely fail to launch under lockTask.",
            )
        }
        packages += resolved
        return packages.toTypedArray()
    }

    private fun resolveSettingsPackages(context: Context): Set<String> {
        val packageManager = context.packageManager
        return packageManager.queryIntentActivities(Intent(Settings.ACTION_SETTINGS), 0)
            .map { it.activityInfo.packageName }
            .toSet()
    }

    private fun resolvePickerPackages(context: Context): Set<String> {
        val packageManager = context.packageManager
        val pickerIntents = listOf(
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE),
            Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),
            Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),
        )
        return pickerIntents
            .flatMap { intent -> packageManager.queryIntentActivities(intent, 0) }
            .map { it.activityInfo.packageName }
            .toSet()
    }

    private const val SETTINGS_PACKAGE = "com.android.settings"
    private const val TAG = "DeviceOwnerPolicy"
}
