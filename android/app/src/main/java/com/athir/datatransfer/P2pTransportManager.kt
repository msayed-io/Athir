package com.athir.datatransfer

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Router-free transport layer for Athir.
 *
 * Two independent mechanisms are implemented, both running entirely on-device:
 *
 *  1. Wi-Fi Direct (WifiP2pManager): the receiver becomes the group owner and the
 *     sender joins the P2P group. No SSID/passphrase needs to be handled by the app
 *     because the platform performs the join through `connect(p2pConfig)`.
 *  2. Local-only hotspot (WifiManager.startLocalOnlyHotspot, API 26+): the receiver
 *     opens a WPA2 hotspot and its SSID/passphrase are published inside the QR code,
 *     so it also works on hardware where Wi-Fi Direct is unavailable.
 *
 * Every hardware call is wrapped in try/catch and logs with [LOG_TAG] so that a device
 * without the required hardware (or an emulator used by CI) degrades gracefully into a
 * clear failure result instead of crashing the process.
 *
 * Nothing here is simulated: when a capability is unavailable the caller receives
 * `Result.failure` with an explicit reason and the UI reports it as such.
 */
class P2pTransportManager(private val context: Context) {

    enum class TransportType { LAN, WIFI_DIRECT, LOCAL_HOTSPOT }

    data class Credentials(
        val type: TransportType,
        /** Populated for LOCAL_HOTSPOT only. */
        val ssid: String? = null,
        /** Populated for LOCAL_HOTSPOT only. */
        val passphrase: String? = null,
        /** Populated for WIFI_DIRECT (group owner address) and LAN. */
        val host: String? = null,
        /** Populated for WIFI_DIRECT: the group owner P2P device address. */
        val p2pDeviceAddress: String? = null,
        val isGroupOwner: Boolean = false
    )

    private val wifiManager: WifiManager? =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val connectivityManager: ConnectivityManager? =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private var p2pManager: WifiP2pManager? = null
    private var p2pChannel: WifiP2pManager.Channel? = null
    private var p2pReceiver: BroadcastReceiver? = null

    private var hotspotReservation: WifiManager.LocalOnlyHotspotReservation? = null
    private var joinedNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private var joinedLegacyNetworkId: Int = -1

    // --- LOCAL ONLY HOTSPOT (receiver side) ---

    fun startLocalOnlyHotspot(onResult: (Result<Credentials>) -> Unit) {
        val wm = wifiManager
        if (wm == null) {
            onResult(Result.failure(IllegalStateException("WifiManager is unavailable on this device")))
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Log.d(LOG_TAG, "startLocalOnlyHotspot: requires API 26+, device is ${Build.VERSION.SDK_INT}")
            onResult(Result.failure(UnsupportedOperationException("Local-only hotspot requires Android 8.0+")))
            return
        }
        if (!hasWifiPermissions()) {
            onResult(Result.failure(SecurityException("Missing Wi-Fi permissions for the local hotspot")))
            return
        }

        try {
            stopLocalOnlyHotspot()
            Log.d(LOG_TAG, "startLocalOnlyHotspot: requesting reservation")
            wm.startLocalOnlyHotspot(
                object : WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                        hotspotReservation = reservation
                        val cfg = reservation.wifiConfiguration
                        val ssid = cfg?.SSID
                        val psk = cfg?.preSharedKey
                        Log.d(LOG_TAG, "startLocalOnlyHotspot: started ssid=$ssid pskAvailable=${!psk.isNullOrBlank()}")
                        if (ssid.isNullOrBlank() || psk.isNullOrBlank()) {
                            onResult(Result.failure(IllegalStateException("Hotspot started without usable credentials")))
                            return
                        }
                        val host = DiscoveryManager.getLocalIpAddress()
                        onResult(
                            Result.success(
                                Credentials(
                                    type = TransportType.LOCAL_HOTSPOT,
                                    ssid = ssid,
                                    passphrase = psk,
                                    host = host,
                                    isGroupOwner = true
                                )
                            )
                        )
                    }

                    override fun onStopped() {
                        Log.d(LOG_TAG, "startLocalOnlyHotspot: stopped")
                        hotspotReservation = null
                    }

                    override fun onFailed(reason: Int) {
                        Log.d(LOG_TAG, "startLocalOnlyHotspot: failed reason=$reason")
                        onResult(Result.failure(IllegalStateException("Local-only hotspot failed (reason=$reason)")))
                    }
                },
                Handler(Looper.getMainLooper())
            )
        } catch (e: Exception) {
            Log.d(LOG_TAG, "startLocalOnlyHotspot threw: ${e.javaClass.simpleName}: ${e.message}")
            onResult(Result.failure(e))
        }
    }

    fun stopLocalOnlyHotspot() {
        try {
            hotspotReservation?.close()
        } catch (e: Exception) {
            Log.d(LOG_TAG, "stopLocalOnlyHotspot: ${e.message}")
        } finally {
            hotspotReservation = null
        }
    }

    // --- WI-FI DIRECT (receiver becomes group owner) ---

    fun startWifiDirectGroup(onResult: (Result<Credentials>) -> Unit) {
        val pm = context.packageManager
        if (!pm.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)) {
            Log.d(LOG_TAG, "startWifiDirectGroup: FEATURE_WIFI_DIRECT is not present")
            onResult(Result.failure(UnsupportedOperationException("Wi-Fi Direct is not supported by this device")))
            return
        }
        if (!hasWifiPermissions()) {
            onResult(Result.failure(SecurityException("Missing Wi-Fi permissions for Wi-Fi Direct")))
            return
        }

        try {
            val manager = ensureP2pManager() ?: run {
                onResult(Result.failure(IllegalStateException("WifiP2pManager could not be initialised")))
                return
            }
            val channel = p2pChannel ?: run {
                onResult(Result.failure(IllegalStateException("WifiP2p channel is unavailable")))
                return
            }

            registerP2pReceiver(manager, channel)

            manager.createGroup(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(LOG_TAG, "startWifiDirectGroup: group created, requesting group info")
                    requestGroupInfo(manager, channel, onResult)
                }

                override fun onFailure(reason: Int) {
                    Log.d(LOG_TAG, "startWifiDirectGroup: createGroup failed reason=$reason")
                    when (reason) {
                        WifiP2pManager.BUSY -> onResult(Result.failure(IllegalStateException("Wi-Fi Direct is busy")))
                        WifiP2pManager.ERROR -> onResult(Result.failure(IllegalStateException("Wi-Fi Direct internal error")))
                        WifiP2pManager.P2P_UNSUPPORTED -> onResult(
                            Result.failure(UnsupportedOperationException("Wi-Fi Direct is unsupported at runtime"))
                        )
                        else -> onResult(Result.failure(IllegalStateException("Wi-Fi Direct group creation failed ($reason)")))
                    }
                }
            })
        } catch (e: Exception) {
            Log.d(LOG_TAG, "startWifiDirectGroup threw: ${e.javaClass.simpleName}: ${e.message}")
            onResult(Result.failure(e))
        }
    }

    private fun requestGroupInfo(
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel,
        onResult: (Result<Credentials>) -> Unit
    ) {
        try {
            manager.requestGroupInfo(channel) { group: WifiP2pGroup? ->
                val ownerAddress = group?.owner?.deviceAddress
                val host = DiscoveryManager.getLocalIpAddress()
                Log.d(
                    LOG_TAG,
                    "requestGroupInfo: group=${group != null} owner=$ownerAddress network=${group?.networkName} host=$host"
                )
                onResult(
                    Result.success(
                        Credentials(
                            type = TransportType.WIFI_DIRECT,
                            ssid = group?.networkName,
                            passphrase = null,
                            host = host,
                            p2pDeviceAddress = ownerAddress,
                            isGroupOwner = true
                        )
                    )
                )
            }
        } catch (e: Exception) {
            Log.d(LOG_TAG, "requestGroupInfo threw: ${e.message}")
            onResult(Result.failure(e))
        }
    }

    /** Sender side: joins an existing Wi-Fi Direct group by device address. */
    fun connectWifiDirect(deviceAddress: String, onResult: (Result<Credentials>) -> Unit) {
        if (deviceAddress.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Empty Wi-Fi Direct device address")))
            return
        }
        try {
            val manager = ensureP2pManager() ?: run {
                onResult(Result.failure(IllegalStateException("WifiP2pManager could not be initialised")))
                return
            }
            val channel = p2pChannel ?: run {
                onResult(Result.failure(IllegalStateException("WifiP2p channel is unavailable")))
                return
            }
            registerP2pReceiver(manager, channel)

            val config = WifiP2pConfig().apply {
                this.deviceAddress = deviceAddress
                wps.setup = android.net.wifi.WpsInfo.PBC
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    groupOwnerIntent = 0
                }
            }
            manager.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(LOG_TAG, "connectWifiDirect: negotiation started for $deviceAddress")
                    onResult(Result.success(Credentials(type = TransportType.WIFI_DIRECT, p2pDeviceAddress = deviceAddress)))
                }

                override fun onFailure(reason: Int) {
                    Log.d(LOG_TAG, "connectWifiDirect: failed reason=$reason")
                    onResult(Result.failure(IllegalStateException("Wi-Fi Direct connect failed ($reason)")))
                }
            })
        } catch (e: Exception) {
            Log.d(LOG_TAG, "connectWifiDirect threw: ${e.javaClass.simpleName}: ${e.message}")
            onResult(Result.failure(e))
        }
    }

    fun stopWifiDirect() {
        try {
            val manager = p2pManager
            val channel = p2pChannel
            if (manager != null && channel != null) {
                manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        Log.d(LOG_TAG, "stopWifiDirect: group removed")
                    }

                    override fun onFailure(reason: Int) {
                        Log.d(LOG_TAG, "stopWifiDirect: removeGroup failed ($reason)")
                    }
                })
            }
            unregisterP2pReceiver()
        } catch (e: Exception) {
            Log.d(LOG_TAG, "stopWifiDirect threw: ${e.message}")
        }
    }

    // --- SENDER SIDE: join the receiver local-only hotspot ---

    fun joinLocalHotspot(ssid: String, passphrase: String, onResult: (Result<Credentials>) -> Unit) {
        if (ssid.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Empty hotspot SSID")))
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            joinWithNetworkSpecifier(ssid, passphrase, onResult)
        } else {
            joinWithLegacyConfiguration(ssid, passphrase, onResult)
        }
    }

    private fun joinWithNetworkSpecifier(
        ssid: String,
        passphrase: String,
        onResult: (Result<Credentials>) -> Unit
    ) {
        val cm = connectivityManager
        if (cm == null) {
            onResult(Result.failure(IllegalStateException("ConnectivityManager is unavailable")))
            return
        }
        try {
            val specifierBuilder = WifiNetworkSpecifier.Builder().setSsid(ssid)
            if (passphrase.isNotBlank()) {
                specifierBuilder.setWpa2Passphrase(passphrase)
            }
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifierBuilder.build())
                .build()

            disconnectJoinedHotspot()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(LOG_TAG, "joinLocalHotspot: joined ssid=$ssid")
                    onResult(Result.success(Credentials(type = TransportType.LOCAL_HOTSPOT, ssid = ssid)))
                }

                override fun onUnavailable() {
                    Log.d(LOG_TAG, "joinLocalHotspot: network unavailable for ssid=$ssid")
                    onResult(Result.failure(IllegalStateException("Could not join the hotspot")))
                }

                override fun onLost(network: Network) {
                    Log.d(LOG_TAG, "joinLocalHotspot: network lost")
                }
            }
            joinedNetworkCallback = callback
            cm.requestNetwork(request, callback, HOTSPOT_JOIN_TIMEOUT_MS)
        } catch (e: Exception) {
            Log.d(LOG_TAG, "joinWithNetworkSpecifier threw: ${e.javaClass.simpleName}: ${e.message}")
            onResult(Result.failure(e))
        }
    }

    @Suppress("DEPRECATION")
    private fun joinWithLegacyConfiguration(
        ssid: String,
        passphrase: String,
        onResult: (Result<Credentials>) -> Unit
    ) {
        val wm = wifiManager
        if (wm == null) {
            onResult(Result.failure(IllegalStateException("WifiManager is unavailable")))
            return
        }
        try {
            val config = WifiConfiguration().apply {
                SSID = "\"$ssid\""
                preSharedKey = "\"$passphrase\""
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
            }
            val networkId = wm.addNetwork(config)
            if (networkId < 0) {
                onResult(Result.failure(IllegalStateException("Could not add the hotspot configuration")))
                return
            }
            joinedLegacyNetworkId = networkId
            wm.disconnect()
            val enabled = wm.enableNetwork(networkId, true)
            wm.reconnect()
            Log.d(LOG_TAG, "joinLocalHotspot(legacy): networkId=$networkId enabled=$enabled")
            if (!enabled) {
                onResult(Result.failure(IllegalStateException("Could not enable the hotspot configuration")))
                return
            }
            onResult(Result.success(Credentials(type = TransportType.LOCAL_HOTSPOT, ssid = ssid)))
        } catch (e: Exception) {
            Log.d(LOG_TAG, "joinWithLegacyConfiguration threw: ${e.javaClass.simpleName}: ${e.message}")
            onResult(Result.failure(e))
        }
    }

    fun disconnectJoinedHotspot() {
        try {
            joinedNetworkCallback?.let { callback ->
                connectivityManager?.unregisterNetworkCallback(callback)
            }
        } catch (e: Exception) {
            Log.d(LOG_TAG, "disconnectJoinedHotspot(callback): ${e.message}")
        }
        joinedNetworkCallback = null
        if (joinedLegacyNetworkId >= 0) {
            try {
                wifiManager?.removeNetwork(joinedLegacyNetworkId)
            } catch (e: Exception) {
                Log.d(LOG_TAG, "disconnectJoinedHotspot(legacy): ${e.message}")
            }
            joinedLegacyNetworkId = -1
        }
    }

    fun stopAll() {
        stopLocalOnlyHotspot()
        stopWifiDirect()
        disconnectJoinedHotspot()
    }

    // --- helpers ---

    private fun ensureP2pManager(): WifiP2pManager? {
        if (p2pManager != null && p2pChannel != null) return p2pManager
        return try {
            val manager = context.applicationContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
            if (manager == null) {
                Log.d(LOG_TAG, "ensureP2pManager: WIFI_P2P_SERVICE unavailable")
                return null
            }
            val channel = manager.initialize(context.applicationContext, Looper.getMainLooper()) { reason ->
                Log.d(LOG_TAG, "WifiP2p channel disconnected, reason=$reason")
            }
            p2pManager = manager
            p2pChannel = channel
            manager
        } catch (e: Exception) {
            Log.d(LOG_TAG, "ensureP2pManager threw: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    private fun registerP2pReceiver(manager: WifiP2pManager, channel: WifiP2pManager.Channel) {
        if (p2pReceiver != null) return
        try {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    try {
                        when (intent?.action) {
                            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                                val enabled = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                                Log.d(
                                    LOG_TAG,
                                    "P2P state changed: enabled=" +
                                        (enabled == WifiP2pManager.WIFI_P2P_STATE_ENABLED)
                                )
                            }
                            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                                Log.d(LOG_TAG, "P2P connection changed")
                                manager.requestConnectionInfo(channel) { info: WifiP2pInfo? ->
                                    Log.d(
                                        LOG_TAG,
                                        "P2P connection info: formed=${info?.groupFormed} " +
                                            "owner=${info?.isGroupOwner} " +
                                            "ownerAddress=${info?.groupOwnerAddress?.hostAddress}"
                                    )
                                }
                            }
                            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                                @Suppress("DEPRECATION")
                                val device = intent
                                    .getParcelableExtra<WifiP2pDevice>(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                                Log.d(LOG_TAG, "P2P device changed: ${device?.deviceAddress}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(LOG_TAG, "P2P receiver threw: ${e.javaClass.simpleName}: ${e.message}")
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
            }
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            p2pReceiver = receiver
        } catch (e: Exception) {
            Log.d(LOG_TAG, "registerP2pReceiver threw: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun unregisterP2pReceiver() {
        try {
            p2pReceiver?.let { context.unregisterReceiver(it) }
        } catch (e: Exception) {
            Log.d(LOG_TAG, "unregisterP2pReceiver: ${e.message}")
        } finally {
            p2pReceiver = null
        }
    }

    private fun hasWifiPermissions(): Boolean {
        val required = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            required.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            required.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return required.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    companion object {
        const val LOG_TAG = "AthirP2p"
        private const val HOTSPOT_JOIN_TIMEOUT_MS = 30_000
    }
}
