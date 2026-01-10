package com.samsung.remote.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

object DebugLogger {

    private const val TAG = "SamsungRemoteDebug"
    private const val PREFS_NAME = "debug_prefs"
    private const val KEY_DEBUG_ENABLED = "debug_enabled"
    private const val MAX_LOGS = 500

    data class LogEntry(
        val timestamp: String,
        val level: LogLevel,
        val tag: String,
        val message: String
    )

    enum class LogLevel {
        VERBOSE, DEBUG, INFO, WARNING, ERROR
    }

    private val logs = CopyOnWriteArrayList<LogEntry>()
    private val listeners = CopyOnWriteArrayList<LogListener>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    interface LogListener {
        fun onNewLog(entry: LogEntry)
    }

    private var debugEnabled = false
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        debugEnabled = prefs.getBoolean(KEY_DEBUG_ENABLED, false)
    }

    fun isDebugEnabled(): Boolean = debugEnabled

    fun setDebugEnabled(enabled: Boolean, context: Context) {
        debugEnabled = enabled
        prefs.edit().putBoolean(KEY_DEBUG_ENABLED, enabled).apply()

        if (enabled) {
            i("DebugLogger", "Mode Debug activé")
        } else {
            i("DebugLogger", "Mode Debug désactivé")
        }
    }

    fun addListener(listener: LogListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: LogListener) {
        listeners.remove(listener)
    }

    fun getLogs(): List<LogEntry> = logs.toList()

    fun clearLogs() {
        logs.clear()
        notifyListeners(LogEntry(
            timestamp = getCurrentTimestamp(),
            level = LogLevel.INFO,
            tag = "DebugLogger",
            message = "Logs effacés"
        ))
    }

    // Logging methods
    fun v(tag: String, message: String) {
        log(LogLevel.VERBOSE, tag, message)
    }

    fun d(tag: String, message: String) {
        log(LogLevel.DEBUG, tag, message)
    }

    fun i(tag: String, message: String) {
        log(LogLevel.INFO, tag, message)
    }

    fun w(tag: String, message: String) {
        log(LogLevel.WARNING, tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val msg = if (throwable != null) {
            "$message\n${throwable.stackTraceToString()}"
        } else {
            message
        }
        log(LogLevel.ERROR, tag, msg)
    }

    private fun log(level: LogLevel, tag: String, message: String) {
        // Always log to Logcat
        when (level) {
            LogLevel.VERBOSE -> Log.v(TAG, "[$tag] $message")
            LogLevel.DEBUG -> Log.d(TAG, "[$tag] $message")
            LogLevel.INFO -> Log.i(TAG, "[$tag] $message")
            LogLevel.WARNING -> Log.w(TAG, "[$tag] $message")
            LogLevel.ERROR -> Log.e(TAG, "[$tag] $message")
        }

        // If debug mode enabled, store in memory
        if (debugEnabled) {
            val entry = LogEntry(
                timestamp = getCurrentTimestamp(),
                level = level,
                tag = tag,
                message = message
            )

            // Add to logs (keep max 500 entries)
            logs.add(entry)
            if (logs.size > MAX_LOGS) {
                logs.removeAt(0)
            }

            // Notify listeners
            notifyListeners(entry)
        }
    }

    private fun getCurrentTimestamp(): String {
        return dateFormat.format(Date())
    }

    private fun notifyListeners(entry: LogEntry) {
        listeners.forEach { listener ->
            try {
                listener.onNewLog(entry)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying listener", e)
            }
        }
    }
}
