package com.example.eew.testlite.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.eew.testlite.R
import com.example.eew.testlite.data.Earthquake
import com.example.eew.testlite.data.EarthquakeCache
import com.example.eew.testlite.data.EarthquakeRepository
import com.example.eew.testlite.data.Settings
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class DetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EARTHQUAKE = "extra_earthquake"
        const val EXTRA_ALL_EARTHQUAKES = "extra_all_earthquakes"
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private var mapView: MapView? = null
    private lateinit var earthquake: Earthquake
    private var comparisonList: List<Earthquake> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        earthquake = intent.getSerializableExtra(EXTRA_EARTHQUAKE) as Earthquake

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar).apply {
            setNavigationOnClickListener { finish() }
        }

        initMap(earthquake)

        findViewById<TextView>(R.id.detailMagnitude).apply {
            text = earthquake.formattedMagnitude()
            setBackgroundColor(earthquake.magnitudeColor())
        }
        findViewById<TextView>(R.id.detailPlace).text = earthquake.place
        findViewById<TextView>(R.id.detailTime).text =
            dateFormat.format(Date(earthquake.timeMillis))
        findViewById<TextView>(R.id.detailAgency).apply {
            if (earthquake.agency.isNotBlank()) {
                text = getString(R.string.detail_agency, earthquake.agency)
                visibility = View.VISIBLE
            } else {
                visibility = View.GONE
            }
        }
        findViewById<TextView>(R.id.detailStatus).apply {
            val label = earthquake.determinationLabel()
            if (label != null) {
                text = label
                visibility = View.VISIBLE
            } else {
                visibility = View.GONE
            }
        }
        findViewById<TextView>(R.id.detailSource).text = getString(earthquake.sourceShortLabelRes())
        findViewById<TextView>(R.id.detailDepth).text =
            String.format(Locale.US, "%.1f km", earthquake.depthKm)
        findViewById<TextView>(R.id.detailCoordinates).text = String.format(
            Locale.US,
            "%.2f, %.2f",
            earthquake.latitude,
            earthquake.longitude
        )

        findViewById<MaterialButton>(R.id.detailUrl).apply {
            text = getString(earthquake.viewOnLabelRes())
            setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(earthquake.url))
                startActivity(intent)
            }
        }

        val all = (intent.getSerializableExtra(EXTRA_ALL_EARTHQUAKES) as? List<*>)
            ?.filterIsInstance<Earthquake>() ?: emptyList()
        val cache = EarthquakeCache.load(applicationContext)
        val base = if (all.isEmpty()) cache else all + cache
        renderComparison(earthquake, base)
        lifecycleScope.launch {
            val fresh = withContext(Dispatchers.IO) {
                runCatching {
                    EarthquakeRepository.fetchRecentEarthquakes(applicationContext)
                }.getOrNull()
            }
            if (!isDestroyed) {
                renderComparison(earthquake, (fresh ?: emptyList()) + cache + base)
            }
        }
    }

    private fun initMap(earthquake: Earthquake) {
        val map = findViewById<MapView>(R.id.mapView)
        mapView = map
        if (earthquake.latitude == 0.0 && earthquake.longitude == 0.0) {
            findViewById<View>(R.id.mapCard).visibility = View.GONE
            return
        }
        Configuration.getInstance().load(
            this, PreferenceManager.getDefaultSharedPreferences(this)
        )
        Configuration.getInstance().userAgentValue = "EarthquakeMonitor/1.0 (osmdroid)"
        map.setTileSource(AmapTileSource())
        map.setBuiltInZoomControls(true)
        try {
            map.setMultiTouchControls(true)
        } catch (e: Exception) {
        }
val point = GeoPoint(earthquake.latitude, earthquake.longitude)
        val marker = Marker(map)
        marker.position = point
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        marker.icon = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.marker_dot)
        marker.title = earthquake.place
        map.overlays.add(marker)
        map.controller.setCenter(point)
        map.controller.setZoom(8.0)
        map.invalidate()

        val scrollView = findViewById<MapScrollView>(R.id.detailScroll)
        scrollView.mapView = map
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onDestroy() {
        mapView?.onDetach()
        super.onDestroy()
    }

    private fun renderComparison(earthquake: Earthquake, all: List<Earthquake>) {
        val title = findViewById<TextView>(R.id.detailCompareTitle)
        val list = findViewById<LinearLayout>(R.id.detailCompareList)
        val empty = findViewById<TextView>(R.id.detailCompareEmpty)
        list.removeAllViews()
        comparisonList = computeComparison(earthquake, all)
        val matches = comparisonList
        if (matches.isEmpty()) {
            title.visibility = View.GONE
            empty.visibility = View.VISIBLE
            return
        }
        title.visibility = View.VISIBLE
        empty.visibility = View.GONE
        matches.forEachIndexed { index, q ->
            if (index > 0) {
                val divider = View(this).apply {
                    setBackgroundColor(getColorCompat(R.color.divider))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
                    )
                }
                list.addView(divider)
            }
            list.addView(compareRow(q, q.id == earthquake.id || q.id == matches.first().id))
        }
    }

    private fun computeComparison(earthquake: Earthquake, all: List<Earthquake>): List<Earthquake> {
        val current = all.firstOrNull { it.id == earthquake.id } ?: earthquake
        return buildList {
            add(current)
            all.filter {
                it.id != earthquake.id && it.source != earthquake.source &&
                    isSameEvent(it, earthquake)
            }
                .distinctBy { it.source }
                .sortedBy { it.source.name }
                .forEach { add(it) }
        }
    }

    private fun compareRow(q: Earthquake, isCurrent: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val source = TextView(this).apply {
            text = buildString {
                append(getString(q.sourceShortLabelRes()))
                if (q.agency.isNotBlank()) append(" (").append(q.agency).append(")")
                if (isCurrent) append(" · ").append(getString(R.string.detail_compare_current))
            }
            textSize = 15f
            setTextColor(getColorCompat(R.color.primary))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val magnitude = TextView(this).apply {
            text = q.formattedMagnitude()
            textSize = 16f
            setTextColor(q.magnitudeColor())
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        }
        header.addView(source)
        header.addView(magnitude)
        row.addView(header)

        val place = TextView(this).apply {
            text = q.place
            textSize = 14f
            setTextColor(getColorCompat(R.color.on_surface))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        row.addView(place)

        val meta = TextView(this).apply {
            text = dateFormat.format(Date(q.timeMillis)) +
                " · 深度 " + String.format(Locale.US, "%.1f", q.depthKm) + " km · " +
                String.format(Locale.US, "%.2f, %.2f", q.latitude, q.longitude)
            textSize = 13f
            setTextColor(getColorCompat(R.color.text_secondary))
        }
        row.addView(meta)
        return row
    }

    private fun isSameEvent(a: Earthquake, b: Earthquake): Boolean {
        if (abs(a.timeMillis - b.timeMillis) > 15 * 60 * 1000L) return false
        return haversineKm(a.latitude, a.longitude, b.latitude, b.longitude) <= 350.0
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun getColorCompat(colorRes: Int): Int {
        return androidx.core.content.ContextCompat.getColor(this, colorRes)
    }
}
