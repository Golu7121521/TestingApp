package com.playvid.data.local

import com.playvid.domain.model.HistoryItem
import com.playvid.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HistoryRepositoryImpl(private val dao: HistoryDao) : HistoryRepository {

    override fun observeAll(): Flow<List<HistoryItem>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun get(source: String): HistoryItem? = dao.getBySource(source)?.toDomain()

    override suspend fun saveProgress(
        source: String, title: String, isLocal: Boolean, positionMs: Long, durationMs: Long
    ) {
        dao.insert(
            HistoryEntity(
                title = title,
                source = source,
                isLocal = isLocal,
                positionMs = positionMs,
                durationMs = durationMs,
                lastPlayedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun clear() = dao.clear()

    private fun HistoryEntity.toDomain() = HistoryItem(
        id = id, title = title, source = source, isLocal = isLocal,
        positionMs = positionMs, durationMs = durationMs, lastPlayedAt = lastPlayedAt
    )
}
