package com.paperweight.os.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "station_profile")
data class StationProfileEntity(
    @PrimaryKey val id: String = DEFAULT_ID,
    val stationName: String,
    val description: String? = null,
    val accentColor: String? = null,
    val localPort: Int,
    val lanUrl: String? = null,
    val publicUrl: String? = null,
    val lastReachableAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    companion object { const val DEFAULT_ID = "default" }
}
