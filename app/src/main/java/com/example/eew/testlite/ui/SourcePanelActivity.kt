/*
 * QSN-lite - 轻量级 Android 地震监测与地震预警应用
 * Copyright (C) 2026 zhou18169621778-art
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.example.eew.testlite.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.widget.NestedScrollView
import com.example.eew.testlite.R
import com.example.eew.testlite.data.AppUpdate
import com.example.eew.testlite.data.DataSource
import com.example.eew.testlite.data.EarthquakeRepository
import com.example.eew.testlite.data.Settings
import com.example.eew.testlite.data.SourceStatus
import com.example.eew.testlite.data.UpdateRepository
import com.example.eew.testlite.data.WeatherPrefs
import com.example.eew.testlite.service.EewService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import java.util.Locale

class SourcePanelActivity : AppCompatActivity() {

    private lateinit var statusList: LinearLayout
    private lateinit var statusSummary: TextView
    private var isRefreshing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_source_panel)

        val toolbar = findViewById<Toolbar>(R.id.panelToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        statusList = findViewById(R.id.statusList)
        statusSummary = findViewById(R.id.statusSummary)

        findViewById<View>(R.id.refreshStatusButton).setOnClickListener { testConnections() }
        initAlertSwitches()
        initWeatherRegion()
        initAdvancedSettings()

        testConnections()
    }

    private fun initAlertSwitches() {
        val serviceSwitch = findViewById<SwitchCompat>(R.id.serviceSwitch)
        val warnOnlySwitch = findViewById<SwitchCompat>(R.id.warnOnlySwitch)
        val weatherSwitch = findViewById<SwitchCompat>(R.id.weatherSwitch)
        val quakeNotifySwitch = findViewById<SwitchCompat>(R.id.quakeNotifySwitch)
        val eewJmaSwitch = findViewById<SwitchCompat>(R.id.eewJmaSwitch)
        val eewCeaSwitch = findViewById<SwitchCompat>(R.id.eewCeaSwitch)
        val eewCeaPrSwitch = findViewById<SwitchCompat>(R.id.eewCeaPrSwitch)
        val eewCwaSwitch = findViewById<SwitchCompat>(R.id.eewCwaSwitch)

        serviceSwitch.isChecked = EewService.isServiceEnabled(this)
        warnOnlySwitch.isChecked = EewService.isWarnOnly(this)
        weatherSwitch.isChecked = EewService.isWeatherEnabled(this)
        quakeNotifySwitch.isChecked = EewService.isQuakeNotifyEnabled(this)
        eewJmaSwitch.isChecked = EewService.isSourceEnabled(this, DataSource.WOLFX_JMA)
        eewCeaSwitch.isChecked = EewService.isSourceEnabled(this, DataSource.CEA)
        eewCeaPrSwitch.isChecked = EewService.isSourceEnabled(this, DataSource.CEA_PR)
        eewCwaSwitch.isChecked = EewService.isSourceEnabled(this, DataSource.CWA)

        serviceSwitch.setOnCheckedChangeListener { _, checked ->
            EewService.setServiceEnabled(this, checked)
            if (checked) {
                EewService.start(this)
            } else {
                EewService.stop(this)
            }
        }
        eewJmaSwitch.setOnCheckedChangeListener { _, checked ->
            EewService.setSourceEnabled(this, DataSource.WOLFX_JMA, checked)
        }
        eewCeaSwitch.setOnCheckedChangeListener { _, checked ->
            EewService.setSourceEnabled(this, DataSource.CEA, checked)
        }
        eewCeaPrSwitch.setOnCheckedChangeListener { _, checked ->
            EewService.setSourceEnabled(this, DataSource.CEA_PR, checked)
        }
        eewCwaSwitch.setOnCheckedChangeListener { _, checked ->
            EewService.setSourceEnabled(this, DataSource.CWA, checked)
        }
        warnOnlySwitch.setOnCheckedChangeListener { _, checked ->
            EewService.setWarnOnly(this, checked)
        }
        weatherSwitch.setOnCheckedChangeListener { _, checked ->
            EewService.setWeatherEnabled(this, checked)
        }
        quakeNotifySwitch.setOnCheckedChangeListener { _, checked ->
            EewService.setQuakeNotifyEnabled(this, checked)
        }
        initQuakeNotifyMag()
    }

    private fun initQuakeNotifyMag() {
        val magValue = findViewById<TextView>(R.id.quakeNotifyMagValue)
        refreshQuakeNotifyMagText(magValue)
        findViewById<View>(R.id.quakeNotifyMagRow).setOnClickListener {
            showQuakeNotifyMagDialog(magValue)
        }
    }

    private fun refreshQuakeNotifyMagText(magValue: TextView) {
        magValue.text = "≥ M" + String.format(Locale.US, "%.1f", EewService.quakeNotifyMag(this))
    }

    private fun showQuakeNotifyMagDialog(magValue: TextView) {
        val options = listOf(2.0f, 2.5f, 3.0f, 3.5f, 4.0f, 4.5f, 5.0f, 5.5f, 6.0f, 6.5f, 7.0f)
        val labels = options.map { "≥ M" + String.format(Locale.US, "%.1f", it) }.toTypedArray()
        val current = EewService.quakeNotifyMag(this)
        val selected = options.indexOfFirst { it >= current }.takeIf { it >= 0 } ?: 0
        AlertDialog.Builder(this)
            .setTitle(R.string.quake_notify_mag_title)
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                dialog.dismiss()
                EewService.setQuakeNotifyMag(this, options[which])
                refreshQuakeNotifyMagText(magValue)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun initWeatherRegion() {
        val regionValue = findViewById<TextView>(R.id.weatherRegionValue)
        refreshWeatherRegionText(regionValue)
        findViewById<View>(R.id.weatherRegionRow).setOnClickListener {
            showWeatherRegionDialog(regionValue)
        }
    }

    private fun refreshWeatherRegionText(regionValue: TextView) {
        val prefs = getSharedPreferences(WeatherPrefs.PREFS, MODE_PRIVATE)
        val province = prefs.getString(WeatherPrefs.KEY_PROVINCE, "") ?: ""
        val city = prefs.getString(WeatherPrefs.KEY_CITY, "") ?: ""
        regionValue.text = when {
            province.isBlank() -> getString(R.string.weather_region_all)
            city.isNotBlank() && city != "全市" -> province + " · " + city
            else -> province
        }
    }

    private fun showWeatherRegionDialog(regionValue: TextView) {
        val provinces = resources.getStringArray(R.array.province_names)
        val options = arrayOf(getString(R.string.weather_region_all)) + provinces
        val prefs = getSharedPreferences(WeatherPrefs.PREFS, MODE_PRIVATE)
        val currentProvince = prefs.getString(WeatherPrefs.KEY_PROVINCE, "") ?: ""
        val selected = options.indexOfFirst { it == currentProvince }
            .takeIf { it >= 0 } ?: 0
        AlertDialog.Builder(this)
            .setTitle(R.string.weather_region_title)
            .setSingleChoiceItems(options, selected) { dialog, which ->
                dialog.dismiss()
                if (which == 0) {
                    prefs.edit()
                        .putString(WeatherPrefs.KEY_PROVINCE, "")
                        .putString(WeatherPrefs.KEY_CITY, "")
                        .apply()
                    refreshWeatherRegionText(regionValue)
                    return@setSingleChoiceItems
                }
                val province = provinces[which - 1]
                showWeatherCityDialog(regionValue, prefs, province)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showWeatherCityDialog(
        regionValue: TextView,
        prefs: android.content.SharedPreferences,
        province: String
    ) {
        val cities = resources.getStringArray(resources.getIdentifier(
            "province_cities_$province", "array", packageName
        ))
        val options = arrayOf("全省") + cities
        val currentCity = prefs.getString(WeatherPrefs.KEY_CITY, "") ?: ""
        val selected = options.indexOfFirst { it == currentCity }.takeIf { it >= 0 } ?: 0
        AlertDialog.Builder(this)
            .setTitle(province)
            .setSingleChoiceItems(options, selected) { dialog, which ->
                dialog.dismiss()
                val city = if (which == 0) "" else cities[which - 1]
                prefs.edit()
                    .putString(WeatherPrefs.KEY_PROVINCE, province)
                    .putString(WeatherPrefs.KEY_CITY, city)
                    .apply()
                refreshWeatherRegionText(regionValue)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun initAdvancedSettings() {
        val content = findViewById<View>(R.id.advancedContent)
        val arrow = findViewById<ImageView>(R.id.advancedArrow)
        content.visibility = View.GONE
        arrow.rotation = 0f
        findViewById<View>(R.id.advancedHeader).setOnClickListener {
            val expanded = content.visibility == View.GONE
            content.visibility = if (expanded) View.VISIBLE else View.GONE
            arrow.rotation = if (expanded) 90f else 0f
        }

        val frontValue = findViewById<TextView>(R.id.frontIntervalValue)
        val backValue = findViewById<TextView>(R.id.backIntervalValue)
        refreshIntervalText(frontValue, Settings.frontIntervalMs(this))
        refreshIntervalText(backValue, Settings.backIntervalMs(this))
        val updateValue = findViewById<TextView>(R.id.updateVersionValue)
        refreshUpdateVersionText(updateValue)
        findViewById<View>(R.id.updateCheckRow).setOnClickListener {
            checkAppUpdate(updateValue)
        }
        findViewById<View>(R.id.frontIntervalRow).setOnClickListener {
            showIntervalDialog(
                frontValue,
                listOf(3000L, 5000L, 8000L, 15000L, 30000L, 60000L),
                Settings.frontIntervalMs(this),
                "前台刷新间隔"
            ) { ms -> Settings.setFrontIntervalMs(this, ms) }
        }
        findViewById<View>(R.id.backIntervalRow).setOnClickListener {
            showIntervalDialog(
                backValue,
                listOf(30000L, 60000L, 120000L, 300000L, 600000L),
                Settings.backIntervalMs(this),
                "后台刷新间隔"
            ) { ms -> Settings.setBackIntervalMs(this, ms) }
        }
    }

    private fun showIntervalDialog(
        valueView: TextView,
        options: List<Long>,
        current: Long,
        title: String,
        onSelected: (Long) -> Unit
    ) {
        val labels = options.map { formatInterval(it) }.toTypedArray()
        val selected = options.indexOfFirst { it >= current }.takeIf { it >= 0 } ?: 0
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                dialog.dismiss()
                onSelected(options[which])
                refreshIntervalText(valueView, options[which])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun refreshIntervalText(view: TextView, ms: Long) {
        view.text = formatInterval(ms)
    }

    /** 「应用版本」行: 显示当前版本, 已有新版本时提示 */
    private fun refreshUpdateVersionText(valueView: TextView) {
        val current = UpdateRepository.currentVersionName(this)
        val cached = UpdateRepository.cachedLatest(this)
        valueView.text = if (cached != null &&
            UpdateRepository.isNewer(cached.versionName, current)
        ) {
            getString(R.string.update_new_version_found, cached.versionName)
        } else {
            current
        }
    }

    /** 手动检查更新 */
    private fun checkAppUpdate(valueView: TextView) {
        valueView.text = getString(R.string.update_checking)
        lifecycleScope.launch {
            val remote = withContext(Dispatchers.IO) { UpdateRepository.fetchLatest() }
            val current = UpdateRepository.currentVersionName(this@SourcePanelActivity)
            if (remote == null) {
                valueView.text = current
                Toast.makeText(
                    this@SourcePanelActivity, R.string.update_check_failed, Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            UpdateRepository.markChecked(this@SourcePanelActivity)
            UpdateRepository.saveLatest(this@SourcePanelActivity, remote)
            if (UpdateRepository.isNewer(remote.versionName, current)) {
                valueView.text = getString(R.string.update_new_version_found, remote.versionName)
                AlertDialog.Builder(this@SourcePanelActivity)
                    .setTitle(getString(R.string.update_dialog_title, remote.versionName))
                    .setMessage(getString(R.string.update_dialog_message))
                    .setPositiveButton(R.string.update_action) { _, _ -> openRelease(remote) }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            } else {
                valueView.text = current
                Toast.makeText(
                    this@SourcePanelActivity,
                    getString(R.string.update_already_latest, current),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun openRelease(update: AppUpdate) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.releaseUrl)))
        } catch (e: Exception) {
            Toast.makeText(this, R.string.update_open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatInterval(ms: Long): String {
        return if (ms >= 60_000L) {
            if (ms % 60_000L == 0L) (ms / 60_000L).toString() + " 分钟"
            else String.format(Locale.US, "%.1f 分钟", ms / 60_000.0)
        } else {
            (ms / 1000L).toString() + " 秒"
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onResume() {
        super.onResume()
    }

    private fun testConnections() {
        if (isRefreshing) return
        isRefreshing = true
        findViewById<View>(R.id.refreshStatusButton).isEnabled = false
        statusList.removeAllViews()
        statusList.addView(statusRow("USGS 美国地质调查局", null))
        statusList.addView(statusRow("Wolfx 中国地震台网", null))
        statusList.addView(statusRow("Wolfx 日本气象厅", null))
        statusList.addView(statusRow("EMSC 欧洲地中海地震中心", null))
        statusList.addView(statusRow("EMSC 震源机制解", null))
        statusList.addView(statusRow("HKO 香港天文台", null))
        statusList.addView(statusRow("BMKG 印度尼西亚气象局", null))
        statusList.addView(statusRow("JMA 日本气象厅", null))
        statusList.addView(statusRow("J-RISQ 地震速報", null))
        statusList.addView(statusRow("Wolfx CWA 预警", null))
        statusList.addView(statusRow("Wolfx CEA-PR 预警", null))
        statusSummary.text = getString(R.string.status_loading)

        lifecycleScope.launch {
            val statuses = withContext(Dispatchers.IO) {
                testAllConnections()
            }
            renderStatuses(statuses)
            isRefreshing = false
            findViewById<View>(R.id.refreshStatusButton).isEnabled = true
        }
    }

    private suspend fun testAllConnections(): List<SourceStatus> {
        return coroutineScope {
            val tasks = mutableListOf<kotlinx.coroutines.Deferred<SourceStatus>>()
            tasks.add(async { pingBuiltin("USGS 美国地质调查局") { sourceFetchStatus(0) } })
            tasks.add(async { pingBuiltin("Wolfx 中国地震台网") { sourceFetchStatus(1) } })
            tasks.add(async { pingBuiltin("Wolfx 日本气象厅") { sourceFetchStatus(2) } })
            tasks.add(async { pingBuiltin("EMSC 欧洲地中海地震中心") { sourceFetchStatus(3) } })
            tasks.add(async { pingBuiltin("EMSC 震源机制解") { sourceFetchStatus(7) } })
            tasks.add(async { pingBuiltin("HKO 香港天文台") { sourceFetchStatus(4) } })
            tasks.add(async { pingBuiltin("BMKG 印度尼西亚气象局") { sourceFetchStatus(5) } })
            tasks.add(async { pingBuiltin("JMA 日本气象厅") { sourceFetchStatus(6) } })
            tasks.add(async { pingBuiltin("J-RISQ 地震速報") { sourceFetchStatus(8) } })
            tasks.add(async {
                pingBuiltin("Wolfx CWA 预警") {
                    runCatching {
                        httpGet("https://api.wolfx.jp/cwa_eew.json?t=${System.currentTimeMillis()}")
                    }
                }
            })
            tasks.add(async {
                pingBuiltin("Wolfx CEA-PR 预警") {
                    runCatching { httpGet("https://api.wolfx.jp/sc_eew.json") }
                }
            })
            tasks.mapNotNull { it.runCatching { await() }.getOrNull() }
                .sortedBy { it.label }
        }
    }

    private suspend fun sourceFetchStatus(index: Int): Result<Unit> {
        return runCatching {
            val urls = listOf(
                "https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson&orderby=time&limit=1",
                "https://api.wolfx.jp/cenc_eqlist.json",
                "https://api.wolfx.jp/jma_eqlist.json",
                "https://www.seismicportal.eu/fdsnws/event/1/query?limit=1&format=json",
                "https://data.weather.gov.hk/weatherAPI/opendata/earthquake.php?dataType=qem&lang=sc",
                "https://data.bmkg.go.id/DataMKG/TEWS/autogempa.json",
                "https://www.data.jma.go.jp/developer/xml/feed/eqvol.xml",
                "https://seismicportal.eu/mtws/api/search?&format=json&downloadAsFile=false" +
                    "&orderby=time-desc&offset=0&limit=1",
                "https://www.j-risq.bosai.go.jp/report/sp/feed/rss2.xml"
            )
            httpGet(urls[index])
        }
    }

    private suspend fun pingBuiltin(label: String, block: suspend () -> Result<Unit>): SourceStatus {
        val start = System.currentTimeMillis()
        val ok = block().isSuccess
        return SourceStatus(label, ok, System.currentTimeMillis() - start)
    }

    private fun renderStatuses(statuses: List<SourceStatus>) {
        statusList.removeAllViews()
        var okCount = 0
        statuses.forEach { s ->
            if (s.ok) okCount++
            statusList.addView(statusRow(s.label, s))
        }
        statusSummary.text =
            getString(R.string.source_status) + ": $okCount/" + statuses.size + " 正常"
    }

    private fun statusRow(label: String, status: SourceStatus?): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val name = TextView(this).apply {
            text = label
            textSize = 16f
            setTextColor(getColorCompat(R.color.on_surface))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(name)

        val statusText = TextView(this).apply {
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            when {
                status == null -> {
                    text = getString(R.string.status_loading)
                    setTextColor(getColorCompat(R.color.text_secondary))
                }
                status.ok -> {
                    text = "✓ " + getString(R.string.status_ok) + " · " + status.elapsedMs + "ms"
                    setTextColor(getColorCompat(0xFF388E3C.toInt()))
                }
                else -> {
                    text = "✗ " + getString(R.string.status_failed)
                    setTextColor(getColorCompat(0xFFD32F2F.toInt()))
                }
            }
        }
        row.addView(statusText)
        return row
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun getColorCompat(color: Int): Int {
        return color
    }

    private fun httpGet(url: String, headers: List<String> = emptyList()) {
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        try {
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (EarthquakeMonitor)")
            headers.forEach { line ->
                val idx = line.indexOf(':')
                if (idx > 0) {
                    connection.setRequestProperty(
                        line.substring(0, idx).trim(),
                        line.substring(idx + 1).trim()
                    )
                }
            }
            val code = connection.responseCode
            if (code !in 200..299) throw RuntimeException("HTTP $code")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
