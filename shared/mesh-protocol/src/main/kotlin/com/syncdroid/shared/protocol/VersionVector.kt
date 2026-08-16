package com.syncdroid.shared.protocol

enum class CausalRelation { Before, After, Equal, Concurrent }

data class VersionVector(val counters: Map<String, Long> = emptyMap()) {
    fun increment(deviceId: String): VersionVector {
        require(deviceId.matches(DEVICE_ID_PATTERN)) { "Invalid device ID in version vector" }
        val current = counters[deviceId] ?: 0L
        require(current in 0 until Long.MAX_VALUE) { "Version-vector counter cannot be incremented" }
        return copy(counters = counters + (deviceId to current + 1L))
    }

    fun merge(other: VersionVector): VersionVector {
        val keys = counters.keys + other.counters.keys
        return VersionVector(keys.associateWith { key ->
            maxOf(validCounter(key, counters[key]), validCounter(key, other.counters[key]))
        })
    }

    fun relationTo(other: VersionVector): CausalRelation {
        val keys = counters.keys + other.counters.keys
        var less = false
        var greater = false
        for (key in keys) {
            val ours = validCounter(key, counters[key])
            val theirs = validCounter(key, other.counters[key])
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
        .sortedBy(Map.Entry<String, Long>::key)
        .joinToString(prefix = "{", postfix = "}") { (deviceId, counter) ->
            require(deviceId.matches(DEVICE_ID_PATTERN)) { "Invalid device ID in version vector" }
            require(counter >= 0) { "Version-vector counter cannot be negative" }
            "\"$deviceId\":$counter"
        }

    companion object {
        fun fromJson(encoded: String): VersionVector {
            if (encoded.isBlank()) return VersionVector()
            val body = encoded.trim().removePrefix("{").removeSuffix("}").trim()
            if (body.isEmpty()) return VersionVector()
            return VersionVector(body.split(',').associate { entry ->
                val separator = entry.indexOf(':')
                require(separator > 0) { "Invalid version vector" }
                val deviceId = entry.substring(0, separator).trim().removeSurrounding("\"")
                require(deviceId.matches(DEVICE_ID_PATTERN)) { "Invalid device ID in version vector" }
                val counter = entry.substring(separator + 1).trim().toLong()
                require(counter >= 0) { "Version-vector counter cannot be negative" }
                deviceId to counter
            })
        }

        private val DEVICE_ID_PATTERN = Regex("[A-Za-z0-9_-]+")

        private fun validCounter(deviceId: String, value: Long?): Long {
            require(deviceId.matches(DEVICE_ID_PATTERN)) { "Invalid device ID in version vector" }
            return (value ?: 0L).also { require(it >= 0) { "Version-vector counter cannot be negative" } }
        }
    }
}
