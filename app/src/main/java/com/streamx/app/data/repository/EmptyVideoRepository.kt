package com.streamx.app.data.repository

import com.streamx.app.domain.model.Video
import com.streamx.app.domain.repository.VideoRepository
import javax.inject.Inject

/**
 * Phase 1 stand-in. Returns an empty feed so the UI shows its real empty state
 * instead of invented videos. Replaced by the NewPipeExtractor implementation in Phase 2.
 */
class EmptyVideoRepository @Inject constructor() : VideoRepository {
    override suspend fun getFeed(category: String): List<Video> = emptyList()
}
