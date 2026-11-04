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
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLServerSocketFactory
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

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
        fun onAllCompleted(isSender: Boolean)
        fun onError(error: String)
        fun onChatMessage(message: String)
    }

    private val isPaused = AtomicBoolean(false)
    private val isCancelled = AtomicBoolean(false)

    private var activeServerSocket: ServerSocket? = null
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
        sslContext: javax.net.ssl.SSLContext,
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
                val socket = serverSocket.accept() as SSLSocket
                activeClientSocket = socket

                handleReceiverConnection(socket, expectedSessionSecret, listener)
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
                socket.close()
                listener.onError("Authentication failed: invalid frame")
                return
            }

            val receivedSecret = String(authPayload, Charsets.UTF_8).trim()
            if (receivedSecret != expectedSessionSecret.trim()) {
                writeFrame(dos, FRAME_AUTH_RES, byteArrayOf(0x01)) // REJECTED
                socket.close()
                listener.onError("Authentication failed: secret mismatch")
                return
            }

            // Auth succeeded
            writeFrame(dos, FRAME_AUTH_RES, byteArrayOf(0x00)) // OK

            // 2. Receive frames loop
            var currentFileOut: OutputStream? = null
            var currentFileName = ""
            var currentFileSize = 0L
            var currentFileReceived = 0L
            var totalBytesExpected = 0L
            var totalBytesTransferred = 0L
            var lastSpeedCheckTime = System.currentTimeMillis()
            var bytesSinceLastCheck = 0L
            var currentSpeed = 0L
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
                        currentFileName = json.optString("name", "received_file")
                        currentFileSize = json.optLong("size", 0L)
                        totalFiles = json.optInt("totalFiles", 1)
                        fileIndex = json.optInt("fileIndex", 0)
                        totalBytesExpected = json.optLong("totalBytes", currentFileSize)
                        currentFileReceived = 0L
                        shaDigest = MessageDigest.getInstance("SHA-256")

                        currentFileOut = createDownloadOutputStream(context, currentFileName, json.optString("mimeType", "*/*"))
                    }

                    FRAME_FILE_CHUNK -> {
                        currentFileOut?.write(payload)
                        shaDigest?.update(payload)
                        currentFileReceived += payload.size
                        totalBytesTransferred += payload.size
                        bytesSinceLastCheck += payload.size

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastSpeedCheckTime
                        if (elapsed >= 100) {
                            currentSpeed = (bytesSinceLastCheck * 1000L) / Math.max(elapsed, 1L)
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

                        listener.onFileCompleted(currentFileName, currentFileSize, true)
                        HistoryStore.getInstance(context).addRecord(
                            currentFileName,
                            "${Math.round(currentFileSize / (1024.0 * 1024.0) * 10.0) / 10.0} MB",
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
        } finally {
            try {
                socket.close()
            } catch (ignored: Exception) {}
        }
    }

    // --- SENDER CLIENT ---
    fun sendFiles(
        host: String,
        port: Int,
        sslContext: javax.net.ssl.SSLContext,
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
            socket.connect(InetSocketAddress(host, port), 8000)
            socket.startHandshake()

            val dos = DataOutputStream(socket.outputStream)
            val dis = DataInputStream(socket.inputStream)

            // 1. Send AUTH_REQ
            writeFrame(dos, FRAME_AUTH_REQ, sessionSecret.toByteArray(Charsets.UTF_8))

            // 2. Read AUTH_RES
            val (authResType, authResPayload) = readFrame(dis)
            if (authResType != FRAME_AUTH_RES || authResPayload.isEmpty() || authResPayload[0] != 0x00.toByte()) {
                socket.close()
                listener.onError("Receiver rejected authentication")
                return
            }

            // 3. Stream all files
            val totalBytes = items.sumOf { it.sizeBytes }
            var totalBytesSent = 0L
            var lastSpeedCheckTime = System.currentTimeMillis()
            var bytesSinceLastCheck = 0L
            var currentSpeed = 0L

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

                val buffer = ByteArray(64 * 1024)
                val sha = MessageDigest.getInstance("SHA-256")
                var read: Int

                inputStream.use { stream ->
                    while (!isCancelled.get()) {
                        while (isPaused.get() && !isCancelled.get()) {
                            Thread.sleep(100)
                        }

                        read = stream.read(buffer)
                        if (read <= 0) break

                        val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
                        writeFrame(dos, FRAME_FILE_CHUNK, chunk)
                        sha.update(chunk)

                        totalBytesSent += read
                        bytesSinceLastCheck += read

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastSpeedCheckTime
                        if (elapsed >= 100) {
                            currentSpeed = (bytesSinceLastCheck * 1000L) / Math.max(elapsed, 1L)
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

                if (!isCancelled.get()) {
                    val hash = sha.digest()
                    writeFrame(dos, FRAME_FILE_DONE, hash)

                    listener.onFileCompleted(item.name, item.sizeBytes, false)
                    HistoryStore.getInstance(context).addRecord(
                        item.name,
                        "${item.sizeMB} MB",
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

    private fun createDownloadOutputStream(context: Context, fileName: String, mimeType: String): OutputStream {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Athir")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Cannot create MediaStore download entry")
            context.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Cannot open download output stream")
        } else {
            val athirDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Athir")
            if (!athirDir.exists()) athirDir.mkdirs()
            val targetFile = File(athirDir, fileName)
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
