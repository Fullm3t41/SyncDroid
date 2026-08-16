package com.syncdroid.app.wifi

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

class WifiSyncPolicyStore(context: Context) {
    private val preferences = context.getSharedPreferences("wifi_sync_policy", Context.MODE_PRIVATE)

    fun load(): WifiSyncPolicy = runCatching {
        val encoded = preferences.getString(KEY_POLICY, null) ?: return WifiSyncPolicy()
        val json = JSONObject(encoded)
        val networksJson = json.optJSONArray("networks") ?: JSONArray()
        WifiSyncPolicy(
            requireApprovedWifi = true,
            networks = List(networksJson.length()) { index ->
                val network = networksJson.getJSONObject(index)
                WifiNetworkRule(
                    ssid = network.getString("ssid"),
                    enabled = network.optBoolean("enabled", true),
                )
            },
        )
    }.getOrDefault(WifiSyncPolicy())

    fun save(policy: WifiSyncPolicy) {
        val networks = JSONArray().also { array ->
            policy.networks.forEach { rule ->
                array.put(JSONObject().put("ssid", rule.ssid).put("enabled", rule.enabled))
            }
        }
        val json = JSONObject()
            .put("requireApprovedWifi", true)
            .put("networks", networks)
        preferences.edit { putString(KEY_POLICY, json.toString()) }
    }

    private companion object {
        const val KEY_POLICY = "policy"
    }
}
