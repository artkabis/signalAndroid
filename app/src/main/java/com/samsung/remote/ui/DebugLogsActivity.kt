package com.samsung.remote.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
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
        adapter = DebugLogAdapter()
        binding.logsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@DebugLogsActivity)
            adapter = this@DebugLogsActivity.adapter
        }
    }

    private fun setupButtons() {
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
