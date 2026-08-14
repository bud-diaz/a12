package com.paperweight.os.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "listener_tokens", indices = [Index(value = ["tokenHash"], unique = true), Index("label")])
data class ListenerTokenEntity(
    @PrimaryKey val id: String,
    val label: String,
    val tokenHash: String,
    val isEnabled: Boolean = true,
    val createdAt: Long,
    val expiresAt: Long? = null,
    val lastUsedAt: Long? = null,
)
