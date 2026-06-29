package com.intervaltimer.android.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "presets")
data class Preset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val finalSoundType: SoundType = SoundType.FANFARE,
    val vibrationEnabled: Boolean = true,
    val loopCount: Int = 0,         // 0 = без повтора, -1 = бесконечно, N = повторить N раз
    val warningSeconds: Int = 10    // предупреждение за N сек до конца интервала (0 = выкл)
)

data class PresetWithIntervals(
    val preset: Preset,
    val intervals: List<Interval>
)
