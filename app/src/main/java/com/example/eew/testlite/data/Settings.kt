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
