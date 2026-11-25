package com.mytune.app.data.api

/** Single place to change the backend. The base URL must end with "/". */
object ApiConfig {
    // saavn.dev moved to saavn.sumit.co (same routes). Others are tried automatically if a host fails.
    const val BASE_URL = "https://saavn.sumit.co/"
    val FALLBACK_HOSTS = listOf("saavn.sumit.co", "saavn.dev")

    // Home screen rows: title to search query (see JioSaavnApi.home()).
    val HOME_SONG_ROWS = listOf(
        "Trending Now" to "trending songs",
        "Fresh Hits" to "latest hindi songs",
        "Romantic Hits" to "romantic hindi songs",
        "Punjabi Hits" to "punjabi hits",
        "Bollywood Party" to "bollywood party songs",
        "Retro Classics" to "90s bollywood hits",
        "English Hits" to "english top hits",
    )
    val HOME_PLAYLIST_ROWS = listOf(
        "Top Charts" to "top charts",
        "Top Playlists" to "top playlists",
        "Editor's Picks" to "best of",
    )
    val HOME_ALBUM_ROWS = listOf(
        "New Releases" to "new releases",
        "Trending Albums" to "latest albums",
    )
    val HOME_MOODS = listOf("chill", "romance", "party", "workout", "sad songs", "focus")
    val HOME_ARTISTS = listOf("Arijit Singh", "Shreya Ghoshal", "Atif Aslam", "Badshah", "Diljit Dosanjh", "Neha Kakkar", "Anirudh Ravichander", "Sonu Nigam")
    // No dedicated podcast endpoint in the API: podcasts/shows are searched as playlists.
    val HOME_PODCAST_ROWS = listOf(
        "Podcasts" to "podcast",
        "Stories & Shows" to "hindi podcast stories",
        "Motivation & Talks" to "motivational podcast",
    )
}
