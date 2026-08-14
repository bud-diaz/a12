package com.paperweight.os.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "schedule_blocks", indices = [Index("dayOfWeek"), Index("playlistId")])
data class ScheduleBlockEntity(
    @PrimaryKey val id: String,
    val name: String,
    val dayOfWeek: Int,
    val startMinutes: Int,
    val endMinutes: Int,
    val playlistId: String? = null,
    val isEnabled: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
)
