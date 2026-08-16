package com.syncdroid.shared.sync

/**
 * An overwrite-only exception is complete only when every device that has
 * participated in the folder has independently reported the path absent or
 * already indexed a tombstone for it.
 *
 * Keeping this decision independent from wall-clock time prevents an offline
 * device from losing its retained copy merely because another peer waited long
 * enough. Removed and declined devices are excluded by the platform when it
 * builds [participantDeviceIds].
 */
fun shouldFinalizeOverwriteOnlyException(
    participantDeviceIds: Set<String>,
    absenceReporterDeviceIds: Set<String>,
    tombstonedDeviceIds: Set<String>,
): Boolean {
    require(participantDeviceIds.none(String::isBlank)) { "Participant device IDs cannot be blank" }
    require(absenceReporterDeviceIds.none(String::isBlank)) { "Absence reporter device IDs cannot be blank" }
    require(tombstonedDeviceIds.none(String::isBlank)) { "Tombstoned device IDs cannot be blank" }
    if (participantDeviceIds.isEmpty()) return false
    val knownAbsent = absenceReporterDeviceIds + tombstonedDeviceIds
    return participantDeviceIds.all(knownAbsent::contains)
}
