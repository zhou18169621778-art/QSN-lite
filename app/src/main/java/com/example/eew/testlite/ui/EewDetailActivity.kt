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
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.eew.testlite.R
import com.example.eew.testlite.data.Eew
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EewDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EEW = "extra_eew"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_eew_detail)

        val eew = intent.getSerializableExtra(EXTRA_EEW) as Eew
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar).setNavigationOnClickListener {
            finish()
        }

        val sourceName = getString(eew.sourceShortLabelRes())
        findViewById<TextView>(R.id.eewBadge).apply {
            text = if (eew.isWarn) {
                "⚠ " + getString(R.string.eew_warning_label) + " · " + sourceName
            } else {
                getString(R.string.eew_report_label) + " · " + sourceName
            }
        }
        findViewById<TextView>(R.id.eewPlace).text = eew.hypocenter.ifBlank { getString(R.string.eew_unknown) }
        findViewById<TextView>(R.id.eewOriginTime).text =
            getString(R.string.eew_origin_time) + ": " +
                dateFormat.format(Date(eew.originTimeMillis))
        findViewById<TextView>(R.id.eewMagnitude).text =
            String.format(Locale.US, "M%.1f", eew.displayMagnitude())

        findViewById<TextView>(R.id.eewIntensity).text =
            if (eew.hasIntensity()) {
                "最大${eew.intensityName()}: " + eew.maxIntensity
            } else {
                getString(R.string.eew_unknown)
            }

        val container = findViewById<LinearLayout>(R.id.eewInfoContainer)
        addInfoRow(container, getString(R.string.eew_epicenter),
            eew.hypocenter.ifBlank { getString(R.string.eew_unknown) })
        addInfoRow(container, getString(R.string.eew_magnitude),
            String.format(Locale.US, "M%.1f", eew.displayMagnitude()))
        addInfoRow(container, "最大" + eew.intensityName(),
            if (eew.hasIntensity()) eew.maxIntensity else getString(R.string.eew_unknown))
        addInfoRow(container, getString(R.string.eew_depth),
            String.format(Locale.US, "%.1f km", eew.depthKm))
        addInfoRow(container, getString(R.string.eew_coordinates),
            String.format(Locale.US, "%.2f, %.2f", eew.latitude, eew.longitude))
        addInfoRow(container, getString(R.string.eew_origin_time),
            dateFormat.format(Date(eew.originTimeMillis)))
        addInfoRow(container, getString(R.string.eew_report_time),
            dateFormat.format(Date(eew.reportTimeMillis)))
        addInfoRow(container, getString(R.string.eew_report_number),
            if (eew.reportNum > 0) "第${eew.reportNum}报" else getString(R.string.eew_unknown))
        if (eew.method.isNotBlank()) {
            addInfoRow(container, getString(R.string.eew_method), eew.method)
        }
        addInfoRow(container, getString(R.string.eew_source), sourceName)
        if (eew.isFinal) {
            addInfoRow(container, getString(R.string.eew_final),
                getString(R.string.eew_final))
        }

        findViewById<View>(R.id.eewOpenList).setOnClickListener {
            val intent = Intent(this, com.example.eew.testlite.MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun addInfoRow(container: LinearLayout, label: String, value: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        val labelText = TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(getColor(R.color.text_secondary))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(labelText)
        val valueText = TextView(this).apply {
            text = value
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(getColor(R.color.on_surface))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        row.addView(valueText)
        container.addView(row)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
