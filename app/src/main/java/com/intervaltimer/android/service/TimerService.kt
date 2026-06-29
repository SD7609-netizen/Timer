package com.intervaltimer.android.service

import android.app.*
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.view.*
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.intervaltimer.android.ui.CircularTimerView
import com.intervaltimer.android.R
import com.intervaltimer.android.data.AppDatabase
import com.intervaltimer.android.data.Interval
import com.intervaltimer.android.data.IntervalType
import com.intervaltimer.android.data.SoundType
import com.intervaltimer.android.ui.MainActivity
import com.intervaltimer.android.widget.TimerWidgetProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TimerService : Service() {

    companion object {
        const val ACTION_START  = "com.intervaltimer.START"
        const val ACTION_PAUSE  = "com.intervaltimer.PAUSE"
        const val ACTION_RESUME = "com.intervaltimer.RESUME"
        const val ACTION_STOP   = "com.intervaltimer.STOP"
        const val EXTRA_PRESET_ID   = "preset_id"
        const val CHANNEL_ID        = "timer_channel"
        const val NOTIF_ID          = 1001
        const val PREFS_NAME        = "timer_settings"
        const val PREF_KEEP_SCREEN  = "keep_screen_on"
        const val PREF_OVERLAY_SIZE = "overlay_size"

        val state: MutableStateFlow<TimerState> = MutableStateFlow(TimerState.Idle)
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var timerJob: Job? = null
    private lateinit var sound: SoundManager

    private var intervals: List<Interval> = emptyList()
    private var finalSound: SoundType = SoundType.FANFARE
    private var vibrationEnabled: Boolean = true
    private var currentIndex = 0
    private var remainingSeconds = 0
    private var isPaused = false
    private var loopCount = 0       // 0 = без повтора, -1 = бесконечно, N = N раз
    private var loopsDone = 0
    private var warningSeconds = 10
    private var warningSent = false

    private var wakeLock: PowerManager.WakeLock? = null

    // Overlay
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var dragInitX = 0
    private var dragInitY = 0
    private var dragTouchX = 0f
    private var dragTouchY = 0f
    private var dragMoved = false

    override fun onCreate() {
        super.onCreate()
        sound = SoundManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START  -> { val id = intent.getLongExtra(EXTRA_PRESET_ID, -1L); if (id != -1L) startTimer(id) }
            ACTION_PAUSE  -> pauseTimer()
            ACTION_RESUME -> resumeTimer()
            ACTION_STOP   -> stopTimer()
        }
        return START_NOT_STICKY
    }

    // ── Start ─────────────────────────────────────────────────────

    private fun startTimer(presetId: Long) {
        scope.launch {
            val db = AppDatabase.get(this@TimerService)
            val preset = db.presetDao().getById(presetId) ?: return@launch
            val loaded = db.intervalDao().getByPreset(presetId)
            if (loaded.isEmpty()) return@launch

            intervals       = loaded
            finalSound      = preset.finalSoundType
            vibrationEnabled = preset.vibrationEnabled
            loopCount       = preset.loopCount
            warningSeconds  = preset.warningSeconds
            loopsDone       = 0
            currentIndex    = 0
            isPaused        = false

            startForeground(NOTIF_ID, buildNotification("Запуск...", ""))
            acquireWakeLockIfNeeded()
            showOverlay()
            runInterval()
        }
    }

    // ── Main timer loop ───────────────────────────────────────────

    private fun runInterval() {
        timerJob?.cancel()
        if (currentIndex >= intervals.size) { onFinished(); return }

        val interval = intervals[currentIndex]
        remainingSeconds = interval.durationSeconds
        warningSent = false

        timerJob = scope.launch {
            while (remainingSeconds > 0) {
                if (isPaused) { delay(200); continue }

                // Предупреждение за N секунд
                if (!warningSent && warningSeconds > 0 && remainingSeconds == warningSeconds) {
                    warningSent = true
                    sound.play(SoundType.BEEP_SINGLE, false)
                }

                val nextName = if (currentIndex + 1 < intervals.size) intervals[currentIndex + 1].name else "—"
                state.value = TimerState.Running(
                    intervalName    = interval.name,
                    nextIntervalName = nextName,
                    remainingSeconds = remainingSeconds,
                    totalSeconds    = interval.durationSeconds,
                    currentIndex    = currentIndex,
                    totalIntervals  = intervals.size,
                    isPaused        = false
                )

                val timeStr     = formatTime(remainingSeconds)
                val prog        = ((interval.durationSeconds - remainingSeconds).toFloat() / interval.durationSeconds * 100).toInt()
                val totalLeft   = intervals.drop(currentIndex + 1).sumOf { it.durationSeconds } + remainingSeconds
                val color       = intervalColor(interval.intervalType)

                updateNotification(interval.name, timeStr)
                updateOverlayUi(
                    top    = "${intervals.size} / ${intervals.size - currentIndex}",
                    name   = interval.name,
                    time   = timeStr,
                    bottom = "ещё ${formatTime(totalLeft)}",
                    progress = prog,
                    accent = color
                )
                updateWidget(interval.name, timeStr, prog)

                delay(1000)
                if (!isPaused) remainingSeconds--
            }

            if (interval.customSoundPath.isNotEmpty()) {
                sound.playCustom(interval.customSoundPath, vibrationEnabled)
            } else {
                sound.play(interval.soundType, vibrationEnabled)
            }
            currentIndex++
            delay(500)
            runInterval()
        }
    }

    private fun intervalColor(type: IntervalType): Int = when (type) {
        IntervalType.REST   -> Color.parseColor("#FF64B5F6")  // синий
        IntervalType.ACTIVE -> Color.parseColor("#FFFFCC00")  // жёлтый
        IntervalType.NORMAL -> Color.parseColor("#FFFFCC00")  // жёлтый
    }

    // ── Loop / Finish ─────────────────────────────────────────────

    private fun onFinished() {
        // Проверяем повтор серии
        if (loopCount != 0 && (loopCount == -1 || loopsDone < loopCount - 1)) {
            if (loopCount != -1) loopsDone++
            currentIndex = 0
            scope.launch {
                sound.play(SoundType.BEEP_DOUBLE, vibrationEnabled)
                delay(1500)
                runInterval()
            }
            return
        }

        sound.play(finalSound, vibrationEnabled)
        state.value = TimerState.Finished
        updateNotification("Готово!", "Все интервалы завершены")
        updateOverlayUi("Финиш", "", "0:00", "Выполнено!", 100, Color.parseColor("#FF4CAF82"))
        updateWidget("Финиш", "0:00", 100)

        scope.launch {
            delay(3000)
            stopSelf()
        }
    }

    // ── Pause / Resume / Stop ─────────────────────────────────────

    private fun pauseTimer() {
        isPaused = true
        val cur = state.value
        if (cur is TimerState.Running) state.value = cur.copy(isPaused = true)
        updateNotification(getCurrentName(), "Пауза · ${formatTime(remainingSeconds)}")
    }

    private fun resumeTimer() {
        isPaused = false
        val cur = state.value
        if (cur is TimerState.Running) state.value = cur.copy(isPaused = false)
    }

    private fun stopTimer() {
        timerJob?.cancel()
        state.value = TimerState.Idle
        releaseWakeLock()
        hideOverlay()
        updateWidget("", "--:--", 0)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun getCurrentName() = intervals.getOrNull(currentIndex)?.name ?: ""

    // ── Wake Lock ─────────────────────────────────────────────────

    private fun acquireWakeLockIfNeeded() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (!prefs.getBoolean(PREF_KEEP_SCREEN, false)) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
            "IntervalTimer::WakeLock"
        )
        wakeLock?.acquire(3_600_000L)  // max 1 час
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
    }

    // ── Floating overlay ──────────────────────────────────────────

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this) || overlayView != null) return

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val sizeDp = when (prefs.getString(PREF_OVERLAY_SIZE, "medium")) {
            "small" -> 100; "large" -> 160; else -> 130
        }
        val sizePx = (sizeDp * resources.displayMetrics.density).toInt()

        overlayView = CircularTimerView(this)
        overlayParams = WindowManager.LayoutParams(
            sizePx, sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 60; y = 200
        }
        windowManager!!.addView(overlayView, overlayParams)

        // Касание: drag или tap (пауза/возобновление)
        overlayView!!.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragInitX  = overlayParams!!.x
                    dragInitY  = overlayParams!!.y
                    dragTouchX = event.rawX
                    dragTouchY = event.rawY
                    dragMoved  = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - dragTouchX
                    val dy = event.rawY - dragTouchY
                    if (dx * dx + dy * dy > 400f) dragMoved = true   // порог ~20px
                    if (dragMoved) {
                        overlayParams!!.x = dragInitX + dx.toInt()
                        overlayParams!!.y = dragInitY + dy.toInt()
                        windowManager!!.updateViewLayout(overlayView, overlayParams)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragMoved) {
                        val cur = state.value
                        if (cur is TimerState.Running) {
                            if (cur.isPaused) resumeTimer() else pauseTimer()
                        }
                    }
                }
            }
            true
        }
    }

    private fun updateOverlayUi(top: String, name: String, time: String, bottom: String, progress: Int, accent: Int) {
        val v = overlayView as? CircularTimerView ?: return
        v.labelTop     = top
        v.intervalName = name
        v.timeText     = time
        v.labelBottom  = bottom
        v.progress     = progress / 100f
        v.accentColor  = accent
    }

    private fun hideOverlay() {
        overlayView?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
        overlayView = null
    }

    // ── Home Screen Widget ─────────────────────────────────────────

    private fun updateWidget(intervalName: String, timeStr: String, progress: Int) {
        val manager = AppWidgetManager.getInstance(this)
        val ids = manager.getAppWidgetIds(ComponentName(this, TimerWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val views = RemoteViews(packageName, R.layout.widget_timer)
        views.setTextViewText(R.id.tvWidgetInterval, intervalName.ifEmpty { "Таймер" })
        views.setTextViewText(R.id.tvWidgetTime, timeStr)
        views.setProgressBar(R.id.progressWidget, 100, progress, false)
        manager.updateAppWidget(ids, views)
    }

    // ── Notification ──────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Интервальный таймер", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Уведомление активного таймера"; setSound(null, null) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(title: String, text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val pauseAction = if (isPaused)
            NotificationCompat.Action(R.drawable.ic_play, "Продолжить", pendingAction(ACTION_RESUME, 2))
        else
            NotificationCompat.Action(R.drawable.ic_pause, "Пауза", pendingAction(ACTION_PAUSE, 1))
        val stopAction = NotificationCompat.Action(R.drawable.ic_stop, "Стоп", pendingAction(ACTION_STOP, 3))

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openIntent)
            .addAction(pauseAction)
            .addAction(stopAction)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun pendingAction(action: String, requestCode: Int): PendingIntent {
        val i = Intent(this, TimerService::class.java).apply { this.action = action }
        return PendingIntent.getService(this, requestCode, i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun updateNotification(title: String, text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(title, text))
    }

    private fun formatTime(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)

    override fun onTaskRemoved(rootIntent: Intent?) { stopTimer(); super.onTaskRemoved(rootIntent) }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        scope.cancel()
        releaseWakeLock()
        hideOverlay()
        state.value = TimerState.Idle
        super.onDestroy()
    }
}

sealed class TimerState {
    object Idle : TimerState()
    object Finished : TimerState()
    data class Running(
        val intervalName: String,
        val nextIntervalName: String,
        val remainingSeconds: Int,
        val totalSeconds: Int,
        val currentIndex: Int,
        val totalIntervals: Int,
        val isPaused: Boolean
    ) : TimerState()
}
