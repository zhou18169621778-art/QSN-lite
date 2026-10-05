package com.example.eew.testlite.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.eew.testlite.R
import com.example.eew.testlite.data.WeatherAlert
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WeatherAlertAdapter(
    private val onItemClick: (WeatherAlert) -> Unit,
    private val onFooterClick: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class FooterState {
        object Loading : FooterState()
        object End : FooterState()
        object Failed : FooterState()
    }

    private val items = mutableListOf<WeatherAlert>()
    private var footer: FooterState? = null

    fun items(): List<WeatherAlert> = items.toList()

    fun submit(newItems: List<WeatherAlert>, footerState: FooterState?) {
        items.clear()
        items.addAll(newItems)
        footer = footerState
        notifyDataSetChanged()
    }

    fun append(newItems: List<WeatherAlert>, footerState: FooterState?) {
        val start = items.size
        items.addAll(newItems)
        footer = footerState
        if (newItems.isEmpty()) {
            notifyItemChanged(start)
        } else {
            notifyItemRangeInserted(start, newItems.size)
            notifyItemChanged(start + newItems.size)
        }
    }

    fun setFooter(footerState: FooterState) {
        footer = footerState
        val position = items.size
        if (position < itemCount) {
            notifyItemChanged(position)
        } else {
            notifyItemInserted(position)
        }
    }

    class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val bar: View = view.findViewById(R.id.weatherBar)
        val typeText: TextView = view.findViewById(R.id.weatherTypeText)
        val stationText: TextView = view.findViewById(R.id.weatherStationText)
        val timeText: TextView = view.findViewById(R.id.weatherTimeText)
    }

    class FooterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val progress: View = view.findViewById(R.id.weatherFooterProgress)
        val text: TextView = view.findViewById(R.id.weatherFooterText)
    }

    override fun getItemViewType(position: Int): Int {
        return if (position >= items.size) TYPE_FOOTER else TYPE_ITEM
    }

    override fun getItemCount(): Int = items.size + if (footer != null) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_FOOTER) {
            FooterViewHolder(inflater.inflate(R.layout.item_weather_footer, parent, false))
        } else {
            ItemViewHolder(inflater.inflate(R.layout.item_weather_alert, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ItemViewHolder) {
            bindItem(holder, items[position])
        } else {
            bindFooter(holder as FooterViewHolder)
        }
    }

    private fun bindItem(holder: ItemViewHolder, alert: WeatherAlert) {
        val color = alert.level.color
        holder.bar.setBackgroundColor(color)
        holder.typeText.text = buildString {
            append(alert.kind)
            if (alert.levelName.isNotBlank()) append(alert.levelName)
            append("预警")
        }
        holder.typeText.setTextColor(color)
        holder.stationText.text = alert.station
        holder.timeText.text = if (alert.issueTimeMillis > 0) {
            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                .format(Date(alert.issueTimeMillis)) + " 发布"
        } else {
            ""
        }
        holder.itemView.setOnClickListener { onItemClick(alert) }
    }

    private fun bindFooter(holder: FooterViewHolder) {
        when (val state = footer) {
            is FooterState.Loading -> {
                holder.progress.visibility = View.VISIBLE
                holder.text.text = holder.itemView.context.getString(R.string.weather_loading_more)
                holder.itemView.setOnClickListener(null)
            }
            is FooterState.End -> {
                holder.progress.visibility = View.GONE
                holder.text.text = holder.itemView.context.getString(R.string.weather_no_more)
                holder.itemView.setOnClickListener(null)
            }
            is FooterState.Failed -> {
                holder.progress.visibility = View.GONE
                holder.text.text = holder.itemView.context.getString(R.string.weather_load_more_failed)
                holder.itemView.setOnClickListener { onFooterClick() }
            }
            null -> {
                holder.progress.visibility = View.GONE
                holder.text.text = ""
                holder.itemView.setOnClickListener(null)
            }
        }
    }

    companion object {
        private const val TYPE_ITEM = 0
        private const val TYPE_FOOTER = 1
    }
}
