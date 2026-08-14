package com.paperweight.os.data.repository

import com.paperweight.os.data.dao.ScheduleDao
import com.paperweight.os.data.db.entity.ScheduleBlockEntity

class ScheduleRepository(private val scheduleDao: ScheduleDao) {
    fun observeBlocks() = scheduleDao.observeBlocks()
    suspend fun getBlock(id: String) = scheduleDao.getBlock(id)
    suspend fun upsertBlock(block: ScheduleBlockEntity) = scheduleDao.upsertBlock(block)
    suspend fun deleteBlock(id: String) = scheduleDao.deleteBlock(id)
}
