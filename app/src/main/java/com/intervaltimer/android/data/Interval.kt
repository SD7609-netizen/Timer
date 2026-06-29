package com.intervaltimer.android.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "intervals")
data class Interval(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val presetId: Long,
    val position: Int,
    val name: String,
    val durationSeconds: Int,
    val soundType: SoundType = SoundType.BEEP_SINGLE
)

enum class SoundType(val label: String) {
    BEEP_SINGLE("Один бип"),
    BEEP_DOUBLE("Двойной бип"),
    BELL("Звонок"),
    WHISTLE("Свисток"),
    CHIME("Колокол"),
    FANFARE("Финальный сигнал")
}
