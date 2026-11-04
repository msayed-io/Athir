package com.athir.datatransfer

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.MediaStore
import android.util.Base64
import android.util.Size
import java.io.ByteArrayOutputStream
import java.io.File

object MediaCatalog {

    data class CatalogItem(
        val id: String,
        val name: String,
        val sizeMB: Double,
        val sizeBytes: Long,
        val uri: String,
        val mimeType: String,
        val category: String,
        val thumbnail: String? = null,
        val extra: String? = null
    )

    // ------------------------------------------------------------------
    // Caching: the send screen used to re-query MediaStore and decode every thumbnail on
    // each visit, which is what produced the "loading" flash every single time.
    // ------------------------------------------------------------------
    private data class CacheEntry(val at: Long, val items: List<CatalogItem>)

    private val cache = java.util.concurrent.ConcurrentHashMap<String, CacheEntry>()
    private const val CACHE_TTL_MS = 90_000L

    fun invalidateCache() {
        cache.clear()
    }

    /** Cached dispatcher used by the plugin. */
    fun query(context: Context, category: String, query: String?, limit: Int): List<CatalogItem> {
        val key = "$category|${query ?: ""}|$limit"
        val now = System.currentTimeMillis()
        cache[key]?.let { entry ->
            if (now - entry.at < CACHE_TTL_MS) return entry.items
        }
        val items = when (category) {
            "apps" -> getApps(context, query, limit)
            "photos" -> getPhotos(context, query, limit)
            "videos" -> getVideos(context, query, limit)
            "audio" -> getAudio(context, query, limit)
            "contacts" -> getContacts(context, query, limit)
            "files" -> getFiles(context, query, limit)
            else -> emptyList()
        }
        cache[key] = CacheEntry(now, items)
        return items
    }

    /** Small, fast thumbnail for any content uri (images, video frames, album art). */
    private fun thumbFromUri(context: Context, uri: Uri, edge: Int = 96): String? {
        return try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(uri, Size(edge, edge), null)
            } else {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    retriever.frameAtTime ?: retriever.embeddedPicture?.let { bytes ->
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                } finally {
                    try {
                        retriever.release()
                    } catch (ignored: Exception) {}
                }
            } ?: return null

            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 72, stream)
            "data:image/jpeg;base64," + Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    private fun drawableToBase64(drawable: Drawable, width: Int = 72, height: Int = 72): String? {
        return try {
            val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
                Bitmap.createScaledBitmap(drawable.bitmap, width, height, true)
            } else {
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            }
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 85, stream)
            val bytes = stream.toByteArray()
            "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    fun getApps(context: Context, query: String? = null, limit: Int = 100): List<CatalogItem> {
        val items = mutableListOf<CatalogItem>()
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }

        val seenPackages = mutableSetOf<String>()
        val lowerQuery = query?.trim()?.lowercase()

        for (info in resolveInfos) {
            val pkgName = info.activityInfo.packageName
            if (pkgName == context.packageName || seenPackages.contains(pkgName)) continue
            seenPackages.add(pkgName)

            val appName = info.loadLabel(pm).toString()
            if (!lowerQuery.isNullOrBlank() && !appName.lowercase().contains(lowerQuery) && !pkgName.lowercase().contains(lowerQuery)) {
                continue
            }

            try {
                val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(pkgName, PackageManager.ApplicationInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(pkgName, 0)
                }

                val sourceDir = appInfo.sourceDir
                val file = File(sourceDir)
                val sizeBytes = if (file.exists()) file.length() else 0L
                val sizeMB = Math.round((sizeBytes / (1024.0 * 1024.0)) * 10.0) / 10.0
                val icon = info.loadIcon(pm)
                val thumb = drawableToBase64(icon, 64, 64)

                val fileUri = Uri.fromFile(file).toString()

                items.add(
                    CatalogItem(
                        id = "app:$pkgName",
                        name = "$appName.apk",
                        sizeMB = if (sizeMB > 0.0) sizeMB else 1.0,
                        sizeBytes = sizeBytes,
                        uri = fileUri,
                        mimeType = "application/vnd.android.package-archive",
                        category = "apps",
                        thumbnail = thumb,
                        extra = pkgName
                    )
                )

                if (items.size >= limit) break
            } catch (ignored: Exception) {
            }
        }

        return items
    }

    fun getPhotos(context: Context, query: String? = null, limit: Int = 100): List<CatalogItem> {
        val items = mutableListOf<CatalogItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE
        )

        var selection: String? = null
        var selectionArgs: Array<String>? = null
        if (!query.isNullOrBlank()) {
            selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
            selectionArgs = arrayOf("%$query%")
        }

        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

                while (cursor.moveToNext() && items.size < limit) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "photo_$id.jpg"
                    val sizeBytes = cursor.getLong(sizeCol)
                    val mime = cursor.getString(mimeCol) ?: "image/jpeg"
                    val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    val sizeMB = Math.round((sizeBytes / (1024.0 * 1024.0)) * 10.0) / 10.0

                    val thumbBase64 = thumbFromUri(context, contentUri)

                    items.add(
                        CatalogItem(
                            id = "photo:$id",
                            name = name,
                            sizeMB = if (sizeMB > 0.0) sizeMB else 0.1,
                            sizeBytes = sizeBytes,
                            uri = contentUri.toString(),
                            mimeType = mime,
                            category = "photos",
                            thumbnail = thumbBase64
                        )
                    )
                }
            }
        } catch (ignored: Exception) {
        }

        return items
    }

    fun getVideos(context: Context, query: String? = null, limit: Int = 100): List<CatalogItem> {
        val items = mutableListOf<CatalogItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.MIME_TYPE
        )

        var selection: String? = null
        var selectionArgs: Array<String>? = null
        if (!query.isNullOrBlank()) {
            selection = "${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?"
            selectionArgs = arrayOf("%$query%")
        }

        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)

                while (cursor.moveToNext() && items.size < limit) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "video_$id.mp4"
                    val sizeBytes = cursor.getLong(sizeCol)
                    val mime = cursor.getString(mimeCol) ?: "video/mp4"
                    val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    val sizeMB = Math.round((sizeBytes / (1024.0 * 1024.0)) * 10.0) / 10.0

                    items.add(
                        CatalogItem(
                            id = "video:$id",
                            name = name,
                            sizeMB = if (sizeMB > 0.0) sizeMB else 0.1,
                            sizeBytes = sizeBytes,
                            uri = contentUri.toString(),
                            mimeType = mime,
                            category = "videos",
                            thumbnail = thumbFromUri(context, contentUri)
                        )
                    )
                }
            }
        } catch (ignored: Exception) {
        }

        return items
    }

    fun getAudio(context: Context, query: String? = null, limit: Int = 100): List<CatalogItem> {
        val items = mutableListOf<CatalogItem>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.ARTIST
        )

        var selection: String? = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        var selectionArgs: Array<String>? = null
        if (!query.isNullOrBlank()) {
            selection += " AND ${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?"
            selectionArgs = arrayOf("%$query%")
        }

        val sortOrder = "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)

                while (cursor.moveToNext() && items.size < limit) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "audio_$id.mp3"
                    val sizeBytes = cursor.getLong(sizeCol)
                    val mime = cursor.getString(mimeCol) ?: "audio/mpeg"
                    val artist = cursor.getString(artistCol)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val sizeMB = Math.round((sizeBytes / (1024.0 * 1024.0)) * 10.0) / 10.0

                    items.add(
                        CatalogItem(
                            id = "audio:$id",
                            name = name,
                            sizeMB = if (sizeMB > 0.0) sizeMB else 0.1,
                            sizeBytes = sizeBytes,
                            uri = contentUri.toString(),
                            mimeType = mime,
                            category = "audio",
                            thumbnail = thumbFromUri(context, contentUri),
                            extra = artist
                        )
                    )
                }
            }
        } catch (ignored: Exception) {
        }

        return items
    }

    fun getContacts(context: Context, query: String? = null, limit: Int = 100): List<CatalogItem> {
        val items = mutableListOf<CatalogItem>()
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.LOOKUP_KEY
        )

        var selection: String? = null
        var selectionArgs: Array<String>? = null
        if (!query.isNullOrBlank()) {
            selection = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?"
            selectionArgs = arrayOf("%$query%")
        }

        try {
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
                val nameCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                val lookupCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)

                while (cursor.moveToNext() && items.size < limit) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "جهة اتصال"
                    val lookupKey = cursor.getString(lookupCol)
                    val vcardUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, lookupKey)

                    items.add(
                        CatalogItem(
                            id = "contact:$id",
                            name = "$name.vcf",
                            sizeMB = 0.1,
                            sizeBytes = 512L,
                            uri = vcardUri.toString(),
                            mimeType = "text/x-vcard",
                            category = "contacts"
                        )
                    )
                }
            }
        } catch (ignored: Exception) {
        }

        return items
    }

    /**
     * Files tab.
     *
     * `MediaStore.Files` alone is not enough: on modern Android it only exposes indexed
     * media, so PDFs, ZIPs, APKs and downloads were missing entirely (the reported "the
     * files tab shows nothing"). The query therefore merges every readable collection:
     * Files + Downloads + Documents, de-duplicated by uri, newest first.
     *
     * Nothing outside those collections is reachable without the Storage Access Framework,
     * which is why the send screen keeps "مستندات الجهاز" as the complete, platform
     * sanctioned path for arbitrary folders.
     */
    fun getFiles(context: Context, query: String? = null, limit: Int = 100): List<CatalogItem> {
        val items = LinkedHashMap<String, CatalogItem>()
        val limitPerCollection = (limit * 2).coerceAtLeast(60)

        fun collect(uri: Uri, idColumn: String) {
            val projection = arrayOf(
                idColumn,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.DATE_MODIFIED
            )
            var selection = "${MediaStore.Files.FileColumns.SIZE} > 0"
            var selectionArgs: Array<String>? = null
            if (!query.isNullOrBlank()) {
                selection += " AND ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
                selectionArgs = arrayOf("%$query%")
            }
            try {
                context.contentResolver.query(
                    uri,
                    projection,
                    selection,
                    selectionArgs,
                    "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(idColumn)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                    val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                    var added = 0
                    while (cursor.moveToNext() && added < limitPerCollection && items.size < limit) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: "file_$id"
                        val sizeBytes = cursor.getLong(sizeCol)
                        if (sizeBytes <= 0L) continue
                        val mime = cursor.getString(mimeCol) ?: "application/octet-stream"
                        val contentUri = ContentUris.withAppendedId(uri, id)
                        val key = contentUri.toString()
                        if (items.containsKey(key)) continue
                        val sizeMB = Math.round((sizeBytes / (1024.0 * 1024.0)) * 10.0) / 10.0
                        items[key] = CatalogItem(
                            id = "file:$id",
                            name = name,
                            sizeMB = if (sizeMB > 0.0) sizeMB else 0.1,
                            sizeBytes = sizeBytes,
                            uri = key,
                            mimeType = mime,
                            category = "files"
                        )
                        added++
                    }
                }
            } catch (ignored: Exception) {
            }
        }

        collect(MediaStore.Files.getContentUri("external"), MediaStore.Files.FileColumns._ID)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            collect(MediaStore.Downloads.EXTERNAL_CONTENT_URI, MediaStore.Downloads._ID)
        }
        return items.values.toList()
    }
}
