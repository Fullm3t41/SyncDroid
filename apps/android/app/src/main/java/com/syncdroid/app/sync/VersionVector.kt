package com.syncdroid.app.sync

enum class CausalRelation { Before, After, Equal, Concurrent }

data class VersionVector(val counters: Map<String, Long> = emptyMap()) {
    fun increment(deviceId: String): VersionVector =
        copy(counters = counters + (deviceId to ((counters[deviceId] ?: 0L) + 1L)))

    fun merge(other: VersionVector): VersionVector {
        val keys = counters.keys + other.counters.keys
        return VersionVector(keys.associateWith { key -> maxOf(counters[key] ?: 0L, other.counters[key] ?: 0L) })
    }

    fun relationTo(other: VersionVector): CausalRelation {
        val keys = counters.keys + other.counters.keys
        var less = false
        var greater = false
        for (key in keys) {
            val ours = counters[key] ?: 0L
            val theirs = other.counters[key] ?: 0L
            if (ours < theirs) less = true
            if (ours > theirs) greater = true
        }
        return when {
            less && greater -> CausalRelation.Concurrent
            less -> CausalRelation.Before
            greater -> CausalRelation.After
            else -> CausalRelation.Equal
        }
    }

    fun toJson(): String = counters.entries
        .sortedBy { it.key }
        .joinToString(prefix = "{", postfix = "}") { (deviceId, counter) ->
            require(deviceId.matches(DEVICE_ID_PATTERN)) { "Invalid device ID in version vector" }
            "\"$deviceId\":$counter"
        }

    companion object {
        fun fromJson(encoded: String): VersionVector {
            if (encoded.isBlank()) return VersionVector()
            val body = encoded.trim().removePrefix("{").removeSuffix("}").trim()
            if (body.isEmpty()) return VersionVector()
            val counters = body.split(',').associate { entry ->
                val separator = entry.indexOf(':')
                require(separator > 0) { "Invalid version vector" }
                val key = entry.substring(0, separator).trim().removeSurrounding("\"")
                require(key.matches(DEVICE_ID_PATTERN)) { "Invalid device ID in version vector" }
                key to entry.substring(separator + 1).trim().toLong()
            }
            return VersionVector(counters)
        }

        private val DEVICE_ID_PATTERN = Regex("[A-Za-z0-9_-]+")
    }
}
