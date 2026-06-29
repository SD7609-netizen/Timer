package com.intervaltimer.android.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "presets")
data class Preset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val finalSoundType: SoundType = SoundType.FANFARE,
    val vibrationEnabled: Boolean = true
)

data class PresetWithIntervals(
    val preset: Preset,
    val intervals: List<Interval>
)
