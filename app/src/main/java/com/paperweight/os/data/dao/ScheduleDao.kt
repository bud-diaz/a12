package com.paperweight.os.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.paperweight.os.data.db.entity.ScheduleBlockEntity
import com.paperweight.os.data.db.entity.SmartPlaylistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Upsert suspend fun upsertBlock(block: ScheduleBlockEntity)
    @Query("SELECT * FROM schedule_blocks ORDER BY dayOfWeek, startMinutes") fun observeBlocks(): Flow<List<ScheduleBlockEntity>>
    @Query("SELECT * FROM schedule_blocks WHERE id = :id LIMIT 1") suspend fun getBlock(id: String): ScheduleBlockEntity?
    @Query("DELETE FROM schedule_blocks WHERE id = :id") suspend fun deleteBlock(id: String)

    @Upsert suspend fun upsertSmartPlaylist(playlist: SmartPlaylistEntity)
    @Query("SELECT * FROM smart_playlists ORDER BY name COLLATE NOCASE") fun observeSmartPlaylists(): Flow<List<SmartPlaylistEntity>>
    @Query("SELECT * FROM smart_playlists WHERE id = :id LIMIT 1") suspend fun getSmartPlaylist(id: String): SmartPlaylistEntity?
}
