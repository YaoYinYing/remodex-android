package com.remodex.mobile.service.logging

import android.content.Context
import android.util.Log
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class LoggerLevel(val priority: Int) {
    DEBUG(0),
    INFO(1),
    WARN(2),
    ERROR(3);

    companion object {
        fun fromStorage(raw: String?): LoggerLevel {
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: INFO
        }
    }
}

data class LoggerSettings(
    val level: LoggerLevel = LoggerLevel.INFO,
    val maxLines: Int = 3_000
)

data class LoggerEntry(
    val id: Long,
    val timestampMillis: Long,
    val level: LoggerLevel,
    val tag: String,
    val message: String
)

object AppLogger {
    private const val MIN_LINES = 200
    private const val MAX_LINES = 20_000
    private val lock = Any()
    private val nextId = AtomicLong(1)
    private val buffer = ArrayDeque<LoggerEntry>()
    private var databaseStore: LoggerDatabaseStore? = null

    private val _settings = MutableStateFlow(LoggerSettings())
    val settings: StateFlow<LoggerSettings> = _settings

    private val _entries = MutableStateFlow<List<LoggerEntry>>(emptyList())
    val entries: StateFlow<List<LoggerEntry>> = _entries

    fun initialize(context: Context) {
        synchronized(lock) {
            if (databaseStore != null) {
                return
            }
            databaseStore = LoggerDatabaseStore(context)
            val restored = databaseStore?.loadRecent(_settings.value.maxLines).orEmpty()
            buffer.clear()
            buffer.addAll(restored)
            val highestId = restored.maxOfOrNull { it.id } ?: 0L
            nextId.set(highestId + 1L)
            _entries.value = buffer.toList()
        }
    }

    fun configure(level: LoggerLevel? = null, maxLines: Int? = null) {
        synchronized(lock) {
            val current = _settings.value
            val resolved = current.copy(
                level = level ?: current.level,
                maxLines = clampLines(maxLines ?: current.maxLines)
            )
            _settings.value = resolved
            trimBuffer(resolved.maxLines)
            databaseStore?.pruneToMaxLines(resolved.maxLines)
            _entries.value = buffer.toList()
        }
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            nextId.set(1)
            databaseStore?.clear()
            _entries.value = emptyList()
        }
    }

    fun debug(tag: String, message: String, throwable: Throwable? = null) {
        log(LoggerLevel.DEBUG, tag, message, throwable)
    }

    fun info(tag: String, message: String, throwable: Throwable? = null) {
        log(LoggerLevel.INFO, tag, message, throwable)
    }

    fun warn(tag: String, message: String, throwable: Throwable? = null) {
        log(LoggerLevel.WARN, tag, message, throwable)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        log(LoggerLevel.ERROR, tag, message, throwable)
    }

    fun log(level: LoggerLevel, tag: String, message: String, throwable: Throwable? = null) {
        val settingsSnapshot = _settings.value
        if (level.priority < settingsSnapshot.level.priority) {
            return
        }

        val normalizedTag = tag.trim().ifBlank { "App" }
        val normalizedMessage = buildString {
            append(message.trim())
            if (throwable != null) {
                append(" | ")
                append(throwable::class.java.simpleName)
                throwable.message?.takeIf { it.isNotBlank() }?.let {
                    append(": ")
                    append(it)
                }
            }
        }
        val redactedMessage = SensitiveLogRedactor.redact(normalizedMessage)

        var entry = LoggerEntry(
            id = nextId.getAndIncrement(),
            timestampMillis = System.currentTimeMillis(),
            level = level,
            tag = normalizedTag,
            message = redactedMessage
        )
        synchronized(lock) {
            val persistedId = databaseStore?.insert(entry)
            if (persistedId != null && persistedId > 0L) {
                entry = entry.copy(id = persistedId)
                if (persistedId >= nextId.get()) {
                    nextId.set(persistedId + 1)
                }
            }

            buffer.addLast(entry)
            trimBuffer(_settings.value.maxLines)
            databaseStore?.pruneToMaxLines(_settings.value.maxLines)
            _entries.value = buffer.toList()
        }

        val logTag = "Remodex-$normalizedTag"
        runCatching {
            when (level) {
                LoggerLevel.DEBUG -> Log.d(logTag, redactedMessage)
                LoggerLevel.INFO -> Log.i(logTag, redactedMessage)
                LoggerLevel.WARN -> Log.w(logTag, redactedMessage)
                LoggerLevel.ERROR -> Log.e(logTag, redactedMessage)
            }
        }
    }

    private fun trimBuffer(maxLines: Int) {
        while (buffer.size > maxLines) {
            buffer.removeFirst()
        }
    }

    private fun clampLines(value: Int): Int {
        return value.coerceIn(MIN_LINES, MAX_LINES)
    }
}
