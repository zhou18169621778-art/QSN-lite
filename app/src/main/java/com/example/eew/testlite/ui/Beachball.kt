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

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 震源机制解 "沙滩球" 渲染。
 *
 * 严格遵循 USGS / IRIS 规范:
 * - 下半球投影 (从上方俯视, 中心 = 直下方向)
 * - 等面积投影 Schmidt (r = sqrt(2) * R * sin(i / 2), i 自下垂向起算,
 *   边缘 i = 90° 对应 r = R)
 * - P 波辐射花样符号决定象限:
 *   压缩象限 (初动向上) 填黑, 膨胀象限 (初动向下) 留白
 * - 细黑外圈
 *
 * 辐射花样: 由双力偶矩张量 M = n⊗s + s⊗n 直接计算 q = rᵀMr = 2(r·n)(r·s)。
 * 其中 s 为上盘 (悬挂盘) 滑动矢量 (rake=+90 => 上盘沿上倾方向上冲), n 取
 * 下倾 × 走向, 指向悬挂盘一侧——n 必须与 s 所属的块配同一方向, 若取底脚
 * 侧法向则 M 整体变号、黑白全部对调 (节面零点集合不变, 弧线形状仍正确)。
 *
 * 轴语义 (USGS/IRIS 约定, 经典易错点): P 轴 = M 最小本征值方向 (最大压缩
 * 应力方向), 位于膨胀 (白) 象限; T 轴 = M 最大本征值方向 (最大张拉应力
 * 方向), 位于压缩 (黑) 象限。即 q(T轴) = +1、q(P轴) = -1; 以 "P 轴位于
 * 压缩象限" 为基准的任何核验都会把反色误判为正确。已用 USGS
 * moment-tensor 产品自带 p_axis/t_axis 在真实事件 (走滑/正断/逆冲/斜滑)
 * 上逐一核对: T 轴方向 q = +1, P 轴方向 q = -1。
 *
 * 注: 早期解析式版本符号整体取反; 2026-10-16 的反色修复又因该错误基准把
 * n 放回底脚侧, 黑白再次对调。本版 n 归位悬挂盘侧, 校验锚点定为
 * q(T)=+1 / q(P)=-1。
 */
object Beachball {

    private const val OUTPUT_PX = 192
    private const val SUPERSAMPLE = 3

    private const val COMPRESSIONAL = 0xFF000000.toInt()
    private const val DILATATIONAL = 0xFFFFFFFF.toInt()

    fun render(strikeDeg: Double, dipDeg: Double, rakeDeg: Double, outputPx: Int = OUTPUT_PX): Bitmap {
        val outSize = outputPx.coerceIn(64, 1024)
        // 大尺寸用 2x 超采样封顶计算量 (512px -> 1024² ≈ 100 万像素, 后台线程渲染)
        val ss = if (outSize > 256) 2 else SUPERSAMPLE
        val n = outSize * ss
        val pixels = IntArray(n * n)

        // 建立双力偶矩张量方向 (坐标系: x=东, y=北, z=上, 单位向量)
        val f = Math.toRadians(strikeDeg)
        val d = Math.toRadians(dipDeg)
        val l = Math.toRadians(rakeDeg)
        // 走向单位向量
        val tx = sin(f)
        val ty = cos(f)
        // 倾向 (水平) 单位向量 = 走向 + 90°
        val dhx = sin(f + Math.PI / 2.0)
        val dhy = cos(f + Math.PI / 2.0)
        // 下倾单位向量: 水平沿倾向 cos(d), 垂直向下 sin(d)
        val ddx = dhx * cos(d)
        val ddy = dhy * cos(d)
        val ddz = -sin(d)
        // 滑动矢量 s (上盘): cos(λ)·走向 + sin(λ)·上倾
        val sinL = sin(l)
        val cosL = cos(l)
        val sx = cosL * tx - sinL * ddx
        val sy = cosL * ty - sinL * ddy
        val sz = -sinL * ddz
        // 法向 n = 下倾 × 走向 (指向悬挂盘一侧, 与上盘滑动矢量 s 属同一块;
        // 走向 × 下倾 指向底脚侧, 会把 M 整体变号)
        val nx = -ty * ddz
        val ny = tx * ddz
        val nz = ty * ddx - tx * ddy

        val half = n / 2.0
        for (py in 0 until n) {
            // y 向下为南; x 向右为东
            val nyImg = (py + 0.5 - half) / half
            for (px in 0 until n) {
                val nxImg = (px + 0.5 - half) / half
                val r = sqrt(nxImg * nxImg + nyImg * nyImg)
                if (r > 1.0) {
                    pixels[py * n + px] = 0x00000000
                    continue
                }
                // Schmidt 等面积逆投影: r = sqrt(2)·sin(i/2)
                val ratio = (r / sqrt(2.0)).coerceIn(-1.0, 1.0)
                val takeoff = 2.0 * asin(ratio)
                // 方位角自北顺时针; 射线方向 (源->台): 水平 sin(i), 垂直 -cos(i) (上为正)
                val sinI = sin(takeoff)
                val cosI = cos(takeoff)
                val az = atan2(nxImg, -nyImg)
                val rx = sinI * sin(az)
                val ry = sinI * cos(az)
                val rz = -cosI
                // q = rᵀMr = 2(r·n)(r·s), q > 0 = 压缩 (初动向上) => 填黑
                val amp = 2.0 * (rx * nx + ry * ny + rz * nz) * (rx * sx + ry * sy + rz * sz)
                pixels[py * n + px] =
                    if (amp >= 0) COMPRESSIONAL else DILATATIONAL
            }
        }
        val big = Bitmap.createBitmap(pixels, n, n, Bitmap.Config.ARGB_8888)
        val scaled = Bitmap.createScaledBitmap(big, outSize, outSize, true)
        if (scaled !== big) big.recycle()
        // 外圈描边 (线宽按输出尺寸比例)
        val out = scaled.copy(Bitmap.Config.ARGB_8888, true)
        scaled.recycle()
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = COMPRESSIONAL
            strokeWidth = outSize * 0.025f
        }
        val inset = outSize * 0.013f
        canvas.drawCircle(
            outSize / 2f, outSize / 2f,
            outSize / 2f - inset, paint
        )
        return out
    }
}
