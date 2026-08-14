package com.paperweight.os.data.repository

import com.paperweight.os.data.dao.AnalyticsDao
import com.paperweight.os.data.db.entity.AnalyticsDailyRollupEntity
import com.paperweight.os.data.db.entity.AnalyticsEventEntity
import com.paperweight.os.data.db.entity.ListenerSessionEntity

class AnalyticsRepository(private val analyticsDao: AnalyticsDao) {
    fun observeRecentEvents(limit: Int = 100) = analyticsDao.observeRecentEvents(limit)
    fun observeActiveSessions() = analyticsDao.observeActiveSessions()
    fun observeDailyRollups() = analyticsDao.observeDailyRollups()
    suspend fun upsertEvent(event: AnalyticsEventEntity) = analyticsDao.upsertEvent(event)
    suspend fun upsertSession(session: ListenerSessionEntity) = analyticsDao.upsertSession(session)
    suspend fun upsertDailyRollup(rollup: AnalyticsDailyRollupEntity) = analyticsDao.upsertDailyRollup(rollup)
}
