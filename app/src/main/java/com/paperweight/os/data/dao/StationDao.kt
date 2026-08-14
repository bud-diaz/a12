package com.paperweight.os.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.paperweight.os.data.db.entity.StationProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StationDao {
    @Upsert suspend fun upsertProfile(profile: StationProfileEntity)
    @Query("SELECT * FROM station_profile WHERE id = 'default' LIMIT 1") fun observeProfile(): Flow<StationProfileEntity?>
    @Query("SELECT * FROM station_profile WHERE id = 'default' LIMIT 1") suspend fun getProfile(): StationProfileEntity?
}
