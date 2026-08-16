package com.syncdroid.app.storage

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import com.syncdroid.app.data.LocalFolderBindingEntity
import java.io.File
import kotlin.math.min

private const val TEN_GIB_BYTES = 10L * 1024 * 1024 * 1024

enum class StorageCapacityState { AVAILABLE, LOW, FULL }

data class StorageCapacity(
    val destinationKey: String,
    val displayName: String,
    val totalBytes: Long,
    val availableBytes: Long,
) {
    val warningThresholdBytes: Long
        get() = lowStorageWarningThreshold(totalBytes)

    val state: StorageCapacityState
        get() = classifyStorageCapacity(totalBytes, availableBytes)
}

sealed interface StorageSyncWarning {
    val destinations: List<StorageCapacity>

    data class Low(
        override val destinations: List<StorageCapacity>,
    ) : StorageSyncWarning

    data class Full(
        override val destinations: List<StorageCapacity>,
        val incomingSizeBytes: Long? = null,
    ) : StorageSyncWarning

    val key: String
        get() {
            val type = when (this) {
                is Low -> "low"
                is Full -> "full"
            }
            return "$type:${destinations.joinToString("|") { it.destinationKey }}"
        }
}

fun lowStorageWarningThreshold(totalBytes: Long): Long =
    min(TEN_GIB_BYTES, totalBytes.coerceAtLeast(0L) / 20L)

fun classifyStorageCapacity(totalBytes: Long, availableBytes: Long): StorageCapacityState {
    if (availableBytes <= 0L) return StorageCapacityState.FULL
    return if (availableBytes < lowStorageWarningThreshold(totalBytes)) {
        StorageCapacityState.LOW
    } else {
        StorageCapacityState.AVAILABLE
    }
}

class LowStorageApprovalStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "low_storage_sync_approvals",
        Context.MODE_PRIVATE,
    )

    fun approve(destinationKeys: Set<String>) {
        val updated = approvedKeys() + destinationKeys
        preferences.edit().putStringSet(KEY_APPROVED_DESTINATIONS, updated).apply()
    }

    fun isApproved(destinationKey: String): Boolean = destinationKey in approvedKeys()

    fun retainOnly(destinationKeys: Set<String>) {
        val retained = approvedKeys().intersect(destinationKeys)
        preferences.edit().putStringSet(KEY_APPROVED_DESTINATIONS, retained).apply()
    }

    private fun approvedKeys(): Set<String> =
        preferences.getStringSet(KEY_APPROVED_DESTINATIONS, emptySet()).orEmpty().toSet()

    private companion object {
        const val KEY_APPROVED_DESTINATIONS = "approved_destination_keys"
    }
}

class StorageCapacityGuard(context: Context) {
    private val appContext = context.applicationContext
    private val storageManager = appContext.getSystemService(StorageManager::class.java)

    fun inspectConfiguredDestinations(
        bindings: List<LocalFolderBindingEntity>,
        folderNames: Map<String, String> = emptyMap(),
    ): List<StorageCapacity> {
        val destinations = buildList {
            capacityForRoot(
                root = appContext.cacheDir,
                keyOverride = "transfer-cache",
                labelOverride = "Phone storage (temporary transfers)",
            )?.let(::add)
            bindings.forEach { binding ->
                val location = binding.localLocation?.takeIf(String::isNotBlank) ?: return@forEach
                val folderName = folderNames[binding.folderId]
                capacityForLocation(location, folderName)?.let(::add)
            }
        }
        return destinations.distinctBy(StorageCapacity::destinationKey)
    }

    fun warningBeforeSync(
        bindings: List<LocalFolderBindingEntity>,
        approvals: LowStorageApprovalStore,
        folderNames: Map<String, String> = emptyMap(),
    ): StorageSyncWarning? {
        val capacities = inspectConfiguredDestinations(bindings, folderNames)
        val full = capacities.filter { it.state == StorageCapacityState.FULL }
        val low = capacities.filter { it.state == StorageCapacityState.LOW }
        approvals.retainOnly(low.mapTo(mutableSetOf(), StorageCapacity::destinationKey))
        if (full.isNotEmpty()) return StorageSyncWarning.Full(full)
        val unapproved = low.filterNot { approvals.isApproved(it.destinationKey) }
        return if (unapproved.isNotEmpty()) StorageSyncWarning.Low(unapproved) else null
    }

    fun warningForIncomingFile(
        binding: LocalFolderBindingEntity,
        incomingSizeBytes: Long,
        approvals: LowStorageApprovalStore,
    ): StorageSyncWarning? {
        val capacities = buildList {
            capacityForRoot(
                root = appContext.cacheDir,
                keyOverride = "transfer-cache",
                labelOverride = "Phone storage (temporary transfers)",
            )?.let(::add)
            binding.localLocation?.let { capacityForLocation(it, null) }?.let(::add)
        }.distinctBy(StorageCapacity::destinationKey)

        val cannotFit = capacities.filter {
            it.state == StorageCapacityState.FULL || it.availableBytes < incomingSizeBytes
        }
        if (cannotFit.isNotEmpty()) {
            return StorageSyncWarning.Full(cannotFit, incomingSizeBytes)
        }
        val unapprovedLow = capacities.filter {
            it.state == StorageCapacityState.LOW && !approvals.isApproved(it.destinationKey)
        }
        if (unapprovedLow.isNotEmpty()) {
            return StorageSyncWarning.Low(unapprovedLow)
        }
        return null
    }

    private fun capacityForLocation(location: String, folderName: String?): StorageCapacity? {
        val root = if (location.startsWith("content://", ignoreCase = true)) {
            rootForTreeUri(Uri.parse(location))
        } else {
            File(location)
        } ?: return null
        val base = capacityForRoot(root) ?: return null
        return if (folderName.isNullOrBlank()) base else base.copy(
            displayName = "${base.displayName} · $folderName",
        )
    }

    private fun capacityForRoot(
        root: File,
        keyOverride: String? = null,
        labelOverride: String? = null,
    ): StorageCapacity? = runCatching {
        val volume = storageManager.getStorageVolume(root)
        val volumeRoot = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            volume?.directory ?: root
        } else {
            root
        }
        val stats = StatFs(volumeRoot.absolutePath)
        val key = keyOverride ?: when {
            volume?.isPrimary == true -> "primary-storage"
            volume != null && !volume.uuid.isNullOrBlank() -> "volume-${volume.uuid}"
            else -> volumeRoot.canonicalPath
        }
        val label = labelOverride ?: when {
            volume?.isPrimary == true -> "Phone storage"
            volume != null -> volume.getDescription(appContext)
            else -> "Device storage"
        }
        StorageCapacity(
            destinationKey = key,
            displayName = label,
            totalBytes = stats.totalBytes,
            availableBytes = stats.availableBytes,
        )
    }.getOrNull()

    private fun rootForTreeUri(uri: Uri): File? = runCatching {
        val documentId = DocumentsContract.getTreeDocumentId(uri)
        if (documentId.startsWith("raw:")) return@runCatching File(documentId.removePrefix("raw:"))
        val volumeId = documentId.substringBefore(':')
        when {
            volumeId.equals("primary", ignoreCase = true) ||
                volumeId.equals("home", ignoreCase = true) -> Environment.getExternalStorageDirectory()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> storageManager.storageVolumes
                .firstOrNull { it.uuid.equals(volumeId, ignoreCase = true) }
                ?.directory
                ?: File("/storage", volumeId).takeIf(File::exists)
            else -> File("/storage", volumeId).takeIf(File::exists)
        }
    }.getOrNull()
}

fun formatStorageBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
}
