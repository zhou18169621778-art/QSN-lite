package com.example.eew.testlite.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.eew.testlite.MainActivity
import com.example.eew.testlite.R
import com.example.eew.testlite.data.DataSource
import com.example.eew.testlite.data.Earthquake
import com.example.eew.testlite.data.EarthquakeCache
import com.example.eew.testlite.data.EarthquakeRepository
import com.example.eew.testlite.data.Eew
import com.example.eew.testlite.data.EewRepository
import com.example.eew.testlite.data.Settings
import com.example.eew.testlite.data.WeatherAlert
import com.example.eew.testlite.data.WeatherAlertRepository
import com.example.eew.testlite.ui.DetailActivity
import com.example.eew.testlite.ui.EewDetailActivity
import com.example.eew.testlite.ui.WeatherAlertActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EewService : Service() {

    companion object {
        private const val TAG = "EewService"
        const val CHANNEL_ID = "eew_alerts"
        const val FOREGROUND_CHANNEL_ID = "eew_foreground"
        const val NOTIFICATION_ID = 1
        private const val PREFS = "eew_prefs"
        private const val KEY_SEEN_JMA = "seen_jma"
        private const val KEY_SEEN_CENC = "seen_cenc"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_JMA_ENABLED = "jma_enabled"
        private const val KEY_CENC_ENABLED = "cenc_enabled"
        private const val KEY_CEA_ENABLED = "cea_enabled"
        private const val KEY_CEA_PR_ENABLED = "cea_pr_enabled"
        private const val KEY_WARN_ONLY = "warn_only"
        private const val KEY_WEATHER_ENABLED = "weather_enabled"
        const val EXTRA_STOP = "extra_stop"
        const val ACTION_STOP = "com.example.eew.testlite.action.STOP"
        private const val MAX_ALERT_AGE_MS = 10 * 60 * 1000L
        private const val KEY_SEEN_WEATHER = "seen_weather_ids"
        private const val MAX_WEATHER_ALERT_AGE_MS = 10 * 60 * 1000L
        private const val KEY_QUAKE_NOTIFY_ENABLED = "quake_notify_enabled"
        private const val KEY_QUAKE_NOTIFY_MAG = "quake_notify_mag"
        private const val KEY_SEEN_QUAKE = "seen_quake_ids"
        private const val DEFAULT_QUAKE_NOTIFY_MAG = 3.0f
        private const val MAX_QUAKE_AGE_MS = 10 * 60 * 1000L

        fun isRecent(eew: Eew, nowMillis: Long = System.currentTimeMillis()): Boolean {
            return eew.reportTimeMillis > 0 &&
                (nowMillis - eew.reportTimeMillis) <= MAX_ALERT_AGE_MS
        }

        fun isServiceEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_SERVICE_ENABLED, true)

        fun isSourceEnabled(context: Context, source: DataSource): Boolean {
            val key = when (source) {
                DataSource.WOLFX_JMA -> KEY_JMA_ENABLED
                DataSource.CEA -> KEY_CEA_ENABLED
                DataSource.CEA_PR -> KEY_CEA_PR_ENABLED
                else -> KEY_CENC_ENABLED
            }
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(key, true)
        }

        fun isWarnOnly(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_WARN_ONLY, false)

        fun isWeatherEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_WEATHER_ENABLED, true)

        fun setWeatherEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_WEATHER_ENABLED, enabled).apply()
        }

        fun isQuakeNotifyEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_QUAKE_NOTIFY_ENABLED, false)

        fun setQuakeNotifyEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_QUAKE_NOTIFY_ENABLED, enabled).apply()
        }

        fun quakeNotifyMag(context: Context): Float =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getFloat(KEY_QUAKE_NOTIFY_MAG, DEFAULT_QUAKE_NOTIFY_MAG)

        fun setQuakeNotifyMag(context: Context, mag: Float) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putFloat(KEY_QUAKE_NOTIFY_MAG, mag).apply()
        }

        fun setServiceEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
        }

        fun setSourceEnabled(context: Context, source: DataSource, enabled: Boolean) {
            val key = when (source) {
                DataSource.WOLFX_JMA -> KEY_JMA_ENABLED
                DataSource.CEA -> KEY_CEA_ENABLED
                DataSource.CEA_PR -> KEY_CEA_PR_ENABLED
                else -> KEY_CENC_ENABLED
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(key, enabled).apply()
        }

        fun setWarnOnly(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_WARN_ONLY, enabled).apply()
        }

        @Volatile
        var latestEew: Eew? = null
            private set

        private const val KEY_LATEST_EEW = "latest_eew"
        private const val KEY_ACTIVE_EEWS = "active_eews"

        private val activeEewsLock = Any()
        private val recentEews = LinkedHashMap<String, Eew>()

        val activeEews: List<Eew>
            get() = synchronized(activeEewsLock) { recentEews.values.toList() }

        private fun updateActiveEews(eew: Eew) {
            val key = eew.source.name + "#" + eew.id
            synchronized(activeEewsLock) {
                if (eew.isCancel) {
                    recentEews.remove(key)
                } else {
                    recentEews[key] = eew
                }
                val cutoff = System.currentTimeMillis() - MAX_ALERT_AGE_MS
                recentEews.entries.removeAll { it.value.reportTimeMillis < cutoff }
            }
        }

        private fun persistActiveEews(context: Context) {
            val list = synchronized(activeEewsLock) { ArrayList(recentEews.values) }
            try {
                val bytes = java.io.ByteArrayOutputStream().use { bos ->
                    java.io.ObjectOutputStream(bos).use { it.writeObject(list) }
                    bos.toByteArray()
                }
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_ACTIVE_EEWS, android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT))
                    .apply()
            } catch (e: Exception) {
                Log.w(TAG, "persist active eews failed", e)
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun loadActiveEews(context: Context) {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ACTIVE_EEWS, null) ?: return
            try {
                val bytes = android.util.Base64.decode(raw, android.util.Base64.DEFAULT)
                val list = java.io.ObjectInputStream(java.io.ByteArrayInputStream(bytes)).use {
                    it.readObject()
                } as? List<Eew> ?: return
                synchronized(activeEewsLock) {
                    recentEews.clear()
                    list.forEach { recentEews[it.source.name + "#" + it.id] = it }
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        fun loadLatestEew(context: Context): Eew? {
            if (synchronized(activeEewsLock) { recentEews.isEmpty() }) {
                loadActiveEews(context)
            }
            latestEew?.let { return it }
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LATEST_EEW, null) ?: return null
            return try {
                val bytes = android.util.Base64.decode(raw, android.util.Base64.DEFAULT)
                (java.io.ObjectInputStream(java.io.ByteArrayInputStream(bytes)).use { it.readObject() }
                    as? Eew).also { latestEew = it }
            } catch (e: Exception) {
                null
            }
        }

        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, EewService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, EewService::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_STOP, true)
            }
            context.stopService(intent)
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var pollJob: Job? = null
    private var backgroundDataJob: Job? = null
    private var weatherPollJob: Job? = null
    private var webSocketClient: EewWebSocketClient? = null

    @Volatile
    private var jmaConnected = false

    @Volatile
    private var cencConnected = false

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        startWebSocket()
        startPolling()
        startBackgroundDataRefresh()
        startWeatherPolling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra(EXTRA_STOP, false) == true) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        pollJob?.cancel()
        backgroundDataJob?.cancel()
        weatherPollJob?.cancel()
        webSocketClient?.close()
        webSocketClient = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateLatestEew(eew: Eew?) {
        latestEew = eew
        if (eew != null) {
            updateActiveEews(eew)
            persistActiveEews(this)
        }
        try {
            val bytes = java.io.ByteArrayOutputStream().use { bos ->
                java.io.ObjectOutputStream(bos).use { it.writeObject(eew) }
                bos.toByteArray()
            }
            getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_LATEST_EEW, android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT))
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "persist failed", e)
        }
    }

    private fun startWebSocket() {
        webSocketClient?.close()
        jmaConnected = false
        cencConnected = false
        webSocketClient = EewWebSocketClient(
            onEew = { eew ->
                updateLatestEew(eew)
                checkAndNotify(eew)
            },
            onConnectionStateChanged = { source, connected ->
                when (source) {
                    DataSource.WOLFX_JMA -> jmaConnected = connected
                    else -> cencConnected = connected
                }
                updateForegroundNotification()
            }
        ).also { it.start() }
    }

    private fun updateForegroundNotification() {
        val text = buildString {
            append("JMA 预警 ").append(if (jmaConnected) "已连接" else "未连接")
            append(" · CEA 预警 ").append(if (cencConnected) "已连接" else "未连接")
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildForegroundNotification(text))
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                try {
                    val warnings = EewRepository.fetchLatestWarnings()
                    updateLatestEew(warnings.firstOrNull())
                    warnings.forEach { checkAndNotify(it) }
                } catch (e: Exception) {
                    Log.w(TAG, "poll failed", e)
                }
                delay(Settings.frontIntervalMs(applicationContext))
            }
        }
    }

    private fun startBackgroundDataRefresh() {
        backgroundDataJob?.cancel()
        backgroundDataJob = scope.launch {
            while (isActive) {
                try {
                    val earthquakes =
                        EarthquakeRepository.fetchRecentEarthquakes(applicationContext)
                    EarthquakeCache.save(applicationContext, earthquakes)
                    checkAndNotifyQuakes(earthquakes)
                } catch (e: Exception) {
                    Log.w(TAG, "background data refresh failed", e)
                }
                delay(Settings.backIntervalMs(applicationContext))
            }
        }
    }

    private fun checkAndNotifyQuakes(earthquakes: List<Earthquake>) {
        if (earthquakes.isEmpty()) return
        if (!isQuakeNotifyEnabled(this)) return
        val magThreshold = quakeNotifyMag(this)
        val now = System.currentTimeMillis()
        val recent = earthquakes.filter {
            it.timeMillis > 0 &&
                now - it.timeMillis <= MAX_QUAKE_AGE_MS &&
                it.magnitude >= magThreshold
        }
        if (recent.isEmpty()) return
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val seen = prefs.getString(KEY_SEEN_QUAKE, "")
            ?.split(",")?.filter { it.isNotBlank() }?.toMutableSet() ?: mutableSetOf()
        var changed = false
        for (quake in recent) {
            if (quake.id.isBlank() || quake.id in seen) continue
            seen.add(quake.id)
            changed = true
            sendQuakeNotification(quake)
        }
        if (changed) {
            prefs.edit()
                .putString(KEY_SEEN_QUAKE, seen.toList().takeLast(200).joinToString(","))
                .apply()
        }
    }

    private fun sendQuakeNotification(quake: Earthquake) {
        val sourceName = quake.sourceShortLabelRes().let { getString(it) }
        val time = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            .format(Date(quake.timeMillis))
        val content = buildString {
            append(quake.place.ifBlank { "位置未定" })
                .append(" · ").append(quake.formattedMagnitude())
            if (quake.depthKm > 0) {
                append(" · 深度 ").append(String.format(Locale.US, "%.0f", quake.depthKm)).append("km")
            }
            append(" · ").append(time)
        }

        val intent = Intent(this, DetailActivity::class.java)
            .putExtra(DetailActivity.EXTRA_EARTHQUAKE, quake)
            .putExtra(
                DetailActivity.EXTRA_ALL_EARTHQUAKES,
                ArrayList(EarthquakeCache.load(applicationContext))
            )
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            this, quake.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("地震情报 · " + quake.formattedMagnitude() + " · " + sourceName)
            .setContentText(content)
            .setStyle(Notification.BigTextStyle().bigText(content))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(Notification.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_RECOMMENDATION)
            .setDefaults(Notification.DEFAULT_ALL)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(quake.id.hashCode(), notification)
    }

    private fun startWeatherPolling() {
        weatherPollJob?.cancel()
        weatherPollJob = scope.launch {
            while (isActive) {
                try {
                    val prefs = getSharedPreferences(
                        com.example.eew.testlite.data.WeatherPrefs.PREFS, MODE_PRIVATE
                    )
                    val province = prefs.getString(
                        com.example.eew.testlite.data.WeatherPrefs.KEY_PROVINCE, ""
                    ) ?: ""
                    val city = prefs.getString(
                        com.example.eew.testlite.data.WeatherPrefs.KEY_CITY, ""
                    ) ?: ""
                    val result = WeatherAlertRepository.fetchAlerts(1, province, city)
                    checkAndNotifyWeather(result.alerts)
                } catch (e: Exception) {
                    Log.w(TAG, "weather poll failed", e)
                }
                delay(Settings.backIntervalMs(applicationContext))
            }
        }
    }

    private fun checkAndNotifyWeather(alerts: List<WeatherAlert>) {
        if (alerts.isEmpty()) return
        if (!isWeatherEnabled(this)) return
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val seen = prefs.getString(KEY_SEEN_WEATHER, "")
            ?.split(",")?.filter { it.isNotBlank() }?.toMutableSet() ?: mutableSetOf()
        val now = System.currentTimeMillis()
        var changed = false
        for (alert in alerts) {
            if (alert.id.isBlank() || alert.id in seen) continue
            if (alert.issueTimeMillis <= 0 ||
                now - alert.issueTimeMillis > MAX_WEATHER_ALERT_AGE_MS
            ) continue
            seen.add(alert.id)
            changed = true
            sendWeatherNotification(alert)
        }
        if (changed) {
            prefs.edit()
                .putString(KEY_SEEN_WEATHER, seen.toList().takeLast(200).joinToString(","))
                .apply()
        }
    }

    private fun sendWeatherNotification(alert: WeatherAlert) {
        val title = buildString {
            append("天气预警")
            if (alert.levelName.isNotBlank()) append("·").append(alert.levelName)
        }
        val time = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            .format(Date(alert.issueTimeMillis))
        val content = buildString {
            append(alert.station).append(" 发布 ")
            append(alert.kind)
            if (alert.levelName.isNotBlank()) append(alert.levelName)
            append("预警 · ").append(time)
        }

        val intent = Intent(this, WeatherAlertActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            this, alert.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_weather_alert)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(Notification.BigTextStyle().bigText(content))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(Notification.PRIORITY_DEFAULT)
            .setCategory(Notification.CATEGORY_RECOMMENDATION)
            .setDefaults(Notification.DEFAULT_ALL)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(alert.id.hashCode(), notification)
    }

    private fun checkAndNotify(eew: Eew) {
        if (eew.isCancel) return
        if (!isRecent(eew)) return
        if (!isSourceEnabled(this, eew.source)) return
        if (isWarnOnly(this) && !eew.isWarn) return

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val key = when (eew.source) {
            DataSource.WOLFX_JMA -> KEY_SEEN_JMA
            else -> KEY_SEEN_CENC
        }
        val seenId = if (eew.reportNum > 0) eew.id + "#" + eew.reportNum else eew.id
        val lastSeen = prefs.getString(key, null)
        if (seenId == lastSeen) return

        prefs.edit().putString(key, seenId).apply()
        sendAlertNotification(eew)
    }

    private fun sendAlertNotification(eew: Eew) {
        val title = buildString {
            append(
                when {
                    eew.isWarn -> "⚠ 紧急地震速报（警报）"
                    else -> "地震速报"
                }
            )
            if (eew.reportNum > 0) append("·第").append(eew.reportNum).append("报")
            if (eew.isFinal) append("·最终")
        }
        val sourceName = getString(eew.sourceShortLabelRes())
        val content = buildString {
            append("【").append(sourceName).append("】")
            if (eew.method.isNotBlank()) append(" ").append(eew.method)
            append(" ").append(eew.hypocenter.ifBlank { "位置未定" })
                .append(" M").append(String.format(Locale.US, "%.1f", eew.displayMagnitude()))
            if (eew.hasIntensity()) {
                append(" ").append(eew.intensityName()).append(eew.maxIntensity)
            }
        }

        val intent = Intent(this, EewDetailActivity::class.java)
            .putExtra(EewDetailActivity.EXTRA_EEW, eew)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            this, eew.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(Notification.BigTextStyle().bigText(content))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(if (eew.isWarn) Notification.PRIORITY_MAX else Notification.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_ALARM)
            .setDefaults(Notification.DEFAULT_ALL)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun buildForegroundNotification(
        contentText: String = getString(R.string.notification_foreground_text)
    ): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, FOREGROUND_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_foreground_title))
            .setContentText(contentText)
            .setContentIntent(pending)
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.notification_channel_desc)
            enableVibration(true)
        }
        val foregroundChannel = NotificationChannel(
            FOREGROUND_CHANNEL_ID,
            getString(R.string.notification_foreground_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_foreground_channel_desc)
            enableVibration(false)
            setSound(null, null)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
        manager.createNotificationChannel(foregroundChannel)
    }
}