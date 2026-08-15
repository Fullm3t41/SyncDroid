package com.syncdroid.app.cloud

import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

class GoogleDriveCloudAdapter(
    private val tokenProvider: AccessTokenProvider,
    private val http: CloudHttpClient = UrlConnectionCloudHttpClient(),
) : CloudObjectStore {
    override suspend fun ensureFolder(parentId: String, name: String): String {
        findItem(parentId, name, foldersOnly = true)?.let { return it.id }
        val metadata = JSONObject().apply {
            put("name", name)
            put("mimeType", FOLDER_MIME)
            put("parents", JSONArray(listOf(parentId)))
        }
        return jsonRequest(
            "POST",
            "$API/files?fields=id,name,size",
            metadata.toString().toByteArray(),
        ).getString("id")
    }

    override suspend fun findObject(parentId: String, name: String): CloudObject? =
        findItem(parentId, name, foldersOnly = false)

    override suspend fun putObject(
        parentId: String,
        name: String,
        contentType: String,
        bytes: ByteArray,
    ): CloudObject {
        val existing = findObject(parentId, name)
        val response = if (existing != null) {
            authorized(
                CloudHttpRequest(
                    method = "POST",
                    url = "$UPLOAD/files/${path(existing.id)}?uploadType=media&fields=id,name,size",
                    headers = mapOf("Content-Type" to contentType, "X-HTTP-Method-Override" to "PATCH"),
                    body = bytes,
                ),
            )
        } else {
            val boundary = "syncdroid-${UUID.randomUUID()}"
            val metadata = JSONObject().apply { put("name", name); put("parents", JSONArray(listOf(parentId))) }
            val body = multipart(boundary, metadata.toString().toByteArray(), contentType, bytes)
            authorized(
                CloudHttpRequest(
                    "POST",
                    "$UPLOAD/files?uploadType=multipart&fields=id,name,size",
                    mapOf("Content-Type" to "multipart/related; boundary=$boundary"),
                    body,
                ),
            )
        }.requireSuccess()
        return JSONObject(String(response.body)).toCloudObject()
    }

    override suspend fun getObject(objectId: String): ByteArray = authorized(
        CloudHttpRequest("GET", "$API/files/${path(objectId)}?alt=media"),
    ).requireSuccess().body

    private suspend fun findItem(parentId: String, name: String, foldersOnly: Boolean): CloudObject? {
        val clauses = mutableListOf("'${escapeQuery(parentId)}' in parents", "name = '${escapeQuery(name)}'", "trashed = false")
        if (foldersOnly) clauses += "mimeType = '$FOLDER_MIME'"
        val q = query(clauses.joinToString(" and "))
        val response = authorized(CloudHttpRequest("GET", "$API/files?q=$q&spaces=drive&fields=files(id,name,size,mimeType)&pageSize=10"))
            .requireSuccess()
        val files = JSONObject(String(response.body)).getJSONArray("files")
        if (files.length() == 0) return null
        return files.getJSONObject(0).toCloudObject()
    }

    private suspend fun jsonRequest(method: String, url: String, body: ByteArray): JSONObject = JSONObject(
        String(authorized(CloudHttpRequest(method, url, mapOf("Content-Type" to "application/json"), body)).requireSuccess().body),
    )

    private suspend fun authorized(request: CloudHttpRequest): CloudHttpResponse = http.execute(
        request.copy(headers = request.headers + ("Authorization" to "Bearer ${tokenProvider.accessToken()}")),
    )

    private fun multipart(boundary: String, metadata: ByteArray, contentType: String, data: ByteArray): ByteArray =
        ByteArrayOutputStream().use { output ->
            output.write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray())
            output.write(metadata)
            output.write("\r\n--$boundary\r\nContent-Type: $contentType\r\n\r\n".toByteArray())
            output.write(data)
            output.write("\r\n--$boundary--\r\n".toByteArray())
            output.toByteArray()
        }

    private fun JSONObject.toCloudObject() = CloudObject(getString("id"), getString("name"), optLong("size", 0))
    private fun escapeQuery(value: String) = value.replace("\\", "\\\\").replace("'", "\\'")
    private fun query(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
    private fun path(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private companion object {
        const val API = "https://www.googleapis.com/drive/v3"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
    }
}
