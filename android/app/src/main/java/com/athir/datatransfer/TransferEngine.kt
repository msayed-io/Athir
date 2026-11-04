package com.athir.datatransfer

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLServerSocketFactory
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Real byte-level transfer engine speaking the ATHR frame protocol over TLS.
 *
 * Capabilities implemented here:
 *  - authenticated session handshake (AUTH_REQ / AUTH_RES);
 *  - resumable transfers: the sender announces size + full-file SHA-256 in FILE_META and
 *    the receiver answers with FILE_ACK carrying the byte offset it already holds, so the
 *    sender seeks and continues instead of restarting;
 *  - a bidirectional control channel inside the same TLS/TCP stream (PAUSE / RESUME /
 *    CANCEL) handled by a dedicated reader thread on the sender side, so control frames
 *    are never lost while file bytes are streaming;
 *  - per-file SHA-256 verification before a file is published to Downloads;
 *  - bounded frame sizes, socket read timeouts and one worker thread per inbound peer.
 *
 * Nothing in this class simulates a transfer: bytes are read from the real content
 * provider and written to real files.
 */
class TransferEngine(private val context: Context) {

    enum class Role { NONE, SENDER, RECEIVER }

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
        fun onResumedFrom(fileName: String, offset: Long)
        fun onAllCompleted(isSender: Boolean)
        fun onError(error: String)
        fun onChatMessage(message: String)
        fun onCancelled(byPeer: Boolean)
        fun onPausedChanged(paused: Boolean)
    }

    private val isPaused = AtomicBoolean(false)
    private val isCancelled = AtomicBoolean(false)

    @Volatile
    private var role: Role = Role.NONE

    @Volatile
    private var activeServerSocket: ServerSocket? = null

    @Volatile
    private var activeClientSocket: Socket? = null

    /** Control frames received from the peer while the sender is busy writing. */
    private val inboundFrames = LinkedBlockingQueue<Pair<Byte, ByteArray>>()

    fun isSender(): Boolean = role == Role.SENDER

    fun cancel() {
        isCancelled.set(true)
        sendControlFrame(FRAME_CANCEL)
        closeSockets()
    }

    fun pause() {
        isPaused.set(true)
        sendControlFrame(FRAME_PAUSE)
    }

    fun resume() {
        isPaused.set(false)
        sendControlFrame(FRAME_RESUME)
    }

    private fun closeSockets() {
        try {
            activeClientSocket?.close()
        } catch (ignored: Exception) {}
        try {
            activeServerSocket?.close()
        } catch (ignored: Exception) {}
    }

    private fun sendControlFrame(frameType: Byte) {
        val socket = activeClientSocket ?: return
        try {
            if (socket.isClosed) return
            writeFrame(DataOutputStream(socket.outputStream), frameType, ByteArray(0))
        } catch (ignored: Exception) {
        }
    }

    // --- RECEIVER ---

    fun startReceiverServer(
        port: Int,
        sslContext: SSLContext,
        expectedSessionSecret: String,
        listener: TransferListener
    ) {
        isCancelled.set(false)
        isPaused.set(false)
        role = Role.RECEIVER

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
            if (!isCancelled.get()) listener.onError("Server socket error: ${e.message}")
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

            val (frameType, authPayload) = readFrame(dis)
            if (frameType != FRAME_AUTH_REQ) {
                writeFrame(dos, FRAME_AUTH_RES, byteArrayOf(0x01))
                listener.onError("Authentication failed: invalid handshake frame")
                return
            }
            val receivedSecret = String(authPayload, Charsets.UTF_8).trim()
            if (expectedSessionSecret.isBlank() || receivedSecret != expectedSessionSecret.trim()) {
                writeFrame(dos, FRAME_AUTH_RES, byteArrayOf(0x01))
                listener.onError("Authentication failed: session secret mismatch")
                return
            }
            writeFrame(dos, FRAME_AUTH_RES, byteArrayOf(0x00))

            var currentOut: java.io.OutputStream? = null
            var currentPart: File? = null
            var currentFileName = ""
            var currentMime = "*/*"
            var currentSize = 0L
            var expectedSha: ByteArray? = null
            var digest: MessageDigest? = null
            var totalBytesExpected = 0L
            var totalTransferred = 0L
            var lastCheck = System.currentTimeMillis()
            var bytesSinceCheck = 0L
            var fileIndex = 0
            var totalFiles = 1

            while (!isCancelled.get()) {
                val (type, payload) = try {
                    readFrame(dis)
                } catch (e: Exception) {
                    break
                }

                when (type) {
                    FRAME_CHAT -> listener.onChatMessage(String(payload, Charsets.UTF_8))

                    FRAME_PAUSE -> listener.onPausedChanged(true)

                    FRAME_RESUME -> listener.onPausedChanged(false)

                    FRAME_CANCEL -> {
                        isCancelled.set(true)
                        currentOut?.close()
                        currentOut = null
                        listener.onCancelled(true)
                        break
                    }

                    FRAME_FILE_META -> {
                        val json = JSONObject(String(payload, Charsets.UTF_8))
                        currentFileName = sanitizeFileName(json.optString("name", "athir_file"))
                        currentMime = json.optString("mimeType", "*/*")
                        currentSize = json.optLong("size", 0L)
                        totalFiles = json.optInt("totalFiles", 1)
                        fileIndex = json.optInt("fileIndex", 0)
                        totalBytesExpected = json.optLong("totalBytes", currentSize)
                        expectedSha = AthrFrameCodec.hexToBytesOrNull(json.optString("sha256", ""))

                        val key = json.optString("sha256", "")
                        val part = PartialFileStore.partFile(context, key, currentFileName)
                        // Only trust a resume offset when the announced size is known;
                        // otherwise sender and receiver could disagree on the position.
                        val offset = if (currentSize > 0L) {
                            PartialFileStore.resumeOffset(part, currentSize)
                        } else {
                            PartialFileStore.delete(part)
                            0L
                        }

                        currentOut?.close()
                        currentOut = null
                        currentPart = part

                        val md = MessageDigest.getInstance("SHA-256")
                        if (offset > 0L) {
                            // Feed the already-received prefix so the final hash still
                            // covers the whole file after a resumed transfer.
                            part.inputStream().use { prefix ->
                                val buffer = ByteArray(256 * 1024)
                                var remaining = offset
                                while (remaining > 0) {
                                    val read = prefix.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                                    if (read <= 0) break
                                    md.update(buffer, 0, read)
                                    remaining -= read
                                }
                            }
                            listener.onResumedFrom(currentFileName, offset)
                        }
                        digest = md
                        if (offset < currentSize || currentSize == 0L) {
                            currentOut = PartialFileStore.openAppend(part, offset > 0L)
                        } else {
                            currentOut = null
                        }
                        totalTransferred = offset

                        writeFrame(dos, FRAME_FILE_ACK, longToBytes(offset))
                    }

                    FRAME_FILE_CHUNK -> {
                        currentOut?.write(payload)
                        digest?.update(payload)
                        totalTransferred += payload.size
                        bytesSinceCheck += payload.size

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastCheck
                        if (elapsed >= PROGRESS_INTERVAL_MS) {
                            val speed = (bytesSinceCheck * 1000L) / Math.max(elapsed, 1L)
                            lastCheck = now
                            bytesSinceCheck = 0L
                            listener.onProgress(
                                totalTransferred,
                                totalBytesExpected,
                                speed,
                                currentFileName,
                                fileIndex,
                                totalFiles
                            )
                        }
                    }

                    FRAME_FILE_DONE -> {
                        currentOut?.flush()
                        currentOut?.close()
                        currentOut = null

                        val actual = digest?.digest()
                        val announced = expectedSha
                        val verified = actual != null && announced != null &&
                            announced.size == actual.size && announced.contentEquals(actual)
                        listener.onIntegrityVerified(currentFileName, verified)

                        val part = currentPart
                        if (verified && part != null) {
                            val published = PartialFileStore.publishToDownloads(
                                context, part, currentFileName, currentMime
                            )
                            if (published) {
                                PartialFileStore.delete(part)
                                listener.onFileCompleted(currentFileName, currentSize, true)
                                HistoryStore.getInstance(context).addRecord(
                                    currentFileName,
                                    TransferEngine.formatSizeMb(currentSize),
                                    "استُلم",
                                    "المرسل",
                                    "مكتمل"
                                )
                            } else {
                                HistoryStore.getInstance(context).addRecord(
                                    currentFileName,
                                    TransferEngine.formatSizeMb(currentSize),
                                    "استُلم",
                                    "المرسل",
                                    "فشل الحفظ"
                                )
                                listener.onError("Could not publish \"$currentFileName\" to Downloads")
                                return
                            }
                        } else {
                            HistoryStore.getInstance(context).addRecord(
                                currentFileName,
                                TransferEngine.formatSizeMb(currentSize),
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
                    }

                    FRAME_SESSION_DONE -> {
                        listener.onAllCompleted(false)
                        break
                    }
                }
            }

            currentOut?.close()
        } catch (e: Exception) {
            if (!isCancelled.get()) listener.onError("Receiver error: ${e.message}")
        }
    }

    // --- SENDER ---

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
        role = Role.SENDER
        inboundFrames.clear()

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

            writeFrame(dos, FRAME_AUTH_REQ, sessionSecret.toByteArray(Charsets.UTF_8))
            val (authResType, authResPayload) = readFrame(dis)
            if (authResType != FRAME_AUTH_RES || authResPayload.isEmpty() || authResPayload[0] != 0x00.toByte()) {
                listener.onError("Receiver rejected authentication for this session.")
                return
            }

            // Control channel: everything the peer sends from now on is read here.
            startControlReader(dis, listener)

            val totalBytes = items.sumOf { it.sizeBytes }
            var totalSent = 0L
            var lastCheck = System.currentTimeMillis()
            var bytesSinceCheck = 0L

            for ((index, item) in items.withIndex()) {
                if (isCancelled.get()) break
                awaitWhilePaused()

                // 1. Announce the file with its full SHA-256 so the receiver can resume
                //    and verify the final content.
                val fullHash = computeSha256(item.uri)
                val metaJson = JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("size", item.sizeBytes)
                    put("mimeType", item.mimeType)
                    put("fileIndex", index)
                    put("totalFiles", items.size)
                    put("totalBytes", totalBytes)
                    put("sha256", fullHash?.let { AthrFrameCodec.toHex(it) } ?: "")
                }
                writeFrame(dos, FRAME_FILE_META, metaJson.toString().toByteArray(Charsets.UTF_8))

                // 2. Wait for the receiver's resume offset.
                val ack = awaitFrame(FRAME_FILE_ACK, ACK_TIMEOUT_MS)
                var offset = 0L
                if (ack != null && ack.second.size == 8) {
                    offset = ByteBuffer.wrap(ack.second).long
                }
                if (offset < 0L) offset = 0L
                if (offset > item.sizeBytes) offset = 0L
                totalSent += offset

                val inputStream = openInputStream(item.uri)
                if (inputStream == null) {
                    listener.onError("Cannot open file: ${item.name}")
                    continue
                }

                val buffer = ByteArray(CHUNK_SIZE_BYTES)
                try {
                    inputStream.use { stream ->
                        if (offset > 0L) stream.skipFully(offset)
                        while (!isCancelled.get()) {
                            awaitWhilePaused()
                            if (drainControlAndCheckCancel()) break

                            val read = stream.read(buffer)
                            if (read <= 0) break
                            val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
                            writeFrame(dos, FRAME_FILE_CHUNK, chunk)

                            totalSent += read
                            bytesSinceCheck += read

                            val now = System.currentTimeMillis()
                            val elapsed = now - lastCheck
                            if (elapsed >= PROGRESS_INTERVAL_MS) {
                                val speed = (bytesSinceCheck * 1000L) / Math.max(elapsed, 1L)
                                lastCheck = now
                                bytesSinceCheck = 0L
                                listener.onProgress(totalSent, totalBytes, speed, item.name, index, items.size)
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
                    writeFrame(dos, FRAME_FILE_DONE, fullHash ?: ByteArray(0))
                    listener.onFileCompleted(item.name, item.sizeBytes, false)
                    HistoryStore.getInstance(context).addRecord(
                        item.name,
                        TransferEngine.formatSizeMb(item.sizeBytes),
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
            if (!isCancelled.get()) listener.onError("Sender error: ${e.message}")
        } finally {
            try {
                socket.close()
            } catch (ignored: Exception) {}
            activeClientSocket = null
            role = Role.NONE
        }
    }

    private fun startControlReader(dis: DataInputStream, listener: TransferListener) {
        Thread({
            while (!isCancelled.get()) {
                try {
                    val frame = readFrame(dis)
                    when (frame.first) {
                        FRAME_CANCEL -> {
                            isCancelled.set(true)
                            listener.onCancelled(true)
                        }
                        FRAME_PAUSE -> {
                            isPaused.set(true)
                            listener.onPausedChanged(true)
                        }
                        FRAME_RESUME -> {
                            isPaused.set(false)
                            listener.onPausedChanged(false)
                        }
                        FRAME_CHAT -> listener.onChatMessage(String(frame.second, Charsets.UTF_8))
                        else -> inboundFrames.put(frame)
                    }
                } catch (e: Exception) {
                    break
                }
            }
        }, "Athir-ControlReader").start()
    }

    private fun awaitFrame(expected: Byte, timeoutMs: Long): Pair<Byte, ByteArray>? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isCancelled.get()) return null
            val frame = inboundFrames.poll(200, TimeUnit.MILLISECONDS) ?: continue
            if (frame.first == expected) return frame
        }
        return null
    }

    /** Returns true when the transfer was cancelled by the peer. */
    private fun drainControlAndCheckCancel(): Boolean {
        while (true) {
            val frame = inboundFrames.poll() ?: return isCancelled.get()
            when (frame.first) {
                FRAME_CANCEL -> {
                    isCancelled.set(true)
                    return true
                }
                FRAME_PAUSE -> isPaused.set(true)
                FRAME_RESUME -> isPaused.set(false)
            }
        }
    }

    private fun awaitWhilePaused() {
        while (isPaused.get() && !isCancelled.get()) {
            try {
                Thread.sleep(100)
            } catch (ignored: InterruptedException) {
                return
            }
        }
    }

    fun sendChatMessage(message: String) {
        val socket = activeClientSocket ?: return
        try {
            writeFrame(DataOutputStream(socket.outputStream), FRAME_CHAT, message.toByteArray(Charsets.UTF_8))
        } catch (ignored: Exception) {}
    }

    // --- helpers ---

    private fun InputStream.skipFully(bytes: Long) {
        var remaining = bytes
        val discard = ByteArray(64 * 1024)
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
                continue
            }
            val read = read(discard, 0, minOf(discard.size.toLong(), remaining).toInt())
            if (read <= 0) break
            remaining -= read
        }
    }

    private fun computeSha256(uri: String): ByteArray? {
        return try {
            val stream = openInputStream(uri) ?: return null
            val md = MessageDigest.getInstance("SHA-256")
            stream.use { input ->
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    md.update(buffer, 0, read)
                }
            }
            md.digest()
        } catch (e: Exception) {
            null
        }
    }

    private fun openInputStream(uriStr: String): InputStream? {
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

    private fun sanitizeFileName(raw: String): String {
        val base = raw
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("[\\u0000-\\u001F]"), "")
            .replace("..", "_")
            .trim()
        val safe = base.ifBlank { "athir_file_${System.currentTimeMillis()}" }
        return if (safe.length > MAX_FILE_NAME_LENGTH) safe.take(MAX_FILE_NAME_LENGTH) else safe
    }

    private fun applyTlsPolicy(socket: SSLSocket) {
        try {
            val supported = socket.supportedProtocols?.toSet() ?: emptySet()
            val allowed = listOf("TLSv1.3", "TLSv1.2").filter { supported.contains(it) }
            if (allowed.isNotEmpty()) socket.enabledProtocols = allowed.toTypedArray()
        } catch (ignored: Exception) {
        }
    }

    companion object {
        const val FRAME_AUTH_REQ: Byte = AthrFrameCodec.FRAME_AUTH_REQ
        const val FRAME_AUTH_RES: Byte = AthrFrameCodec.FRAME_AUTH_RES
        const val FRAME_CHAT: Byte = AthrFrameCodec.FRAME_CHAT
        const val FRAME_FILE_META: Byte = AthrFrameCodec.FRAME_FILE_META
        const val FRAME_FILE_CHUNK: Byte = AthrFrameCodec.FRAME_FILE_CHUNK
        const val FRAME_FILE_ACK: Byte = AthrFrameCodec.FRAME_FILE_ACK
        const val FRAME_FILE_DONE: Byte = AthrFrameCodec.FRAME_FILE_DONE
        const val FRAME_PAUSE: Byte = AthrFrameCodec.FRAME_PAUSE
        const val FRAME_RESUME: Byte = AthrFrameCodec.FRAME_RESUME
        const val FRAME_CANCEL: Byte = AthrFrameCodec.FRAME_CANCEL
        const val FRAME_SESSION_DONE: Byte = AthrFrameCodec.FRAME_SESSION_DONE

        const val CHUNK_SIZE_BYTES = AthrFrameCodec.CHUNK_SIZE_BYTES
        const val MAX_PAYLOAD_BYTES = AthrFrameCodec.MAX_PAYLOAD_BYTES

        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val SOCKET_READ_TIMEOUT_MS = 300_000
        private const val ACK_TIMEOUT_MS = 20_000
        private const val PROGRESS_INTERVAL_MS = 100L
        private const val MAX_FILE_NAME_LENGTH = 180

        fun formatSizeMb(bytes: Long): String {
            val mb = Math.round((bytes / (1024.0 * 1024.0)) * 10.0) / 10.0
            return "$mb MB"
        }

        fun longToBytes(value: Long): ByteArray = AthrFrameCodec.longToBytes(value)

        fun writeFrame(dos: DataOutputStream, frameType: Byte, payload: ByteArray) =
            AthrFrameCodec.writeFrame(dos, frameType, payload)

        fun readFrame(dis: DataInputStream): Pair<Byte, ByteArray> = AthrFrameCodec.readFrame(dis)
    }
}
