package com.paperweight.os.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vault_tracks",
    indices = [Index(value = ["visibility"]), Index(value = ["storagePath"], unique = true)]
)
data class VaultTrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val sourceUri: String,
    val storagePath: String,
    val durationMs: Long,
    val mimeType: String,
    val visibility: String,
    val suggestedPriceCents: Int = 0,
    val minimumPriceCents: Int = 0,
    val allowFree: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
)
