package com.paperweight.os.server

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import java.net.Inet4Address

/** Resolves this device's current LAN IPv4 address, for building a listener-facing LAN URL. */
object LanAddress {
    fun currentIpv4(context: Context): String? {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val activeNetwork = connectivityManager.activeNetwork ?: return null
        val linkProperties = connectivityManager.getLinkProperties(activeNetwork) ?: return null
        return linkProperties.linkAddresses
            .mapNotNull(LinkAddress::getAddress)
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    }
}
