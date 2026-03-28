package com.remodex.mobile.service.logging

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

private const val LOGGER_DB_NAME = "remodex_logger.db"
private const val LOGGER_DB_VERSION = 1
private const val TABLE_LOG_ENTRIES = "log_entries"

class LoggerDatabaseStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    LOGGER_DB_NAME,
    null,
    LOGGER_DB_VERSION
) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_LOG_ENTRIES (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestampMillis INTEGER NOT NULL,
                level TEXT NOT NULL,
                tag TEXT NOT NULL,
                message TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX idx_log_entries_timestamp
            ON $TABLE_LOG_ENTRIES(timestampMillis)
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_LOG_ENTRIES")
        onCreate(db)
    }

    fun loadRecent(limit: Int): List<LoggerEntry> {
        val safeLimit = limit.coerceAtLeast(1)
        val rows = mutableListOf<LoggerEntry>()
        val query = """
            SELECT id, timestampMillis, level, tag, message
            FROM $TABLE_LOG_ENTRIES
            ORDER BY id DESC
            LIMIT $safeLimit
        """.trimIndent()
        readableDatabase.rawQuery(query, null).use { cursor ->
            while (cursor.moveToNext()) {
                val levelRaw = cursor.getString(2)
                rows += LoggerEntry(
                    id = cursor.getLong(0),
                    timestampMillis = cursor.getLong(1),
                    level = LoggerLevel.fromStorage(levelRaw),
                    tag = cursor.getString(3),
                    message = cursor.getString(4)
                )
            }
        }
        return rows.asReversed()
    }

    fun insert(entry: LoggerEntry): Long {
        val values = ContentValues().apply {
            put("timestampMillis", entry.timestampMillis)
            put("level", entry.level.name)
            put("tag", entry.tag)
            put("message", entry.message)
        }
        return writableDatabase.insert(TABLE_LOG_ENTRIES, null, values)
    }

    fun clear() {
        writableDatabase.delete(TABLE_LOG_ENTRIES, null, null)
    }

    fun pruneToMaxLines(maxLines: Int) {
        val safeMax = maxLines.coerceAtLeast(1)
        writableDatabase.execSQL(
            """
            DELETE FROM $TABLE_LOG_ENTRIES
            WHERE id NOT IN (
                SELECT id FROM $TABLE_LOG_ENTRIES
                ORDER BY id DESC
                LIMIT $safeMax
            )
            """.trimIndent()
        )
    }
}
