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

package com.example.eew.testlite.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.example.eew.testlite.R
import com.example.eew.testlite.data.AppUpdate

/**
 * 新版本提醒通知。
 *
 * 与地震预警通知分开频道, 避免打扰; Android 13+ 需要 POST_NOTIFICATIONS 权限,
 * 未授权时静默跳过 (应用内横幅仍会提示)。
 */
object UpdateNotifier {

    const val CHANNEL_ID = "app_updates"
    private const val NOTIFICATION_ID = 1002

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.update_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.update_channel_desc)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    fun notify(context: Context, update: AppUpdate) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val releaseIntent = Intent(Intent.ACTION_VIEW, Uri.parse(update.releaseUrl))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val releasePending = PendingIntent.getActivity(
            context, NOTIFICATION_ID, releaseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            Notification.Builder(context)
        }
        builder.setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.update_notification_title, update.versionName))
            .setContentText(context.getString(R.string.update_notification_text))
            .setStyle(Notification.BigTextStyle().bigText(context.getString(R.string.update_notification_text)))
            .setContentIntent(releasePending)
            .setAutoCancel(true)
            .setPriority(Notification.PRIORITY_DEFAULT)
            .setCategory(Notification.CATEGORY_RECOMMENDATION)
        if (update.apkUrl.isNotBlank()) {
            val downloadIntent = Intent(Intent.ACTION_VIEW, Uri.parse(update.apkUrl))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val downloadPending = PendingIntent.getActivity(
                context, NOTIFICATION_ID + 1, downloadIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                Notification.Action.Builder(
                    R.drawable.ic_open_link,
                    context.getString(R.string.update_download_action),
                    downloadPending
                ).build()
            )
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, builder.build())
    }
}
