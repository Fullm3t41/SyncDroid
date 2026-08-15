package com.syncdroid.app.cloud

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.json.JSONObject

class OneDriveCloudAdapter(
    private val tokenProvider: AccessTokenProvider,
    private val http: CloudHttpClient = UrlConnectionCloudHttpClient(),
) : CloudObjectStore {
    override suspend fun ensureFolder(parentId: String, name: String): String {
        findChild(parentId, name, foldersOnly = true)?.let { return it.id }
        val body = JSONObject().apply {
            put("name", name)
            put("folder", JSONObject())
            put("@microsoft.graph.conflictBehavior", "fail")
        }.toString().toByteArray()
        val response = authorized(
            CloudHttpRequest(
                "POST",
                "$GRAPH/me/drive/items/${path(parentId)}/children",
                mapOf("Content-Type" to "application/json"),
                body,
            ),
        ).requireSuccess()
        return JSONObject(String(response.body)).getString("id")
    }

    override suspend fun findObject(parentId: String, name: String): CloudObject? =
        findChild(parentId, name, foldersOnly = false)

    override suspend fun putObject(parentId: String, name: String, contentType: String, bytes: ByteArray): CloudObject {
        require(bytes.size <= SIMPLE_UPLOAD_LIMIT) { "OneDrive objects over 250 MB require an upload session" }
        val url = "$GRAPH/me/drive/items/${path(parentId)}:/${path(name)}:/content"
        val response = authorized(
            CloudHttpRequest("PUT", url, mapOf("Content-Type" to contentType), bytes),
        ).requireSuccess()
        return JSONObject(String(response.body)).toCloudObject()
    }

    override suspend fun getObject(objectId: String): ByteArray = authorized(
        CloudHttpRequest("GET", "$GRAPH/me/drive/items/${path(objectId)}/content"),
    ).requireSuccess().body

    private suspend fun findChild(parentId: String, name: String, foldersOnly: Boolean): CloudObject? {
        var next: String? = "$GRAPH/me/drive/items/${path(parentId)}/children?%24select=id,name,size,folder&%24top=200"
        while (next != null) {
            val response = authorized(CloudHttpRequest("GET", next)).requireSuccess()
            val json = JSONObject(String(response.body))
            val values = json.getJSONArray("value")
            for (index in 0 until values.length()) {
                val item = values.getJSONObject(index)
                if (item.getString("name").equals(name, ignoreCase = true) && (!foldersOnly || item.has("folder"))) {
                    return item.toCloudObject()
                }
            }
            next = json.optString("@odata.nextLink").takeIf(String::isNotBlank)
        }
        return null
    }

    private suspend fun authorized(request: CloudHttpRequest): CloudHttpResponse = http.execute(
        request.copy(headers = request.headers + ("Authorization" to "Bearer ${tokenProvider.accessToken()}")),
    )

    private fun JSONObject.toCloudObject() = CloudObject(getString("id"), getString("name"), optLong("size", 0))
    private fun path(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private companion object {
        const val GRAPH = "https://graph.microsoft.com/v1.0"
        const val SIMPLE_UPLOAD_LIMIT = 250 * 1024 * 1024
    }
}
