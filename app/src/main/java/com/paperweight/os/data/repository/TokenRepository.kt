package com.paperweight.os.data.repository

import com.paperweight.os.data.dao.TokenDao
import com.paperweight.os.data.db.entity.ListenerTokenEntity

class TokenRepository(private val tokenDao: TokenDao) {
    fun observeTokens() = tokenDao.observeTokens()
    suspend fun upsertToken(token: ListenerTokenEntity) = tokenDao.upsertToken(token)
    suspend fun findEnabledByHash(tokenHash: String) = tokenDao.findEnabledByHash(tokenHash)
    suspend fun markUsed(id: String, lastUsedAt: Long) = tokenDao.markUsed(id, lastUsedAt)
}
