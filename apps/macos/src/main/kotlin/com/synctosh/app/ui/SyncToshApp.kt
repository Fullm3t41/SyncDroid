package com.synctosh.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.synctosh.app.model.MainSection
import com.synctosh.app.model.ThemeMode
import com.synctosh.app.mesh.MeshRuntime
import com.synctosh.app.mesh.LocalFolderBindingState
import com.synctosh.app.mesh.MeshFolder
import com.synctosh.app.platform.AppPreferences
import com.synctosh.app.platform.MacDeviceName
import com.synctosh.app.platform.MacFolderPicker
import java.nio.file.Path

private enum class SecondaryScreen { PowerDiscovery, FileHistory }

@Composable
fun SyncToshApp(
    preferences: AppPreferences,
    runtime: MeshRuntime,
    discoveryInterval: Int,
    discoveryWindow: Long,
    onDiscoveryIntervalChanged: (Int) -> Unit,
    onDiscoveryWindowChanged: (Long) -> Unit,
    onCloseToNotificationBar: () -> Unit,
) {
    val meshState by runtime.state.collectAsState()
    var selectedSection by remember { mutableStateOf(preferences.selectedSection) }
    var themeMode by remember { mutableStateOf(preferences.themeMode) }
    var deviceName by remember { mutableStateOf(preferences.deviceName ?: MacDeviceName.current()) }
    var secondaryScreen by remember { mutableStateOf<SecondaryScreen?>(null) }
    var featureNotice by remember { mutableStateOf<String?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameDraft by remember { mutableStateOf(deviceName) }
    var showCreateMesh by remember { mutableStateOf(false) }
    var showJoinMesh by remember { mutableStateOf(false) }
    var showPairingOffer by remember { mutableStateOf(false) }
    var folderToConfigure by remember { mutableStateOf<MeshFolder?>(null) }
    var folderConfigurationError by remember { mutableStateOf<String?>(null) }
    val promptedFolderIds = remember { mutableSetOf<String>() }

    LaunchedEffect(meshState.pairingOffer) {
        if (meshState.pairingOffer != null) showPairingOffer = true
    }

    LaunchedEffect(showJoinMesh, meshState.profile?.groupId) {
        // The runtime publishes a profile only after the remote completion was
        // authenticated, imported, acknowledged, and saved successfully.
        if (showJoinMesh && meshState.profile != null) {
            showJoinMesh = false
            runtime.dismissError()
        }
    }

    LaunchedEffect(meshState.folders) {
        val pending = meshState.folders.firstOrNull {
            it.bindingState == LocalFolderBindingState.PENDING_CONFIGURATION && it.folderId !in promptedFolderIds
        }
        if (pending != null && folderToConfigure == null) {
            promptedFolderIds += pending.folderId
            folderToConfigure = pending
        }
    }

    fun selectSection(section: MainSection) {
        selectedSection = section
        preferences.selectedSection = section
        secondaryScreen = null
    }

    fun requestRename() {
        renameDraft = deviceName
        showRenameDialog = true
    }

    SyncToshTheme(themeMode) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (secondaryScreen == null) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        MainSection.entries.forEach { section ->
                            NavigationBarItem(
                                selected = selectedSection == section,
                                onClick = { selectSection(section) },
                                icon = { Icon(section.icon(), contentDescription = null) },
                                label = { Text(section.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.fillMaxSize().widthIn(max = 1_080.dp)) {
                    when (secondaryScreen) {
                        SecondaryScreen.PowerDiscovery -> PowerDiscoveryScreen(
                            intervalMinutes = discoveryInterval,
                            windowSeconds = discoveryWindow,
                            onIntervalChanged = onDiscoveryIntervalChanged,
                            onWindowChanged = onDiscoveryWindowChanged,
                            onBack = { secondaryScreen = null },
                        )
                        SecondaryScreen.FileHistory -> FileHistoryScreen(
                            events = meshState.fileHistory,
                            folders = meshState.folders,
                            deviceNames = buildMap {
                                put(meshState.localDeviceId, deviceName)
                                meshState.peers.forEach { put(it.deviceId, it.name) }
                            },
                            busy = meshState.busy,
                            error = meshState.error,
                            onRecover = { runtime.recoverFile(it.eventId) },
                            onBack = { secondaryScreen = null },
                        )
                        null -> when (selectedSection) {
                            MainSection.Sync -> SyncScreen(
                                deviceName = deviceName,
                                peers = meshState.peers,
                                folders = meshState.folders,
                                meshName = meshState.profile?.groupName,
                                runtimeStatus = meshState.status,
                                busy = meshState.busy,
                                onSyncNow = runtime::syncNow,
                                onRenameDevice = ::requestRename,
                                onCloseToNotificationBar = onCloseToNotificationBar,
                            )
                            MainSection.Folders -> FoldersScreen(
                                folders = meshState.folders,
                                onAddFolder = { featureNotice = "Folder access" },
                                onConfigureFolder = { folderToConfigure = it },
                                onDeclineFolder = { runtime.declineFolder(it.folderId) },
                                onOpenFolder = { folder ->
                                    runCatching {
                                        MacFolderPicker.openInFinder(Path.of(requireNotNull(folder.localPath)))
                                    }.onFailure {
                                        folderConfigurationError = it.message ?: "Could not open this folder in Finder"
                                    }
                                },
                            )
                            MainSection.Devices -> DevicesScreen(
                                deviceName = deviceName,
                                peers = meshState.peers,
                                onStartMesh = {
                                    if (meshState.profile == null) showCreateMesh = true
                                    else if (meshState.pairingOffer != null) showPairingOffer = true
                                    else runtime.createPairingOffer()
                                },
                                onJoinMesh = {
                                    runtime.dismissError()
                                    showJoinMesh = true
                                },
                                onRenameDevice = ::requestRename,
                                hasMesh = meshState.profile != null,
                            )
                            MainSection.Chat -> ChatScreen(
                                messages = meshState.chatMessages,
                                currentDeviceId = meshState.localDeviceId,
                                deviceNames = buildMap {
                                    put(meshState.localDeviceId, deviceName)
                                    meshState.peers.forEach { put(it.deviceId, it.name) }
                                },
                                meshAvailable = meshState.profile != null,
                                onSend = runtime::sendChat,
                            )
                            MainSection.Settings -> SettingsScreen(
                                themeMode = themeMode,
                                onThemeModeChanged = {
                                    themeMode = it
                                    preferences.themeMode = it
                                },
                                onOpenPowerSettings = { secondaryScreen = SecondaryScreen.PowerDiscovery },
                                onOpenFileHistory = { secondaryScreen = SecondaryScreen.FileHistory },
                                onFeatureRequested = { featureNotice = it },
                            )
                        }
                    }
                }
            }
        }

        if (showRenameDialog) {
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Update device name") },
                text = {
                    Column {
                        Text("This nickname identifies the Mac throughout your trusted mesh.")
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = renameDraft,
                            onValueChange = { if (it.length <= 64) renameDraft = it },
                            label = { Text("Device name") },
                            singleLine = true,
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            deviceName = renameDraft.trim()
                            preferences.deviceName = deviceName
                            showRenameDialog = false
                        },
                        enabled = renameDraft.isNotBlank() && renameDraft.trim() != deviceName,
                    ) { Text("Update") }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
                },
            )
        }

        if (showCreateMesh) {
            CreateMeshDialog(
                onDismiss = { showCreateMesh = false },
                onCreate = {
                    showCreateMesh = false
                    runtime.createMesh(it)
                },
            )
        }

        if (showJoinMesh) {
            JoinMeshDialog(
                attemptsRemaining = meshState.attemptsRemaining,
                busy = meshState.busy,
                error = meshState.error,
                onDismiss = {
                    showJoinMesh = false
                    runtime.dismissError()
                },
                onJoin = runtime::joinMesh,
            )
        }

        meshState.pairingOffer?.takeIf { showPairingOffer }?.let { offer ->
            PairingOfferDialog(offer, onDismiss = { showPairingOffer = false })
        }

        folderToConfigure?.let { folder ->
            AlertDialog(
                onDismissRequest = { folderToConfigure = null },
                title = { Text("Configure ${folder.displayName}") },
                text = {
                    Column {
                        Text("Choose where this mesh folder should live on this Mac.")
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (folder.includePatterns.isEmpty() && folder.excludePatterns.isEmpty()) {
                                "All files will be included."
                            } else {
                                buildString {
                                    if (folder.includePatterns.isNotEmpty()) append("Include: ${folder.includePatterns.joinToString()}\n")
                                    if (folder.excludePatterns.isNotEmpty()) append("Exclude: ${folder.excludePatterns.joinToString()}")
                                }.trim()
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            runCatching { MacFolderPicker.chooseExisting("Choose an existing folder") }
                                .onSuccess { path ->
                                    if (path != null) {
                                        runtime.configureFolder(folder.folderId, path)
                                        folderToConfigure = null
                                    }
                                }
                                .onFailure {
                                    folderToConfigure = null
                                    folderConfigurationError = it.message ?: "Could not choose that folder"
                                }
                        },
                    ) { Text("Choose existing") }
                    TextButton(
                        onClick = {
                            runCatching {
                                MacFolderPicker.chooseParentAndCreate(
                                    "Choose a location for ${folder.displayName}",
                                    folder.displayName,
                                )
                            }.onSuccess { path ->
                                if (path != null) {
                                    runtime.configureFolder(folder.folderId, path)
                                    folderToConfigure = null
                                }
                            }.onFailure {
                                folderToConfigure = null
                                folderConfigurationError = it.message ?: "Could not create that folder"
                            }
                        },
                    ) { Text("Create new") }
                },
                dismissButton = { TextButton(onClick = { folderToConfigure = null }) { Text("Later") } },
            )
        }

        folderConfigurationError?.let { error ->
            AlertDialog(
                onDismissRequest = { folderConfigurationError = null },
                title = { Text("Folder configuration needs attention") },
                text = { Text(error) },
                confirmButton = { TextButton(onClick = { folderConfigurationError = null }) { Text("Done") } },
            )
        }

        if (meshState.error != null && !showJoinMesh) {
            AlertDialog(
                onDismissRequest = runtime::dismissError,
                title = { Text("Mesh action needs attention") },
                text = { Text(requireNotNull(meshState.error)) },
                confirmButton = { TextButton(onClick = runtime::dismissError) { Text("Done") } },
            )
        }

        featureNotice?.let { feature ->
            AlertDialog(
                onDismissRequest = { featureNotice = null },
                title = { Text(feature) },
                text = {
                    Text(
                        when (feature) {
                            "Folder access" ->
                                "The in-app browser will be enabled with persistent macOS folder permissions in the next storage milestone."
                            "Cloud sync" ->
                                "Google Drive and OneDrive will use the shared encrypted cloud format after local peer sync is interoperable."
                            "Background operation" ->
                                "Closing the window keeps SyncTosh in the menu bar. While hidden, it uses the interval and discovery window selected in Power & discovery. Reopen it from the menu-bar icon for continuous discovery."
                            else -> "SyncTosh 0.1.2 · macOS preview"
                        },
                    )
                },
                confirmButton = {
                    TextButton(onClick = { featureNotice = null }) { Text("Done") }
                },
            )
        }
    }
}

private fun MainSection.icon(): ImageVector = when (this) {
    MainSection.Sync -> Icons.Rounded.Sync
    MainSection.Folders -> Icons.Rounded.Folder
    MainSection.Devices -> Icons.Rounded.Devices
    MainSection.Chat -> Icons.Rounded.ChatBubbleOutline
    MainSection.Settings -> Icons.Rounded.Settings
}
