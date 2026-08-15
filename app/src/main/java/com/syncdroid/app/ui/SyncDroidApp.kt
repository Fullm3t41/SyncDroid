package com.syncdroid.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.text.InputFilter
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doAfterTextChanged
import com.syncdroid.app.model.PeerDevice
import com.syncdroid.app.BuildConfig
import com.syncdroid.app.model.SaveStatus
import com.syncdroid.app.model.SaveFolder
import com.syncdroid.app.data.SyncDroidDatabase
import com.syncdroid.app.data.SyncExceptionEntity
import com.syncdroid.app.data.ChatMessageEntity
import com.syncdroid.app.cloud.CloudSyncPolicy
import com.syncdroid.app.cloud.CloudSyncPolicyStore
import com.syncdroid.app.cloud.CloudSyncScope
import com.syncdroid.app.mesh.AndroidDeviceIdentity
import com.syncdroid.app.mesh.LocalFolderBindingState
import com.syncdroid.app.mesh.LocalDeviceNameStore
import com.syncdroid.app.mesh.LocalMeshProfileStore
import com.syncdroid.app.mesh.MembershipEvent
import com.syncdroid.app.mesh.MeshFolderRepository
import com.syncdroid.app.mesh.MeshChatRepository
import com.syncdroid.app.mesh.MeshMembershipRepository
import com.syncdroid.app.mesh.MeshNsdDiscovery
import com.syncdroid.app.mesh.MeshWifiPresence
import com.syncdroid.app.mesh.PairingCodeOffer
import com.syncdroid.app.mesh.PairingCodes
import com.syncdroid.app.mesh.PairingCoordinator
import com.syncdroid.app.mesh.PairingAttemptLimiter
import com.syncdroid.app.mesh.defaultDeviceName
import com.syncdroid.app.mesh.decodePublicKey
import com.syncdroid.app.notifications.SyncNotificationCenter
import com.syncdroid.app.service.SyncServiceController
import com.syncdroid.app.scheduling.alignedDiscoveryWindows
import com.syncdroid.app.scheduling.DiscoveryPolicy
import com.syncdroid.app.scheduling.DiscoveryPolicyStore
import com.syncdroid.app.storage.SyncFilterRules
import com.syncdroid.app.storage.FolderConfiguration
import com.syncdroid.app.storage.FolderConfigurationStore
import com.syncdroid.app.storage.managedStorageRoots
import com.syncdroid.app.ui.components.LocalMesh
import com.syncdroid.app.ui.components.SaveCard
import com.syncdroid.app.ui.theme.SyncDroidTheme
import com.syncdroid.app.ui.theme.ThemePreferenceStore
import com.syncdroid.app.wifi.WifiSyncPolicy
import com.syncdroid.app.wifi.WifiSyncPolicyStore
import com.syncdroid.app.wifi.hasWifiRuntimePermission
import com.syncdroid.app.wifi.requiredWifiRuntimePermissions
import com.syncdroid.app.wifi.rememberWifiConnectionState
import java.time.LocalDateTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.syncdroid.app.sync.VersionVector
import com.syncdroid.app.sync.ConflictResolutionRepository
import com.syncdroid.app.sync.ConflictVersionDetails
import com.syncdroid.app.sync.FolderDeletionPolicy
import com.syncdroid.app.sync.FolderExceptionRepository
import com.syncdroid.app.sync.SyncStatusStore
import com.syncdroid.app.sync.detailedSyncTimestamp
import com.syncdroid.app.sync.relativeLastSyncLabel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import androidx.core.view.WindowCompat

private enum class MainTab(val label: String, val icon: ImageVector) {
    Sync("Sync", Icons.Rounded.Sync),
    Folders("Folders", Icons.Rounded.Folder),
    Devices("Devices", Icons.Rounded.Devices),
    Chat("Chat", Icons.Rounded.ChatBubbleOutline),
    Settings("Settings", Icons.Rounded.Settings),
}

private enum class ConflictReviewAction { KeepLocal, KeepRemote, KeepBoth }

@Composable
fun SyncDroidApp() {
    val context = LocalContext.current
    val view = LocalView.current
    val systemDarkTheme = isSystemInDarkTheme()
    val themePreferenceStore = remember(context) { ThemePreferenceStore(context) }
    var darkTheme by rememberSaveable {
        mutableStateOf(themePreferenceStore.load(systemDarkTheme))
    }
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Sync) }
    var showPowerSettings by rememberSaveable { mutableStateOf(false) }
    var showWifiRules by rememberSaveable { mutableStateOf(false) }
    var showCloudSettings by rememberSaveable { mutableStateOf(false) }
    var showPairing by rememberSaveable { mutableStateOf(false) }
    var folderSettingsId by rememberSaveable { mutableStateOf<String?>(null) }
    var openFolderContentsId by rememberSaveable { mutableStateOf<String?>(null) }
    var pairingOffer by remember { mutableStateOf<PairingCodeOffer?>(null) }
    var showFileManager by rememberSaveable { mutableStateOf(false) }
    var openCreateOnFileManager by rememberSaveable { mutableStateOf(false) }
    var showStorageChoice by rememberSaveable { mutableStateOf(false) }
    var pendingSystemUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingSystemName by rememberSaveable { mutableStateOf("") }
    var folderBeingConfiguredId by rememberSaveable { mutableStateOf<String?>(null) }
    var showRenameDevice by rememberSaveable { mutableStateOf(false) }
    var renameDeviceDraft by rememberSaveable { mutableStateOf("") }
    var leavingMesh by remember { mutableStateOf(false) }
    var dismissedWifiSuggestionSsid by rememberSaveable { mutableStateOf<String?>(null) }
    var observedWifiSuggestionSsid by rememberSaveable { mutableStateOf<String?>(null) }
    var showConflictReview by rememberSaveable { mutableStateOf(false) }
    var conflictReviewFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var conflictResolutionError by remember { mutableStateOf<String?>(null) }
    var resolvingConflict by remember { mutableStateOf(false) }
    val folderStore = remember(context) { FolderConfigurationStore(context) }
    val database = remember(context) { SyncDroidDatabase.get(context) }
    val identity = remember { AndroidDeviceIdentity() }
    var meshIdentityError by remember { mutableStateOf<String?>(null) }
    val deviceNameStore = remember(context) { LocalDeviceNameStore(context) }
    var localDeviceName by rememberSaveable { mutableStateOf(deviceNameStore.load()) }
    val notifications = remember(context) { SyncNotificationCenter(context) }
    val syncStatusStore = remember(context) { SyncStatusStore(context) }
    val syncServiceSnapshot by SyncServiceController.snapshot.collectAsState()
    val appInForeground by SyncServiceController.appInForeground.collectAsState()
    val activeSyncPeerIds = syncServiceSnapshot.activePeerIds
    val lastSuccessfulSyncMillis = remember(syncStatusStore, syncServiceSnapshot.syncRevision) {
        syncStatusStore.lastSuccessfulSyncMillis()
    }
    val meshProfileStore = remember(context) { LocalMeshProfileStore(context) }
    var meshProfileRevision by remember { mutableIntStateOf(0) }
    val meshProfile = remember(meshProfileStore, meshProfileRevision) { meshProfileStore.getOrCreate() }
    var pairingCoordinator by remember { mutableStateOf<PairingCoordinator?>(null) }
    var pairingStatus by remember { mutableStateOf<String?>(null) }
    val pairingAttemptLimiter = remember(context) { PairingAttemptLimiter(context) }
    var pairingAttemptRevision by remember { mutableIntStateOf(0) }
    val pairingAttemptState = remember(pairingAttemptRevision, showPairing) { pairingAttemptLimiter.state() }
    val meshMembership = remember(database) { MeshMembershipRepository(database.meshDao()) }
    val meshFolders = remember(database, identity.deviceId) {
        MeshFolderRepository(database, identity.deviceId)
    }
    val folderExceptions = remember(database, identity.deviceId) {
        FolderExceptionRepository(database, identity)
    }
    val conflictResolutions = remember(context, database, identity.deviceId) {
        ConflictResolutionRepository(context, database, identity.deviceId)
    }
    val meshChat = remember(database, identity.deviceId) { MeshChatRepository(database, identity) }
    val scope = rememberCoroutineScope()
    val localFolderViews by remember(database, identity.deviceId, meshProfile.groupId) {
        database.syncDao().observeLocalFolderViews(identity.deviceId, meshProfile.groupId)
    }.collectAsState(initial = emptyList())
    val meshDevices by remember(database, meshProfile.groupId) {
        database.meshDao().observeDevices(meshProfile.groupId)
    }.collectAsState(initial = emptyList())
    val chatMessages by remember(database, meshProfile.groupId) {
        database.chatDao().observeMessages(meshProfile.groupId)
    }.collectAsState(initial = emptyList())
    val unresolvedConflicts by remember(database) {
        database.syncDao().observeUnresolvedConflicts()
    }.collectAsState(initial = emptyList())
    val conflictsBeingReviewed = unresolvedConflicts.filter {
        conflictReviewFolderId == null || it.folderId == conflictReviewFolderId
    }
    val activeConflict = conflictsBeingReviewed.firstOrNull()
    val activeConflictDetails by produceState<ConflictVersionDetails?>(
        initialValue = null,
        activeConflict?.conflictId,
    ) {
        value = activeConflict?.let { conflictResolutions.details(it) }
    }
    val activeSyncExceptions by remember(database) {
        database.syncDao().observeAllActiveExceptions()
    }.collectAsState(initial = emptyList())
    val wifiPolicyStore = remember(context) { WifiSyncPolicyStore(context) }
    val discoveryPolicyStore = remember(context) { DiscoveryPolicyStore(context) }
    val cloudPolicyStore = remember(context) { CloudSyncPolicyStore(context) }
    var cloudPolicyRevision by remember { mutableIntStateOf(0) }
    val cloudPolicy = remember(cloudPolicyStore, cloudPolicyRevision) { cloudPolicyStore.load() }
    var wifiPolicyRevision by remember { mutableIntStateOf(0) }
    var discoveryPolicyRevision by remember { mutableIntStateOf(0) }
    val discoveryPolicy = remember(
        discoveryPolicyStore,
        discoveryPolicyRevision,
        syncServiceSnapshot.policyRevision,
    ) { discoveryPolicyStore.load() }
    var wifiPermissionRevision by remember { mutableIntStateOf(0) }
    var notificationPermissionRevision by remember { mutableIntStateOf(0) }
    val wifiPolicy = remember(wifiPolicyStore, wifiPolicyRevision) { wifiPolicyStore.load() }
    val wifiPermissionGranted = remember(wifiPermissionRevision) { hasWifiRuntimePermission(context) }
    val wifiConnection = rememberWifiConnectionState(wifiPolicyRevision + wifiPermissionRevision)
    val syncAllowed = wifiPermissionGranted && wifiPolicy.allowsSyncWithForegroundOverride(
        isWifiConnected = wifiConnection.isWifiConnected,
        currentSsid = wifiConnection.ssid,
        appInForeground = appInForeground,
    )
    val legacySaves = remember(folderStore) {
        folderStore.load().map { configuration ->
                SaveFolder(
                    game = configuration.name,
                    path = configuration.path,
                    level = 0,
                    updatedOn = "Not yet synced",
                    copies = 1,
                    status = SaveStatus.Synced,
                    filterSummary = configuration.rules.summary(),
                )
        }
    }
    val meshSaves = localFolderViews.map { folder ->
        val rules = SyncFilterRules(
            includes = JSONArray(folder.includePatternsJson).toStringList(),
            excludes = JSONArray(folder.excludePatternsJson).toStringList(),
        )
        val lastFolderSync = syncStatusStore.lastSuccessfulFolderSyncMillis(folder.folderId)
        val hasConflict = unresolvedConflicts.any { it.folderId == folder.folderId }
        SaveFolder(
            game = folder.displayName,
            path = folder.localLocation.orEmpty(),
            level = 0,
            updatedOn = when {
                folder.bindingState == LocalFolderBindingState.PENDING_CONFIGURATION.name -> "Needs a local folder"
                folder.bindingState == LocalFolderBindingState.DECLINED.name -> "Declined on this device"
                activeSyncPeerIds.isNotEmpty() -> "Sync in progress"
                else -> detailedSyncTimestamp(lastFolderSync)
            },
            copies = 1,
            status = when {
                folder.bindingState == LocalFolderBindingState.PENDING_CONFIGURATION.name -> SaveStatus.Configure
                folder.bindingState == LocalFolderBindingState.DECLINED.name -> SaveStatus.Declined
                hasConflict -> SaveStatus.Conflict
                activeSyncPeerIds.isNotEmpty() -> SaveStatus.Syncing
                else -> SaveStatus.Synced
            },
            filterSummary = rules.summary(),
            meshFolderId = folder.folderId,
            overwriteOnly = folder.deletionPolicy == FolderDeletionPolicy.OVERWRITE_ONLY.name,
            exceptionCount = activeSyncExceptions.count { it.folderId == folder.folderId },
            supportsFolderSettings = true,
        )
    }
    val configuredSaves = legacySaves + meshSaves
    val peerDevices = meshDevices
        .filter { it.deviceId != identity.deviceId && it.trustState == "TRUSTED" }
        .map { device ->
            val online = device.lastSeenAtMillis?.let { System.currentTimeMillis() - it < ONLINE_WINDOW_MILLIS } == true
            PeerDevice(
                deviceId = device.deviceId,
                name = device.displayName,
                detail = if (online) "Online" else device.lastSeenAtMillis?.let { "Previously connected" } ?: "Not currently online",
                online = online,
                initials = device.displayName.initials(),
                lastOnlineAtMillis = device.lastSeenAtMillis,
            )
        }
    val currentWifiSsid = wifiConnection.ssid.takeIf { wifiConnection.isWifiConnected }
    val wifiAlreadyApproved = wifiPolicy.allowsSync(wifiConnection.isWifiConnected, currentWifiSsid)
    val nearbyPresencePeerIds by produceState(
        initialValue = emptySet(),
        appInForeground,
        wifiConnection.isWifiConnected,
        currentWifiSsid,
        wifiAlreadyApproved,
        meshProfile.groupId,
        identity.deviceId,
    ) {
        if (!appInForeground || !wifiConnection.isWifiConnected || currentWifiSsid == null || wifiAlreadyApproved) {
            value = emptySet()
            return@produceState
        }
        val presence = MeshWifiPresence(context, identity.deviceId, meshProfile.groupId)
        val regularMeshBrowser = MeshNsdDiscovery(context, identity.deviceId, advertise = false)
        try {
            presence.start()
            regularMeshBrowser.start()
        } catch (_: Throwable) {
            presence.close()
            regularMeshBrowser.close()
            value = emptySet()
            return@produceState
        }
        try {
            combine(presence.peerIds, regularMeshBrowser.peers) { presenceIds, regularPeers ->
                presenceIds + regularPeers.keys
            }.collect { value = it }
        } finally {
            presence.close()
            regularMeshBrowser.close()
        }
    }
    val trustedDeviceIds = meshDevices
        .asSequence()
        .filter { it.deviceId != identity.deviceId && it.trustState == "TRUSTED" }
        .map { it.deviceId }
        .toSet()
    val sameMeshPeerPresent = nearbyPresencePeerIds.any(trustedDeviceIds::contains)
    val showWifiSuggestion = appInForeground &&
        currentWifiSsid != null &&
        !wifiAlreadyApproved &&
        sameMeshPeerPresent &&
        dismissedWifiSuggestionSsid != currentWifiSsid

    LaunchedEffect(wifiConnection.isWifiConnected, currentWifiSsid) {
        if (currentWifiSsid != observedWifiSuggestionSsid) {
            observedWifiSuggestionSsid = currentWifiSsid
            dismissedWifiSuggestionSsid = null
        }
    }

    suspend fun ensureLocalMembership() {
        val existing = database.meshDao().getDevice(meshProfile.groupId, identity.deviceId)
            ?: if (
                meshMembership.restoreCreatorProjection(
                    groupId = meshProfile.groupId,
                    groupName = meshProfile.groupName,
                    expectedCreatorDeviceId = identity.deviceId,
                )
            ) database.meshDao().getDevice(meshProfile.groupId, identity.deviceId) else null
        if (existing != null) {
            if (existing.displayName != localDeviceName) {
                val parents = database.meshDao().membershipEvents(meshProfile.groupId)
                val version = parents.fold(VersionVector()) { merged, event ->
                    merged.merge(VersionVector.fromJson(event.versionVectorJson))
                }.increment(identity.deviceId)
                meshMembership.apply(
                    meshProfile.groupName,
                    MembershipEvent.createDeviceNameUpdate(
                        groupId = meshProfile.groupId,
                        subjectDisplayName = localDeviceName,
                        signer = identity,
                        parentEventIds = parents.map { it.eventId },
                        version = version,
                    ),
                ).getOrThrow()
            }
            return
        }
        val bootstrap = MembershipEvent.createAddDevice(
            groupId = meshProfile.groupId,
            subjectDisplayName = localDeviceName,
            subjectPublicKey = identity.publicKey,
            signer = identity,
            parentEventIds = emptyList(),
            version = VersionVector().increment(identity.deviceId),
        )
        meshMembership.apply(meshProfile.groupName, bootstrap).getOrThrow()
    }

    suspend fun ensureLocalMembershipSafely(): Boolean = try {
        ensureLocalMembership()
        meshIdentityError = null
        true
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        meshIdentityError = "This mesh was created with an earlier device identity. Start or join a mesh again to restore secure syncing."
        android.util.Log.e("SyncDroidIdentity", "Could not establish local mesh membership", error)
        false
    }

    LaunchedEffect(meshProfile.groupId, identity.deviceId) {
        ensureLocalMembershipSafely()
    }
    LaunchedEffect(showPairing) {
        if (showPairing && pairingOffer == null) pairingOffer = PairingCodes.create()
    }
    LaunchedEffect(
        unresolvedConflicts.size,
        localFolderViews.count { it.bindingState == LocalFolderBindingState.PENDING_CONFIGURATION.name },
        notificationPermissionRevision,
    ) {
        notifications.updateActionItems(
            conflicts = unresolvedConflicts.size,
            foldersToConfigure = localFolderViews.count {
                it.bindingState == LocalFolderBindingState.PENDING_CONFIGURATION.name
            },
        )
    }
    LaunchedEffect(showPairing, pairingOffer?.invitationId, meshProfile.groupId) {
        if (!showPairing) return@LaunchedEffect
        val offer = pairingOffer ?: return@LaunchedEffect
        if (!ensureLocalMembershipSafely()) {
            pairingStatus = meshIdentityError
            return@LaunchedEffect
        }
        val coordinator = PairingCoordinator(context, database, identity, meshProfileStore)
        pairingCoordinator = coordinator
        try {
            coordinator.offer(offer, meshProfile)
            launch { coordinator.status.collect { pairingStatus = it } }
            awaitCancellation()
        } finally {
            coordinator.close()
            if (pairingCoordinator === coordinator) pairingCoordinator = null
        }
    }

    fun addFolder(path: String, name: String, rules: SyncFilterRules) {
        val configureId = folderBeingConfiguredId
        folderBeingConfiguredId = null
        scope.launch {
            if (!ensureLocalMembershipSafely()) return@launch
            if (configureId != null) {
                meshFolders.configureLocalFolder(configureId, path)
            } else {
                meshFolders.announceLocalFolder(
                    groupId = meshProfile.groupId,
                    localLocation = path,
                    displayName = name,
                    rules = rules,
                    signer = identity,
                )
            }
        }
    }

    fun updateLocalDeviceName(name: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty() || cleanName.length > 64 || cleanName == localDeviceName) return
        scope.launch {
            if (!ensureLocalMembershipSafely()) return@launch
            val parents = database.meshDao().membershipEvents(meshProfile.groupId)
            val version = parents.fold(VersionVector()) { merged, event ->
                merged.merge(VersionVector.fromJson(event.versionVectorJson))
            }.increment(identity.deviceId)
            meshMembership.apply(
                meshProfile.groupName,
                MembershipEvent.createDeviceNameUpdate(
                    groupId = meshProfile.groupId,
                    subjectDisplayName = cleanName,
                    signer = identity,
                    parentEventIds = parents.map { it.eventId },
                    version = version,
                ),
            ).getOrThrow()
            deviceNameStore.save(cleanName)
            localDeviceName = cleanName
        }
    }

    suspend fun recordDeviceRemoval(deviceId: String) {
        check(ensureLocalMembershipSafely()) { meshIdentityError ?: "Could not verify this mesh identity" }
        val target = requireNotNull(database.meshDao().getDevice(meshProfile.groupId, deviceId)) {
            "Device is no longer part of this mesh"
        }
        require(target.trustState == "TRUSTED") { "Device is no longer part of this mesh" }
        val parents = database.meshDao().membershipEvents(meshProfile.groupId)
        val version = parents.fold(VersionVector()) { merged, event ->
            merged.merge(VersionVector.fromJson(event.versionVectorJson))
        }.increment(identity.deviceId)
        meshMembership.apply(
            meshProfile.groupName,
            MembershipEvent.createRemoveDevice(
                groupId = meshProfile.groupId,
                subjectDisplayName = target.displayName,
                subjectPublicKey = decodePublicKey(target.publicKeyBase64),
                signer = identity,
                parentEventIds = parents.map { it.eventId },
                version = version,
            ),
        ).getOrThrow()
    }

    fun removeMeshDevice(deviceId: String) {
        scope.launch {
            runCatching { recordDeviceRemoval(deviceId) }
                .onSuccess { SyncServiceController.requestRefresh(context) }
                .onFailure { error ->
                    meshIdentityError = error.message ?: "Could not remove this device from the mesh."
                }
        }
    }

    fun leaveCurrentMesh() {
        if (leavingMesh) return
        leavingMesh = true
        scope.launch {
            try {
                val previousSyncRevision = SyncServiceController.snapshot.value.syncRevision
                val hasOnlinePeer = peerDevices.any(PeerDevice::online)
                recordDeviceRemoval(identity.deviceId)
                SyncServiceController.requestRefresh(context)
                if (hasOnlinePeer) {
                    withTimeoutOrNull(LEAVE_MESH_ANNOUNCEMENT_TIMEOUT_MILLIS) {
                        SyncServiceController.snapshot.first { it.syncRevision > previousSyncRevision }
                    }
                }
                meshProfileStore.createNew("My mesh")
                meshProfileRevision++
                SyncServiceController.requestRefresh(context)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                meshIdentityError = error.message ?: "Could not leave the mesh."
            } finally {
                leavingMesh = false
            }
        }
    }

    val systemFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            pendingSystemUri = uri.toString()
            pendingSystemName = folderDisplayName(context, uri)
        }
    }
    val allFilesSettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            showFileManager = true
        } else {
            systemFolderLauncher.launch(null)
        }
    }
    val appPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        wifiPermissionRevision++
        notificationPermissionRevision++
        SyncServiceController.requestRefresh(context)
    }
    LaunchedEffect(Unit) {
        val missing = buildList {
            addAll(requiredWifiRuntimePermissions().filter {
                context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            })
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (missing.isNotEmpty()) appPermissionLauncher.launch(missing.toTypedArray())
    }

    fun beginAddFolder() {
        openCreateOnFileManager = false
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> systemFolderLauncher.launch(null)
            Environment.isExternalStorageManager() -> showFileManager = true
            else -> showStorageChoice = true
        }
    }

    fun beginConfigureFolder(save: SaveFolder, createNew: Boolean) {
        folderBeingConfiguredId = save.meshFolderId
        openCreateOnFileManager = createNew
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> systemFolderLauncher.launch(null)
            Environment.isExternalStorageManager() -> showFileManager = true
            else -> showStorageChoice = true
        }
    }

    fun declineFolder(save: SaveFolder) {
        scope.launch {
            meshFolders.declineLocalFolder(save.meshFolderId)
        }
    }

    fun acceptWifiSuggestion() {
        val ssid = currentWifiSsid ?: return
        wifiPolicyStore.save(wifiPolicy.withNetworkEnabled(ssid))
        dismissedWifiSuggestionSsid = ssid
        wifiPolicyRevision++
        SyncServiceController.requestRefresh(context)
    }

    fun openConflictReview(folderId: String? = null) {
        conflictReviewFolderId = folderId
        conflictResolutionError = null
        showConflictReview = true
    }

    fun resolveActiveConflict(action: ConflictReviewAction) {
        val details = activeConflictDetails ?: return
        if (resolvingConflict) return
        resolvingConflict = true
        conflictResolutionError = null
        scope.launch {
            try {
                when (action) {
                    ConflictReviewAction.KeepLocal -> conflictResolutions.keepLocal(details)
                    ConflictReviewAction.KeepRemote -> conflictResolutions.keepRemote(details)
                    ConflictReviewAction.KeepBoth -> conflictResolutions.keepBoth(details)
                }
                SyncServiceController.requestRefresh(context)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                conflictResolutionError = error.message ?: "The conflict could not be resolved."
            } finally {
                resolvingConflict = false
            }
        }
    }

    LaunchedEffect(showConflictReview, activeConflict?.conflictId) {
        if (showConflictReview && activeConflict == null) {
            showConflictReview = false
            conflictReviewFolderId = null
        }
    }

    SyncDroidTheme(darkTheme = darkTheme) {
        SideEffect {
            val activity = context as? Activity ?: return@SideEffect
            activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
            activity.window.navigationBarColor = android.graphics.Color.TRANSPARENT
            activity.window.isStatusBarContrastEnforced = false
            activity.window.isNavigationBarContrastEnforced = false
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                if (!showPowerSettings && !showWifiRules && !showCloudSettings && !showPairing && folderSettingsId == null && openFolderContentsId == null && !showFileManager && pendingSystemUri == null) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        MainTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
                },
            ) { scaffoldPadding ->
            if (showPowerSettings) {
                PowerSettingsScreen(
                    modifier = Modifier.padding(scaffoldPadding),
                    onBack = { showPowerSettings = false },
                    wifiPolicy = wifiPolicy,
                    discoveryPolicy = discoveryPolicy,
                    onDiscoveryPolicyChanged = { updated ->
                        discoveryPolicyStore.save(updated)
                        discoveryPolicyRevision++
                        SyncServiceController.requestRefresh(context)
                    },
                    onOpenWifiRules = {
                        showPowerSettings = false
                        showWifiRules = true
                    },
                )
            } else if (showWifiRules) {
                WifiRulesScreen(
                    onBack = {
                        showWifiRules = false
                        showPowerSettings = true
                    },
                    onRulesChanged = {
                        wifiPolicyRevision++
                        SyncServiceController.requestRefresh(context)
                    },
                    modifier = Modifier.padding(scaffoldPadding),
                )
            } else if (showCloudSettings) {
                CloudSyncSettingsScreen(
                    policy = cloudPolicy,
                    onScopeChanged = { scope ->
                        cloudPolicyStore.setScope(scope)
                        cloudPolicyRevision++
                    },
                    onBack = { showCloudSettings = false },
                    modifier = Modifier.padding(scaffoldPadding),
                )
            } else if (showPairing) {
                PairingScreen(
                    offer = pairingOffer,
                    status = pairingStatus,
                    currentMeshName = meshProfile.groupName,
                    canStartNewMesh = meshDevices.none {
                        it.deviceId != identity.deviceId && it.trustState == "TRUSTED"
                    } &&
                        localFolderViews.isEmpty(),
                    joinAttemptsRemaining = pairingAttemptState.attemptsRemaining(System.currentTimeMillis()),
                    joinLockedUntilMillis = pairingAttemptState.lockedUntilMillis,
                    onJoin = { code ->
                        val attemptState = pairingAttemptLimiter.state()
                        pairingAttemptRevision++
                        val coordinator = pairingCoordinator
                        if (attemptState.lockedUntilMillis > System.currentTimeMillis()) {
                            pairingStatus = "Too many incorrect codes. Pairing is temporarily locked."
                        } else if (coordinator == null) {
                            pairingStatus = "Pairing is still starting. Try again in a moment."
                        } else {
                            scope.launch {
                                val joined = try {
                                    coordinator.join(code)
                                } catch (cancellation: CancellationException) {
                                    throw cancellation
                                } catch (_: Throwable) {
                                    val updated = pairingAttemptLimiter.recordFailure()
                                    pairingAttemptRevision++
                                    pairingStatus = if (updated.lockedUntilMillis > System.currentTimeMillis()) {
                                        "Five unsuccessful codes. Pairing is locked for 15 minutes."
                                    } else {
                                        "${updated.attemptsRemaining(System.currentTimeMillis())} attempts remaining."
                                    }
                                    return@launch
                                }
                                pairingAttemptLimiter.recordSuccess()
                                pairingAttemptRevision++
                                pairingStatus = "Joined ${joined.groupName}. This device is now trusted."
                                meshProfileRevision++
                                SyncServiceController.requestRefresh(context)
                            }
                        }
                    },
                    onStartNewMesh = { name ->
                        meshProfileStore.createNew(name)
                        meshProfileRevision++
                        SyncServiceController.requestRefresh(context)
                        pairingStatus = "New mesh ready · share the code below"
                    },
                    onRegenerate = { pairingOffer = PairingCodes.create() },
                    onBack = { showPairing = false },
                    modifier = Modifier.padding(scaffoldPadding),
                )
            } else if (openFolderContentsId != null) {
                val folder = configuredSaves.firstOrNull { it.meshFolderId == openFolderContentsId }
                if (folder == null) {
                    LaunchedEffect(openFolderContentsId) { openFolderContentsId = null }
                } else {
                    ConfiguredFolderContentsScreen(
                        folder = folder,
                        deviceNames = buildMap {
                            meshDevices.forEach { put(it.deviceId, it.displayName) }
                            put(identity.deviceId, localDeviceName)
                        },
                        loadVersions = { database.syncDao().fileVersions(folder.meshFolderId) },
                        onExclude = { paths ->
                            paths.forEach { folderExceptions.record(folder.meshFolderId, it) }
                        },
                        onFilesChanged = { SyncServiceController.requestRefresh(context) },
                        onBack = { openFolderContentsId = null },
                        modifier = Modifier.padding(scaffoldPadding),
                    )
                }
            } else if (folderSettingsId != null) {
                val folder = configuredSaves.firstOrNull { it.meshFolderId == folderSettingsId }
                if (folder == null) {
                    LaunchedEffect(folderSettingsId) { folderSettingsId = null }
                } else {
                    FolderSettingsScreen(
                        folder = folder,
                        exceptions = activeSyncExceptions.filter { it.folderId == folder.meshFolderId },
                        onOverwriteOnlyChanged = { enabled ->
                            scope.launch {
                                folderExceptions.setDeletionPolicy(
                                    folder.meshFolderId,
                                    if (enabled) FolderDeletionPolicy.OVERWRITE_ONLY else FolderDeletionPolicy.PROPAGATE,
                                )
                            }
                        },
                        onUndoException = { relativePath ->
                            scope.launch { folderExceptions.undo(folder.meshFolderId, relativePath) }
                        },
                        onBack = { folderSettingsId = null },
                        modifier = Modifier.padding(scaffoldPadding),
                    )
                }
            } else if (showFileManager) {
                FileManagerScreen(
                    storageRoots = remember(context) { managedStorageRoots(context) },
                    onBack = { showFileManager = false },
                    openCreateFolderInitially = openCreateOnFileManager,
                    onFolderSelected = { path, name, rules ->
                        addFolder(path, name, rules)
                        showFileManager = false
                    },
                    modifier = Modifier.padding(scaffoldPadding),
                )
            } else if (pendingSystemUri != null) {
                FilterEditorScreen(
                    folderName = pendingSystemName,
                    initialRules = SyncFilterRules(),
                    onBack = { pendingSystemUri = null },
                    onSave = { rules ->
                        addFolder(pendingSystemUri.orEmpty(), pendingSystemName, rules)
                        pendingSystemUri = null
                    },
                    modifier = Modifier.padding(scaffoldPadding),
                )
            } else {
                when (selectedTab) {
                    MainTab.Sync -> SyncScreen(
                        statusText = if (activeSyncPeerIds.isNotEmpty()) {
                            "Sync in progress"
                        } else {
                            relativeLastSyncLabel(lastSuccessfulSyncMillis)
                        },
                        currentDeviceName = localDeviceName,
                        syncAllowed = syncAllowed,
                        wifiGateEnabled = wifiPolicy.requireApprovedWifi,
                        currentSsid = wifiConnection.ssid,
                        peers = peerDevices,
                        folders = configuredSaves,
                        reviewCount = unresolvedConflicts.size,
                        onReviewConflicts = { openConflictReview() },
                        onRenameCurrentDevice = {
                            renameDeviceDraft = localDeviceName
                            showRenameDevice = true
                        },
                        onSyncNow = { SyncServiceController.requestRefresh(context) },
                        modifier = Modifier.padding(scaffoldPadding),
                    )
                    MainTab.Folders -> FoldersScreen(
                        folders = configuredSaves,
                        onAddFolder = ::beginAddFolder,
                        onCreateFolderFor = { beginConfigureFolder(it, createNew = true) },
                        onChooseFolderFor = { beginConfigureFolder(it, createNew = false) },
                        onDeclineFolder = ::declineFolder,
                        cloudPolicy = cloudPolicy,
                        onCloudFolderChanged = { folderId, enabled ->
                            cloudPolicyStore.setFolderEnabled(folderId, enabled)
                            cloudPolicyRevision++
                        },
                        onOpenFolderSettings = { folderSettingsId = it.meshFolderId },
                        onOpenFolder = { openFolderContentsId = it.meshFolderId },
                        onReviewConflicts = { openConflictReview(it.meshFolderId) },
                        modifier = Modifier.padding(scaffoldPadding),
                    )
                    MainTab.Devices -> DevicesScreen(
                        localDeviceName = localDeviceName,
                        devices = peerDevices,
                        onPairDevice = {
                            pairingOffer = PairingCodes.create()
                            showPairing = true
                        },
                        onRemoveDevice = ::removeMeshDevice,
                        onLeaveMesh = ::leaveCurrentMesh,
                        leavingMesh = leavingMesh,
                        modifier = Modifier.padding(scaffoldPadding),
                    )
                    MainTab.Chat -> ChatScreen(
                        messages = chatMessages,
                        currentDeviceId = identity.deviceId,
                        deviceNames = meshDevices.associate { it.deviceId to it.displayName },
                        onSend = { body ->
                            scope.launch { meshChat.send(meshProfile.groupId, body) }
                        },
                        modifier = Modifier.padding(scaffoldPadding),
                    )
                    MainTab.Settings -> SettingsScreen(
                        darkTheme = darkTheme,
                        onDarkThemeChange = { selectedDarkTheme ->
                            darkTheme = selectedDarkTheme
                            themePreferenceStore.save(selectedDarkTheme)
                        },
                        onOpenPowerSettings = { showPowerSettings = true },
                        onOpenCloudSettings = { showCloudSettings = true },
                        cloudScope = cloudPolicy.scope,
                        modifier = Modifier.padding(scaffoldPadding),
                    )
                }
            }
            }
            AnimatedVisibility(
                visible = showWifiSuggestion,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            ) {
                WifiNetworkSuggestionBanner(
                    ssid = currentWifiSsid.orEmpty(),
                    onYes = ::acceptWifiSuggestion,
                    onNo = { dismissedWifiSuggestionSsid = currentWifiSsid },
                )
            }
        }

        if (showStorageChoice) {
            StorageAccessDialog(
                onDismiss = { showStorageChoice = false },
                onGrantAccess = {
                    showStorageChoice = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        allFilesSettingsLauncher.launch(manageAllFilesIntent(context))
                    } else {
                        systemFolderLauncher.launch(null)
                    }
                },
                onUseSystemPicker = {
                    showStorageChoice = false
                    systemFolderLauncher.launch(null)
                },
            )
        }
        if (showRenameDevice) {
            AlertDialog(
                onDismissRequest = { showRenameDevice = false },
                title = { Text("Update device name") },
                text = {
                    Column {
                        Text("This nickname identifies the device throughout your trusted mesh.")
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = renameDeviceDraft,
                            onValueChange = { if (it.length <= 64) renameDeviceDraft = it },
                            label = { Text("Device name") },
                            singleLine = true,
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            updateLocalDeviceName(renameDeviceDraft)
                            showRenameDevice = false
                        },
                        enabled = renameDeviceDraft.isNotBlank() &&
                            renameDeviceDraft.trim() != localDeviceName,
                    ) { Text("Update") }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDevice = false }) { Text("Cancel") }
                },
            )
        }
        if (showConflictReview && activeConflict != null) {
            ConflictReviewDialog(
                details = activeConflictDetails,
                remainingCount = conflictsBeingReviewed.size,
                localDeviceName = localDeviceName,
                deviceNames = meshDevices.associate { it.deviceId to it.displayName },
                resolving = resolvingConflict,
                error = conflictResolutionError,
                onKeepLocal = { resolveActiveConflict(ConflictReviewAction.KeepLocal) },
                onKeepRemote = { resolveActiveConflict(ConflictReviewAction.KeepRemote) },
                onKeepBoth = { resolveActiveConflict(ConflictReviewAction.KeepBoth) },
                onDismiss = {
                    if (!resolvingConflict) {
                        showConflictReview = false
                        conflictReviewFolderId = null
                    }
                },
            )
        }
    }
}

@Composable
private fun ConflictReviewDialog(
    details: ConflictVersionDetails?,
    remainingCount: Int,
    localDeviceName: String,
    deviceNames: Map<String, String>,
    resolving: Boolean,
    error: String?,
    onKeepLocal: () -> Unit,
    onKeepRemote: () -> Unit,
    onKeepBoth: () -> Unit,
    onDismiss: () -> Unit,
) {
    val remoteDeviceName = details?.remote?.let { remote ->
        deviceNames[remote.deviceId] ?: remote.deviceId.take(8)
    }.orEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Review file conflict")
                Text(
                    "$remainingCount ${if (remainingCount == 1) "conflict" else "conflicts"} remaining",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            if (details == null) {
                Text("Loading both versions…")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        details.local.relativePath,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    ConflictVersionCard(
                        title = "On $localDeviceName",
                        editorName = deviceNames[details.local.originDeviceId]
                            ?: details.local.originDeviceId.take(8),
                        sizeBytes = details.local.sizeBytes,
                        modifiedAtMillis = details.local.modifiedAtMillis,
                        sha256 = details.local.contentSha256,
                        deleted = details.local.deleted,
                    )
                    ConflictVersionCard(
                        title = "From $remoteDeviceName",
                        editorName = deviceNames[details.remote.originDeviceId]
                            ?: details.remote.originDeviceId.ifBlank { details.remote.deviceId }.take(8),
                        sizeBytes = details.remote.sizeBytes,
                        modifiedAtMillis = details.remote.modifiedAtMillis,
                        sha256 = details.remote.contentSha256,
                        deleted = details.remote.deleted,
                    )
                    Text(
                        "Keep both preserves this device's copy as ${details.suggestedRenamedPath}. The selected remote copy returns to the original filename when that device is available.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (error != null) {
                        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onKeepLocal,
                    enabled = details != null && !resolving,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Keep this device's version") }
                OutlinedButton(
                    onClick = onKeepRemote,
                    enabled = details != null && !resolving,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (remoteDeviceName.isEmpty()) "Keep other version" else "Keep $remoteDeviceName version") }
                OutlinedButton(
                    onClick = onKeepBoth,
                    enabled = details?.local?.deleted == false && !resolving,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (resolving) "Resolving…" else "Keep both") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !resolving) { Text("Review later") }
        },
    )
}

@Composable
private fun ConflictVersionCard(
    title: String,
    editorName: String,
    sizeBytes: Long,
    modifiedAtMillis: Long,
    sha256: String,
    deleted: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                if (deleted) "Deleted version" else "${formatConflictFileSize(sizeBytes)} · edited ${formatChatTime(modifiedAtMillis)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Last edited by ${editorName.ifBlank { "Unknown device" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (sha256.isNotBlank()) {
                Text(
                    "Hash ${sha256.take(12)}…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatConflictFileSize(bytes: Long): String = when {
    bytes < 1_024 -> "$bytes B"
    bytes < 1_024 * 1_024 -> "%.1f KB".format(Locale.getDefault(), bytes / 1_024.0)
    bytes < 1_024L * 1_024 * 1_024 -> "%.1f MB".format(Locale.getDefault(), bytes / (1_024.0 * 1_024))
    else -> "%.1f GB".format(Locale.getDefault(), bytes / (1_024.0 * 1_024 * 1_024))
}

@Composable
private fun WifiNetworkSuggestionBanner(
    ssid: String,
    onYes: () -> Unit,
    onNo: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Add this Wi-Fi network?", style = MaterialTheme.typography.titleMedium)
                Text(
                    ssid,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onNo) { Text("No") }
            TextButton(onClick = onYes) { Text("Yes") }
        }
    }
}

@Composable
private fun ChatScreen(
    messages: List<ChatMessageEntity>,
    currentDeviceId: String,
    deviceNames: Map<String, String>,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var showCopiedConfirmation by remember { mutableStateOf(false) }
    var copyConfirmationToken by remember { mutableIntStateOf(0) }
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    fun sendDraft() {
        val message = draft.trim()
        if (message.isNotEmpty()) {
            onSend(message)
            draft = ""
        }
    }
    LaunchedEffect(messages.lastOrNull()?.messageId) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    LaunchedEffect(copyConfirmationToken) {
        if (showCopiedConfirmation) {
            val expectedToken = copyConfirmationToken
            delay(1_000)
            if (copyConfirmationToken == expectedToken) showCopiedConfirmation = false
        }
    }

    Box(modifier.fillMaxSize().imePadding()) {
      Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text("Mesh chat", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Private group chat for trusted devices on this mesh",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier.padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape,
                    ) {
                        Icon(
                            Icons.Rounded.ChatBubbleOutline,
                            contentDescription = null,
                            modifier = Modifier.padding(16.dp).size(28.dp),
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("Start the conversation", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Messages will reach the other devices during the next local mesh sync.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(messages, key = ChatMessageEntity::messageId) { message ->
                    ChatMessageRow(
                        message = message,
                        authorName = deviceNames[message.authorDeviceId]
                            ?: message.authorDeviceId.take(8),
                        isCurrentDevice = message.authorDeviceId == currentDeviceId,
                        onCopy = {
                            clipboard.setText(AnnotatedString(message.body))
                            showCopiedConfirmation = true
                            copyConfirmationToken++
                        },
                    )
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 5.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.toByteArray(Charsets.UTF_8).size <= 4_000) draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message the mesh") },
                    shape = RoundedCornerShape(24.dp),
                    minLines = 1,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = { sendDraft() },
                        onDone = { sendDraft() },
                        onGo = { sendDraft() },
                    ),
                )
                val canSend = draft.isNotBlank()
                Surface(
                    color = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = CircleShape,
                ) {
                    IconButton(
                        onClick = ::sendDraft,
                        enabled = canSend,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send message")
                    }
                }
            }
        }
      }
      if (showCopiedConfirmation) {
          Surface(
              modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp),
              color = MaterialTheme.colorScheme.inverseSurface,
              contentColor = MaterialTheme.colorScheme.inverseOnSurface,
              shape = RoundedCornerShape(18.dp),
              shadowElevation = 5.dp,
          ) {
              Text(
                  "Copied text.",
                  modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                  style = MaterialTheme.typography.labelLarge,
              )
          }
      }
    }
}

@Composable
private fun ChatMessageRow(
    message: ChatMessageEntity,
    authorName: String,
    isCurrentDevice: Boolean,
    onCopy: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isCurrentDevice) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isCurrentDevice) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        authorName.initials(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
        }
        Column(
            modifier = Modifier.fillMaxWidth(0.84f),
            horizontalAlignment = if (isCurrentDevice) Alignment.End else Alignment.Start,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    if (isCurrentDevice) "You" else authorName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    formatChatTime(message.createdAtMillis),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(5.dp))
            Surface(
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = onCopy,
                    onLongClickLabel = "Copy message text",
                ),
                color = if (isCurrentDevice) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (isCurrentDevice) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                shape = RoundedCornerShape(
                    topStart = if (isCurrentDevice) 20.dp else 6.dp,
                    topEnd = if (isCurrentDevice) 6.dp else 20.dp,
                    bottomStart = 20.dp,
                    bottomEnd = 20.dp,
                ),
            ) {
                Text(
                    message.body,
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

private fun formatChatTime(timestamp: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(timestamp))

@Composable
private fun SyncScreen(
    statusText: String,
    currentDeviceName: String,
    syncAllowed: Boolean,
    wifiGateEnabled: Boolean,
    currentSsid: String?,
    peers: List<PeerDevice>,
    folders: List<SaveFolder>,
    reviewCount: Int,
    onReviewConflicts: () -> Unit,
    onRenameCurrentDevice: () -> Unit,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(statusText, style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onSyncNow, enabled = syncAllowed) {
                    Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Sync now")
                }
            }
        }
        item { LocalMeshHeader(syncAllowed, wifiGateEnabled, currentSsid, peers.count { it.online }) }
        item {
            LocalMesh(
                currentDevice = currentDeviceName,
                peers = peers,
                onCurrentDeviceLongPress = onRenameCurrentDevice,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(peers.count { it.online }.toString(), "online", Modifier.weight(1f))
                MetricCard(folders.size.toString(), "folders", Modifier.weight(1f))
                MetricCard(
                    reviewCount.toString(),
                    "needs review",
                    Modifier.weight(1f),
                    alert = reviewCount > 0,
                    onClick = onReviewConflicts.takeIf { reviewCount > 0 },
                )
            }
        }
        if (folders.isEmpty()) {
            item { EmptyStateCard("No folders yet", "Add a folder to start building this mesh.") }
        } else {
            item {
                SectionLabel("ACTIVE FOLDERS")
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    folders.filter {
                        it.status != SaveStatus.Configure && it.status != SaveStatus.Declined
                    }.forEach { folder ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(15.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(folder.game, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        folder.updatedOn,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalMeshHeader(syncAllowed: Boolean, wifiGateEnabled: Boolean, currentSsid: String?, peerCount: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(9.dp)
                .background(
                    if (syncAllowed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                    CircleShape,
                ),
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(if (syncAllowed) "Local mesh online" else "Local mesh paused", style = MaterialTheme.typography.titleLarge)
            Text(
                when {
                    !wifiGateEnabled -> "Local Wi-Fi only"
                    syncAllowed && currentSsid != null -> "Sync allowed on $currentSsid"
                    else -> "Connect to an approved Wi-Fi network to sync"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
            Text(
                if (peerCount == 1) "1 peer" else "$peerCount peers",
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun MetricCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    alert: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = if (onClick == null) modifier else modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (alert) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                color = if (alert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (alert) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FoldersScreen(
    folders: List<SaveFolder>,
    onAddFolder: () -> Unit,
    onCreateFolderFor: (SaveFolder) -> Unit = {},
    onChooseFolderFor: (SaveFolder) -> Unit = {},
    onDeclineFolder: (SaveFolder) -> Unit = {},
    cloudPolicy: CloudSyncPolicy,
    onCloudFolderChanged: (String, Boolean) -> Unit,
    onOpenFolder: (SaveFolder) -> Unit,
    onOpenFolderSettings: (SaveFolder) -> Unit,
    onReviewConflicts: (SaveFolder) -> Unit,
    modifier: Modifier = Modifier,
) {
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Folders", style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
                Button(onClick = onAddFolder) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add")
                }
            }
            Text(
                "Tap a card to see its copies and latest progress.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
        items(folders, key = { it.meshFolderId }) { save ->
            SaveCard(
                save = save,
                expanded = expanded[save.meshFolderId] == true,
                onToggle = { expanded[save.meshFolderId] = expanded[save.meshFolderId] != true },
                onCreateNewFolder = { onCreateFolderFor(save) },
                onChooseExistingFolder = { onChooseFolderFor(save) },
                onDeclineFolder = { onDeclineFolder(save) },
                cloudEnabled = cloudPolicy.isEnabledFor(save.meshFolderId),
                cloudEditable = cloudPolicy.scope == CloudSyncScope.SELECTED_FOLDERS,
                cloudDetail = when (cloudPolicy.scope) {
                    CloudSyncScope.DISABLED -> "Disabled in global settings"
                    CloudSyncScope.SELECTED_FOLDERS -> if (cloudPolicy.isEnabledFor(save.meshFolderId)) {
                        "Enabled for this folder"
                    } else {
                        "Disabled for this folder"
                    }
                    CloudSyncScope.ALL_FOLDERS -> "Enabled by the all-folders setting"
                },
                onCloudEnabledChange = { onCloudFolderChanged(save.meshFolderId, it) },
                onOpenFolder = { onOpenFolder(save) },
                onOpenFolderSettings = { onOpenFolderSettings(save) },
                onReviewConflicts = { onReviewConflicts(save) },
            )
        }
        if (folders.isEmpty()) {
            item { EmptyStateCard("No folders configured", "Add a local folder, or pair another device to receive mesh folders.") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderSettingsScreen(
    folder: SaveFolder,
    exceptions: List<SyncExceptionEntity>,
    onOverwriteOnlyChanged: (Boolean) -> Unit,
    onUndoException: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(folder.game) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    "Control how this folder handles files that disappear from one device.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                SettingsCard {
                    SettingsSwitchRow(
                        icon = Icons.Rounded.Folder,
                        title = "Overwrite only",
                        detail = if (folder.overwriteOnly) {
                            "Deleted files become exceptions; other copies are kept"
                        } else {
                            "Deletes are synced to other devices and cloud storage"
                        },
                        checked = folder.overwriteOnly,
                        onCheckedChange = onOverwriteOnlyChanged,
                    )
                }
            }
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(
                        "Undoing an exception makes that file eligible again. If another device or cloud storage still has it, SyncDroid can copy it back into this folder.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            item {
                SectionLabel("EXCEPTED ITEMS")
            }
            if (exceptions.isEmpty()) {
                item {
                    EmptyStateCard(
                        "No exceptions",
                        if (folder.overwriteOnly) {
                            "Files deleted from this folder will appear here instead of being deleted elsewhere."
                        } else {
                            "Enable Overwrite only to keep other copies when a local file is deleted."
                        },
                    )
                }
            } else {
                items(exceptions, key = { "${it.folderId}:${it.relativePath}" }) { exception ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(exception.relativePath, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Excluded from future syncs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { onUndoException(exception.relativePath) }) {
                                Text("Undo")
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun DevicesScreen(
    localDeviceName: String,
    devices: List<PeerDevice>,
    onPairDevice: () -> Unit,
    onRemoveDevice: (String) -> Unit,
    onLeaveMesh: () -> Unit,
    leavingMesh: Boolean,
    modifier: Modifier = Modifier,
) {
    var devicePendingRemoval by remember { mutableStateOf<PeerDevice?>(null) }
    var showLeaveConfirmation by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Your mesh", style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
                Button(onClick = onPairDevice) {
                    Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pair")
                }
            }
            Text(
                "Every device is an equal peer. This view centres the device in your hand.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            LocalMesh(
                currentDevice = localDeviceName,
                peers = devices,
            )
        }
        items(devices, key = PeerDevice::deviceId) { device ->
            SwipeableDeviceRow(
                device = device,
                onDelete = { devicePendingRemoval = device },
            )
        }
        if (devices.isEmpty()) item { EmptyStateCard("No paired devices", "Use a six-digit code from a device already in the mesh.") }
        item {
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = { showLeaveConfirmation = true },
                enabled = !leavingMesh,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (leavingMesh) "Leaving mesh…" else "Leave mesh")
            }
            Text(
                "Leaving removes this device from the shared mesh. Your local files are not deleted.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    devicePendingRemoval?.let { device ->
        AlertDialog(
            onDismissRequest = { devicePendingRemoval = null },
            title = { Text("Remove ${device.name}?") },
            text = {
                Text(
                    "This signed removal will sync to the other devices. ${device.name} will no longer be able to join this mesh.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveDevice(device.deviceId)
                        devicePendingRemoval = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { devicePendingRemoval = null }) { Text("Cancel") }
            },
        )
    }

    if (showLeaveConfirmation) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirmation = false },
            title = { Text("Leave this mesh?") },
            text = {
                Text(
                    "SyncDroid will try to announce that this device has left, then move it to a new private mesh. Other devices and your local files will not be deleted.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLeaveConfirmation = false
                        onLeaveMesh()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Leave mesh") }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirmation = false }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableDeviceRow(
    device: PeerDevice,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { distance -> distance * 0.35f },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.error, RoundedCornerShape(18.dp))
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onError),
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Delete")
                }
            }
        },
        content = { DeviceRow(device) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PairingScreen(
    offer: PairingCodeOffer?,
    status: String?,
    currentMeshName: String,
    canStartNewMesh: Boolean,
    joinAttemptsRemaining: Int,
    joinLockedUntilMillis: Long,
    onJoin: (String) -> Unit,
    onStartNewMesh: (String) -> Unit,
    onRegenerate: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var joinCode by rememberSaveable { mutableStateOf("") }
    var showStartMeshDialog by rememberSaveable { mutableStateOf(false) }
    var meshNameDraft by rememberSaveable(currentMeshName) { mutableStateOf(currentMeshName) }
    val pairingListState = rememberLazyListState()
    val pairingScope = rememberCoroutineScope()
    val joinItemIndex = if (canStartNewMesh) 3 else 2
    val focusedJoinScrollOffset = with(LocalDensity.current) { 48.dp.roundToPx() }
    var pairingClockMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val joinLockRemainingMillis = (joinLockedUntilMillis - pairingClockMillis).coerceAtLeast(0L)
    LaunchedEffect(joinLockedUntilMillis) {
        while (joinLockedUntilMillis > System.currentTimeMillis()) {
            pairingClockMillis = System.currentTimeMillis()
            delay(1_000L)
        }
        pairingClockMillis = System.currentTimeMillis()
    }
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Pair a device") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        LazyColumn(
            state = pairingListState,
            modifier = Modifier
                .weight(1f)
                .imePadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    "Both devices must be on an approved local Wi-Fi network. The existing mesh member approves the new device.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canStartNewMesh) {
                item {
                    SectionLabel("START A NEW MESH")
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Create your own mesh", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "This device becomes the first equal member; it does not become a permanent host.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedButton(onClick = { showStartMeshDialog = true }) {
                                Text("Start mesh")
                            }
                        }
                    }
                }
            }
            item {
                SectionLabel("ADD A DEVICE TO THIS MESH")
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Enter this code on the new device", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            offer?.code?.chunked(3)?.joinToString("  ") ?: "— — —  — — —",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Expires after 5 minutes",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        TextButton(onClick = onRegenerate) { Text("Generate a new code") }
                    }
                }
            }
            item {
                SectionLabel("JOIN AN EXISTING MESH")
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        SixDigitCodeField(
                            value = joinCode,
                            onValueChange = { value ->
                                joinCode = value.filter(Char::isDigit).take(6)
                            },
                            onFocusChanged = { focused ->
                                if (focused) {
                                    pairingScope.launch {
                                        delay(150)
                                        pairingListState.animateScrollToItem(
                                            index = joinItemIndex,
                                            scrollOffset = focusedJoinScrollOffset,
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { onJoin(joinCode) },
                            enabled = joinCode.length == 6 && joinLockRemainingMillis == 0L,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Find existing device")
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (joinLockRemainingMillis > 0L) {
                                "Too many incorrect codes · try again in ${formatLockout(joinLockRemainingMillis)}"
                            } else {
                                "$joinAttemptsRemaining attempts remaining."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        status?.let {
                            Spacer(Modifier.height(10.dp))
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
    if (showStartMeshDialog) {
        AlertDialog(
            onDismissRequest = { showStartMeshDialog = false },
            title = { Text("Start a new mesh") },
            text = {
                OutlinedTextField(
                    value = meshNameDraft,
                    onValueChange = { if (it.length <= 64) meshNameDraft = it },
                    label = { Text("Mesh name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onStartNewMesh(meshNameDraft.trim())
                        showStartMeshDialog = false
                    },
                    enabled = meshNameDraft.isNotBlank(),
                ) { Text("Start mesh") }
            },
            dismissButton = {
                TextButton(onClick = { showStartMeshDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SixDigitCodeField(
    value: String,
    onValueChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Box(modifier = modifier.widthIn(max = 520.dp)) {
        AndroidView(
            factory = { context ->
                EditText(context).apply {
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    setTextColor(AndroidColor.TRANSPARENT)
                    setHintTextColor(AndroidColor.TRANSPARENT)
                    isCursorVisible = false
                    isSingleLine = true
                    inputType = InputType.TYPE_CLASS_NUMBER
                    keyListener = DigitsKeyListener.getInstance("0123456789")
                    filters = arrayOf(InputFilter.LengthFilter(6))
                    imeOptions = EditorInfo.IME_ACTION_DONE or
                        EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                        EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                    hint = "Six-digit code"
                    setPadding(0, 0, 0, 0)
                    setOnFocusChangeListener { _, hasFocus ->
                        focused = hasFocus
                        onFocusChanged(hasFocus)
                    }
                    doAfterTextChanged { editable ->
                        val digits = editable?.toString().orEmpty().filter(Char::isDigit).take(6)
                        if (digits != value) onValueChange(digits)
                    }
                }
            },
            update = { input ->
                if (input.text.toString() != value) {
                    input.setText(value)
                    input.setSelection(value.length)
                }
            },
            modifier = Modifier.matchParentSize(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(6) { index ->
                val active = focused && index == value.length.coerceAtMost(5)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp)
                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(12.dp))
                        .border(
                            width = if (active) 2.dp else 1.dp,
                            color = if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(12.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        value.getOrNull(index)?.toString().orEmpty(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

private fun formatLockout(remainingMillis: Long): String {
    val totalSeconds = ((remainingMillis + 999L) / 1_000L).coerceAtLeast(0L)
    return "%d:%02d".format(Locale.US, totalSeconds / 60L, totalSeconds % 60L)
}

@Composable
private fun DeviceRow(device: PeerDevice) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                color = if (device.online) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        device.initials,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (device.online) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleMedium)
                Text(device.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                if (device.online) "Online" else "Offline",
                style = MaterialTheme.typography.labelLarge,
                color = if (device.online) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    onOpenPowerSettings: () -> Unit,
    onOpenCloudSettings: () -> Unit,
    cloudScope: CloudSyncScope,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.displaySmall)
            Text("Shape how SyncDroid behaves on this device.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SettingsCard {
                SettingsSwitchRow(
                    icon = if (darkTheme) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                    title = "Dark mode",
                    detail = if (darkTheme) "Using the dark appearance" else "Using the light appearance",
                    checked = darkTheme,
                    onCheckedChange = onDarkThemeChange,
                )
            }
        }
        item {
            SettingsCard {
                SettingsActionRow(
                    icon = Icons.Rounded.Cloud,
                    title = "Cloud sync",
                    detail = when (cloudScope) {
                        CloudSyncScope.DISABLED -> "Off"
                        CloudSyncScope.SELECTED_FOLDERS -> "Selected folder by folder"
                        CloudSyncScope.ALL_FOLDERS -> "Enabled for all folders"
                    },
                    onClick = onOpenCloudSettings,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                SettingsActionRow(
                    icon = Icons.Rounded.BatterySaver,
                    title = "Power & discovery",
                    detail = "Wi-Fi rules, schedules and ping windows",
                    onClick = onOpenPowerSettings,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                SettingsActionRow(
                    icon = Icons.Rounded.Info,
                    title = "About SyncDroid",
                    detail = "Local-first · version ${BuildConfig.VERSION_NAME}",
                    onClick = {},
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CloudSyncSettingsScreen(
    policy: CloudSyncPolicy,
    onScopeChanged: (CloudSyncScope) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Cloud sync") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    "Choose whether cloud sync is disabled, selected folder by folder, or inherited by every mesh folder.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                SectionLabel("CLOUD COVERAGE")
                Spacer(Modifier.height(8.dp))
                SettingsCard {
                    CloudScopeRow(
                        title = "Off",
                        detail = "Keep all files on the local mesh",
                        selected = policy.scope == CloudSyncScope.DISABLED,
                        onClick = { onScopeChanged(CloudSyncScope.DISABLED) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                    CloudScopeRow(
                        title = "Folder by folder",
                        detail = "Each expanded folder card has its own cloud switch",
                        selected = policy.scope == CloudSyncScope.SELECTED_FOLDERS,
                        onClick = { onScopeChanged(CloudSyncScope.SELECTED_FOLDERS) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                    CloudScopeRow(
                        title = "All folders",
                        detail = "Cloud sync is inherited by every current and future folder",
                        selected = policy.scope == CloudSyncScope.ALL_FOLDERS,
                        onClick = { onScopeChanged(CloudSyncScope.ALL_FOLDERS) },
                    )
                }
            }
            item {
                SectionLabel("ACCOUNTS")
                Spacer(Modifier.height(8.dp))
                SettingsCard {
                    SettingsInfoRow(Icons.Rounded.Cloud, "Google Drive", "Not connected")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                    SettingsInfoRow(Icons.Rounded.Cloud, "OneDrive", "Not connected")
                }
            }
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(
                        "Cloud files use the same hashes, version history and conflict checks as local peers. Account connection becomes available when the Google and Microsoft OAuth app registrations are added to this build.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun CloudScopeRow(
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(24.dp),
            shape = CircleShape,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            if (selected) Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PowerSettingsScreen(
    onBack: () -> Unit,
    wifiPolicy: WifiSyncPolicy,
    discoveryPolicy: DiscoveryPolicy,
    onDiscoveryPolicyChanged: (DiscoveryPolicy) -> Unit,
    onOpenWifiRules: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interval = discoveryPolicy.intervalMinutes
    val windowDuration = if (discoveryPolicy.windowSeconds < 60) {
        "${discoveryPolicy.windowSeconds} seconds"
    } else {
        "${discoveryPolicy.windowSeconds / 60} minutes"
    }
    val scheduleNow by produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            val now = LocalDateTime.now()
            val millisToNextMinute = ((60 - now.second) * 1_000L - now.nano / 1_000_000L)
                .coerceAtLeast(100L)
            delay(millisToNextMinute)
            value = LocalDateTime.now()
        }
    }
    val windows = alignedDiscoveryWindows(
        scheduleNow,
        intervalMinutes = interval,
        windowSeconds = discoveryPolicy.windowSeconds,
        count = 3,
    )
    val showWindowDates = interval >= 24 * 60 || windows.any {
        it.start.toLocalDate() != scheduleNow.toLocalDate()
    }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Power & discovery") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    "Coordinate short discovery windows so sleeping devices still have time to meet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                SettingsCard {
                    SettingsSwitchRow(
                        icon = Icons.Rounded.BatterySaver,
                        title = "Scheduled discovery",
                        detail = "Wake briefly to look for local peers",
                        checked = discoveryPolicy.scheduledDiscoveryEnabled,
                        onCheckedChange = {
                            onDiscoveryPolicyChanged(discoveryPolicy.copy(scheduledDiscoveryEnabled = it))
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                    SettingsActionRow(
                        icon = Icons.Rounded.Wifi,
                        title = "Wi-Fi sync switch",
                        detail = "${wifiPolicy.enabledNetworkCount()} registered networks enabled",
                        onClick = onOpenWifiRules,
                    )
                }
            }
            item {
                SectionLabel("DISCOVERY INTERVAL")
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DiscoveryPolicy.SUPPORTED_INTERVALS.sorted().chunked(4).forEach { intervalRow ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            intervalRow.forEach { minutes ->
                                IntervalButton(
                                    minutes = minutes,
                                    selected = interval == minutes,
                                    onClick = {
                                        onDiscoveryPolicyChanged(
                                            discoveryPolicy.copy(intervalMinutes = minutes, windowSecondsOverride = null),
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
            item {
                SectionLabel("DISCOVERY WINDOW")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30L, 60L, 120L, 300L).forEach { seconds ->
                        WindowButton(
                            seconds = seconds,
                            selected = discoveryPolicy.windowSeconds == seconds,
                            onClick = {
                                onDiscoveryPolicyChanged(discoveryPolicy.copy(windowSecondsOverride = seconds))
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            item {
                SettingsCard {
                    SettingsInfoRow(Icons.Rounded.Schedule, "Rendezvous starts", windows.first().startLabel(showWindowDates))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                    SettingsInfoRow(Icons.Rounded.Schedule, "Following ping", windows[1].startLabel(showWindowDates))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                    SettingsInfoRow(Icons.Rounded.Sync, "Discovery window", windowDuration)
                }
            }
            item {
                SectionLabel("UPCOMING WINDOWS")
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    windows.forEachIndexed { index, window ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(15.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 15.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    modifier = Modifier.size(28.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = CircleShape,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }
                                Spacer(Modifier.width(11.dp))
                                Text(window.label(showWindowDates), style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.weight(1f))
                                Text("active $windowDuration", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(11.dp))
                        Text(
                            "Rendezvous times share a midnight-based calendar on every device. The 48-hour cadence runs on alternating midnights and the weekly cadence runs Monday at 00:00. Five-minute discovery defaults to a 30-second window; all other intervals default to five minutes. Your selected window is $windowDuration.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun IntervalButton(minutes: Int, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (selected) Icon(Icons.Rounded.Check, null, modifier = Modifier.size(15.dp))
            Text(intervalButtonLabel(minutes), style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun intervalButtonLabel(minutes: Int): String = when {
    minutes == 7 * 24 * 60 -> "1 wk"
    minutes % (24 * 60) == 0 -> "${minutes / 60} hr"
    minutes % 60 == 0 -> "${minutes / 60} hr"
    else -> "$minutes min"
}

@Composable
private fun WindowButton(seconds: Long, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (selected) Icon(Icons.Rounded.Check, null, modifier = Modifier.size(15.dp))
            Text(if (seconds < 60) "${seconds}s" else "${seconds / 60} min", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsActionRow(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsInfoRow(icon: ImageVector, title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon)
        Spacer(Modifier.width(13.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsIcon(icon: ImageVector) {
    Surface(
        modifier = Modifier.size(40.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StorageAccessDialog(
    onDismiss: () -> Unit,
    onGrantAccess: () -> Unit,
    onUseSystemPicker: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose how to browse") },
        text = {
            Text(
                "Allow full file access to use SyncDroid's built-in file manager and create folders. " +
                    "If you prefer not to, Android's folder picker will be used instead.",
            )
        },
        confirmButton = { TextButton(onClick = onGrantAccess) { Text("Allow built-in manager") } },
        dismissButton = { TextButton(onClick = onUseSystemPicker) { Text("Use system picker") } },
    )
}

private fun folderDisplayName(context: Context, uri: Uri): String {
    val displayName = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
    return displayName?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.substringAfterLast(':')?.takeIf { it.isNotBlank() }
        ?: "Selected folder"
}

@RequiresApi(Build.VERSION_CODES.R)
private fun manageAllFilesIntent(context: Context): Intent = Intent(
    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
    Uri.Builder().scheme("package").opaquePart(context.packageName).build(),
)

private fun JSONArray.toStringList(): List<String> =
    List(length()) { index -> getString(index) }

@Composable
private fun EmptyStateCard(title: String, detail: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun String.initials(): String = trim()
    .split(Regex("\\s+"))
    .filter(String::isNotBlank)
    .take(2)
    .joinToString("") { it.take(1).uppercase() }
    .ifBlank { "?" }

private const val ONLINE_WINDOW_MILLIS = 5 * 60 * 1000L
private const val LEAVE_MESH_ANNOUNCEMENT_TIMEOUT_MILLIS = 30_000L
