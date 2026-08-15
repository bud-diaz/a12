package com.paperweight.os.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.paperweight.os.data.dao.AnalyticsDao
import com.paperweight.os.data.dao.ScheduleDao
import com.paperweight.os.data.dao.StationDao
import com.paperweight.os.data.dao.TokenDao
import com.paperweight.os.data.dao.VaultDao
import com.paperweight.os.data.db.entity.AnalyticsDailyRollupEntity
import com.paperweight.os.data.db.entity.AnalyticsEventEntity
import com.paperweight.os.data.db.entity.ListenerSessionEntity
import com.paperweight.os.data.db.entity.ListenerTokenEntity
import com.paperweight.os.data.db.entity.ScheduleBlockEntity
import com.paperweight.os.data.db.entity.SmartPlaylistEntity
import com.paperweight.os.data.db.entity.StationProfileEntity
import com.paperweight.os.data.db.entity.VaultCollectionEntity
import com.paperweight.os.data.db.entity.VaultCollectionTrackCrossRef
import com.paperweight.os.data.db.entity.VaultHighlightEntity
import com.paperweight.os.data.db.entity.VaultTrackEntity

@Database(
    entities = [
        VaultTrackEntity::class,
        VaultCollectionEntity::class,
        VaultCollectionTrackCrossRef::class,
        VaultHighlightEntity::class,
        ScheduleBlockEntity::class,
        SmartPlaylistEntity::class,
        ListenerTokenEntity::class,
        AnalyticsEventEntity::class,
        AnalyticsDailyRollupEntity::class,
        ListenerSessionEntity::class,
        StationProfileEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun tokenDao(): TokenDao
    abstract fun analyticsDao(): AnalyticsDao
    abstract fun stationDao(): StationDao

    companion object {
        const val DATABASE_NAME = "paperweight-os.db"

        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DATABASE_NAME)
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
        }
    }
}
