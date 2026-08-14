package com.paperweight.os.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "vault_collection_tracks",
    primaryKeys = ["collectionId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = VaultCollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = VaultTrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("trackId")]
)
data class VaultCollectionTrackCrossRef(
    val collectionId: String,
    val trackId: String,
    val sortOrder: Int = 0,
)
