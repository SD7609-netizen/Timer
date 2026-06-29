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
    BEEP_SINGLE("Короткий бип"),
    BEEP_DOUBLE("Двойной бип"),
    BEEP_TRIPLE("Тройной бип"),
    BELL("Звонок"),
    WHISTLE("Свисток"),
    CHIME("Колокол"),
    LOW_BEEP("Низкий сигнал"),
    HIGH_BEEP("Высокий сигнал"),
    DING("Динь"),
    PULSE("Пульс"),
    FANFARE("Финальный сигнал")
}
