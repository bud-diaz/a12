package com.paperweight.os.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.paperweight.os.data.db.entity.ListenerTokenEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TokenDao {
    @Upsert suspend fun upsertToken(token: ListenerTokenEntity)
    @Query("SELECT * FROM listener_tokens ORDER BY createdAt DESC") fun observeTokens(): Flow<List<ListenerTokenEntity>>
    @Query("SELECT * FROM listener_tokens WHERE tokenHash = :tokenHash AND isEnabled = 1 LIMIT 1") suspend fun findEnabledByHash(tokenHash: String): ListenerTokenEntity?
    @Query("UPDATE listener_tokens SET lastUsedAt = :lastUsedAt WHERE id = :id") suspend fun markUsed(id: String, lastUsedAt: Long)
}
