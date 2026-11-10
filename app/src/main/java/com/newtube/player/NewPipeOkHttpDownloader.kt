package com.newtube.player

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NPRequest
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit

/**
 * Official-style OkHttp Downloader for NewPipeExtractor.
 * Preserves NewPipe's internal Innertube headers (User-Agent, X-YouTube-Client-Name, Origin, etc.)
 * and passes the SOCS cookie to bypass EU/Global consent walls.
 */
class NewPipeOkHttpDownloader : Downloader() {

    val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
        .build()

    override fun execute(request: NPRequest): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBody = if (dataToSend != null) {
            dataToSend.toRequestBody(null)
        } else if (httpMethod == "POST" || httpMethod == "PUT") {
            ByteArray(0).toRequestBody(null)
        } else {
            null
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .method(httpMethod, requestBody)

        // Default desktop User-Agent ONLY if NewPipeExtractor didn't specify its own Innertube client User-Agent
        requestBuilder.header(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
        )
        // Add YouTube consent cookie so stream extraction is never blocked by consent pages
        requestBuilder.header("Cookie", "SOCS=CAI; PREF=hl=en&gl=IN")

        // Apply all headers set by NewPipeExtractor (this overrides User-Agent when NewPipe uses IOS/ANDROID Innertube clients)
        for ((headerName, headerValueList) in headers) {
            if (headerValueList.isNotEmpty()) {
                requestBuilder.removeHeader(headerName)
                for (headerValue in headerValueList) {
                    requestBuilder.addHeader(headerName, headerValue)
                }
            }
        }

        val response = client.newCall(requestBuilder.build()).execute()
        if (response.code == 429) {
            response.close()
            throw ReCaptchaException("reCaptcha Challenge requested", url)
        }

        val bodyString = response.body?.string() ?: ""
        val latestUrl = response.request.url.toString()
        return Response(
            response.code,
            response.message,
            response.headers.toMultimap(),
            bodyString,
            latestUrl
        )
    }

    companion object {
        val instance: NewPipeOkHttpDownloader by lazy { NewPipeOkHttpDownloader() }
    }
}
