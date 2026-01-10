package com.samsung.remote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.samsung.remote.R
import com.samsung.remote.databinding.ActivityDebugLogsBinding
import com.samsung.remote.adapter.DebugLogAdapter
import com.samsung.remote.util.DebugLogger

class DebugLogsActivity : AppCompatActivity(), DebugLogger.LogListener {

    private lateinit var binding: ActivityDebugLogsBinding
    private lateinit var adapter: DebugLogAdapter
    private var autoScroll = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDebugLogsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupButtons()
        loadExistingLogs()

        // Register listener for new logs
        DebugLogger.addListener(this)

        updateLogCount()
    }

    private fun setupRecyclerView() {
        adapter = DebugLogAdapter(
            onLogLongClick = { logEntry ->
                copyLogToClipboard(logEntry)
            }
        )
        binding.logsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@DebugLogsActivity)
            adapter = this@DebugLogsActivity.adapter
        }
    }

    private fun setupButtons() {
        binding.copyAllLogsButton.setOnClickListener {
            copyAllLogsToClipboard()
        }

        binding.clearLogsButton.setOnClickListener {
            DebugLogger.clearLogs()
            adapter.clearLogs()
            updateLogCount()
        }

        binding.autoScrollButton.setOnClickListener {
            autoScroll = !autoScroll
            binding.autoScrollTextView.text = if (autoScroll) {
                "Auto-scroll: ON"
            } else {
                "Auto-scroll: OFF"
            }
        }

        binding.closeButton.setOnClickListener {
            finish()
        }
    }

    private fun copyAllLogsToClipboard() {
        val logsText = DebugLogger.getLogsAsText()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Samsung Remote Debug Logs", logsText)
        clipboard.setPrimaryClip(clip)

        Toast.makeText(this, R.string.logs_copied, Toast.LENGTH_SHORT).show()
        DebugLogger.i("DebugLogsActivity", "Logs copiés dans le presse-papiers (${DebugLogger.getLogs().size} logs)")
    }

    private fun copyLogToClipboard(logEntry: DebugLogger.LogEntry) {
        val levelStr = when (logEntry.level) {
            DebugLogger.LogLevel.VERBOSE -> "[V]"
            DebugLogger.LogLevel.DEBUG -> "[D]"
            DebugLogger.LogLevel.INFO -> "[I]"
            DebugLogger.LogLevel.WARNING -> "[W]"
            DebugLogger.LogLevel.ERROR -> "[E]"
        }

        val logText = "${logEntry.timestamp} $levelStr ${logEntry.tag}\n${logEntry.message}"
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Samsung Remote Debug Log", logText)
        clipboard.setPrimaryClip(clip)

        Toast.makeText(this, R.string.log_copied, Toast.LENGTH_SHORT).show()
        DebugLogger.d("DebugLogsActivity", "Log copié: ${logEntry.tag}")
    }

    private fun loadExistingLogs() {
        val logs = DebugLogger.getLogs()
        adapter.setLogs(logs)
        scrollToBottom()
    }

    override fun onNewLog(entry: DebugLogger.LogEntry) {
        runOnUiThread {
            adapter.addLog(entry)
            updateLogCount()

            if (autoScroll) {
                scrollToBottom()
            }
        }
    }

    private fun scrollToBottom() {
        if (adapter.itemCount > 0) {
            binding.logsRecyclerView.smoothScrollToPosition(adapter.itemCount - 1)
        }
    }

    private fun updateLogCount() {
        binding.logCountTextView.text = "${adapter.itemCount} logs"
    }

    override fun onDestroy() {
        super.onDestroy()
        DebugLogger.removeListener(this)
    }
}
