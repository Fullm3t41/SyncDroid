package com.syncdroid.shared.sync

data class MeshRouteCandidate(
    val deviceId: String,
    val lastSessionAtMillis: Long = 0L,
    val active: Boolean = false,
)

/**
 * Builds a sparse, deterministic initial graph. Each device initiates sessions
 * with at most [maxFanout] devices after it in identity order; sessions are
 * bidirectional, so the highest identity is still reached by lower identities.
 */
fun initialMeshFanoutTargets(
    localDeviceId: String,
    peerDeviceIds: Collection<String>,
    maxFanout: Int = 2,
): List<String> {
    require(maxFanout > 0) { "Mesh fan-out must be positive" }
    return peerDeviceIds.asSequence()
        .filter { it.isNotBlank() && it > localDeviceId }
        .distinct()
        .sorted()
        .take(maxFanout)
        .toList()
}

/**
 * After receiving new data, prefer idle devices that have not been contacted
 * recently. A stable per-device score spreads equal-age choices across seeders.
 */
fun propagationFanoutTargets(
    localDeviceId: String,
    sourceDeviceId: String?,
    peers: Collection<MeshRouteCandidate>,
    maxFanout: Int = 2,
): List<String> {
    require(maxFanout > 0) { "Mesh fan-out must be positive" }
    return peers.asSequence()
        .filter { !it.active && it.deviceId.isNotBlank() }
        .filter { it.deviceId != localDeviceId && it.deviceId != sourceDeviceId }
        .distinctBy(MeshRouteCandidate::deviceId)
        .sortedWith(
            compareBy<MeshRouteCandidate> { it.lastSessionAtMillis }
                .thenBy { stableRouteScore(localDeviceId, it.deviceId) }
                .thenBy(MeshRouteCandidate::deviceId),
        )
        .take(maxFanout)
        .map(MeshRouteCandidate::deviceId)
        .toList()
}

private fun stableRouteScore(localDeviceId: String, peerDeviceId: String): Long {
    var value = -0x340d631b7bdddcdbL
    for (character in "$localDeviceId\u0000$peerDeviceId") {
        value = (value xor character.code.toLong()) * 0x100000001b3L
    }
    return value and Long.MAX_VALUE
}
