package com.radarlite.ui

import android.view.*
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.radarlite.R
import com.radarlite.db.AlertLogEntry
import java.text.SimpleDateFormat
import java.util.*

class AlertLogAdapter : RecyclerView.Adapter<AlertLogAdapter.VH>() {
    private var items: List<AlertLogEntry> = emptyList()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val time  : TextView = view.findViewById(R.id.tvAlertTime)
        val info  : TextView = view.findViewById(R.id.tvAlertInfo)
        val speed : TextView = view.findViewById(R.id.tvAlertSpeed)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_alert, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = items[position]
        holder.time.text  = formatTime(entry.timestamp)
        holder.info.text  = when (entry.cameraType) {
            "red_light"     -> "Red light camera"
            "average_speed" -> "Average speed zone"
            else            -> "Speed camera"
        }
        holder.speed.text = entry.speedLimit?.let { "$it km/h" } ?: ""
    }

    fun submit(list: List<AlertLogEntry>) {
        items = list
        notifyDataSetChanged()
    }

    private fun formatTime(ts: Long): String {
        val diff = System.currentTimeMillis() - ts
        return when {
            diff < 60_000     -> "Just now"
            diff < 3_600_000  -> "${diff / 60_000}m ago"
            diff < 86_400_000 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
            else              -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(ts))
        }
    }
}
