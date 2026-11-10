package com.playvid.presentation.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.playvid.domain.model.PlaybackRequest
import com.playvid.domain.usecase.UrlValidation
import com.playvid.domain.usecase.ValidateStreamUrlUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class ServerLink(val title: String, val url: String)

class ServerViewModel : ViewModel() {
    private val _links = MutableStateFlow<List<ServerLink>>(emptyList())
    val links = _links.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    // Active item processing state (Direct stream ya Scraping ke dauran loader maintain karne ke liye)
    private val _processingUrl = MutableStateFlow<String?>(null)
    val processingUrl = _processingUrl.asStateFlow()

    // Backend base URL
    private val baseUrl = "https://playvid-server.onrender.com"

    init {
        fetchLinks()
    }

    fun fetchLinks() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val apiUrl = "$baseUrl/api/links"
                val connection = URL(apiUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 8000
                connection.readTimeout = 8000

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = JSONArray(response)
                    val parsedLinks = mutableListOf<ServerLink>()

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        parsedLinks.add(
                            ServerLink(
                                title = obj.getString("title").trim(),
                                url = obj.getString("url").trim()
                            )
                        )
                    }
                    _links.value = parsedLinks
                } else {
                    _links.value = emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _links.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun playLink(
        link: ServerLink,
        validateUrl: ValidateStreamUrlUseCase,
        onValid: (PlaybackRequest) -> Unit,
        onError: (String) -> Unit
    ) {
        if (_processingUrl.value != null) return // Double-click prevent karne ke liye
        
        val cleanUrl = link.url.trim()

        viewModelScope.launch {
            _processingUrl.value = cleanUrl

            // Check: Kya yeh direct video file hai ya web page jise scrape karna padega?
            val isDirectMedia = cleanUrl.contains(".m3u8") || 
                                cleanUrl.contains(".mp4") || 
                                cleanUrl.contains(".mkv")

            val finalStreamUrl = if (isDirectMedia) {
                cleanUrl
            } else {
                // Agar webpage link hai toh backend scraper endpoint call karein
                fetchScrapedStreamUrl(cleanUrl)
            }

            if (finalStreamUrl.isNullOrBlank()) {
                onError("Live stream link fetch nahi ho saka. Match check karein.")
                _processingUrl.value = null
                return@launch
            }

            // URL Validation check
            when (val result = validateUrl(finalStreamUrl)) {
                is UrlValidation.Invalid -> {
                    onError(result.message)
                    _processingUrl.value = null
                }
                is UrlValidation.Valid -> {
                    onValid(
                        PlaybackRequest(
                            source = result.url,
                            title = link.title.ifBlank { result.title },
                            isLocal = false
                        )
                    )
                    delay(600)
                    _processingUrl.value = null
                }
            }
        }
    }

    // Backend ke /api/scrape se live link nikalne ka network call
    private suspend fun fetchScrapedStreamUrl(targetWebUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val encodedTarget = URLEncoder.encode(targetWebUrl, "UTF-8")
            val scrapeEndpoint = "$baseUrl/api/scrape?url=$encodedTarget"
            
            val connection = URL(scrapeEndpoint).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 35000 // Headless browser ke loading time ke liye high timeout
            connection.readTimeout = 35000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                
                if (json.optString("status") == "success") {
                    return@withContext json.optString("stream_url", null)
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
