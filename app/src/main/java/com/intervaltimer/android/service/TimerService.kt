package com.intervaltimer.android.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.intervaltimer.android.R
import com.intervaltimer.android.data.AppDatabase
import com.intervaltimer.android.data.Interval
import com.intervaltimer.android.data.SoundType
import com.intervaltimer.android.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TimerService : Service() {

    companion object {
        const val ACTION_START = "com.intervaltimer.START"
        const val ACTION_PAUSE = "com.intervaltimer.PAUSE"
        const val ACTION_RESUME = "com.intervaltimer.RESUME"
        const val ACTION_STOP = "com.intervaltimer.STOP"
        const val EXTRA_PRESET_ID = "preset_id"
        const val CHANNEL_ID = "timer_channel"
        const val NOTIF_ID = 1001

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

    // Floating overlay
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var dragInitX = 0
    private var dragInitY = 0
    private var dragTouchX = 0f
    private var dragTouchY = 0f

    override fun onCreate() {
        super.onCreate()
        sound = SoundManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val presetId = intent.getLongExtra(EXTRA_PRESET_ID, -1L)
                if (presetId != -1L) startTimer(presetId)
            }
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESUME -> resumeTimer()
            ACTION_STOP -> stopTimer()
        }
        return START_NOT_STICKY
    }

    private fun startTimer(presetId: Long) {
        scope.launch {
            val db = AppDatabase.get(this@TimerService)
            val preset = db.presetDao().getById(presetId) ?: return@launch
            val loaded = db.intervalDao().getByPreset(presetId)
            if (loaded.isEmpty()) return@launch

            intervals = loaded
            finalSound = preset.finalSoundType
            vibrationEnabled = preset.vibrationEnabled
            currentIndex = 0
            isPaused = false

            startForeground(NOTIF_ID, buildNotification("Запуск...", ""))
            showOverlay()
            runInterval()
        }
    }

    private fun runInterval() {
        timerJob?.cancel()
        if (currentIndex >= intervals.size) {
            onFinished()
            return
        }
        val interval = intervals[currentIndex]
        remainingSeconds = interval.durationSeconds

        timerJob = scope.launch {
            while (remainingSeconds > 0) {
                if (isPaused) { delay(200); continue }

                val nextName = if (currentIndex + 1 < intervals.size) intervals[currentIndex + 1].name else "—"
                state.value = TimerState.Running(
                    intervalName = interval.name,
                    nextIntervalName = nextName,
                    remainingSeconds = remainingSeconds,
                    totalSeconds = interval.durationSeconds,
                    currentIndex = currentIndex,
                    totalIntervals = intervals.size,
                    isPaused = false
                )
                val timeStr = formatTime(remainingSeconds)
                updateNotification(interval.name, timeStr)
                updateOverlayUi(interval.name, timeStr,
                    ((interval.durationSeconds - remainingSeconds).toFloat() / interval.durationSeconds * 100).toInt())
                delay(1000)
                if (!isPaused) remainingSeconds--
            }

            sound.play(interval.soundType, vibrationEnabled)
            currentIndex++
            delay(500)
            runInterval()
        }
    }

    private fun onFinished() {
        sound.play(finalSound, vibrationEnabled)
        state.value = TimerState.Finished
        updateNotification("Готово!", "Все интервалы завершены")
        updateOverlayUi("Готово!", "0:00", 100)
        scope.launch {
            delay(3000)
            stopSelf()
        }
    }

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
        hideOverlay()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun getCurrentName() = intervals.getOrNull(currentIndex)?.name ?: ""

    // ── Floating overlay ──────────────────────────────────────────

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) return
        if (overlayView != null) return

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = LayoutInflater.from(this).inflate(R.layout.layout_floating_timer, null)

        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 60
            y = 200
        }

        windowManager!!.addView(overlayView, overlayParams)

        overlayView!!.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        dragInitX = overlayParams!!.x
                        dragInitY = overlayParams!!.y
                        dragTouchX = event.rawX
                        dragTouchY = event.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        overlayParams!!.x = dragInitX + (event.rawX - dragTouchX).toInt()
                        overlayParams!!.y = dragInitY + (event.rawY - dragTouchY).toInt()
                        windowManager!!.updateViewLayout(overlayView, overlayParams)
                    }
                }
                return true
            }
        })
    }

    private fun updateOverlayUi(name: String, time: String, progress: Int) {
        val v = overlayView ?: return
        v.findViewById<TextView>(R.id.tvOverlayName)?.text = name
        v.findViewById<TextView>(R.id.tvOverlayTime)?.text = time
        v.findViewById<ProgressBar>(R.id.overlayProgress)?.progress = progress
    }

    private fun hideOverlay() {
        overlayView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
    }

    // ── Notification ─────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Интервальный таймер", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Уведомление активного таймера"
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(title: String, text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val pauseAction = if (isPaused) {
            NotificationCompat.Action(R.drawable.ic_play, "Продолжить", pendingAction(ACTION_RESUME, 2))
        } else {
            NotificationCompat.Action(R.drawable.ic_pause, "Пауза", pendingAction(ACTION_PAUSE, 1))
        }
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
        val intent = Intent(this, TimerService::class.java).apply { this.action = action }
        return PendingIntent.getService(this, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun updateNotification(title: String, text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(title, text))
    }

    private fun formatTime(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%d:%02d".format(m, s)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopTimer()
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
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
