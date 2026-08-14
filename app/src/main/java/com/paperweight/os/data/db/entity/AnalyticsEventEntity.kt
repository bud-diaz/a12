package com.paperweight.os.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "analytics_events", indices = [Index("eventType"), Index("createdAt"), Index("sessionId")])
data class AnalyticsEventEntity(
    @PrimaryKey val id: String,
    val eventType: String,
    val sessionId: String? = null,
    val trackId: String? = null,
    val payloadJson: String? = null,
    val createdAt: Long,
)
