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
 * Jian Project 数据源鉴权。
 *
 * 凭证链：登录密钥(lk_, 5 分钟一次性) -> 长期 Token(rt_, 180 天) -> 访问令牌(at_, 约 1 小时)。
 * lk_ 只用于当场换出 rt_ 并保存；rt_ 长期保存在本地；at_ 在每次连接前用 rt_ 现换并缓存。
 */
object JianAuth {
    const val ERR_NO_CREDENTIAL = 0
    const val ERR_EXPIRED_REFRESH = 4202

    private const val PREFS = "jian_auth_prefs"
    private const val KEY_RT = "rt_token"
    private const val KEY_RT_EXPIRES_AT = "rt_expires_at"
    private const val REFRESH_URL = "https://auth.sismotide.top/api/refresh"
    private const val ACCESS_URL = "https://auth.sismotide.top/api/access"
    private const val ACCESS_REFRESH_AHEAD_MS = 5 * 60 * 1000L

    class AuthException(val code: Int, message: String) : Exception(message) {
        /** rt_ 无效/过期或账号被封禁时无法自行恢复，需用户重新配置凭证 */
        fun isCredentialInvalid(): Boolean =
            code == ERR_EXPIRED_REFRESH || code == 4201 || code == 4006
    }

    @Volatile
    private var cachedAccess: String? = null

    @Volatile
    private var cachedAccessExpiresAt = 0L

    fun hasCredential(context: Context): Boolean =
        prefs(context).getString(KEY_RT, null)?.isNotBlank() == true

    fun rtExpiresAt(context: Context): Long =
        prefs(context).getLong(KEY_RT_EXPIRES_AT, 0L)

    /**
     * 保存用户粘贴的凭证：自动识别 lk_（当场换 rt_）或 rt_（直接校验保存）。
     * 成功后返回 rt_ 的失效时刻（Unix 秒，0 表示未知）。
     */
    fun saveCredential(context: Context, input: String): Long {
        val token = input.trim().trim('"', '\'', ' ', '\n', '\t')
        return when {
            token.startsWith("lk_") -> exchangeLoginKey(context, token)
            token.startsWith("rt_") -> probeAndSaveRefreshToken(context, token)
            else -> throw IllegalArgumentException("凭证需以 lk_ 或 rt_ 开头")
        }
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
        cachedAccess = null
        cachedAccessExpiresAt = 0L
    }

    /** 获取可用的访问令牌；过期或临近过期时用 rt_ 现换。必须在子线程调用。 */
    @Synchronized
    fun accessToken(context: Context): String {
        val now = System.currentTimeMillis()
        cachedAccess?.takeIf { now < cachedAccessExpiresAt }?.let { return it }
        val prefs = prefs(context)
        val rt = prefs.getString(KEY_RT, null)?.takeIf { it.isNotBlank() }
            ?: throw AuthException(ERR_NO_CREDENTIAL, "未配置访问凭证")
        if (prefs.getLong(KEY_RT_EXPIRES_AT, 0L) in 1..now / 1000) {
            throw AuthException(ERR_EXPIRED_REFRESH, "长期 Token 已过期，请重新申请")
        }
        val json = postForJson(ACCESS_URL, rt)
        val token = json.optString("token")
        if (!json.optBoolean("ok") || token.isBlank()) {
            throw AuthException(json.optInt("code", -1), errorText(json, "换票失败"))
        }
        json.optLong("refresh_expires_at", 0L).takeIf { it > 0 }?.let {
            prefs.edit().putLong(KEY_RT_EXPIRES_AT, it).apply()
        }
        cachedAccess = token
        cachedAccessExpiresAt =
            now + json.optLong("expires_after_sec", 3600L) * 1000L - ACCESS_REFRESH_AHEAD_MS
        return token
    }

    /** 访问令牌被服务端判定无效时丢弃缓存，下次重换 */
    fun invalidateAccessToken() {
        cachedAccess = null
        cachedAccessExpiresAt = 0L
    }

    private fun exchangeLoginKey(context: Context, loginKey: String): Long {
        val json = postForJson(REFRESH_URL, loginKey)
        val token = json.optString("token")
        if (!json.optBoolean("ok") || !token.startsWith("rt_")) {
            throw AuthException(json.optInt("code", -1), errorText(json, "登录密钥换票失败"))
        }
        val expiresAtSec = json.optLong("refresh_expires_at", 0L)
        prefs(context).edit()
            .putString(KEY_RT, token)
            .putLong(KEY_RT_EXPIRES_AT, expiresAtSec)
            .apply()
        cachedAccess = null
        cachedAccessExpiresAt = 0L
        return expiresAtSec
    }

    private fun probeAndSaveRefreshToken(context: Context, rt: String): Long {
        val json = postForJson(ACCESS_URL, rt)
        if (!json.optBoolean("ok")) {
            throw AuthException(json.optInt("code", -1), errorText(json, "长期 Token 校验失败"))
        }
        val expiresAtSec = json.optLong("refresh_expires_at", 0L)
        prefs(context).edit()
            .putString(KEY_RT, rt)
            .putLong(KEY_RT_EXPIRES_AT, expiresAtSec)
            .apply()
        cachedAccess = null
        cachedAccessExpiresAt = 0L
        return expiresAtSec
    }

    private fun errorText(json: JSONObject, fallback: String): String =
        json.optString("message").ifBlank { json.optString("error") }.ifBlank { fallback }

    private fun postForJson(urlString: String, bearer: String): JSONObject {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $bearer")
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            try {
                JSONObject(body)
            } catch (e: Exception) {
                throw AuthException(-1, "HTTP $code")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
