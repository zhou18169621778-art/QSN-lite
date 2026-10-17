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

/**
 * 震源机制解缓存: 按 Earthquake.id 索引。
 *
 * 独立于 Earthquake / SQLite 存储, 避免改动 Earthquake 数据类
 * (Java 序列化兼容) 与数据库结构 (DB_VERSION 升级)。
 */
object MomentTensorStore {
    private const val FILE_NAME = "mt_cache.bin"

    @Volatile
    private var cache: Map<String, MomentTensor> = emptyMap()

    fun get(quakeId: String): MomentTensor? = cache[quakeId]

    @Synchronized
    fun putAll(items: Map<String, MomentTensor>) {
        if (items.isEmpty()) return
        cache = cache + items
    }

    @Synchronized
    fun save(context: Context) {
        if (cache.isEmpty()) return
        try {
            val file = File(context.cacheDir, FILE_NAME)
            ObjectOutputStream(file.outputStream().buffered()).use { it.writeObject(cache) }
        } catch (e: Exception) {
            // 缓存写入失败不影响主流程
        }
    }

    @Synchronized
    fun load(context: Context): Map<String, MomentTensor> {
        if (cache.isNotEmpty()) return cache
        return try {
            val file = File(context.cacheDir, FILE_NAME)
            if (!file.exists()) return emptyMap()
            @Suppress("UNCHECKED_CAST")
            (ObjectInputStream(file.inputStream().buffered()).use { it.readObject() }
                as? Map<String, MomentTensor>)?.also { cache = it } ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    @Synchronized
    fun clear(context: Context) {
        cache = emptyMap()
        try {
            File(context.cacheDir, FILE_NAME).delete()
        } catch (e: Exception) {
        }
    }
}
