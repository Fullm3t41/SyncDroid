package com.syncdroid.app.storage

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

data class FolderConfiguration(
    val path: String,
    val name: String,
    val rules: SyncFilterRules,
)

class FolderConfigurationStore(context: Context) {
    private val preferences = context.getSharedPreferences("folder_configurations", Context.MODE_PRIVATE)

    fun load(): List<FolderConfiguration> = runCatching {
        val encoded = preferences.getString(KEY_FOLDERS, null) ?: return emptyList()
        val array = JSONArray(encoded)
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            FolderConfiguration(
                path = item.getString("path"),
                name = item.getString("name"),
                rules = SyncFilterRules(
                    includes = item.getJSONArray("includes").toStringList(),
                    excludes = item.getJSONArray("excludes").toStringList(),
                ),
            )
        }
    }.getOrDefault(emptyList())

    fun upsert(configuration: FolderConfiguration) {
        val updated = load().toMutableList()
        val existingIndex = updated.indexOfFirst { it.path == configuration.path }
        if (existingIndex >= 0) updated[existingIndex] = configuration else updated.add(configuration)
        preferences.edit { putString(KEY_FOLDERS, updated.toJson().toString()) }
    }

    private fun List<FolderConfiguration>.toJson(): JSONArray = JSONArray().also { array ->
        forEach { configuration ->
            array.put(
                JSONObject()
                    .put("path", configuration.path)
                    .put("name", configuration.name)
                    .put("includes", JSONArray(configuration.rules.includes))
                    .put("excludes", JSONArray(configuration.rules.excludes)),
            )
        }
    }

    private fun JSONArray.toStringList(): List<String> =
        List(length()) { index -> getString(index) }

    private companion object {
        const val KEY_FOLDERS = "folders"
    }
}
