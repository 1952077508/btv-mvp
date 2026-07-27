package com.btv.mvp.data

import android.content.Context
import kotlinx.coroutines.*
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

    private var db: AppDatabase? = null
    private var scope: CoroutineScope? = null
    private var initialized = false

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        db = AppDatabase.getInstance(context)
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        scope?.launch {
            val entities = db?.logDao()?.getRecent(500) ?: emptyList()
            _logs.clear()
            _logs.addAll(entities.map { entity ->
                Entry(entity.timestamp, Level.valueOf(entity.level), entity.tag, entity.message)
            }.reversed())
        }
    }

    @Synchronized
    fun log(level: Level, tag: String, message: String) {
        val entry = Entry(System.currentTimeMillis(), level, tag, message)
        _logs.add(entry)
        if (_logs.size > 500) {
            _logs.removeAt(0)
        }
        listener?.invoke(entry)

        scope?.launch {
            try {
                db?.logDao()?.insert(
                    LogEntity(
                        timestamp = entry.timestamp,
                        level = entry.level.name,
                        tag = entry.tag,
                        message = entry.message
                    )
                )
                db?.logDao()?.trimTo(500)
            } catch (_: Exception) {}
        }
    }

    fun i(tag: String, message: String) = log(Level.INFO, tag, message)
    fun w(tag: String, message: String) = log(Level.WARN, tag, message)
    fun e(tag: String, message: String) = log(Level.ERROR, tag, message)
    fun d(tag: String, message: String) = log(Level.DEBUG, tag, message)

    @Synchronized
    fun clear() {
        _logs.clear()
        scope?.launch {
            try { db?.logDao()?.clearAll() } catch (_: Exception) {}
        }
    }
}
