package com.samsung.remote.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.samsung.remote.R
import com.samsung.remote.util.DebugLogger

class DebugLogAdapter(
    private val onLogLongClick: ((DebugLogger.LogEntry) -> Unit)? = null
) : RecyclerView.Adapter<DebugLogAdapter.LogViewHolder>() {

    private val logs = mutableListOf<DebugLogger.LogEntry>()

    fun addLog(entry: DebugLogger.LogEntry) {
        logs.add(entry)
        notifyItemInserted(logs.size - 1)
    }

    fun setLogs(newLogs: List<DebugLogger.LogEntry>) {
        logs.clear()
        logs.addAll(newLogs)
        notifyDataSetChanged()
    }

    fun clearLogs() {
        val size = logs.size
        logs.clear()
        notifyItemRangeRemoved(0, size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_debug_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val logEntry = logs[position]
        holder.bind(logEntry)

        // Set long click listener
        holder.itemView.setOnLongClickListener {
            onLogLongClick?.invoke(logEntry)
            true
        }
    }

    override fun getItemCount(): Int = logs.size

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val levelIndicator: View = itemView.findViewById(R.id.levelIndicator)
        private val timestampTextView: TextView = itemView.findViewById(R.id.timestampTextView)
        private val levelTextView: TextView = itemView.findViewById(R.id.levelTextView)
        private val tagTextView: TextView = itemView.findViewById(R.id.tagTextView)
        private val messageTextView: TextView = itemView.findViewById(R.id.messageTextView)

        fun bind(entry: DebugLogger.LogEntry) {
            timestampTextView.text = entry.timestamp
            tagTextView.text = entry.tag
            messageTextView.text = entry.message

            when (entry.level) {
                DebugLogger.LogLevel.VERBOSE -> {
                    levelIndicator.setBackgroundColor(Color.parseColor("#9E9E9E"))
                    levelTextView.text = "[V]"
                    levelTextView.setTextColor(Color.parseColor("#9E9E9E"))
                }
                DebugLogger.LogLevel.DEBUG -> {
                    levelIndicator.setBackgroundColor(Color.parseColor("#2196F3"))
                    levelTextView.text = "[D]"
                    levelTextView.setTextColor(Color.parseColor("#2196F3"))
                }
                DebugLogger.LogLevel.INFO -> {
                    levelIndicator.setBackgroundColor(Color.parseColor("#4CAF50"))
                    levelTextView.text = "[I]"
                    levelTextView.setTextColor(Color.parseColor("#4CAF50"))
                }
                DebugLogger.LogLevel.WARNING -> {
                    levelIndicator.setBackgroundColor(Color.parseColor("#FF9800"))
                    levelTextView.text = "[W]"
                    levelTextView.setTextColor(Color.parseColor("#FF9800"))
                }
                DebugLogger.LogLevel.ERROR -> {
                    levelIndicator.setBackgroundColor(Color.parseColor("#F44336"))
                    levelTextView.text = "[E]"
                    levelTextView.setTextColor(Color.parseColor("#F44336"))
                }
            }
        }
    }
}
