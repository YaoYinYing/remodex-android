package com.remodex.mobile.service.logging

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

    private val _settings = MutableStateFlow(LoggerSettings())
    val settings: StateFlow<LoggerSettings> = _settings

    private val _entries = MutableStateFlow<List<LoggerEntry>>(emptyList())
    val entries: StateFlow<List<LoggerEntry>> = _entries

    fun configure(level: LoggerLevel? = null, maxLines: Int? = null) {
        synchronized(lock) {
            val current = _settings.value
            val resolved = current.copy(
                level = level ?: current.level,
                maxLines = clampLines(maxLines ?: current.maxLines)
            )
            _settings.value = resolved
            trimBuffer(resolved.maxLines)
            _entries.value = buffer.toList()
        }
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
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

        synchronized(lock) {
            buffer.addLast(
                LoggerEntry(
                    id = nextId.getAndIncrement(),
                    timestampMillis = System.currentTimeMillis(),
                    level = level,
                    tag = normalizedTag,
                    message = normalizedMessage
                )
            )
            trimBuffer(_settings.value.maxLines)
            _entries.value = buffer.toList()
        }

        val logTag = "Remodex-$normalizedTag"
        runCatching {
            when (level) {
                LoggerLevel.DEBUG -> Log.d(logTag, normalizedMessage, throwable)
                LoggerLevel.INFO -> Log.i(logTag, normalizedMessage, throwable)
                LoggerLevel.WARN -> Log.w(logTag, normalizedMessage, throwable)
                LoggerLevel.ERROR -> Log.e(logTag, normalizedMessage, throwable)
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
