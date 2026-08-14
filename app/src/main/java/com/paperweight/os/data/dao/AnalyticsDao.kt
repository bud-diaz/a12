package com.paperweight.os.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.paperweight.os.data.db.entity.AnalyticsDailyRollupEntity
import com.paperweight.os.data.db.entity.AnalyticsEventEntity
import com.paperweight.os.data.db.entity.ListenerSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalyticsDao {
    @Upsert suspend fun upsertEvent(event: AnalyticsEventEntity)
    @Query("SELECT * FROM analytics_events ORDER BY createdAt DESC LIMIT :limit") fun observeRecentEvents(limit: Int = 100): Flow<List<AnalyticsEventEntity>>
    @Upsert suspend fun upsertSession(session: ListenerSessionEntity)
    @Query("SELECT * FROM listener_sessions WHERE endedAt IS NULL ORDER BY lastHeartbeatAt DESC") fun observeActiveSessions(): Flow<List<ListenerSessionEntity>>
    @Upsert suspend fun upsertDailyRollup(rollup: AnalyticsDailyRollupEntity)
    @Query("SELECT * FROM analytics_daily_rollups ORDER BY day DESC") fun observeDailyRollups(): Flow<List<AnalyticsDailyRollupEntity>>
}
