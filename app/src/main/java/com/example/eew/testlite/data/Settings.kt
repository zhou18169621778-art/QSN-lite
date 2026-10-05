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

package com.example.eew.testlite.data

import android.content.Context

object Settings {
    private const val PREFS = "settings_prefs"
    const val KEY_FRONT_INTERVAL_MS = "front_interval_ms"
    const val KEY_BACK_INTERVAL_MS = "back_interval_ms"

    const val DEFAULT_FRONT_INTERVAL_MS = 8_000L
    const val DEFAULT_BACK_INTERVAL_MS = 60_000L

    fun frontIntervalMs(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_FRONT_INTERVAL_MS, DEFAULT_FRONT_INTERVAL_MS)

    fun setFrontIntervalMs(context: Context, ms: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_FRONT_INTERVAL_MS, ms).apply()
    }

    fun backIntervalMs(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_BACK_INTERVAL_MS, DEFAULT_BACK_INTERVAL_MS)

    fun setBackIntervalMs(context: Context, ms: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_BACK_INTERVAL_MS, ms).apply()
    }
}
