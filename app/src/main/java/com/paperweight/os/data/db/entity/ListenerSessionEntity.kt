package com.paperweight.os.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "listener_sessions", indices = [Index("startedAt"), Index("lastHeartbeatAt")])
data class ListenerSessionEntity(
    @PrimaryKey val id: String,
    val userAgent: String? = null,
    val remoteAddress: String? = null,
    val startedAt: Long,
    val lastHeartbeatAt: Long,
    val endedAt: Long? = null,
)
