package com.athir.datatransfer

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class HistoryStore(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    data class HistoryRecord(
        val id: Long,
        val fileName: String,
        val fileSizeDisplay: String,
        val direction: String, // "أُرسل" or "استُلم"
        val peerName: String,
        val status: String, // "مكتمل" or "فشل"
        val timestamp: Long
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_HISTORY (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_FILE_NAME TEXT NOT NULL,
                $COL_SIZE_DISPLAY TEXT NOT NULL,
                $COL_DIRECTION TEXT NOT NULL,
                $COL_PEER_NAME TEXT NOT NULL,
                $COL_STATUS TEXT NOT NULL,
                $COL_TIMESTAMP INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HISTORY")
        onCreate(db)
    }

    fun addRecord(
        fileName: String,
        sizeDisplay: String,
        direction: String,
        peerName: String = "",
        status: String = "مكتمل"
    ): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_FILE_NAME, fileName)
            put(COL_SIZE_DISPLAY, sizeDisplay)
            put(COL_DIRECTION, direction)
            put(COL_PEER_NAME, peerName)
            put(COL_STATUS, status)
            put(COL_TIMESTAMP, System.currentTimeMillis())
        }
        return db.insert(TABLE_HISTORY, null, values)
    }

    fun getRecords(limit: Int = 100): List<HistoryRecord> {
        val records = mutableListOf<HistoryRecord>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_HISTORY,
            null,
            null,
            null,
            null,
            null,
            "$COL_TIMESTAMP DESC",
            limit.toString()
        )

        cursor.use {
            val idCol = it.getColumnIndexOrThrow(COL_ID)
            val nameCol = it.getColumnIndexOrThrow(COL_FILE_NAME)
            val sizeCol = it.getColumnIndexOrThrow(COL_SIZE_DISPLAY)
            val dirCol = it.getColumnIndexOrThrow(COL_DIRECTION)
            val peerCol = it.getColumnIndexOrThrow(COL_PEER_NAME)
            val statusCol = it.getColumnIndexOrThrow(COL_STATUS)
            val timeCol = it.getColumnIndexOrThrow(COL_TIMESTAMP)

            while (it.moveToNext()) {
                records.add(
                    HistoryRecord(
                        id = it.getLong(idCol),
                        fileName = it.getString(nameCol),
                        fileSizeDisplay = it.getString(sizeCol),
                        direction = it.getString(dirCol),
                        peerName = it.getString(peerCol),
                        status = it.getString(statusCol),
                        timestamp = it.getLong(timeCol)
                    )
                )
            }
        }
        return records
    }

    fun clearHistory() {
        val db = writableDatabase
        db.delete(TABLE_HISTORY, null, null)
    }

    companion object {
        private const val DATABASE_NAME = "athir_transfers.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_HISTORY = "transfer_history"
        private const val COL_ID = "id"
        private const val COL_FILE_NAME = "file_name"
        private const val COL_SIZE_DISPLAY = "size_display"
        private const val COL_DIRECTION = "direction"
        private const val COL_PEER_NAME = "peer_name"
        private const val COL_STATUS = "status"
        private const val COL_TIMESTAMP = "timestamp"

        @Volatile
        private var instance: HistoryStore? = null

        fun getInstance(context: Context): HistoryStore {
            return instance ?: synchronized(this) {
                instance ?: HistoryStore(context.applicationContext).also { instance = it }
            }
        }
    }
}
