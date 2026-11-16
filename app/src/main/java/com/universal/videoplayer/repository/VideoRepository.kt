package com.universal.videoplayer.repository

import com.universal.videoplayer.data.VideoDao
import com.universal.videoplayer.data.VideoEntity

class VideoRepository(private val dao: VideoDao) {
    val history = dao.observeHistory()
    suspend fun save(video: VideoEntity) = dao.save(video)
    suspend fun delete(url: String) = dao.delete(url)
    suspend fun clear() = dao.clear()
    val playlists = dao.observePlaylists()
    suspend fun createPlaylist(name: String) = dao.createPlaylist(
        com.universal.videoplayer.data.PlaylistEntity(name = name)
    )
}
