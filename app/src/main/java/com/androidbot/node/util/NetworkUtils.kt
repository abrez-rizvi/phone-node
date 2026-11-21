package com.androidbot.node.util

import java.net.Inet4Address
import java.net.NetworkInterface

data class NetworkStatus(
    val ipAddress: String,
    val networkType: String,
    val isCellular: Boolean,
    val advice: String
)

object NetworkUtils {

    fun getNetworkStatus(): NetworkStatus {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return NetworkStatus(
                ipAddress = "127.0.0.1",
                networkType = "Loopback",
                isCellular = false,
                advice = "Localhost only"
            )

            var wifiIp: String? = null
            var cellularIp: String? = null
            var otherIp: String? = null

            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue

                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val hostAddress = addr.hostAddress ?: continue
                        val name = intf.name.lowercase()
                        when {
                            name.contains("wlan") || name.contains("ap") || name.contains("eth") -> {
                                if (wifiIp == null) wifiIp = hostAddress
                            }
                            name.contains("rmnet") || name.contains("ccmni") || name.contains("pdp") -> {
                                if (cellularIp == null) cellularIp = hostAddress
                            }
                            else -> {
                                if (otherIp == null) otherIp = hostAddress
                            }
                        }
                    }
                }
            }

            if (wifiIp != null) {
                return NetworkStatus(
                    ipAddress = wifiIp,
                    networkType = "Wi-Fi LAN",
                    isCellular = false,
                    advice = "Directly reachable on same local Wi-Fi subnet or via Cloudflare Tunnel."
                )
            } else if (cellularIp != null) {
                return NetworkStatus(
                    ipAddress = cellularIp,
                    networkType = "Mobile Data (5G/4G CGNAT)",
                    isCellular = true,
                    advice = "Inbound ports blocked by cellular carrier (CGNAT). Use Cloudflare Tunnel or USB bridge."
                )
            } else if (otherIp != null) {
                return NetworkStatus(
                    ipAddress = otherIp,
                    networkType = "Active Interface",
                    isCellular = false,
                    advice = "Connected via local network interface."
                )
            }
        } catch (_: Exception) {
            // Fallback
        }
        return NetworkStatus(
            ipAddress = "127.0.0.1",
            networkType = "Loopback (Offline)",
            isCellular = false,
            advice = "Connect to Wi-Fi or turn on Mobile Data with Tunnel."
        )
    }

    fun getLocalIpAddress(): String {
        return getNetworkStatus().ipAddress
    }
}
