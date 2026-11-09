package com.litetube.app.net

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.net.HttpURLConnection
import java.net.URL

/**
 * Simple HTTP downloader used by NewPipeExtractor.
 * Uses HttpURLConnection so no extra networking library is needed.
 */
class NpDownloader private constructor() : Downloader() {

    override fun execute(request: Request): Response {
        val conn = URL(request.url()).openConnection() as HttpURLConnection
        conn.requestMethod = request.httpMethod()
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("User-Agent", USER_AGENT)

        for ((key, values) in request.headers()) {
            for (value in values) conn.addRequestProperty(key, value)
        }

        request.dataToSend()?.let { data ->
            conn.doOutput = true
            conn.outputStream.use { it.write(data) }
        }

        val code = conn.responseCode
        val stream = if (code >= 400) conn.errorStream else conn.inputStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

        val headers = HashMap<String, List<String>>()
        for ((key, values) in conn.headerFields) {
            if (key != null) headers[key] = values
        }

        return Response(code, conn.responseMessage.orEmpty(), headers, body, conn.url.toString())
    }

    companion object {
        val instance = NpDownloader()
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
    }
}
