package com.playvid.download

enum class DownloadKind { VIDEO, AUDIO }

enum class DownloadStatus { QUEUED, DOWNLOADING, MERGING, PAUSED, COMPLETED, CANCELED, FAILED }

/** One in-progress (or just-finished) download, shown in the Download tab's active list. */
data class DownloadJob(
    val id: Int,
    val title: String,
    val kind: DownloadKind,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Int = 0,
    val error: String? = null
)

/** A previously finished download, found by scanning Movies/PlayVid and Music/PlayVid. */
data class CompletedItem(
    val uri: String,
    val name: String,
    val kind: DownloadKind,
    val sizeBytes: Long,
    val dateAddedSec: Long
)
