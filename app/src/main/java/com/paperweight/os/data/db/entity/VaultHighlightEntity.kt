package com.paperweight.os.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vault_highlights",
    foreignKeys = [ForeignKey(entity = VaultTrackEntity::class, parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("trackId")]
)
data class VaultHighlightEntity(
    @PrimaryKey val id: String,
    val trackId: String,
    val label: String,
    val startMs: Long,
    val endMs: Long,
    val createdAt: Long,
)
