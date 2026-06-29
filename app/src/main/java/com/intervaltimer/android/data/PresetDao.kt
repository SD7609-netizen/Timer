package com.intervaltimer.android.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PresetDao {
    @Query("SELECT * FROM presets ORDER BY id DESC")
    fun getAllFlow(): Flow<List<Preset>>

    @Query("SELECT * FROM presets WHERE id = :id")
    suspend fun getById(id: Long): Preset?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preset: Preset): Long

    @Update
    suspend fun update(preset: Preset)

    @Delete
    suspend fun delete(preset: Preset)
}

@Dao
interface IntervalDao {
    @Query("SELECT * FROM intervals WHERE presetId = :presetId ORDER BY position ASC")
    suspend fun getByPreset(presetId: Long): List<Interval>

    @Query("SELECT * FROM intervals WHERE presetId = :presetId ORDER BY position ASC")
    fun getByPresetFlow(presetId: Long): Flow<List<Interval>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(interval: Interval): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(intervals: List<Interval>)

    @Delete
    suspend fun delete(interval: Interval)

    @Query("DELETE FROM intervals WHERE presetId = :presetId")
    suspend fun deleteByPreset(presetId: Long)
}
