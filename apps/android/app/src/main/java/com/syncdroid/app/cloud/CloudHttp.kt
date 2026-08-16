package com.syncdroid.app.cloud

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun interface AccessTokenProvider {
    suspend fun accessToken(): String
}

data class CloudHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
)

data class CloudHttpResponse(
    val status: Int,
    val headers: Map<String, List<String>>,
    val body: ByteArray,
) {
    fun requireSuccess(): CloudHttpResponse {
        if (status !in 200..299) throw CloudHttpException(status, String(body).take(500))
        return this
    }
}

class CloudHttpException(val statusCode: Int, detail: String) :
    IllegalStateException("Cloud request failed ($statusCode): $detail")

fun interface CloudHttpClient {
    suspend fun execute(request: CloudHttpRequest): CloudHttpResponse
}

class UrlConnectionCloudHttpClient : CloudHttpClient {
    override suspend fun execute(request: CloudHttpRequest): CloudHttpResponse = withContext(Dispatchers.IO) {
        val connection = URL(request.url).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 20_000
            connection.readTimeout = 60_000
            connection.requestMethod = request.method
            request.headers.forEach(connection::setRequestProperty)
            request.body?.let { body ->
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..399) connection.inputStream else connection.errorStream
            CloudHttpResponse(status, connection.headerFields.filterKeys { it != null }, stream?.use { it.readBytes() } ?: byteArrayOf())
        } finally {
            connection.disconnect()
        }
    }
}
