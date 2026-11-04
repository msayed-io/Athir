package com.athir.datatransfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Hardware-free verification of the resume bookkeeping used before a chunked transfer
 * continues from an offset. Runs on the CI JVM, no device required.
 */
class PartialFileStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun writePart(bytes: Int): File {
        val file = tempFolder.newFile("abc123.part")
        file.writeBytes(ByteArray(bytes) { it.toByte() })
        return file
    }

    @Test
    fun `missing part file yields zero offset`() {
        val missing = File(tempFolder.root, "does-not-exist.part")
        assertEquals(0L, PartialFileStore.resumeOffset(missing, 1024L))
    }

    @Test
    fun `partial content is resumed from its real length`() {
        val part = writePart(4096)
        assertEquals(4096L, PartialFileStore.resumeOffset(part, 10_000L))
        // The file must survive a resumable decision.
        assertEquals(4096L, part.length())
    }

    @Test
    fun `empty part file yields zero offset`() {
        val part = writePart(0)
        assertEquals(0L, PartialFileStore.resumeOffset(part, 10_000L))
    }

    @Test
    fun `oversized part is discarded`() {
        val part = writePart(2048)
        assertEquals(0L, PartialFileStore.resumeOffset(part, 1024L))
        assertFalse("oversized partial must be deleted", part.exists())
    }

    @Test
    fun `complete but unverified part is discarded`() {
        val part = writePart(1024)
        assertEquals(0L, PartialFileStore.resumeOffset(part, 1024L))
        assertFalse("complete partial must be re-received", part.exists())
    }

    @Test
    fun `unknown expected size falls back to the on-disk length`() {
        val part = writePart(777)
        assertEquals(777L, PartialFileStore.resumeOffset(part, 0L))
    }
}
