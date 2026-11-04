package com.athir.datatransfer

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Holds partially received files so a transfer can resume from the last byte that
 * actually reached the disk.
 *
 * Design: every incoming file is first written to an app-private directory
 * (`<externalFilesDir>/athir_partial/<sha256>.part`). Because it is a plain file we can
 * query its exact length and seek/append, which is impossible with a MediaStore stream.
 * When the whole file is received and its SHA-256 is verified, it is copied into the
 * public `Downloads/Athir` collection and the partial file is removed.
 */
object PartialFileStore {

    private const val LOG_TAG = "AthirPartial"
    private const val PARTIAL_DIR = "athir_partial"

    fun partialDir(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(base, PARTIAL_DIR)
        if (!dir.exists() && !dir.mkdirs()) {
            Log.d(LOG_TAG, "Could not create partial directory at ${dir.absolutePath}")
        }
        return dir
    }

    fun partFile(context: Context, key: String, fileName: String): File {
        val safeKey = key.ifBlank { fileName.filter { it.isLetterOrDigit() }.take(24) }.take(64)
        return File(partialDir(context), "$safeKey.part")
    }

    /** Bytes already on disk for this transfer, or 0 when there is nothing reusable. */
    fun resumeOffset(partFile: File, expectedSize: Long): Long {
        if (!partFile.exists()) return 0L
        val length = partFile.length()
        return when {
            length <= 0L -> 0L
            expectedSize > 0L && length > expectedSize -> {
                Log.d(LOG_TAG, "Discarding oversized partial file (${length}B > ${expectedSize}B)")
                partFile.delete()
                0L
            }
            expectedSize in 1 until Long.MAX_VALUE && length == expectedSize -> {
                // Fully received but never finalised: restart cleanly rather than
                // trusting an unverified file.
                Log.d(LOG_TAG, "Discarding complete-but-unverified partial file")
                partFile.delete()
                0L
            }
            else -> length
        }
    }

    fun openAppend(partFile: File, append: Boolean): OutputStream {
        partFile.parentFile?.mkdirs()
        return FileOutputStream(partFile, append)
    }

    fun delete(partFile: File) {
        try {
            if (partFile.exists() && !partFile.delete()) {
                Log.d(LOG_TAG, "Could not delete ${partFile.name}")
            }
        } catch (e: Exception) {
            Log.d(LOG_TAG, "delete threw: ${e.message}")
        }
    }

    /**
     * Copies a verified partial file into the device Downloads collection.
     * @return true when the file reached its final public location.
     */
    fun publishToDownloads(context: Context, source: File, fileName: String, mimeType: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Athir")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return false
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    source.inputStream().use { input -> input.copyTo(out, 128 * 1024) }
                } ?: return false
                true
            } else {
                val target = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "Athir"
                )
                if (!target.exists() && !target.mkdirs()) {
                    Log.d(LOG_TAG, "Cannot create ${target.absolutePath}")
                    return false
                }
                val destination = File(target, fileName)
                val basePath = target.canonicalPath + File.separator
                if (!destination.canonicalPath.startsWith(basePath)) {
                    Log.d(LOG_TAG, "Rejected unsafe destination path for $fileName")
                    return false
                }
                source.inputStream().use { input ->
                    FileOutputStream(destination).use { out -> input.copyTo(out, 128 * 1024) }
                }
                true
            }
        } catch (e: Exception) {
            Log.d(LOG_TAG, "publishToDownloads threw: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }
}
