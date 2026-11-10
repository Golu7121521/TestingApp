package com.streamx.app.domain.repository

import com.streamx.app.domain.model.Video

/**
 * Source of the home feed. Phase 1 has no extraction layer yet, so the only
 * implementation is [com.streamx.app.data.repository.EmptyVideoRepository],
 * which returns no videos rather than fake data. Phase 2 adds a NewPipeExtractor-backed implementation.
 */
interface VideoRepository {
    /** Returns the first page of the feed for [category]. Throws on network or extraction failure. */
    suspend fun getFeed(category: String): List<Video>
}
