package com.athir.datatransfer

import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer

/**
 * ATHR frame wire format.
 *
 * Pure JVM code with no Android dependencies on purpose: the CI pipeline runs
 * `testDebugUnitTest` on a normal JVM without any device or emulator, so this codec can be
 * exercised for real (round-trip, bounds rejection, resume-offset encoding) on every push.
 */
object AthrFrameCodec {

    val MAGIC = byteArrayOf(0x41, 0x54, 0x48, 0x52) // "ATHR"
    const val PROTOCOL_VERSION: Byte = 0x02

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

    const val CHUNK_SIZE_BYTES = 64 * 1024
    const val MAX_PAYLOAD_BYTES = 512 * 1024

    fun writeFrame(dos: DataOutputStream, frameType: Byte, payload: ByteArray) {
        dos.write(MAGIC)
        dos.writeByte(PROTOCOL_VERSION.toInt())
        dos.writeByte(frameType.toInt())
        dos.writeInt(payload.size)
        if (payload.isNotEmpty()) dos.write(payload)
        dos.flush()
    }

    fun readFrame(dis: DataInputStream): Pair<Byte, ByteArray> {
        val magic = ByteArray(4)
        dis.readFully(magic)
        if (!magic.contentEquals(MAGIC)) throw IllegalStateException("Invalid ATHR frame magic")
        val version = dis.readByte()
        if (version != PROTOCOL_VERSION) throw IllegalStateException("Unsupported protocol version: $version")
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

    fun longToBytes(value: Long): ByteArray = ByteBuffer.allocate(8).putLong(value).array()

    fun bytesToLong(bytes: ByteArray): Long? =
        if (bytes.size == 8) ByteBuffer.wrap(bytes).long else null

    fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    fun hexToBytesOrNull(hex: String): ByteArray? {
        if (hex.isEmpty() || hex.length % 2 != 0) return null
        return try {
            ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        } catch (e: Exception) {
            null
        }
    }
}
