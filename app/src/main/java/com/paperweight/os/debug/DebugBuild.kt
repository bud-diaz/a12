package com.paperweight.os.debug

import android.content.Context
import android.content.pm.ApplicationInfo

object DebugBuild {
    fun isDebuggable(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
