package com.paperweight.os.data.repository

import com.paperweight.os.data.dao.StationDao
import com.paperweight.os.data.db.entity.StationProfileEntity

class StationRepository(private val stationDao: StationDao) {
    fun observeProfile() = stationDao.observeProfile()
    suspend fun getProfile() = stationDao.getProfile()
    suspend fun upsertProfile(profile: StationProfileEntity) = stationDao.upsertProfile(profile)
}
