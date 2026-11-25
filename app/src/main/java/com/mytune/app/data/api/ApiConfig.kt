package com.mytune.app.data.api

/** Single place to change the backend. The base URL must end with "/". */
object ApiConfig {
    // saavn.dev moved to saavn.sumit.co (same routes). Others are tried automatically if a host fails.
    const val BASE_URL = "https://saavn.sumit.co/"
    val FALLBACK_HOSTS = listOf("saavn.sumit.co", "saavn.dev")

    // The unofficial API has no "home" endpoint, so home rows are built from searches.
    const val HOME_TRENDING_QUERY = "latest hits"
    const val HOME_NEW_QUERY = "new releases"
    const val HOME_PLAYLIST_QUERY = "top 50"
}
