package com.androidbot.node.util

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            for (intf in interfaces) {
                // Ignore loopback and inactive interfaces
                if (intf.isLoopback || !intf.isUp) continue

                val addresses = intf.inetAddresses
                for (addr in addresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val hostAddress = addr.hostAddress ?: continue
                        // Prioritize Wi-Fi and hotspot interfaces
                        if (intf.name.contains("wlan", ignoreCase = true) ||
                            intf.name.contains("ap", ignoreCase = true) ||
                            intf.name.contains("eth", ignoreCase = true)
                        ) {
                            return hostAddress
                        }
                    }
                }
            }

            // Fallback: return first non-loopback IPv4
            val allInterfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            for (intf in allInterfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (_: Exception) {
            // Ignored
        }
        return "127.0.0.1"
    }
}
