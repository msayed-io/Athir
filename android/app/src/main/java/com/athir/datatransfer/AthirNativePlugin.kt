package com.athir.datatransfer

import android.os.Build
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
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

    private var activeKeyPair: KeyPair? = null
    private var activeCert: X509Certificate? = null
    private var activeSessionSecret: String = ""
    private var activeServerPort: Int = 0

    override fun load() {
        super.load()
        transferEngine = TransferEngine(context)
        discoveryManager = DiscoveryManager(context)
        scannerEngine = ScannerEngine(context)
    }

    @PluginMethod
    fun getDeviceInfo(call: PluginCall) {
        val ret = JSObject().apply {
            put("deviceId", IdentityStore.getDeviceId(context))
            put("displayName", IdentityStore.getDisplayName(context))
            put("androidVersion", Build.VERSION.RELEASE ?: "Android")
            put("sdkInt", Build.VERSION.SDK_INT)
            put("localIp", DiscoveryManager.getLocalIpAddress())
            put("wifiDirectSupported", true)
            put("hotspotSupported", true)
            put("wifiAwareSupported", Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
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
        val status = PermissionCoordinator.checkAllPermissions(context)
        val ret = JSObject().apply {
            put("mediaGranted", status.mediaGranted)
            put("nearbyGranted", status.nearbyGranted)
            put("cameraGranted", status.cameraGranted)
            put("notificationGranted", status.notificationGranted)
            put("contactsGranted", status.contactsGranted)
            put("allGranted", status.allGranted)
            put("sdkInt", status.sdkInt)
        }
        call.resolve(ret)
    }

    @PluginMethod
    override fun requestPermissions(call: PluginCall) {
        val missing = PermissionCoordinator.getMissingPermissions(context)
        if (missing.isEmpty()) {
            checkPermissions(call)
            return
        }

        // Request all missing permissions directly on activity
        activity?.let { act ->
            act.requestPermissions(missing.toTypedArray(), 1002)
        }
        call.resolve(JSObject().put("requested", true))
    }

    @PluginMethod
    fun getMediaCatalog(call: PluginCall) {
        val category = call.getString("category") ?: "apps"
        val query = call.getString("query")
        val limit = call.getInt("limit") ?: 100

        thread {
            val items: List<MediaCatalog.CatalogItem> = when (category) {
                "apps" -> MediaCatalog.getApps(context, query, limit)
                "photos" -> MediaCatalog.getPhotos(context, query, limit)
                "videos" -> MediaCatalog.getVideos(context, query, limit)
                "audio" -> MediaCatalog.getAudio(context, query, limit)
                "contacts" -> MediaCatalog.getContacts(context, query, limit)
                "files" -> MediaCatalog.getFiles(context, query, limit)
                else -> emptyList()
            }

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
                val kp = SessionSecurity.generateKeyPair()
                val cert = SessionSecurity.generateSelfSignedCertificate(kp)
                val secret = SessionSecurity.generateSessionSecret()
                val fp = SessionSecurity.getCertificateFingerprint(cert)

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
                val qrDataUrl = QrEncoder.generateQrDataUrl(qrString, 512)

                // Start advertising via mDNS
                discoveryManager?.startAdvertising(displayName, activeServerPort, deviceId, fp, secret)

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
        call.resolve(JSObject().put("success", true))
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

        if (host.isNullOrBlank() || port <= 0 || fp.isBlank() || secret.isBlank()) {
            call.reject("Missing connection details: host, port, fingerprint, or secret")
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
        activity?.let { act ->
            if (act is androidx.lifecycle.LifecycleOwner) {
                scannerEngine?.startScanning(act, object : ScannerEngine.ScannerListener {
                    override fun onQrScanned(rawContent: String, parsedPayload: JSONObject?) {
                        val data = JSObject().apply {
                            put("rawContent", rawContent)
                            if (parsedPayload != null) {
                                put("host", parsedPayload.optString("host"))
                                put("port", parsedPayload.optInt("port"))
                                put("fingerprint", parsedPayload.optString("fp"))
                                put("secret", parsedPayload.optString("secret"))
                                put("peerName", parsedPayload.optString("name"))
                                put("peerId", parsedPayload.optString("id"))
                            }
                        }
                        notifyListeners("qrScanned", data)
                    }

                    override fun onScanError(error: String) {
                        notifyListeners("scanError", JSObject().put("error", error))
                    }
                })
                call.resolve(JSObject().put("scanning", true))
            } else {
                call.reject("Activity is not a LifecycleOwner")
            }
        } ?: call.reject("Activity is null")
    }

    @PluginMethod
    fun stopScanner(call: PluginCall) {
        scannerEngine?.stopScanning()
        call.resolve(JSObject().put("stopped", true))
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
