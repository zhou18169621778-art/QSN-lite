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

import java.io.Serializable

/**
 * 震源机制解 (矩张量解)。
 *
 * 数据来源:
 * - EMSC MTWS (Moment Tensor Web Service): https://seismicportal.eu/mtws/api/search
 *   取每个地震的 preferred 解 (mt_preferred), 按 ev_unid 去重。
 * - USGS moment-tensor 产品: https://earthquake.usgs.gov/fdsnws/event
 *   近 7 天 M5.5+ 含 MT 事件, 每事件取 preferredWeight 最大的解。
 *
 * 角度约定 (与 MTWS / Aki & Richards 一致):
 * - strike: 断层走向, 0-360, 北起顺时针
 * - dip: 倾角, 0-90
 * - rake: 滑移角, -180-180 (0=左旋走滑, 180/-180=右旋走滑, +90=逆冲, -90=正断)
 * - 轴方位角 (az): 0-360 北起顺时针; 倾伏角 (plunge): 向下为正
 */
data class MomentTensor(
    val quakeId: String,
    val mtId: Long,
    val eventUnid: String,
    val strike1: Double,
    val dip1: Double,
    val rake1: Double,
    val strike2: Double,
    val dip2: Double,
    val rake2: Double,
    val mw: Double,
    val depthKm: Double,
    val centroidLat: Double,
    val centroidLon: Double,
    val centroidTimeMillis: Long,
    val region: String,
    val catalog: String,
    val dcPercent: Double,
    val clvdPercent: Double,
    val isoPercent: Double,
    val pAz: Double,
    val pPlunge: Double,
    val tAz: Double,
    val tPlunge: Double,
    val nAz: Double,
    val nPlunge: Double
) : Serializable {

    /** 机制解类型 (优先按 P/T/N 轴倾伏判定, 与节面选取无关; 轴缺失时退回 NP1 rake 分带) */
    fun mechanismType(): MechanismType = MechanismType.classify(this)
}

/**
 * 震源机制解类型判定。
 *
 * 标准 rake 分带 (USGS / IRIS 教学通用划分):
 * - |rake| <= 30 或 |rake| >= 150 : 走滑 (rake >= 0 为左旋, 否则右旋)
 * - rake in [60, 120] : 逆冲/逆断 (dip < 45 为低角度逆冲, 否则高角度逆断)
 * - rake in [-120, -60] : 正断层
 * - rake in (30, 60) / (120, 150) : 逆冲兼走滑 (斜滑)
 * - rake in (-60, -30) / (-150, -120) : 正断兼走滑 (斜滑)
 */
enum class MechanismType(val label: String) {
    NORMAL("正断层型"),
    THRUST("逆冲型"),
    REVERSE("逆断层型"),
    STRIKE_SLIP_LEFT("左旋走滑型"),
    STRIKE_SLIP_RIGHT("右旋走滑型"),
    OBLIQUE_REVERSE("逆冲兼走滑型"),
    OBLIQUE_NORMAL("正断兼走滑型");

    companion object {
        /**
         * 主判定: 用 P/T/N 轴倾伏角确定机制大类 (与节面选取无关), 大类内再用 rake 细分。
         *
         * - N 轴最陡 (P、T 较缓) => 走滑族; |rake| <= 90 为左旋, 否则右旋
         *   (rake 接近 0 = 左旋, 接近 ±180 = 右旋, 90 为分界)
         * - P 轴最陡 => 正断族; rake in [-120, -60] 为正断, 否则正断兼走滑
         * - T 轴最陡 => 逆断族; rake in [60, 120] 为逆断/逆冲
         *   (min(dip1, dip2) < 45 为低角度逆冲, 否则高角度逆断), 否则逆冲兼走滑
         *
         * 轴全 0 / 非有限值 (源缺轴) 时退回 [classify] 的 NP1 rake 分带。
         * 已用 USGS 事件核对: 与 P/T 轴定义自洽, 斜滑事件不再随节面选取跳变
         * (如 us6000u2mq 原按 NP1 rake=154 误判右旋走滑, 实际 P 轴最陡为正断族)。
         */
        fun classify(mt: MomentTensor): MechanismType {
            val p = mt.pPlunge
            val t = mt.tPlunge
            val n = mt.nPlunge
            val axesOk = p.isFinite() && t.isFinite() && n.isFinite() &&
                p >= 0.0 && p <= 90.0 && t >= 0.0 && t <= 90.0 && n >= 0.0 && n <= 90.0 &&
                p + t + n > 1.0 // 三轴正交, 合法解倾伏和必远大于 0; 全 0 视为缺轴
            if (!axesOk) return classify(mt.dip1, mt.rake1)
            val rake = normalize(mt.rake1)
            return when {
                n >= p && n >= t ->
                    if (kotlin.math.abs(rake) <= 90.0) STRIKE_SLIP_LEFT else STRIKE_SLIP_RIGHT
                p >= t ->
                    if (rake in -120.0..-60.0) NORMAL else OBLIQUE_NORMAL
                else ->
                    if (rake in 60.0..120.0) {
                        if (kotlin.math.min(mt.dip1, mt.dip2) < 45.0) THRUST else REVERSE
                    } else OBLIQUE_REVERSE
            }
        }

        fun classify(dip: Double, rakeRaw: Double): MechanismType {
            return classifyRakeBands(dip, rakeRaw)
        }

        private fun normalize(rakeRaw: Double): Double {
            var rake = rakeRaw % 360.0
            if (rake > 180.0) rake -= 360.0
            if (rake < -180.0) rake += 360.0
            return rake
        }

        private fun classifyRakeBands(dip: Double, rakeRaw: Double): MechanismType {
            // rake 归一化到 [-180, 180]
            var rake = rakeRaw % 360.0
            if (rake > 180.0) rake -= 360.0
            if (rake < -180.0) rake += 360.0
            val abs = kotlin.math.abs(rake)
            return when {
                abs <= 30.0 -> STRIKE_SLIP_LEFT
                abs >= 150.0 -> STRIKE_SLIP_RIGHT
                rake in 60.0..120.0 -> if (dip < 45.0) THRUST else REVERSE
                rake in -120.0..-60.0 -> NORMAL
                rake > 0 -> OBLIQUE_REVERSE
                else -> OBLIQUE_NORMAL
            }
        }
    }
}
