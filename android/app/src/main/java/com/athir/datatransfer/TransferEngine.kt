package com.athir.datatransfer

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLServerSocketFactory
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Real byte-level transfer engine speaking the ATHR frame protocol over TLS.
 *
 * Hardening applied in this revision (see docs/NATIVE_REAL_TRANSFER_REPORT.md):
 *  - every frame declares a bounded payload length, oversized frames are rejected;
 *  - the receiver verifies the per-file SHA-256 hash sent in FILE_DONE before
 *    reporting success (integrity is enforced, not decorative);
 *  - peer supplied file names are sanitized and the resolved destination path is
 *    validated against directory traversal;
 *  - accepted sockets carry a read timeout so a stalled peer cannot pin the
 *    receiver forever;
 *  - each inbound connection is served on its own worker thread.
 */
class TransferEngine(private val context: Context) {

    interface TransferListener {
        fun onProgress(
            bytesTransferred: Long,
            totalBytes: Long,
            speedBytesPerSec: Long,
            currentFileName: String,
            fileIndex: Int,
            totalFiles: Int
        )
        fun onFileCompleted(fileName: String, sizeBytes: Long, isReceived: Boolean)
        fun onIntegrityVerified(fileName: String, verified: Boolean)
        fun onAllCompleted(isSender: Boolean)
        fun onError(error: String)
        fun onChatMessage(message: String)
    }

    private val isPaused = AtomicBoolean(false)
    private val isCancelled = AtomicBoolean(false)

    @Volatile
    private var activeServerSocket: ServerSocket? = null

    @Volatile
    private var activeClientSocket: Socket? = null

    fun cancel() {
        isCancelled.set(true)
        try {
            activeClientSocket?.close()
        } catch (ignored: Exception) {}
        try {
            activeServerSocket?.close()
        } catch (ignored: Exception) {}
    }

    fun pause() {
        isPaused.set(true)
    }

    fun resume() {
        isPaused.set(false)
    }

    // --- RECEIVER SERVER ---
    fun startReceiverServer(
        port: Int,
        sslContext: SSLContext,
        expectedSessionSecret: String,
        listener: TransferListener
    ) {
        isCancelled.set(false)
        isPaused.set(false)

        val ssf: SSLServerSocketFactory = sslContext.serverSocketFactory
        val serverSocket = ssf.createServerSocket(port) as SSLServerSocket
        serverSocket.needClientAuth = false
        activeServerSocket = serverSocket

        try {
            while (!isCancelled.get()) {
                val socket = try {
                    serverSocket.accept() as SSLSocket
                } catch (e: Exception) {
                    if (isCancelled.get()) break else continue
                }
                activeClientSocket = socket
                // Serve every inbound connection on its own thread so that a slow or
                // malicious peer cannot block legitimate senders.
                Thread({
                    try {
                        socket.soTimeout = SOCKET_READ_TIMEOUT_MS
                        applyTlsPolicy(socket)
                        handleReceiverConnection(socket, expectedSessionSecret, listener)
                    } catch (ignored: Exception) {
                    } finally {
                        try {
                            socket.close()
                        } catch (ignored: Exception) {}
                    }
                }, "Athir-ReceiverConn").start()
            }
        } catch (e: Exception) {
            if (!isCancelled.get()) {
                listener.onError("Server socket error: ${e.message}")
            }
        } finally {
            try {
                serverSocket.close()
            } catch (ignored: Exception) {}
            activeServerSocket = null
        }
    }

    private fun handleReceiverConnection(
        socket: SSLSocket,
        expectedSessionSecret: String,
        listener: TransferListener
    ) {
        try {
            val dis = DataInputStream(socket.inputStream)
            val dos = DataOutputStream(socket.outputStream)

            // 1. Authenticate handshake
            val (frameType, authPayload) = readFrame(dis)
            if (frameType != FRAME_AUTH_REQ) {
                writeFrame(dos, FRAME_AUTH_RES, byteArrayOf(0x01)) // REJECTED
                listener.onError("Authentication failed: invalid handshake frame")
                return
            }

            val receivedSecret = String(authPayload, Charsets.UTF_8).trim()
            if (expectedSessionSecret.isBlank() || receivedSecret != expectedSessionSecret.trim()) {
                writeFrame(dos, FRAME_AUTH_RES, byteArrayOf(0x01)) // REJECTED
                listener.onError("Authentication failed: session secret mismatch")
                return
            }

            // Auth succeeded
            writeFrame(dos, FRAME_AUTH_RES, byteArrayOf(0x00)) // OK

            // 2. Receive frames loop
            var currentFileOut: OutputStream? = null
            var currentFileName = ""
            var currentFileSize = 0L
            var totalBytesExpected = 0L
            var totalBytesTransferred = 0L
            var lastSpeedCheckTime = System.currentTimeMillis()
            var bytesSinceLastCheck = 0L
            var fileIndex = 0
            var totalFiles = 1
            var shaDigest: MessageDigest? = null

            while (!isCancelled.get()) {
                val (type, payload) = try {
                    readFrame(dis)
                } catch (e: Exception) {
                    break
                }

                when (type) {
                    FRAME_CHAT -> {
                        val text = String(payload, Charsets.UTF_8)
                        listener.onChatMessage(text)
                    }

                    FRAME_FILE_META -> {
                        val json = JSONObject(String(payload, Charsets.UTF_8))
                        val rawName = json.optString("name", "athir_file")
                        currentFileName = sanitizeFileName(rawName)
                        currentFileSize = json.optLong("size", 0L)
                        totalFiles = json.optInt("totalFiles", 1)
                        fileIndex = json.optInt("fileIndex", 0)
                        totalBytesExpected = json.optLong("totalBytes", currentFileSize)
                        shaDigest = MessageDigest.getInstance("SHA-256")

                        currentFileOut?.close()
                        currentFileOut = createDownloadOutputStream(
                            context,
                            currentFileName,
                            json.optString("mimeType", "*/*")
                        )
                    }

                    FRAME_FILE_CHUNK -> {
                        currentFileOut?.write(payload)
                        shaDigest?.update(payload)
                        totalBytesTransferred += payload.size
                        bytesSinceLastCheck += payload.size

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastSpeedCheckTime
                        if (elapsed >= PROGRESS_INTERVAL_MS) {
                            val currentSpeed = (bytesSinceLastCheck * 1000L) / Math.max(elapsed, 1L)
                            lastSpeedCheckTime = now
                            bytesSinceLastCheck = 0L

                            listener.onProgress(
                                totalBytesTransferred,
                                totalBytesExpected,
                                currentSpeed,
                                currentFileName,
                                fileIndex,
                                totalFiles
                            )
                        }
                    }

                    FRAME_FILE_DONE -> {
                        currentFileOut?.flush()
                        currentFileOut?.close()
                        currentFileOut = null

                        val actual = shaDigest?.digest()
                        val verified = actual != null &&
                            payload.size == actual.size &&
                            payload.contentEquals(actual)

                        listener.onIntegrityVerified(currentFileName, verified)

                        if (!verified) {
                            HistoryStore.getInstance(context).addRecord(
                                currentFileName,
                                formatSizeMb(currentFileSize),
                                "استُلم",
                                "المرسل",
                                "فشل التحقق"
                            )
                            listener.onError(
                                "Integrity check failed for \"$currentFileName\": the received bytes " +
                                    "do not match the SHA-256 hash announced by the sender."
                            )
                            return
                        }

                        listener.onFileCompleted(currentFileName, currentFileSize, true)
                        HistoryStore.getInstance(context).addRecord(
                            currentFileName,
                            formatSizeMb(currentFileSize),
                            "استُلم",
                            "المرسل",
                            "مكتمل"
                        )
                    }

                    FRAME_SESSION_DONE -> {
                        listener.onAllCompleted(false)
                        break
                    }

                    FRAME_PAUSE -> {
                        isPaused.set(true)
                    }

                    FRAME_RESUME -> {
                        isPaused.set(false)
                    }

                    FRAME_CANCEL -> {
                        isCancelled.set(true)
                        break
                    }
                }
            }

            currentFileOut?.close()
        } catch (e: Exception) {
            if (!isCancelled.get()) {
                listener.onError("Receiver error: ${e.message}")
            }
        }
    }

    // --- SENDER CLIENT ---
    fun sendFiles(
        host: String,
        port: Int,
        sslContext: SSLContext,
        sessionSecret: String,
        items: List<SelectionStore.SelectedItem>,
        listener: TransferListener
    ) {
        isCancelled.set(false)
        isPaused.set(false)

        val sf: SSLSocketFactory = sslContext.socketFactory
        val socket = sf.createSocket() as SSLSocket
        activeClientSocket = socket

        try {
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = SOCKET_READ_TIMEOUT_MS
            applyTlsPolicy(socket)
            socket.startHandshake()

            val dos = DataOutputStream(socket.outputStream)
            val dis = DataInputStream(socket.inputStream)

            // 1. Send AUTH_REQ
            writeFrame(dos, FRAME_AUTH_REQ, sessionSecret.toByteArray(Charsets.UTF_8))

            // 2. Read AUTH_RES
            val (authResType, authResPayload) = readFrame(dis)
            if (authResType != FRAME_AUTH_RES || authResPayload.isEmpty() || authResPayload[0] != 0x00.toByte()) {
                listener.onError("Receiver rejected authentication for this session.")
                return
            }

            // 3. Stream all files
            val totalBytes = items.sumOf { it.sizeBytes }
            var totalBytesSent = 0L
            var lastSpeedCheckTime = System.currentTimeMillis()
            var bytesSinceLastCheck = 0L

            for ((index, item) in items.withIndex()) {
                if (isCancelled.get()) break

                // Send FILE_META
                val metaJson = JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("size", item.sizeBytes)
                    put("mimeType", item.mimeType)
                    put("fileIndex", index)
                    put("totalFiles", items.size)
                    put("totalBytes", totalBytes)
                }
                writeFrame(dos, FRAME_FILE_META, metaJson.toString().toByteArray(Charsets.UTF_8))

                val inputStream: InputStream? = openInputStream(context, item.uri)
                if (inputStream == null) {
                    listener.onError("Cannot open file: ${item.name}")
                    continue
                }

                val buffer = ByteArray(CHUNK_SIZE_BYTES)
                val sha = MessageDigest.getInstance("SHA-256")

                try {
                    inputStream.use { stream ->
                        while (!isCancelled.get()) {
                            while (isPaused.get() && !isCancelled.get()) {
                                Thread.sleep(100)
                            }

                            val read = stream.read(buffer)
                            if (read <= 0) break

                            val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
                            writeFrame(dos, FRAME_FILE_CHUNK, chunk)
                            sha.update(chunk)

                            totalBytesSent += read
                            bytesSinceLastCheck += read

                            val now = System.currentTimeMillis()
                            val elapsed = now - lastSpeedCheckTime
                            if (elapsed >= PROGRESS_INTERVAL_MS) {
                                val currentSpeed = (bytesSinceLastCheck * 1000L) / Math.max(elapsed, 1L)
                                lastSpeedCheckTime = now
                                bytesSinceLastCheck = 0L

                                listener.onProgress(
                                    totalBytesSent,
                                    totalBytes,
                                    currentSpeed,
                                    item.name,
                                    index,
                                    items.size
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (!isCancelled.get()) {
                        listener.onError("Failed while sending \"${item.name}\": ${e.message}")
                        return
                    }
                }

                if (!isCancelled.get()) {
                    writeFrame(dos, FRAME_FILE_DONE, sha.digest())
                    listener.onFileCompleted(item.name, item.sizeBytes, false)
                    HistoryStore.getInstance(context).addRecord(
                        item.name,
                        formatSizeMb(item.sizeBytes),
                        "أُرسل",
                        "المستلم",
                        "مكتمل"
                    )
                }
            }

            if (!isCancelled.get()) {
                writeFrame(dos, FRAME_SESSION_DONE, ByteArray(0))
                listener.onAllCompleted(true)
            }
        } catch (e: Exception) {
            if (!isCancelled.get()) {
                listener.onError("Sender error: ${e.message}")
            }
        } finally {
            try {
                socket.close()
            } catch (ignored: Exception) {}
            activeClientSocket = null
        }
    }

    fun sendChatMessage(message: String) {
        val socket = activeClientSocket ?: return
        try {
            val dos = DataOutputStream(socket.outputStream)
            writeFrame(dos, FRAME_CHAT, message.toByteArray(Charsets.UTF_8))
        } catch (ignored: Exception) {}
    }

    /** Restricts the TLS handshake to modern protocol versions on both ends. */
    private fun applyTlsPolicy(socket: SSLSocket) {
        try {
            val supported = socket.supportedProtocols?.toSet() ?: emptySet()
            val allowed = listOf("TLSv1.3", "TLSv1.2").filter { supported.contains(it) }
            if (allowed.isNotEmpty()) {
                socket.enabledProtocols = allowed.toTypedArray()
            }
        } catch (ignored: Exception) {
        }
    }

    private fun openInputStream(context: Context, uriStr: String): InputStream? {
        return try {
            val uri = Uri.parse(uriStr)
            if (uri.scheme == "file") {
                File(uri.path ?: "").inputStream()
            } else {
                context.contentResolver.openInputStream(uri)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Strips directory components and control characters from a peer supplied name.
     * Peer input is untrusted: it must never influence the destination directory.
     */
    private fun sanitizeFileName(raw: String): String {
        val base = raw
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("[\\u0000-\\u001F]"), "")
            .replace("..", "_")
            .trim()
        val fallback = "athir_file_${System.currentTimeMillis()}"
        val safe = base.ifBlank { fallback }
        return if (safe.length > MAX_FILE_NAME_LENGTH) safe.take(MAX_FILE_NAME_LENGTH) else safe
    }

    private fun createDownloadOutputStream(context: Context, fileName: String, mimeType: String): OutputStream {
        val safeName = sanitizeFileName(fileName)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Athir")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Cannot create MediaStore download entry")
            context.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Cannot open download output stream")
        } else {
            val athirDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Athir"
            )
            if (!athirDir.exists() && !athirDir.mkdirs()) {
                throw IllegalStateException("Cannot create Athir downloads directory")
            }
            val targetFile = File(athirDir, safeName)
            // Defense in depth: the canonical path must stay inside the Athir directory.
            val basePath = athirDir.canonicalPath + File.separator
            if (!targetFile.canonicalPath.startsWith(basePath)) {
                throw SecurityException("Rejected unsafe destination path for \"$safeName\"")
            }
            FileOutputStream(targetFile)
        }
    }

    companion object {
        // 4 bytes Magic 'A','T','H','R'
        private val MAGIC = byteArrayOf('A'.code.toByte(), 'T'.code.toByte(), 'H'.code.toByte(), 'R'.code.toByte())
        private const val PROTOCOL_VERSION: Byte = 0x01

        const val FRAME_AUTH_REQ: Byte = 0x01
        const val FRAME_AUTH_RES: Byte = 0x02
        const val FRAME_CHAT: Byte = 0x03
        const val FRAME_FILE_META: Byte = 0x10
        const val FRAME_FILE_CHUNK: Byte = 0x11
        const val FRAME_FILE_ACK: Byte = 0x12
        const val FRAME_FILE_DONE: Byte = 0x13
        const val FRAME_PAUSE: Byte = 0x20
        const val FRAME_RESUME: Byte = 0x21
        const val FRAME_CANCEL: Byte = 0x30
        const val FRAME_SESSION_DONE: Byte = 0xFF.toByte()

        /** 64 KB payload per chunk frame. */
        const val CHUNK_SIZE_BYTES = 64 * 1024

        /** Hard ceiling for any inbound frame payload (chunks are the largest legitimate frames). */
        const val MAX_PAYLOAD_BYTES = 512 * 1024

        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val SOCKET_READ_TIMEOUT_MS = 300_000
        private const val PROGRESS_INTERVAL_MS = 100L
        private const val MAX_FILE_NAME_LENGTH = 180

        fun formatSizeMb(bytes: Long): String {
            val mb = Math.round((bytes / (1024.0 * 1024.0)) * 10.0) / 10.0
            return "$mb MB"
        }

        fun writeFrame(dos: DataOutputStream, frameType: Byte, payload: ByteArray) {
            dos.write(MAGIC)
            dos.writeByte(PROTOCOL_VERSION.toInt())
            dos.writeByte(frameType.toInt())
            dos.writeInt(payload.size)
            if (payload.isNotEmpty()) {
                dos.write(payload)
            }
            dos.flush()
        }

        fun readFrame(dis: DataInputStream): Pair<Byte, ByteArray> {
            val magic = ByteArray(4)
            dis.readFully(magic)
            if (!magic.contentEquals(MAGIC)) {
                throw IllegalStateException("Invalid ATHR frame magic")
            }
            val version = dis.readByte()
            if (version != PROTOCOL_VERSION) {
                throw IllegalStateException("Unsupported protocol version: $version")
            }
            val frameType = dis.readByte()
            val length = dis.readInt()
            if (length < 0 || length > MAX_PAYLOAD_BYTES) {
                throw IllegalStateException("Rejected frame payload length: $length")
            }
            val payload = if (length > 0) {
                val data = ByteArray(length)
                dis.readFully(data)
                data
            } else {
                ByteArray(0)
            }
            return Pair(frameType, payload)
        }
    }
}
