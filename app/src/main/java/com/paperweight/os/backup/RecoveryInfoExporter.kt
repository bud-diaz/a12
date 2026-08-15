package com.paperweight.os.backup

import com.paperweight.os.data.prefs.SecurePreferences

/**
 * One-time on-screen reveal of secrets that don't round-trip through the
 * automatic SD-card backup — the Android Keystore key backing
 * [SecurePreferences] does not survive a reinstall/factory reset, so this is
 * the operator's only way to note them down externally for a legitimate
 * re-provisioning (plan decision #12). Phase 3 built this as a placeholder
 * before any real secret existed; Phase 9's frp registration secret/auth
 * token are the first real content it has to show.
 */
object RecoveryInfoExporter {
    fun message(securePreferences: SecurePreferences): String {
        val secret = securePreferences.registrationSecret
        val authToken = securePreferences.frpAuthToken
        if (secret == null && authToken == null) {
            return "Automatic backups restore only non-secret device config and the local database. " +
                "Once you register a public frp tunnel from Station, its registration secret and auth " +
                "token will appear here — write them down externally, because Android Keystore keys " +
                "do not survive reinstall/factory reset and these will not come back with a normal restore."
        }
        return buildString {
            appendLine("These do NOT survive reinstall/factory reset — automatic backups exclude them by design.")
            appendLine("Write them down externally before any real re-provisioning.")
            appendLine()
            secret?.let { appendLine("system.pape registration secret: $it") }
            authToken?.let { appendLine("frp auth token: $it") }
        }.trim()
    }
}
