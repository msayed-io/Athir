package com.athir.datatransfer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.result.ActivityResult
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import org.json.JSONObject
import java.net.ServerSocket
import java.security.KeyPair
import java.security.cert.X509Certificate
import kotlin.concurrent.thread

@CapacitorPlugin(
    name = "AthirNative",
    permissions = [
        Permission(strings = [android.Manifest.permission.CAMERA], alias = "camera")
    ]
)
class AthirNativePlugin : Plugin() {

    private var transferEngine: TransferEngine? = null
    private var discoveryManager: DiscoveryManager? = null
    private var scannerEngine: ScannerEngine? = null
    private var p2pTransport: P2pTransportManager? = null

    /** Live receive session; the QR payload is regenerated when a P2P transport starts. */
    private var activeSession: JSONObject? = null

    private var activeKeyPair: KeyPair? = null
    private var activeCert: X509Certificate? = null
    private var activeSessionSecret: String = ""
    private var activeServerPort: Int = 0

    override fun load() {
        super.load()
        liveInstance = this
        transferEngine = TransferEngine(context)
        discoveryManager = DiscoveryManager(context)
        scannerEngine = ScannerEngine(context)
        p2pTransport = P2pTransportManager(context)
    }

    @PluginMethod
    fun getDeviceInfo(call: PluginCall) {
        val ret = JSObject().apply {
            put("deviceId", IdentityStore.getDeviceId(context))
            put("displayName", IdentityStore.getDisplayName(context))
            put("androidVersion", Build.VERSION.RELEASE ?: "Android")
            put("sdkInt", Build.VERSION.SDK_INT)
            put("localIp", DiscoveryManager.getLocalIpAddress())
            put("wifiDirectSupported", DiscoveryManager.isWifiDirectSupported(context))
            put("hotspotSupported", DiscoveryManager.isHotspotSupported(context))
            put("wifiAwareSupported", DiscoveryManager.isWifiAwareSupported(context))
        }
        call.resolve(ret)
    }

    @PluginMethod
    fun setDisplayName(call: PluginCall) {
        val name = call.getString("name") ?: ""
        if (name.isNotBlank()) {
            IdentityStore.setDisplayName(context, name)
            call.resolve(JSObject().put("success", true).put("name", name))
        } else {
            call.reject("Display name cannot be empty")
        }
    }

    @PluginMethod
    override fun checkPermissions(call: PluginCall) {
        call.resolve(buildPermissionStatusObject())
    }

    @PluginMethod
    override fun requestPermissions(call: PluginCall) {
        val missing = PermissionCoordinator.getMissingPermissions(context)
        if (missing.isEmpty()) {
            checkPermissions(call)
            return
        }

        // Request all missing permissions directly on the activity. The authoritative
        // answer arrives asynchronously in handleRequestPermissionsResult below, which
        // emits a "permissionsChanged" event with the real, post-dialog status.
        val act = activity
        if (act == null) {
            call.reject("Activity is not available")
            return
        }
        act.requestPermissions(missing.toTypedArray(), REQUEST_CODE_PERMISSIONS)
        call.resolve(JSObject().put("requested", true).put("code", REQUEST_CODE_PERMISSIONS))
    }

    /**
     * Called from MainActivity.onRequestPermissionsResult (see MainActivity.java) as soon as
     * the system permission dialog is answered, so the UI can publish the real, post-dialog
     * status instead of assuming a grant. MainActivity is used because Capacitor 8 only
     * forwards that callback to legacy plugins; a registered @CapacitorPlugin never sees it.
     */
    internal fun publishPermissionState(requestCode: Int) {
        if (requestCode != REQUEST_CODE_PERMISSIONS) return
        notifyListeners("permissionsChanged", buildPermissionStatusObject())
    }

    private fun buildPermissionStatusObject(): JSObject {
        val status = PermissionCoordinator.checkAllPermissions(context)
        return JSObject().apply {
            put("mediaGranted", status.mediaGranted)
            put("nearbyGranted", status.nearbyGranted)
            put("cameraGranted", status.cameraGranted)
            put("notificationGranted", status.notificationGranted)
            put("contactsGranted", status.contactsGranted)
            put("allGranted", status.allGranted)
            put("sdkInt", status.sdkInt)
        }
    }


    @PluginMethod
    fun getMediaCatalog(call: PluginCall) {
        val category = call.getString("category") ?: "apps"
        val query = call.getString("query")
        val limit = call.getInt("limit") ?: 100

        thread {
            // Cached dispatcher: re-entering the send screen no longer re-queries
            // MediaStore and re-decodes every thumbnail.
            val items: List<MediaCatalog.CatalogItem> =
                MediaCatalog.query(context, category, query, limit)

            val jsArray = JSArray()
            for (item in items) {
                val obj = JSObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("sizeMB", item.sizeMB)
                    put("sizeBytes", item.sizeBytes)
                    put("uri", item.uri)
                    put("mimeType", item.mimeType)
                    put("category", item.category)
                    item.thumbnail?.let { put("thumbnail", it) }
                    item.extra?.let { put("extra", it) }
                }
                jsArray.put(obj)
            }

            val ret = JSObject().apply {
                put("category", category)
                put("items", jsArray)
                put("count", items.size)
            }
            call.resolve(ret)
        }
    }

    @PluginMethod
    fun updateSelection(call: PluginCall) {
        val action = call.getString("action") ?: "toggle"
        val id = call.getString("id") ?: ""
        val name = call.getString("name") ?: ""
        val sizeMB = call.getDouble("sizeMB") ?: 0.0
        val sizeBytes = call.getLong("sizeBytes") ?: 0L
        val uri = call.getString("uri") ?: ""
        val mimeType = call.getString("mimeType") ?: "*/*"

        val item = SelectionStore.SelectedItem(id, name, sizeMB, sizeBytes, uri, mimeType)

        val isSelected = when (action) {
            "add" -> {
                SelectionStore.add(item)
                true
            }
            "remove" -> {
                SelectionStore.remove(id)
                false
            }
            else -> {
                SelectionStore.toggle(item)
            }
        }

        val ret = JSObject().apply {
            put("id", id)
            put("isSelected", isSelected)
            put("totalCount", SelectionStore.getCount())
            put("totalMB", SelectionStore.getTotalMB())
            put("totalBytes", SelectionStore.getTotalBytes())
        }
        call.resolve(ret)
    }

    @PluginMethod
    fun getSelection(call: PluginCall) {
        val items = SelectionStore.getAll()
        val jsArray = JSArray()
        for (item in items) {
            jsArray.put(JSObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("sizeMB", item.sizeMB)
                put("sizeBytes", item.sizeBytes)
                put("uri", item.uri)
                put("mimeType", item.mimeType)
            })
        }
        val ret = JSObject().apply {
            put("items", jsArray)
            put("totalCount", SelectionStore.getCount())
            put("totalMB", SelectionStore.getTotalMB())
            put("totalBytes", SelectionStore.getTotalBytes())
        }
        call.resolve(ret)
    }

    @PluginMethod
    fun clearSelection(call: PluginCall) {
        SelectionStore.clear()
        call.resolve(JSObject().put("success", true))
    }

    @PluginMethod
    fun startReceiving(call: PluginCall) {
        thread {
            try {
                // Find random available port
                val tempSocket = ServerSocket(0)
                activeServerPort = tempSocket.localPort
                tempSocket.close()

                // Generate ephemeral TLS keypair and self-signed certificate
                val startedAt = System.currentTimeMillis()
                val kp = SessionSecurity.generateKeyPair()
                val cert = SessionSecurity.generateSelfSignedCertificate(kp)
                val secret = SessionSecurity.generateSessionSecret()
                val fp = SessionSecurity.getCertificateFingerprint(cert)
                // Timed so a device log can tell whether a slow QR is crypto or encoding.
                Log.d("AthirQr", "keypair+certificate in ${System.currentTimeMillis() - startedAt}ms")

                activeKeyPair = kp
                activeCert = cert
                activeSessionSecret = secret

                val deviceId = IdentityStore.getDeviceId(context)
                val displayName = IdentityStore.getDisplayName(context)
                val localIp = DiscoveryManager.getLocalIpAddress()

                // Prepare QR payload
                val qrObj = JSONObject().apply {
                    put("v", 1)
                    put("id", deviceId)
                    put("name", displayName)
                    put("host", localIp)
                    put("port", activeServerPort)
                    put("fp", fp)
                    put("secret", secret)
                    put("transport", "lan")
                }
                val qrString = qrObj.toString()
                activeSession = qrObj
                val qrStartedAt = System.currentTimeMillis()
                val qrDataUrl = QrEncoder.generateQrDataUrl(qrString, 512)
                Log.d(
                    "AthirQr",
                    "payload=${qrString.length}B encoded in ${System.currentTimeMillis() - qrStartedAt}ms"
                )

                // Start advertising via mDNS
                // The session secret is intentionally NOT advertised over mDNS.
                discoveryManager?.startAdvertising(displayName, activeServerPort, deviceId, fp)

                // Start listening via TLS ServerSocket in background thread
                thread(name = "Athir-ReceiverThread") {
                    val sslContext = SessionSecurity.createServerSslContext(kp, cert)
                    transferEngine?.startReceiverServer(
                        activeServerPort,
                        sslContext,
                        secret,
                        createTransferListener()
                    )
                }

                val ret = JSObject().apply {
                    put("deviceId", deviceId)
                    put("displayName", displayName)
                    put("host", localIp)
                    put("port", activeServerPort)
                    put("fingerprint", fp)
                    put("secret", secret)
                    put("qrPayload", qrString)
                    put("qrDataUrl", qrDataUrl)
                }
                call.resolve(ret)
            } catch (e: Exception) {
                call.reject("Failed to start receiving: ${e.message}")
            }
        }
    }

    @PluginMethod
    fun stopReceiving(call: PluginCall) {
        discoveryManager?.stopAdvertising()
        transferEngine?.cancel()
        p2pTransport?.stopAll()
        activeSession = null
        call.resolve(JSObject().put("success", true))
    }

    // ------------------------------------------------------------------
    // Router-free transports (Wi-Fi Direct / local-only hotspot)
    // ------------------------------------------------------------------

    /**
     * Called by the receiving screen when the user asks for a transfer without a router.
     * Tries a local-only hotspot first (it yields SSID + WPA2 passphrase that can travel
     * inside the QR code), then falls back to Wi-Fi Direct group ownership.
     */
    @PluginMethod
    fun startOfflineTransport(call: PluginCall) {
        val session = activeSession
        if (session == null) {
            call.reject("Start receiving before requesting an offline transport")
            return
        }
        val manager = p2pTransport
        if (manager == null) {
            call.reject("Offline transport is unavailable")
            return
        }

        thread(name = "Athir-OfflineTransport") {
            try {
                manager.startLocalOnlyHotspot { hotspot ->
                    if (hotspot.isSuccess) {
                        val c = hotspot.getOrThrow()
                        Log.d(P2pTransportManager.LOG_TAG, "offline transport = local hotspot")
                        resolveOfflineTransport(call, session, "hotspot", c.ssid, c.passphrase, c.host, null)
                        return@startLocalOnlyHotspot
                    }
                    Log.d(
                        P2pTransportManager.LOG_TAG,
                        "local hotspot unavailable (${hotspot.exceptionOrNull()?.message}), falling back to Wi-Fi Direct"
                    )
                    manager.startWifiDirectGroup { p2p ->
                        if (p2p.isSuccess) {
                            val c = p2p.getOrThrow()
                            Log.d(P2pTransportManager.LOG_TAG, "offline transport = wifi direct")
                            resolveOfflineTransport(
                                call, session, "p2p", c.ssid, null, c.host, c.p2pDeviceAddress
                            )
                        } else {
                            val reason = p2p.exceptionOrNull()?.message ?: "unknown"
                            call.reject("NO_OFFLINE_TRANSPORT: $reason")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(P2pTransportManager.LOG_TAG, "startOfflineTransport threw: ${e.message}")
                call.reject("Failed to start offline transport: ${e.message}")
            }
        }
    }

    private fun resolveOfflineTransport(
        call: PluginCall,
        session: JSONObject,
        transport: String,
        ssid: String?,
        passphrase: String?,
        host: String?,
        p2pDeviceAddress: String?
    ) {
        try {
            val effectiveHost = if (!host.isNullOrBlank() && host != "127.0.0.1") host else HOTSPOT_DEFAULT_GATEWAY
            session.put("transport", transport)
            session.put("host", effectiveHost)
            if (!ssid.isNullOrBlank()) session.put("ssid", ssid)
            if (!passphrase.isNullOrBlank()) session.put("psk", passphrase)
            if (!p2pDeviceAddress.isNullOrBlank()) session.put("p2pAddr", p2pDeviceAddress)

            val payload = session.toString()
            val qrDataUrl = QrEncoder.generateQrDataUrl(payload, 512)

            val ret = JSObject().apply {
                put("transport", transport)
                put("ssid", ssid ?: "")
                put("passphrase", passphrase ?: "")
                put("host", effectiveHost)
                put("p2pDeviceAddress", p2pDeviceAddress ?: "")
                put("qrPayload", payload)
                put("qrDataUrl", qrDataUrl)
            }
            call.resolve(ret)
        } catch (e: Exception) {
            call.reject("Failed to publish offline transport: ${e.message}")
        }
    }

    @PluginMethod
    fun stopOfflineTransport(call: PluginCall) {
        p2pTransport?.stopAll()
        activeSession?.let {
            try {
                it.put("transport", "lan")
                it.remove("ssid")
                it.remove("psk")
                it.remove("p2pAddr")
            } catch (ignored: Exception) {}
        }
        call.resolve(JSObject().put("success", true))
    }

    /** Called by the sending device after scanning a QR that carries offline credentials. */
    @PluginMethod
    fun joinOfflineTransport(call: PluginCall) {
        val manager = p2pTransport
        if (manager == null) {
            call.reject("Offline transport is unavailable")
            return
        }
        val ssid = call.getString("ssid") ?: ""
        val psk = call.getString("psk") ?: ""
        val p2pAddress = call.getString("p2pDeviceAddress") ?: ""

        thread(name = "Athir-JoinTransport") {
            try {
                if (p2pAddress.isNotBlank()) {
                    manager.connectWifiDirect(p2pAddress) { result ->
                        if (result.isSuccess) {
                            call.resolve(JSObject().put("joined", true).put("transport", "p2p"))
                        } else {
                            call.reject("P2P_JOIN_FAILED: ${result.exceptionOrNull()?.message}")
                        }
                    }
                } else if (ssid.isNotBlank()) {
                    manager.joinLocalHotspot(ssid, psk) { result ->
                        if (result.isSuccess) {
                            call.resolve(JSObject().put("joined", true).put("transport", "hotspot"))
                        } else {
                            call.reject("HOTSPOT_JOIN_FAILED: ${result.exceptionOrNull()?.message}")
                        }
                    }
                } else {
                    call.reject("No offline transport credentials were provided")
                }
            } catch (e: Exception) {
                Log.d(P2pTransportManager.LOG_TAG, "joinOfflineTransport threw: ${e.message}")
                call.reject("Failed to join offline transport: ${e.message}")
            }
        }
    }

    // ------------------------------------------------------------------
    // Invite: send Athir itself to someone who does not have it
    // ------------------------------------------------------------------

    /**
     * Real share sheet with the installed APK attached.
     *
     * The invite screen used to call `navigator.share`, which the Android WebView does not
     * implement, and then showed a fake "sharing via X" toast - nothing ever left the app.
     * This sends the real package file through the system chooser (WhatsApp, Bluetooth,
     * Nearby Share, mail, ...) and grants read access to that one uri only.
     */
    @PluginMethod
    fun shareApp(call: PluginCall) {
        try {
            val apk = java.io.File(context.applicationInfo.sourceDir)
            val intent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_SUBJECT, INVITE_SUBJECT)
                putExtra(Intent.EXTRA_TEXT, INVITE_TEXT)
            }

            var attached = false
            if (apk.exists()) {
                try {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        apk
                    )
                    intent.type = "application/vnd.android.package-archive"
                    intent.putExtra(Intent.EXTRA_STREAM, uri)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    attached = true
                } catch (e: Exception) {
                    Log.d("AthirShare", "APK attach failed: ${e.message}")
                }
            }
            if (!attached) {
                intent.type = "text/plain"
            }

            val chooser = Intent.createChooser(intent, INVITE_TITLE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val host = activity
            if (host != null) host.startActivity(chooser) else context.startActivity(chooser)
            call.resolve(JSObject().put("shared", true).put("apkAttached", attached))
        } catch (e: Exception) {
            Log.d("AthirShare", "shareApp failed: ${e.message}")
            call.reject("SHARE_FAILED: ${e.message}")
        }
    }

    // ------------------------------------------------------------------
    // Storage Access Framework document picker
    // ------------------------------------------------------------------

    @PluginMethod
    fun pickDocuments(call: PluginCall) {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
            }
            startActivityForResult(call, intent, "documentPickerCallback")
        } catch (e: Exception) {
            Log.d("AthirPicker", "pickDocuments threw: ${e.message}")
            call.reject("Failed to open the document picker: ${e.message}")
        }
    }

    @ActivityCallback
    private fun documentPickerCallback(call: PluginCall?, result: ActivityResult) {
        if (call == null) return
        if (result.resultCode != Activity.RESULT_OK) {
            call.reject("PICKER_CANCELLED")
            return
        }
        val data = result.data
        if (data == null) {
            call.reject("The document picker returned no data")
            return
        }

        val uris = mutableListOf<Uri>()
        try {
            data.clipData?.let { clip ->
                for (index in 0 until clip.itemCount) uris.add(clip.getItemAt(index).uri)
            }
            data.data?.let { uris.add(it) }
        } catch (e: Exception) {
            Log.d("AthirPicker", "reading picker result threw: ${e.message}")
        }

        if (uris.isEmpty()) {
            call.resolve(JSObject().put("items", JSArray()).put("count", 0))
            return
        }

        val items = JSArray()
        for (uri in uris) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (ignored: Exception) {}
            val described = describeUri(uri)
            items.put(
                JSObject().apply {
                    put("id", "saf:${uri.toString().hashCode()}")
                    put("name", described.first)
                    put("sizeBytes", described.second)
                    put("sizeMB", Math.round((described.second / (1024.0 * 1024.0)) * 10.0) / 10.0)
                    put("uri", uri.toString())
                    put("mimeType", context.contentResolver.getType(uri) ?: "*/*")
                    put("category", "files")
                }
            )
        }
        call.resolve(JSObject().put("items", items).put("count", uris.size))
    }

    private fun describeUri(uri: Uri): Pair<String, Long> {
        var name = "athir_document"
        var size = 0L
        try {
            context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0) cursor.getString(nameIndex)?.let { name = it }
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        } catch (e: Exception) {
            Log.d("AthirPicker", "describeUri threw: ${e.message}")
        }
        return Pair(name, size)
    }

    @PluginMethod
    fun startDiscovery(call: PluginCall) {
        discoveryManager?.startDiscovery(object : DiscoveryManager.PeerDiscoveryListener {
            override fun onPeerFound(peer: DiscoveryManager.DiscoveredPeer) {
                val data = JSObject().apply {
                    put("peerId", peer.peerId)
                    put("peerName", peer.peerName)
                    put("host", peer.host)
                    put("port", peer.port)
                    put("fingerprint", peer.fingerprint)
                    put("secret", peer.secret)
                    put("source", peer.source)
                    put("requiresQr", peer.secret.isBlank())
                }
                notifyListeners("deviceDiscovered", data)
            }

            override fun onPeerLost(peerId: String) {
                val data = JSObject().apply {
                    put("peerId", peerId)
                }
                notifyListeners("deviceLost", data)
            }

            override fun onDiscoveryError(error: String) {
                val data = JSObject().apply {
                    put("error", error)
                }
                notifyListeners("discoveryError", data)
            }
        })
        call.resolve(JSObject().put("discovering", true))
    }

    @PluginMethod
    fun stopDiscovery(call: PluginCall) {
        discoveryManager?.stopDiscovery()
        call.resolve(JSObject().put("success", true))
    }

    @PluginMethod
    fun connectAndSend(call: PluginCall) {
        var host = call.getString("host")
        var port = call.getInt("port") ?: 0
        var fp = call.getString("fingerprint") ?: ""
        var secret = call.getString("secret") ?: ""

        // If qrPayload provided, parse from it
        val qrPayload = call.getString("qrPayload")
        if (!qrPayload.isNullOrBlank()) {
            try {
                val json = JSONObject(qrPayload)
                host = json.optString("host", host)
                port = json.optInt("port", port)
                fp = json.optString("fp", fp)
                secret = json.optString("secret", secret)
            } catch (ignored: Exception) {}
        }

        if (host.isNullOrBlank() || port <= 0 || fp.isBlank()) {
            call.reject("Missing connection details: host, port, or certificate fingerprint")
            return
        }
        if (secret.isBlank()) {
            call.reject(
                "MISSING_PAIRING_SECRET: this device was found over mDNS, which never carries " +
                    "the session secret. Scan the receiver QR code to pair securely."
            )
            return
        }

        val items = SelectionStore.getAll()
        if (items.isEmpty()) {
            call.reject("No files selected for transfer")
            return
        }

        TransferService.start(context, items[0].name)

        thread(name = "Athir-SenderThread") {
            try {
                val sslContext = SessionSecurity.createClientSslContext(fp)
                transferEngine?.sendFiles(host, port, sslContext, secret, items, createTransferListener())
            } catch (e: Exception) {
                TransferService.stop(context)
                notifyListeners("transferError", JSObject().put("error", e.message ?: "Unknown error"))
            }
        }

        call.resolve(JSObject().put("started", true).put("itemCount", items.size))
    }

    @PluginMethod
    fun cancelTransfer(call: PluginCall) {
        transferEngine?.cancel()
        TransferService.stop(context)
        call.resolve(JSObject().put("cancelled", true))
    }

    @PluginMethod
    fun pauseTransfer(call: PluginCall) {
        transferEngine?.pause()
        call.resolve(JSObject().put("paused", true))
    }

    @PluginMethod
    fun resumeTransfer(call: PluginCall) {
        transferEngine?.resume()
        call.resolve(JSObject().put("resumed", true))
    }

    @PluginMethod
    fun sendChatMessage(call: PluginCall) {
        val text = call.getString("text") ?: ""
        if (text.isNotBlank()) {
            transferEngine?.sendChatMessage(text)
            call.resolve(JSObject().put("sent", true))
        } else {
            call.reject("Message text is empty")
        }
    }

    @PluginMethod
    fun getTransferHistory(call: PluginCall) {
        val records = HistoryStore.getInstance(context).getRecords()
        val jsArray = JSArray()
        for (r in records) {
            jsArray.put(JSObject().apply {
                put("id", r.id)
                put("fileName", r.fileName)
                put("fileSizeDisplay", r.fileSizeDisplay)
                put("direction", r.direction)
                put("peerName", r.peerName)
                put("status", r.status)
                put("timestamp", r.timestamp)
            })
        }
        call.resolve(JSObject().put("records", jsArray))
    }

    @PluginMethod
    fun clearTransferHistory(call: PluginCall) {
        HistoryStore.getInstance(context).clearHistory()
        call.resolve(JSObject().put("success", true))
    }

    @PluginMethod
    fun startScanner(call: PluginCall) {
        // A scanner started without the CAMERA permission can only fail silently, which is
        // exactly what produced the black scan page before. Refuse loudly instead.
        if (!PermissionCoordinator.isPermissionGranted(
                context,
                android.Manifest.permission.CAMERA
            )
        ) {
            Log.d("AthirScan", "startScanner rejected: CAMERA permission is not granted")
            call.reject("CAMERA_PERMISSION_REQUIRED")
            return
        }
        val act = activity
        act?.let { host ->
            if (host is androidx.lifecycle.LifecycleOwner) {
                scannerEngine?.startScanning(host, host, object : ScannerEngine.ScannerListener {
                    override fun onQrScanned(rawContent: String, parsedPayload: JSONObject?) {
                        val data = JSObject().apply {
                            put("rawContent", rawContent)
                            put("source", DiscoveryManager.SOURCE_QR)
                            if (parsedPayload != null) {
                                put("host", parsedPayload.optString("host"))
                                put("port", parsedPayload.optInt("port"))
                                put("fingerprint", parsedPayload.optString("fp"))
                                put("secret", parsedPayload.optString("secret"))
                                put("peerName", parsedPayload.optString("name"))
                                put("peerId", parsedPayload.optString("id"))
                                put("transport", parsedPayload.optString("transport", "lan"))
                                put("ssid", parsedPayload.optString("ssid"))
                                put("psk", parsedPayload.optString("psk"))
                                put("p2pDeviceAddress", parsedPayload.optString("p2pAddr"))
                            }
                        }
                        notifyListeners("qrScanned", data)
                    }

                    override fun onScanError(error: String) {
                        Log.d("AthirScan", "scanError: $error")
                        notifyListeners("scanError", JSObject().put("error", error))
                    }

                    override fun onCameraState(state: String, detail: String) {
                        Log.d("AthirScan", "cameraState=$state detail=$detail")
                        notifyListeners(
                            "cameraState",
                            JSObject().put("state", state).put("detail", detail)
                        )
                    }
                })
                call.resolve(JSObject().put("scanning", true))
            } else {
                call.reject("Activity is not a LifecycleOwner")
            }
        } ?: call.reject("Activity is null")
    }

    /**
     * The scan page reports where its camera window is (CSS px + device pixel ratio) so the
     * native preview can be placed exactly there, above the WebView.
     */
    @PluginMethod
    fun setScannerWindow(call: PluginCall) {
        try {
            val left = call.getFloat("x") ?: 0f
            val top = call.getFloat("y") ?: 0f
            val width = call.getFloat("width") ?: 0f
            val height = call.getFloat("height") ?: 0f
            val density = call.getFloat("density") ?: 0f
            if (width <= 0f || height <= 0f) {
                call.reject("Invalid camera window size")
                return
            }
            scannerEngine?.setWindow(
                ScannerEngine.WindowRect(left, top, width, height, density),
                bridge?.webView
            )
            call.resolve(JSObject().put("applied", true))
        } catch (e: Exception) {
            call.reject("Failed to apply camera window: ${e.message}")
        }
    }

    @PluginMethod
    fun stopScanner(call: PluginCall) {
        scannerEngine?.stopScanning()
        call.resolve(JSObject().put("stopped", true))
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 1002
        private const val INVITE_TITLE = "شارك أثير"
        private const val INVITE_SUBJECT = "أثير | Athir - نقل الملفات بدون إنترنت"
        private const val INVITE_TEXT =
            "أثير: تطبيق لنقل الملفات والصور والفيديو بين جهازين قريبين، بدون إنترنت " +
                "وبتشفير كامل. ثبّت الملف المرفق وابدأ النقل."
        private const val HOTSPOT_DEFAULT_GATEWAY = "192.168.49.1"

        @Volatile
        private var liveInstance: AthirNativePlugin? = null

        /** Invoked by MainActivity after the system permission dialog is answered. */
        @JvmStatic
        fun onActivityPermissionResult(requestCode: Int) {
            liveInstance?.publishPermissionState(requestCode)
        }

        /** Exposed to MainActivity so it can decide whether to forward the result at all. */
        @JvmStatic
        fun permissionRequestCode(): Int = REQUEST_CODE_PERMISSIONS
    }

    private fun createTransferListener(): TransferEngine.TransferListener {
        return object : TransferEngine.TransferListener {
            override fun onProgress(
                bytesTransferred: Long,
                totalBytes: Long,
                speedBytesPerSec: Long,
                currentFileName: String,
                fileIndex: Int,
                totalFiles: Int
            ) {
                val pct = if (totalBytes > 0) (bytesTransferred * 100.0 / totalBytes).toInt() else 0
                val speedMBs = Math.round((speedBytesPerSec / (1024.0 * 1024.0)) * 10.0) / 10.0
                val speedDisplay = if (speedMBs > 0.0) "$speedMBs MB/s" else "${speedBytesPerSec / 1024} KB/s"

                TransferService.update(context, currentFileName, pct, speedDisplay)

                val data = JSObject().apply {
                    put("bytesTransferred", bytesTransferred)
                    put("totalBytes", totalBytes)
                    put("speedBytesPerSec", speedBytesPerSec)
                    put("speedDisplay", speedDisplay)
                    put("currentFileName", currentFileName)
                    put("fileIndex", fileIndex)
                    put("totalFiles", totalFiles)
                    put("percent", pct)
                    put("sentMB", Math.round((bytesTransferred / (1024.0 * 1024.0)) * 10.0) / 10.0)
                    put("totalMB", Math.round((totalBytes / (1024.0 * 1024.0)) * 10.0) / 10.0)
                }
                notifyListeners("transferProgress", data)
            }

            override fun onResumedFrom(fileName: String, offset: Long) {
                val data = JSObject().apply {
                    put("fileName", fileName)
                    put("offsetBytes", offset)
                }
                notifyListeners("transferResumed", data)
            }

            override fun onCancelled(byPeer: Boolean) {
                TransferService.stop(context)
                notifyListeners("transferCancelled", JSObject().put("byPeer", byPeer))
            }

            override fun onPausedChanged(paused: Boolean) {
                notifyListeners("transferPaused", JSObject().put("paused", paused))
            }

            override fun onIntegrityVerified(fileName: String, verified: Boolean) {
                val data = JSObject().apply {
                    put("fileName", fileName)
                    put("verified", verified)
                }
                notifyListeners("fileIntegrity", data)
            }

            override fun onFileCompleted(fileName: String, sizeBytes: Long, isReceived: Boolean) {
                val data = JSObject().apply {
                    put("fileName", fileName)
                    put("sizeBytes", sizeBytes)
                    put("isReceived", isReceived)
                }
                notifyListeners("fileCompleted", data)
            }

            override fun onAllCompleted(isSender: Boolean) {
                TransferService.stop(context)
                val data = JSObject().apply {
                    put("success", true)
                    put("isSender", isSender)
                }
                notifyListeners("transferComplete", data)
            }

            override fun onError(error: String) {
                TransferService.stop(context)
                val data = JSObject().apply {
                    put("error", error)
                }
                notifyListeners("transferError", data)
            }

            override fun onChatMessage(message: String) {
                val data = JSObject().apply {
                    put("message", message)
                }
                notifyListeners("messageReceived", data)
            }
        }
    }
}
