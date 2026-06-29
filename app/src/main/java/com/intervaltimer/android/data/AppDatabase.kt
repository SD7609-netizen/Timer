package com.intervaltimer.android.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Database(entities = [Preset::class, Interval::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun presetDao(): PresetDao
    abstract fun intervalDao(): IntervalDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "timer.db")
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
        }
    }
}

class Converters {
    @TypeConverter fun soundToString(s: SoundType): String = s.name
    @TypeConverter fun stringToSound(s: String): SoundType = SoundType.valueOf(s)
    @TypeConverter fun intervalTypeToString(t: IntervalType): String = t.name
    @TypeConverter fun stringToIntervalType(s: String): IntervalType =
        try { IntervalType.valueOf(s) } catch (_: Exception) { IntervalType.NORMAL }
}
