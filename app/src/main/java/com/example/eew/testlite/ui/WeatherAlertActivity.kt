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

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.eew.testlite.R
import com.example.eew.testlite.data.WeatherAlert
import com.example.eew.testlite.data.WeatherAlertRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WeatherAlertActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var statusLayout: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var toolbar: Toolbar
    private lateinit var adapter: WeatherAlertAdapter
    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null
    private var pageNo = 1
    private var hasMore = true
    private var isLoadingMore = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weather_alert)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_arrow_back)
        recyclerView = findViewById(R.id.recyclerView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        statusLayout = findViewById(R.id.statusLayout)
        statusText = findViewById(R.id.statusText)

        adapter = WeatherAlertAdapter(
            onItemClick = { openDetail(it) },
            onFooterClick = { loadMore() }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as? LinearLayoutManager ?: return
                val total = lm.itemCount
                val last = lm.findLastVisibleItemPosition()
                if (last >= total - 3) {
                    loadMore()
                }
            }
        })
        swipeRefresh.setOnRefreshListener { loadData(isRefresh = true) }

        loadData(isRefresh = false)
    }

    override fun onResume() {
        super.onResume()
        loadJob?.cancel()
        loadData(isRefresh = true)
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            finish()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        loadJob?.cancel()
        loadMoreJob?.cancel()
        super.onDestroy()
    }

    private fun loadData(isRefresh: Boolean) {
        if (isRefresh && loadJob?.isActive == true) return
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            if (adapter.items().isEmpty()) {
                showLoading()
            }
            try {
                val result = withContext(Dispatchers.IO) {
                    withTimeout(12000) {
                        WeatherAlertRepository.fetchAlerts(1)
                    }
                }
                if (!isActive) return@launch
                pageNo = 1
                hasMore = result.hasMore
                val footerState = if (result.hasMore) null
                else WeatherAlertAdapter.FooterState.End
                adapter.submit(result.alerts, footerState)
                showContent()
            } catch (e: Exception) {
                if (isActive && adapter.items().isEmpty()) {
                    showError()
                } else if (isActive) {
                    Toast.makeText(this@WeatherAlertActivity, R.string.weather_refresh_failed, Toast.LENGTH_SHORT).show()
                }
            } finally {
                swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun loadMore() {
        if (!hasMore || isLoadingMore || loadJob?.isActive == true) return
        isLoadingMore = true
        adapter.setFooter(WeatherAlertAdapter.FooterState.Loading)
        loadMoreJob?.cancel()
        loadMoreJob = lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    withTimeout(12000) {
                        WeatherAlertRepository.fetchAlerts(pageNo + 1)
                    }
                }
                if (!isActive) return@launch
                pageNo++
                hasMore = result.hasMore
                val footerState = if (result.hasMore) null
                else WeatherAlertAdapter.FooterState.End
                adapter.append(result.alerts, footerState)
            } catch (e: Exception) {
                if (isActive) {
                    adapter.setFooter(WeatherAlertAdapter.FooterState.Failed)
                }
            } finally {
                isLoadingMore = false
            }
        }
    }

    private fun showLoading() {
        recyclerView.visibility = View.GONE
        statusLayout.visibility = View.VISIBLE
        statusText.text = getString(R.string.loading)
    }

    private fun showContent() {
        statusLayout.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        val updateTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        toolbar.subtitle = getString(R.string.last_update, updateTime)
    }

    private fun showError() {
        recyclerView.visibility = View.GONE
        statusLayout.visibility = View.VISIBLE
        statusText.text = getString(R.string.error_network)
    }

    private fun openDetail(alert: WeatherAlert) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(alert.detailUrl)))
        } catch (e: Exception) {
            Toast.makeText(this, R.string.weather_open_failed, Toast.LENGTH_SHORT).show()
        }
    }
}
