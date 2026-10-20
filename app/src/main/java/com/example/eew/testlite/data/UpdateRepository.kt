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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub Release 更新信息。
 *
 * @param tag 原始标签, 如 "v1.2"
 * @param versionName 归一化后的版本号, 如 "1.2"
 * @param releaseUrl Release 页面地址
 * @param notes 更新日志 (Markdown 原文)
 * @param apkUrl APK 资产直链, 找不到时为空
 * @param apkSize APK 字节数
 * @param publishedAt 发布时间 (ISO8601)
 */
data class AppUpdate(
    val tag: String,
    val versionName: String,
    val releaseUrl: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    val publishedAt: String
)

/**
 * 应用更新检查: 从 GitHub Releases 拉取最新版本, 与本地版本比较。
 *
 * 设计要点:
 * - 走 GitHub 官方 API `/releases/latest`, 无需登录, 匿名调用有 60 次/小时限额,
 *   因此本地限流为每 6 小时最多一次;
 * - 同一版本只提醒一次 (KEY_LAST_NOTIFIED_TAG), 用户手动关闭横幅后不再打扰
 *   (KEY_DISMISSED_TAG), 但仍会在下次检查到更新时记录最新版本;
 * - 任何网络/解析异常都静默失败, 不影响主流程。
 */
object UpdateRepository {

    private const val API_URL =
        "https://api.github.com/repos/zhou18169621778-art/QSN-lite/releases/latest"
    private const val RELEASES_PAGE = "https://github.com/zhou18169621778-art/QSN-lite/releases"

    private const val PREFS = "update_prefs"
    private const val KEY_LAST_CHECK = "last_check_ms"
    private const val KEY_LAST_ATTEMPT = "last_attempt_ms"
    private const val KEY_LAST_NOTIFIED_TAG = "last_notified_tag"
    private const val KEY_DISMISSED_TAG = "dismissed_tag"
    private const val KEY_LATEST_JSON = "latest_json"

    /** 自动检查间隔: 6 小时 */
    const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

    /** 上次检查失败时的重试间隔: 30 分钟 */
    const val RETRY_INTERVAL_MS = 30 * 60 * 1000L

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 当前安装的版本号 (versionName) */
    fun currentVersionName(context: Context): String {
        return try {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
                .orEmpty()
        } catch (e: Exception) {
            ""
        }
    }

    /** 是否到了检查时间 (成功后 6 小时一次, 失败后 30 分钟重试) */
    fun shouldCheck(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val lastAttempt = prefs(context).getLong(KEY_LAST_ATTEMPT, 0L)
        val lastSuccess = prefs(context).getLong(KEY_LAST_CHECK, 0L)
        val interval = if (lastSuccess >= lastAttempt) CHECK_INTERVAL_MS else RETRY_INTERVAL_MS
        return now - lastAttempt >= interval
    }

    /** 记录一次尝试 (无论成功与否) */
    fun markAttempt(context: Context, now: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_LAST_ATTEMPT, now).apply()
    }

    /** 记录一次成功检查 */
    fun markChecked(context: Context, now: Long = System.currentTimeMillis()) {
        prefs(context).edit()
            .putLong(KEY_LAST_CHECK, now)
            .putLong(KEY_LAST_ATTEMPT, now)
            .apply()
    }

    /** 该版本是否已经提醒过 (避免重复打扰) */
    fun shouldNotify(context: Context, update: AppUpdate): Boolean {
        return prefs(context).getString(KEY_LAST_NOTIFIED_TAG, null) != update.tag
    }

    fun markNotified(context: Context, update: AppUpdate) {
        prefs(context).edit().putString(KEY_LAST_NOTIFIED_TAG, update.tag).apply()
    }

    /** 用户是否已手动关闭该版本的横幅 */
    fun isDismissed(context: Context, update: AppUpdate): Boolean {
        return prefs(context).getString(KEY_DISMISSED_TAG, null) == update.tag
    }

    fun dismiss(context: Context, update: AppUpdate) {
        prefs(context).edit().putString(KEY_DISMISSED_TAG, update.tag).apply()
    }

    /** 缓存最近一次拿到的版本信息, 供离线时展示横幅 */
    fun saveLatest(context: Context, update: AppUpdate) {
        val json = JSONObject()
            .put("tag", update.tag)
            .put("versionName", update.versionName)
            .put("releaseUrl", update.releaseUrl)
            .put("notes", update.notes)
            .put("apkUrl", update.apkUrl)
            .put("apkSize", update.apkSize)
            .put("publishedAt", update.publishedAt)
        prefs(context).edit().putString(KEY_LATEST_JSON, json.toString()).apply()
    }

    fun cachedLatest(context: Context): AppUpdate? {
        val raw = prefs(context).getString(KEY_LATEST_JSON, null) ?: return null
        return try {
            val json = JSONObject(raw)
            AppUpdate(
                tag = json.optString("tag"),
                versionName = json.optString("versionName"),
                releaseUrl = json.optString("releaseUrl"),
                notes = json.optString("notes"),
                apkUrl = json.optString("apkUrl"),
                apkSize = json.optLong("apkSize", 0L),
                publishedAt = json.optString("publishedAt")
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 拉取 GitHub 上最新的 Release。失败一律返回 null (静默降级)。
     */
    fun fetchLatest(): AppUpdate? {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 10000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "QSN-lite")
            }
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            val tag = root.optString("tag_name")
            if (tag.isBlank()) return null
            var apkUrl = ""
            var apkSize = 0L
            val assets = root.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }
            return AppUpdate(
                tag = tag,
                versionName = normalizeVersion(tag),
                releaseUrl = root.optString("html_url").ifBlank { RELEASES_PAGE },
                notes = root.optString("body"),
                apkUrl = apkUrl,
                apkSize = apkSize,
                publishedAt = root.optString("published_at")
            )
        } catch (e: Exception) {
            return null
        } finally {
            try {
                connection?.disconnect()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    /** 远端版本是否新于本地版本 (按数字段逐段比较) */
    fun isNewer(remoteVersion: String, localVersion: String): Boolean {
        val remote = versionParts(remoteVersion)
        val local = versionParts(localVersion)
        if (remote.isEmpty() || local.isEmpty()) return false
        for (i in 0 until maxOf(remote.size, local.size)) {
            val r = remote.getOrElse(i) { 0 }
            val l = local.getOrElse(i) { 0 }
            if (r != l) return r > l
        }
        return false
    }

    /** "v1.2" -> "1.2" */
    fun normalizeVersion(value: String): String {
        return value.trim().removePrefix("v").removePrefix("V")
    }

    private fun versionParts(value: String): List<Int> {
        val cleaned = value.trim().removePrefix("v").removePrefix("V")
            .substringBefore('-').substringBefore('+')
        if (cleaned.isBlank()) return emptyList()
        val parts = mutableListOf<Int>()
        for (segment in cleaned.split('.')) {
            val num = segment.toIntOrNull() ?: return emptyList()
            parts.add(num)
        }
        return parts
    }
}
