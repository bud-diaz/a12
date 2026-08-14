package com.paperweight.os.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.paperweight.os.data.db.entity.VaultCollectionEntity
import com.paperweight.os.data.db.entity.VaultCollectionTrackCrossRef
import com.paperweight.os.data.db.entity.VaultHighlightEntity
import com.paperweight.os.data.db.entity.VaultTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Upsert suspend fun upsertTrack(track: VaultTrackEntity)
    @Query("SELECT * FROM vault_tracks ORDER BY title COLLATE NOCASE") fun observeTracks(): Flow<List<VaultTrackEntity>>
    @Query("SELECT * FROM vault_tracks WHERE id = :id LIMIT 1") suspend fun getTrack(id: String): VaultTrackEntity?
    @Query("DELETE FROM vault_tracks WHERE id = :id") suspend fun deleteTrack(id: String)

    @Upsert suspend fun upsertCollection(collection: VaultCollectionEntity)
    @Query("SELECT * FROM vault_collections ORDER BY name COLLATE NOCASE") fun observeCollections(): Flow<List<VaultCollectionEntity>>
    @Upsert suspend fun upsertCollectionTrack(ref: VaultCollectionTrackCrossRef)

    @Upsert suspend fun upsertHighlight(highlight: VaultHighlightEntity)
    @Query("SELECT * FROM vault_highlights WHERE trackId = :trackId ORDER BY startMs") fun observeHighlights(trackId: String): Flow<List<VaultHighlightEntity>>
}
