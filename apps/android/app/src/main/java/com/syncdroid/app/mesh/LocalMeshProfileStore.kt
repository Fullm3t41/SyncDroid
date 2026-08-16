package com.syncdroid.app.mesh

import android.content.Context
import java.util.UUID

data class LocalMeshProfile(
    val groupId: String,
    val groupName: String,
)

class LocalMeshProfileStore(context: Context) {
    private val preferences = context.getSharedPreferences("local_mesh_profile", Context.MODE_PRIVATE)

    fun getOrCreate(): LocalMeshProfile {
        val existingId = preferences.getString(KEY_GROUP_ID, null)
        val existingName = preferences.getString(KEY_GROUP_NAME, null)
        if (existingId != null && existingName != null) return LocalMeshProfile(existingId, existingName)

        return createNew("My mesh")
    }

    fun createNew(groupName: String): LocalMeshProfile {
        val cleanName = groupName.trim()
        require(cleanName.isNotEmpty()) { "Mesh name cannot be blank" }
        require(cleanName.length <= 64) { "Mesh name is too long" }
        return LocalMeshProfile(UUID.randomUUID().toString(), cleanName).also(::save)
    }

    fun save(profile: LocalMeshProfile) {
        require(profile.groupId.isNotBlank() && profile.groupName.isNotBlank())
        preferences.edit()
            .putString(KEY_GROUP_ID, profile.groupId)
            .putString(KEY_GROUP_NAME, profile.groupName)
            .apply()
    }

    private companion object {
        const val KEY_GROUP_ID = "group_id"
        const val KEY_GROUP_NAME = "group_name"
    }
}
