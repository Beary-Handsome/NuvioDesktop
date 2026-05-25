package com.nuvio.app.features.trailer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

internal object TrailerExtractionPlatform {
    val defaultHeaders: Map<String, String> = mapOf(
        "accept-language" to "en-US,en;q=0.9",
        "user-agent" to
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36",
    )

    suspend fun performRequest(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String?,
        timeoutMillis: Long,
    ): TrailerRequestResponse = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = method.uppercase()
            connection.connectTimeout = timeoutMillis.toInt()
            connection.readTimeout = timeoutMillis.toInt()
            connection.instanceFollowRedirects = true
            val mergedHeaders = defaultHeaders + headers
            mergedHeaders.forEach { (name, value) ->
                if (!name.equals("Accept-Encoding", ignoreCase = true)) {
                    connection.setRequestProperty(name, value)
                }
            }
            if (body != null && (method.uppercase() == "POST" || method.uppercase() == "PUT")) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            }
            val responseCode = connection.responseCode
            val responseBody = runCatching {
                (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                    ?.readAllBytes()?.decodeToString().orEmpty()
            }.getOrDefault("")
            TrailerRequestResponse(
                ok = responseCode in 200..299,
                status = responseCode,
                statusText = connection.responseMessage ?: "",
                url = connection.url.toString(),
                body = responseBody,
            )
        } catch (e: Exception) {
            TrailerRequestResponse(
                ok = false,
                status = 0,
                statusText = e.message ?: "Request failed",
                url = url,
                body = "",
            )
        }
    }

    suspend fun buildPlaybackSource(
        bestManifest: ManifestCandidate?,
        bestProgressive: StreamCandidate?,
        bestVideo: StreamCandidate?,
        bestAudio: StreamCandidate?,
    ): TrailerPlaybackSource? = withContext(Dispatchers.Default) {
        val bestCombinedIsManifest = bestManifest != null &&
            (bestProgressive == null || bestManifest.height > bestProgressive.height)

        val combinedUrl = if (bestCombinedIsManifest) {
            bestManifest.selectedVariantUrl
        } else {
            bestProgressive?.url
        }

        val videoUrl = bestVideo?.url ?: combinedUrl ?: return@withContext null
        val audioUrl = if (bestVideo?.url != null) bestAudio?.url else null

        TrailerPlaybackSource(
            videoUrl = videoUrl,
            audioUrl = audioUrl,
        )
    }
}
