package com.paperweight.os.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "analytics_daily_rollups")
data class AnalyticsDailyRollupEntity(
    @PrimaryKey val day: String,
    val listenerCount: Int,
    val playCount: Int,
    val totalListenMs: Long,
    val updatedAt: Long,
)
