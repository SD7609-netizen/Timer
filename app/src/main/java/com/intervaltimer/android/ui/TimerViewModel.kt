package com.intervaltimer.android.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.intervaltimer.android.data.AppDatabase
import com.intervaltimer.android.data.Interval
import com.intervaltimer.android.data.Preset
import com.intervaltimer.android.data.SoundType
import com.intervaltimer.android.service.TimerService
import com.intervaltimer.android.service.TimerState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TimerViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    val presets = db.presetDao().getAllFlow()
    val timerState: StateFlow<TimerState> = TimerService.state

    fun startTimer(presetId: Long) {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, TimerService::class.java).apply {
            action = TimerService.ACTION_START
            putExtra(TimerService.EXTRA_PRESET_ID, presetId)
        }
        ctx.startForegroundService(intent)
    }

    fun pause() = sendAction(TimerService.ACTION_PAUSE)
    fun resume() = sendAction(TimerService.ACTION_RESUME)
    fun stop() = sendAction(TimerService.ACTION_STOP)

    private fun sendAction(action: String) {
        val ctx = getApplication<Application>()
        ctx.startService(Intent(ctx, TimerService::class.java).apply { this.action = action })
    }

    fun loadIntervals(presetId: Long, callback: (List<Interval>) -> Unit) {
        viewModelScope.launch {
            callback(db.intervalDao().getByPreset(presetId))
        }
    }

    fun savePreset(name: String, finalSound: SoundType, vibration: Boolean,
                   loopCount: Int, warningSeconds: Int,
                   intervals: List<Interval>, onDone: (Long) -> Unit) {
        viewModelScope.launch {
            val preset = Preset(name = name, finalSoundType = finalSound, vibrationEnabled = vibration,
                loopCount = loopCount, warningSeconds = warningSeconds)
            val id = db.presetDao().insert(preset)
            val withIds = intervals.mapIndexed { i, iv -> iv.copy(presetId = id, position = i, id = 0) }
            db.intervalDao().insertAll(withIds)
            onDone(id)
        }
    }

    fun updatePreset(preset: Preset, intervals: List<Interval>) {
        viewModelScope.launch {
            db.presetDao().update(preset)
            db.intervalDao().deleteByPreset(preset.id)
            val withIds = intervals.mapIndexed { i, iv -> iv.copy(presetId = preset.id, position = i, id = 0) }
            db.intervalDao().insertAll(withIds)
        }
    }

    fun deletePreset(preset: Preset) {
        viewModelScope.launch {
            db.intervalDao().deleteByPreset(preset.id)
            db.presetDao().delete(preset)
        }
    }
}
