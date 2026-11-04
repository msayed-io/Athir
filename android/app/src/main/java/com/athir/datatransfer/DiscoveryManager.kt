package com.athir.datatransfer

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.net.Inet4Address
import java.net.NetworkInterface

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
        val secret: String
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
        fingerprint: String,
        secret: String
    ) {
        if (isAdvertising) stopAdvertising()

        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = serviceName
            this.serviceType = SERVICE_TYPE
            this.port = port
            setAttribute("id", peerId)
            setAttribute("fp", fingerprint)
            setAttribute("sc", secret)
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

                                val attrs = serviceInfo.attributes
                                val peerId = attrs["id"]?.let { String(it) } ?: peerName
                                val fp = attrs["fp"]?.let { String(it) } ?: ""
                                val sc = attrs["sc"]?.let { String(it) } ?: ""

                                listener.onPeerFound(
                                    DiscoveredPeer(
                                        peerId = peerId,
                                        peerName = peerName,
                                        host = host,
                                        port = port,
                                        fingerprint = fp,
                                        secret = sc
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

        fun getLocalIpAddress(): String {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val intf = interfaces.nextElement()
                    if (intf.isLoopback || !intf.isUp) continue
                    val addrs = intf.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            return addr.hostAddress ?: "127.0.0.1"
                        }
                    }
                }
            } catch (ignored: Exception) {}
            return "127.0.0.1"
        }
    }
}
