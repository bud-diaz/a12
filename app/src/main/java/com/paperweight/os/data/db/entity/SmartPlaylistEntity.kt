package com.paperweight.os.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "smart_playlists")
data class SmartPlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val rulesJson: String,
    val createdAt: Long,
    val updatedAt: Long,
)
