package com.example.eew.testlite.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.eew.testlite.R
import com.example.eew.testlite.data.Earthquake
import com.example.eew.testlite.data.Eew
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class EarthquakeListItem {
    abstract val timeMillis: Long

    data class Quake(val earthquake: Earthquake) : EarthquakeListItem() {
        override val timeMillis: Long
            get() = earthquake.timeMillis
    }

    data class EewAlert(val eew: Eew) : EarthquakeListItem() {
        override val timeMillis: Long
            get() = eew.originTimeMillis.takeIf { it > 0 } ?: eew.reportTimeMillis
    }
}

class EarthquakeAdapter(
    private var items: List<EarthquakeListItem>,
    private val onItemClick: (Earthquake) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var onEewClick: ((Eew) -> Unit)? = null

    fun updateData(newItems: List<EarthquakeListItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val magnitudeText: TextView = view.findViewById(R.id.magnitudeText)
        val magnitudeBadge: View = view.findViewById(R.id.magnitudeBadge)
        val placeText: TextView = view.findViewById(R.id.placeText)
        val depthText: TextView = view.findViewById(R.id.depthText)
        val timeText: TextView = view.findViewById(R.id.timeText)
        val sourceText: TextView = view.findViewById(R.id.sourceText)
        val chevron: ImageView = view.findViewById(R.id.chevron)
    }

    class EewViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val eewBadgeBg: View = view.findViewById(R.id.eewBadgeBg)
        val magnitudeText: TextView = view.findViewById(R.id.eewMagnitude)
        val tagText: TextView = view.findViewById(R.id.eewTag)
        val placeText: TextView = view.findViewById(R.id.eewPlace)
        val timeText: TextView = view.findViewById(R.id.eewTime)
        val sourceText: TextView = view.findViewById(R.id.eewSource)
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position] is EarthquakeListItem.EewAlert) TYPE_EEW else TYPE_QUAKE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_EEW) {
            EewViewHolder(inflater.inflate(R.layout.item_eew, parent, false))
        } else {
            ViewHolder(inflater.inflate(R.layout.item_earthquake, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is EarthquakeListItem.EewAlert -> bindEew(holder as EewViewHolder, item.eew)
            is EarthquakeListItem.Quake -> bindQuake(holder as ViewHolder, item.earthquake)
        }
    }

    private fun bindEew(holder: EewViewHolder, e: Eew) {
        holder.magnitudeText.text = String.format(Locale.US, "%.1f", e.displayMagnitude())
        holder.magnitudeText.setTextColor(0xFFFFFFFF.toInt())
        holder.eewBadgeBg.setBackgroundColor(if (e.isWarn) 0xFFE33B2B.toInt() else 0xFFF57C00.toInt())
        holder.tagText.text = buildString {
            append("EEW预警")
            if (e.method.isNotBlank()) append("·").append(e.method)
            if (e.reportNum > 0) append("·第").append(e.reportNum).append("报")
            if (e.isFinal) append("·最终")
        }
        holder.placeText.text = e.hypocenter
        holder.timeText.text = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(e.reportTimeMillis)) + " 发布"
        holder.sourceText.text =
            holder.itemView.context.getString(e.sourceShortLabelRes())
        holder.itemView.setOnClickListener { onEewClick?.invoke(e) }
    }

    private fun bindQuake(holder: ViewHolder, item: Earthquake) {
        holder.magnitudeText.text = item.formattedMagnitudeShort()
        holder.magnitudeText.setTextColor(item.magnitudeColor())
        holder.magnitudeBadge.setBackgroundColor(item.magnitudeColor())
        holder.placeText.text = item.place
        holder.depthText.text = String.format(Locale.US, "深度 %.1f km", item.depthKm)
        holder.timeText.text = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(item.timeMillis))
        holder.sourceText.text = holder.itemView.context.getString(item.sourceShortLabelRes())
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount(): Int = items.size

    companion object {
        private const val TYPE_EEW = 0
        private const val TYPE_QUAKE = 1
    }
}
