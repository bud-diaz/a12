package com.paperweight.os.data.repository

import com.paperweight.os.data.dao.VaultDao
import com.paperweight.os.data.db.entity.VaultTrackEntity

class VaultRepository(private val vaultDao: VaultDao) {
    fun observeTracks() = vaultDao.observeTracks()
    suspend fun getTrack(id: String) = vaultDao.getTrack(id)
    suspend fun upsertTrack(track: VaultTrackEntity) = vaultDao.upsertTrack(track)
    suspend fun deleteTrack(id: String) = vaultDao.deleteTrack(id)
}
