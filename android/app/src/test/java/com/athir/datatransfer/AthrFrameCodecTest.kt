package com.athir.datatransfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Hardware-free verification of the ATHR wire format.
 *
 * Runs on the CI runner JVM (`./gradlew testDebugUnitTest`) with no emulator and no device.
 * It proves the frame envelope, the payload cap and the resume-offset encoding that the
 * transfer engine relies on.
 */
class AthrFrameCodecTest {

    private fun encode(frameType: Byte, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        AthrFrameCodec.writeFrame(DataOutputStream(out), frameType, payload)
        return out.toByteArray()
    }

    private fun decode(bytes: ByteArray): Pair<Byte, ByteArray> =
        AthrFrameCodec.readFrame(DataInputStream(ByteArrayInputStream(bytes)))

    @Test
    fun `empty control frame round-trips`() {
        val (type, payload) = decode(encode(AthrFrameCodec.FRAME_PAUSE, ByteArray(0)))
        assertEquals(AthrFrameCodec.FRAME_PAUSE, type)
        assertEquals(0, payload.size)
    }

    @Test
    fun `chunk payload round-trips byte for byte`() {
        val payload = ByteArray(AthrFrameCodec.CHUNK_SIZE_BYTES) { (it % 251).toByte() }
        val (type, decoded) = decode(encode(AthrFrameCodec.FRAME_FILE_CHUNK, payload))
        assertEquals(AthrFrameCodec.FRAME_FILE_CHUNK, type)
        assertTrue(payload.contentEquals(decoded))
    }

    @Test
    fun `resume offset survives the ACK frame`() {
        val offset = 3L * 1024L * 1024L * 1024L + 12345L // larger than 32-bit
        val encoded = AthrFrameCodec.longToBytes(offset)
        val (type, decoded) = decode(encode(AthrFrameCodec.FRAME_FILE_ACK, encoded))
        assertEquals(AthrFrameCodec.FRAME_FILE_ACK, type)
        assertEquals(offset, AthrFrameCodec.bytesToLong(decoded))
    }

    @Test
    fun `oversized payload is rejected on write path length`() {
        val tooLarge = AthrFrameCodec.MAX_PAYLOAD_BYTES + 1
        val out = ByteArrayOutputStream()
        val dos = DataOutputStream(out)
        dos.write(AthrFrameCodec.MAGIC)
        dos.writeByte(AthrFrameCodec.PROTOCOL_VERSION.toInt())
        dos.writeByte(AthrFrameCodec.FRAME_FILE_CHUNK.toInt())
        dos.writeInt(tooLarge)
        dos.flush()

        val exception = assertThrows(IllegalStateException::class.java) {
            AthrFrameCodec.readFrame(DataInputStream(ByteArrayInputStream(out.toByteArray())))
        }
        assertTrue(exception.message!!.contains("Rejected frame payload length"))
    }

    @Test
    fun `corrupted magic is rejected`() {
        val bytes = encode(AthrFrameCodec.FRAME_SESSION_DONE, ByteArray(0))
        bytes[0] = 0x00
        assertThrows(IllegalStateException::class.java) {
            AthrFrameCodec.readFrame(DataInputStream(ByteArrayInputStream(bytes)))
        }
    }

    @Test
    fun `unexpected protocol version is rejected`() {
        val bytes = encode(AthrFrameCodec.FRAME_CHAT, "مرحبا".toByteArray())
        bytes[4] = 0x01 // previous protocol revision
        assertThrows(IllegalStateException::class.java) {
            AthrFrameCodec.readFrame(DataInputStream(ByteArrayInputStream(bytes)))
        }
    }

    @Test
    fun `sha256 hex helpers round-trip`() {
        val raw = ByteArray(32) { it.toByte() }
        val hex = AthrFrameCodec.toHex(raw)
        assertEquals(64, hex.length)
        assertTrue(raw.contentEquals(AthrFrameCodec.hexToBytesOrNull(hex)))
        assertNull(AthrFrameCodec.hexToBytesOrNull(""))
        assertNull(AthrFrameCodec.hexToBytesOrNull("abc"))
        assertNull(AthrFrameCodec.hexToBytesOrNull("zz"))
    }

    @Test
    fun `bytesToLong rejects wrong width`() {
        assertNull(AthrFrameCodec.bytesToLong(ByteArray(4)))
    }
}
