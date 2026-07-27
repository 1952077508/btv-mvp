package com.btv.mvp.data

import java.text.SimpleDateFormat
import java.util.*

object AppLogger {
    enum class Level { INFO, WARN, ERROR, DEBUG }

    data class Entry(
        val timestamp: Long,
        val level: Level,
        val tag: String,
        val message: String
    ) {
        fun formatted(): String {
            val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            return "[${sdf.format(Date(timestamp))}] [${level.name.padEnd(5)}] [$tag] $message"
        }
    }

    private val _logs = mutableListOf<Entry>()
    val logs: List<Entry> get() = _logs.toList()

    @Volatile
    var listener: ((Entry) -> Unit)? = null

    @Synchronized
    fun log(level: Level, tag: String, message: String) {
        val entry = Entry(System.currentTimeMillis(), level, tag, message)
        _logs.add(entry)
        if (_logs.size > 500) {
            _logs.removeAt(0)
        }
        listener?.invoke(entry)
    }

    fun i(tag: String, message: String) = log(Level.INFO, tag, message)
    fun w(tag: String, message: String) = log(Level.WARN, tag, message)
    fun e(tag: String, message: String) = log(Level.ERROR, tag, message)
    fun d(tag: String, message: String) = log(Level.DEBUG, tag, message)

    @Synchronized
    fun clear() {
        _logs.clear()
    }
}
