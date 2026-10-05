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
