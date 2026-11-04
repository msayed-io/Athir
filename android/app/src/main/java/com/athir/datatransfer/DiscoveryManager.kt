package com.athir.datatransfer

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Local network discovery via Android Network Service Discovery (mDNS / DNS-SD).
 *
 * SECURITY NOTE (see docs/NATIVE_REAL_TRANSFER_REPORT.md):
 * The session secret is NEVER advertised here. mDNS TXT records are broadcast in
 * clear text on the local network and can be read by any device (or any service
 * browser app). Advertising the pairing secret there would defeat the entire
 * authentication model. Only non-sensitive routing metadata is published:
 *   - id : random per-install device id (no personal data)
 *   - fp : SHA-256 fingerprint of the TLS certificate (public by design)
 *   - v  : discovery protocol version
 * The pairing secret reaches the sender exclusively over the out-of-band QR code.
 * A peer found through mDNS therefore requires a QR scan before a transfer can start.
 */
class DiscoveryManager(private val context: Context) {

    interface PeerDiscoveryListener {
        fun onPeerFound(peer: DiscoveredPeer)
        fun onPeerLost(peerId: String)
        fun onDiscoveryError(error: String)
    }

    data class DiscoveredPeer(
        val peerId: String,
        val peerName: String,
        val host: String,
        val port: Int,
        val fingerprint: String,
        /** Always empty for mDNS discovery: the secret is only delivered through the QR code. */
        val secret: String,
        val source: String
    )

    private val nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private var isAdvertising = false
    private var isDiscovering = false

    fun startAdvertising(
        serviceName: String,
        port: Int,
        peerId: String,
        fingerprint: String
    ) {
        if (isAdvertising) stopAdvertising()

        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = serviceName
            this.serviceType = SERVICE_TYPE
            this.port = port
            setAttribute("id", peerId)
            setAttribute("fp", fingerprint)
            setAttribute("v", DISCOVERY_PROTOCOL_VERSION.toString())
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo?) {
                isAdvertising = true
            }

            override fun onRegistrationFailed(info: NsdServiceInfo?, errorCode: Int) {
                isAdvertising = false
            }

            override fun onServiceUnregistered(info: NsdServiceInfo?) {
                isAdvertising = false
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo?, errorCode: Int) {
                isAdvertising = false
            }
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            isAdvertising = false
        }
    }

    fun stopAdvertising() {
        if (isAdvertising && registrationListener != null) {
            try {
                nsdManager.unregisterService(registrationListener)
            } catch (ignored: Exception) {}
            isAdvertising = false
            registrationListener = null
        }
    }

    fun startDiscovery(listener: PeerDiscoveryListener) {
        if (isDiscovering) stopDiscovery()

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String?) {
                isDiscovering = true
            }

            override fun onServiceFound(service: NsdServiceInfo?) {
                if (service == null) return
                if (service.serviceType == SERVICE_TYPE || service.serviceType == "$SERVICE_TYPE.") {
                    try {
                        nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {}

                            override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
                                if (serviceInfo == null) return
                                val host = serviceInfo.host?.hostAddress ?: return
                                val port = serviceInfo.port
                                val peerName = serviceInfo.serviceName

                                val attrs = serviceInfo.attributes ?: emptyMap()
                                val peerId = attrs["id"]?.let { String(it, Charsets.UTF_8) } ?: peerName
                                // The certificate fingerprint is public information (it is also
                                // stored inside the QR code), and it is required to decide whether
                                // a later QR scan matches the advertised device.
                                val fp = attrs["fp"]?.let { String(it, Charsets.UTF_8) } ?: ""

                                listener.onPeerFound(
                                    DiscoveredPeer(
                                        peerId = peerId,
                                        peerName = peerName,
                                        host = host,
                                        port = port,
                                        fingerprint = fp,
                                        secret = "",
                                        source = SOURCE_MDNS
                                    )
                                )
                            }
                        })
                    } catch (ignored: Exception) {}
                }
            }

            override fun onServiceLost(service: NsdServiceInfo?) {
                if (service != null) {
                    listener.onPeerLost(service.serviceName)
                }
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                isDiscovering = false
            }

            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                isDiscovering = false
                listener.onDiscoveryError("NSD start failed with code: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                isDiscovering = false
            }
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            isDiscovering = false
            listener.onDiscoveryError("Failed to start discovery: ${e.message}")
        }
    }

    fun stopDiscovery() {
        if (isDiscovering && discoveryListener != null) {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
            } catch (ignored: Exception) {}
            isDiscovering = false
            discoveryListener = null
        }
    }

    companion object {
        const val SERVICE_TYPE = "_athir-p2p._tcp."
        const val SOURCE_MDNS = "mdns"
        const val SOURCE_QR = "qr"
        const val DISCOVERY_PROTOCOL_VERSION = 1

        /**
         * Returns the best local IPv4 address for LAN transfers.
         * Wi-Fi interfaces are preferred so that a cellular or VPN address
         * is never published in the QR code.
         */
        fun getLocalIpAddress(): String {
            val candidates = mutableListOf<Pair<String, String>>()
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val intf = interfaces.nextElement()
                    if (intf.isLoopback || !intf.isUp) continue
                    val name = intf.name ?: ""
                    val addrs = intf.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        if (!addr.isLoopbackAddress && addr is Inet4Address && !addr.isLinkLocalAddress) {
                            candidates.add(Pair(name, addr.hostAddress ?: ""))
                        }
                    }
                }
            } catch (ignored: Exception) {}

            if (candidates.isEmpty()) return "127.0.0.1"

            // Prefer Wi-Fi / hotspot / softap interfaces over cellular or VPN tunnels.
            val preferredPrefixes = listOf("wlan", "ap", "wifi", "swlan", "eth")
            val blockedPrefixes = listOf("tun", "ppp", "rmnet", "ccmni", "clat", "dummy")
            candidates.firstOrNull { (name, _) ->
                preferredPrefixes.any { name.startsWith(it, ignoreCase = true) }
            }?.let { return it.second }
            candidates.firstOrNull { (name, _) ->
                blockedPrefixes.none { name.startsWith(it, ignoreCase = true) }
            }?.let { return it.second }
            return candidates.first().second
        }

        /** Real hardware capability checks, no hardcoded assumptions. */
        fun isWifiDirectSupported(context: Context): Boolean {
            return context.packageManager.hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_WIFI_DIRECT
            )
        }

        fun isWifiAwareSupported(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            return context.packageManager.hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_WIFI_AWARE
            )
        }

        fun isHotspotSupported(context: Context): Boolean {
            // WifiManager.startLocalOnlyHotspot (the only hotspot API available to a
            // third-party app) exists from Android 8.0 (API 26); below that there is no
            // supported way to create a hotspot without system privileges.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            return context.applicationContext.getSystemService(Context.WIFI_SERVICE) is android.net.wifi.WifiManager
        }
    }
}
