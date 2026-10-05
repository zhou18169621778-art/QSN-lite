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
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

object EarthquakeCache {
    private const val FILE_NAME = "earthquake_cache.bin"

    @Volatile
    private var lastSaved: List<Earthquake> = emptyList()

    @Synchronized
    fun save(context: Context, earthquakes: List<Earthquake>) {
        if (earthquakes.isEmpty()) return
        lastSaved = earthquakes
        try {
            val file = File(context.cacheDir, FILE_NAME)
            ObjectOutputStream(file.outputStream().buffered()).use { it.writeObject(earthquakes) }
        } catch (e: Exception) {
            // 缓存写入失败不影响主流程
        }
    }

    @Synchronized
    fun load(context: Context): List<Earthquake> {
        if (lastSaved.isNotEmpty()) return lastSaved
        return try {
            val file = File(context.cacheDir, FILE_NAME)
            if (!file.exists()) return emptyList()
            @Suppress("UNCHECKED_CAST")
            (ObjectInputStream(file.inputStream().buffered()).use { it.readObject() } as? List<Earthquake>)
                ?.filter { it.timeMillis > 0L } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun clear(context: Context) {
        lastSaved = emptyList()
        try {
            File(context.cacheDir, FILE_NAME).delete()
        } catch (e: Exception) {
        }
    }
}
