package com.paperweight.os.backup

object RecoveryInfoExporter {
    fun message(): String =
        "Automatic backups restore only non-secret device config and the local database. " +
            "Future frp/reachability secrets will need to be written down or exported separately because Android Keystore keys do not survive reinstall/factory reset."
}
