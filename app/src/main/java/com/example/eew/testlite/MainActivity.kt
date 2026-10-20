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

package com.example.eew.testlite

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.lifecycle.lifecycleScope
import com.example.eew.testlite.data.AppUpdate
import com.example.eew.testlite.data.Earthquake
import com.example.eew.testlite.data.EarthquakeCache
import com.example.eew.testlite.data.EarthquakeRepository
import com.example.eew.testlite.data.Eew
import com.example.eew.testlite.data.MomentTensorStore
import com.example.eew.testlite.data.Settings
import com.example.eew.testlite.data.UpdateRepository
import com.example.eew.testlite.service.EewService
import com.example.eew.testlite.service.UpdateNotifier
import com.example.eew.testlite.ui.DetailActivity
import com.example.eew.testlite.ui.EarthquakeAdapter
import com.example.eew.testlite.ui.EarthquakeListItem
import com.example.eew.testlite.ui.EewDetailActivity
import com.example.eew.testlite.ui.SourcePanelActivity
import com.example.eew.testlite.ui.WeatherAlertActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var statusLayout: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var toolbar: Toolbar
    private lateinit var warningBanner: View
    private lateinit var warningTitle: TextView
    private lateinit var warningText: TextView
    private lateinit var warningClose: View
    private lateinit var updateBanner: View
    private lateinit var updateTitle: TextView
    private lateinit var updateText: TextView
    private lateinit var updateAction: View
    private lateinit var updateClose: View
    private var latestUpdate: AppUpdate? = null
    private var loadJob: Job? = null
    private var autoRefreshJob: Job? = null

    private val notificationPermissionRequest = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        recyclerView = findViewById(R.id.recyclerView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        statusLayout = findViewById(R.id.statusLayout)
        statusText = findViewById(R.id.statusText)
        warningBanner = findViewById(R.id.warningBanner)
        warningTitle = findViewById(R.id.warningTitle)
        warningText = findViewById(R.id.warningText)
        warningClose = findViewById(R.id.warningClose)
        updateBanner = findViewById(R.id.updateBanner)
        updateTitle = findViewById(R.id.updateTitle)
        updateText = findViewById(R.id.updateText)
        updateAction = findViewById(R.id.updateAction)
        updateClose = findViewById(R.id.updateClose)

        recyclerView.layoutManager = LinearLayoutManager(this)
        swipeRefresh.setOnRefreshListener { loadData(isRefresh = true) }
        warningBanner.setOnClickListener {
            if (EewService.latestEew != null) {
                loadData(isRefresh = true)
            }
        }
        warningClose.setOnClickListener {
            dismissWarningBanner()
        }
        updateBanner.setOnClickListener { openUpdatePage() }
        updateAction.setOnClickListener { openUpdatePage() }
        updateClose.setOnClickListener { dismissUpdateBanner() }

        MomentTensorStore.load(applicationContext)
        requestNotificationPermissionIfNeeded()
        if (EewService.isServiceEnabled(this)) {
            EewService.start(this)
            EewService.loadLatestEew(this)
        }
        refreshWarningBanner()
        showCachedUpdateBanner()

        loadData(isRefresh = false)
    }

    override fun onResume() {
        super.onResume()
        refreshWarningBanner()
        showCachedUpdateBanner()
        checkForUpdate()
        loadJob?.cancel()
        loadData(isRefresh = true)
        startAutoRefresh()
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_panel -> {
                startActivity(Intent(this, SourcePanelActivity::class.java))
                true
            }
            R.id.action_weather -> {
                startActivity(Intent(this, WeatherAlertActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onPause() {
        super.onPause()
        stopAutoRefresh()
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = lifecycleScope.launch {
            while (true) {
                delay(Settings.frontIntervalMs(this@MainActivity))
                refreshWarningBanner()
                loadData(isRefresh = true)
            }
        }
    }

    private fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    notificationPermissionRequest
                )
            }
        }
    }

    private fun refreshWarningBanner() {
        val eew = EewService.latestEew
        if (eew == null || !EewService.isRecent(eew)) {
            warningBanner.visibility = View.GONE
            return
        }
        val prefs = getSharedPreferences("banner_prefs", MODE_PRIVATE)
        if (prefs.getString("dismissed_eew", null) == eew.id) {
            warningBanner.visibility = View.GONE
            return
        }
        warningBanner.visibility = View.VISIBLE
        warningTitle.text = if (eew.isWarn) {
            getString(R.string.latest_warning) + "（警报）"
        } else {
            getString(R.string.latest_warning)
        }
        val sourceName = getString(eew.sourceShortLabelRes())
        val time = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            .format(Date(eew.originTimeMillis))
        warningText.text = "$sourceName · ${eew.hypocenter.ifBlank { "位置未定" }}" +
            (if (eew.method.isNotBlank()) " · ${eew.method}" else "") +
            " · M" + String.format(Locale.US, "%.1f", eew.displayMagnitude()) +
            if (eew.hasIntensity()) {
                " · ${eew.intensityName()}${eew.maxIntensity}"
            } else {
                ""
            } + " · $time"
    }

    private fun dismissWarningBanner() {
        warningBanner.visibility = View.GONE
        val eew = EewService.latestEew
        if (eew != null) {
            getSharedPreferences("banner_prefs", MODE_PRIVATE)
                .edit()
                .putString("dismissed_eew", eew.id)
                .apply()
        }
    }

    /**
     * 检查 GitHub Release 是否有新版本。自动检查每 6 小时最多一次,
     * 发现新版本时展示横幅并推送一次通知。
     */
    private fun checkForUpdate() {
        if (!UpdateRepository.shouldCheck(this)) return
        UpdateRepository.markAttempt(this)
        lifecycleScope.launch {
            val remote = withContext(Dispatchers.IO) { UpdateRepository.fetchLatest() }
            if (remote == null) return@launch
            UpdateRepository.markChecked(this@MainActivity)
            UpdateRepository.saveLatest(this@MainActivity, remote)
            val current = UpdateRepository.currentVersionName(this@MainActivity)
            if (!UpdateRepository.isNewer(remote.versionName, current)) {
                hideUpdateBanner()
                return@launch
            }
            showUpdateBanner(remote)
            if (UpdateRepository.shouldNotify(this@MainActivity, remote)) {
                UpdateNotifier.notify(this@MainActivity, remote)
                UpdateRepository.markNotified(this@MainActivity, remote)
            }
        }
    }

    /** 离线时用缓存的版本信息展示横幅 */
    private fun showCachedUpdateBanner() {
        val cached = UpdateRepository.cachedLatest(this) ?: return
        val current = UpdateRepository.currentVersionName(this)
        if (!UpdateRepository.isNewer(cached.versionName, current)) return
        if (UpdateRepository.isDismissed(this, cached)) return
        showUpdateBanner(cached)
    }

    private fun showUpdateBanner(update: AppUpdate) {
        latestUpdate = update
        updateTitle.text = getString(R.string.update_banner_title, update.versionName)
        updateText.text = getString(R.string.update_banner_text)
        updateBanner.visibility = View.VISIBLE
    }

    private fun hideUpdateBanner() {
        updateBanner.visibility = View.GONE
    }

    private fun dismissUpdateBanner() {
        hideUpdateBanner()
        latestUpdate?.let { UpdateRepository.dismiss(this, it) }
    }

    private fun openUpdatePage() {
        val update = latestUpdate ?: return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(update.releaseUrl)))
        } catch (e: Exception) {
            android.widget.Toast.makeText(
                this, R.string.update_open_failed, android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun loadData(isRefresh: Boolean) {
        if (isRefresh && loadJob?.isActive == true) return
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            val cached = withContext(Dispatchers.IO) {
                EarthquakeCache.load(applicationContext)
            }
            if (cached.isNotEmpty()) {
                showContent(cached)
            } else {
                showLoading()
            }
            try {
                val earthquakes = withContext(Dispatchers.IO) {
                    withTimeout(30000) {
                        EarthquakeRepository.fetchRecentEarthquakes(applicationContext)
                    }
                }
                if (isActive) {
                    withContext(Dispatchers.IO) {
                        EarthquakeCache.save(applicationContext, earthquakes)
                    }
                    showContent(earthquakes)
                }
            } catch (e: Exception) {
                if (isActive && cached.isEmpty()) showError()
            } finally {
                swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun showLoading() {
        recyclerView.visibility = View.GONE
        statusLayout.visibility = View.VISIBLE
        statusText.text = getString(R.string.loading)
    }

    private fun showContent(earthquakes: List<Earthquake>) {
        statusLayout.visibility = View.GONE
        val updateTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        toolbar.subtitle = getString(R.string.last_update, updateTime)
        val items = mergeEew(earthquakes)
        if (items.isEmpty()) {
            recyclerView.visibility = View.GONE
            statusLayout.visibility = View.VISIBLE
            statusText.text = getString(R.string.no_data)
        } else {
            recyclerView.visibility = View.VISIBLE
            val existing = recyclerView.adapter as? EarthquakeAdapter
            val adapter = existing ?: EarthquakeAdapter(items) { earthquake ->
                startActivity(
                    Intent(this, DetailActivity::class.java)
                        .putExtra(DetailActivity.EXTRA_EARTHQUAKE, earthquake)
                        .putExtra(DetailActivity.EXTRA_ALL_EARTHQUAKES, ArrayList(earthquakes))
                )
            }
            if (existing != null) {
                adapter.updateData(items)
            } else {
                adapter.onEewClick = { eew ->
                    startActivity(
                        Intent(this, EewDetailActivity::class.java)
                            .putExtra(EewDetailActivity.EXTRA_EEW, eew)
                    )
                }
                recyclerView.adapter = adapter
            }
        }
    }

    private fun mergeEew(earthquakes: List<Earthquake>): List<EarthquakeListItem> {
        val active = EewService.activeEews
            .filter { EewService.isRecent(it) && !it.isCancel }
        if (active.isEmpty()) {
            return earthquakes.map { EarthquakeListItem.Quake(it) }
        }
        val unmatched = active.filter { eew ->
            earthquakes.none { quake -> isOfficialMatch(eew, quake) }
        }
        return buildList {
            unmatched.forEach { add(EarthquakeListItem.EewAlert(it)) }
            earthquakes.forEach { add(EarthquakeListItem.Quake(it)) }
        }.sortedByDescending { it.timeMillis }
    }

    private fun isOfficialMatch(eew: Eew, quake: Earthquake): Boolean {
        val eewTime = eew.originTimeMillis.takeIf { it > 0 } ?: eew.reportTimeMillis
        if (kotlin.math.abs(quake.timeMillis - eewTime) > 10 * 60 * 1000L) return false
        if (eew.latitude != 0.0 && quake.latitude != 0.0) {
            if (kotlin.math.abs(quake.latitude - eew.latitude) > 3.0) return false
            if (kotlin.math.abs(quake.longitude - eew.longitude) > 3.0) return false
        }
        if (eew.magnitude > 0.0) {
            return kotlin.math.abs(quake.magnitude - eew.magnitude) <= 1.5
        }
        return true
    }

    private fun showError() {
        recyclerView.visibility = View.GONE
        statusLayout.visibility = View.VISIBLE
        statusText.text = getString(R.string.error_network)
    }

    override fun onDestroy() {
        loadJob?.cancel()
        super.onDestroy()
    }
}