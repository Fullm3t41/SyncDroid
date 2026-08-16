package com.synctosh.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.synctosh.app.model.MeshPeer
import com.synctosh.app.model.ThemeMode
import com.synctosh.app.mesh.LocalFolderBindingState
import com.synctosh.app.mesh.FileHistoryAction
import com.synctosh.app.mesh.FileHistoryEvent
import com.synctosh.app.mesh.MeshFolder
import com.synctosh.app.mesh.MeshChatMessage
import com.synctosh.app.mesh.SUPPORTED_DISCOVERY_INTERVALS
import com.synctosh.app.mesh.SUPPORTED_DISCOVERY_WINDOWS
import com.synctosh.app.mesh.discoveryIntervalLabel
import com.synctosh.app.mesh.discoveryWindowLabel
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.synctosh.app.mesh.upcomingDiscoveryWindows
import kotlinx.coroutines.delay

@Composable
fun SyncScreen(
    deviceName: String,
    peers: List<MeshPeer>,
    folders: List<MeshFolder>,
    meshName: String?,
    runtimeStatus: String,
    busy: Boolean,
    onSyncNow: () -> Unit,
    onRenameDevice: () -> Unit,
    onCloseToNotificationBar: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= WIDE_SCREEN_BREAKPOINT
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 26.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(runtimeStatus, style = MaterialTheme.typography.displaySmall)
                    Text(
                        meshName?.let { "Connected to $it." } ?: "Start or join a mesh to begin local synchronization.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = onCloseToNotificationBar) {
                        Icon(Icons.Rounded.Computer, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Close to notification bar.")
                    }
                    Button(
                        onClick = onSyncNow,
                        enabled = meshName != null && !busy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onBackground,
                            contentColor = MaterialTheme.colorScheme.background,
                        ),
                    ) {
                        Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Sync now")
                    }
                }
            }
            }
            if (wide) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1.25f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            MeshStatusCard(meshName, peers)
                            LocalMeshView(
                                currentDevice = deviceName,
                                peers = peers,
                                onRenameCurrentDevice = onRenameDevice,
                            )
                        }
                        Column(Modifier.weight(0.9f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            SyncMetrics(peers, folders)
                            Column {
                                SectionLabel("ACTIVE FOLDERS")
                                Spacer(Modifier.height(8.dp))
                                ActiveFoldersSummary(folders)
                            }
                        }
                    }
                }
            } else {
                item { MeshStatusCard(meshName, peers) }
                item {
                    LocalMeshView(
                        currentDevice = deviceName,
                        peers = peers,
                        onRenameCurrentDevice = onRenameDevice,
                    )
                }
                item { SyncMetrics(peers, folders) }
                item {
                    SectionLabel("ACTIVE FOLDERS")
                    Spacer(Modifier.height(8.dp))
                    ActiveFoldersSummary(folders)
                }
            }
        }
    }
}

@Composable
private fun MeshStatusCard(meshName: String?, peers: List<MeshPeer>) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(11.dp))
            Column {
                Text(meshName?.let { "Local mesh online" } ?: "Local mesh not connected", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (meshName == null) "Discovery will remain local to your Wi-Fi network."
                    else "${peers.count(MeshPeer::online)} nearby · ${peers.size} trusted peer${if (peers.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun SyncMetrics(peers: List<MeshPeer>, folders: List<MeshFolder>) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricCard(peers.count(MeshPeer::online).toString(), "online", Modifier.weight(1f))
        MetricCard(
            folders.count { it.bindingState == LocalFolderBindingState.CONFIGURED }.toString(),
            "folders",
            Modifier.weight(1f),
        )
        MetricCard(
            folders.count { it.bindingState == LocalFolderBindingState.PENDING_CONFIGURATION }.toString(),
            "needs review",
            Modifier.weight(1f),
            alert = folders.any { it.bindingState == LocalFolderBindingState.PENDING_CONFIGURATION },
        )
    }
}

@Composable
private fun ActiveFoldersSummary(folders: List<MeshFolder>) {
    val configured = folders.filter { it.bindingState == LocalFolderBindingState.CONFIGURED }
    if (configured.isEmpty()) {
        EmptyStateCard(
            if (folders.any { it.bindingState == LocalFolderBindingState.PENDING_CONFIGURATION }) {
                "Folder configuration needed"
            } else {
                "No active folders"
            },
            if (folders.any { it.bindingState == LocalFolderBindingState.PENDING_CONFIGURATION }) {
                "Open Folders and choose a local location before synchronization begins."
            } else {
                "Add a folder after starting or joining a mesh."
            },
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            configured.forEach { folder ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(folder.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            folder.localPath.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FoldersScreen(
    folders: List<MeshFolder>,
    onAddFolder: () -> Unit,
    onConfigureFolder: (MeshFolder) -> Unit,
    onDeclineFolder: (MeshFolder) -> Unit,
    onOpenFolder: (MeshFolder) -> Unit,
) {
    var explanationExpanded by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= WIDE_SCREEN_BREAKPOINT
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 26.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Folders", style = MaterialTheme.typography.displaySmall)
                    Text(
                        "Choose what belongs in your local mesh.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = onAddFolder) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add")
                }
            }
            }
            item {
                val accessCard: @Composable () -> Unit = {
                    ExpandableInfoCard(
                        title = "How folder sync works",
                        summary = "Folder access, updates, exceptions, reviews and recovery",
                        expanded = explanationExpanded,
                        onToggle = { explanationExpanded = !explanationExpanded },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            FolderGuideSection(
                                title = "Folder access",
                                body = "Each mesh folder has a shared name and set of filters, but every device chooses where its local copy lives. When a folder is announced by another device, it appears as Configure. macOS only gives SyncTosh access to the location you select.",
                            )
                            FolderGuideSection(
                                title = "Declining a folder",
                                body = "Decline if you do not want this folder on this Mac. It remains marked Declined, does not download its contents here and does not affect copies on other devices. You can configure it later by choosing a local folder.",
                            )
                            FolderGuideSection(
                                title = "Choosing which file is newer",
                                body = "SyncTosh compares each file's version history and SHA-256 content hash rather than relying only on its last-edited time. A change that follows the version already known by the mesh becomes the update. Identical content is skipped, even if timestamps differ, so receiving a file does not create an endless sync back to its sender.",
                            )
                            FolderGuideSection(
                                title = "When review is required",
                                body = "A review is required when two devices independently change the same file before either receives the other's update. Neither version can safely be called newer, so SyncTosh records a conflict instead of silently overwriting one. Conflict review lets you keep the local version, keep the incoming version, or preserve both; Keep both gives the additional copy a numbered suffix such as _1 before the file extension. Your decision becomes the next version shared with the mesh.",
                            )
                            FolderGuideSection(
                                title = "Deletions and exceptions",
                                body = "Normal folder deletions are recorded and shared with the mesh. In an Overwrite-only folder, deleting a file instead creates an exception: the file is not deleted from other devices and is not downloaded back to this one. Exceptions can be undone individually. Once every participating device reports the file absent, the mesh recognises the deletion as complete and removes the exception automatically.",
                            )
                            FolderGuideSection(
                                title = "30-day data recovery",
                                body = "When possible, SyncTosh saves a recovery copy before applying a deletion. Open Settings > File history on the device holding that copy to restore it within 30 days. A restored file is scanned as a new update and can sync back to the other devices.",
                            )
                        }
                    }
                }
                val folderList: @Composable () -> Unit = {
                    if (folders.isEmpty()) {
                        EmptyStateCard(
                            "No folders configured",
                            "Add a local folder, or pair with an existing mesh to receive its folder list.",
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            folders.forEach { folder ->
                                MeshFolderCard(folder, onConfigureFolder, onDeclineFolder, onOpenFolder)
                            }
                        }
                    }
                }
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.weight(1f)) { accessCard() }
                        Box(Modifier.weight(1f)) { folderList() }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        accessCard()
                        folderList()
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderGuideSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MeshFolderCard(
    folder: MeshFolder,
    onConfigure: (MeshFolder) -> Unit,
    onDecline: (MeshFolder) -> Unit,
    onOpenFolder: (MeshFolder) -> Unit,
) {
    var expanded by remember(folder.folderId) { mutableStateOf(false) }
    val summary = when (folder.bindingState) {
        LocalFolderBindingState.PENDING_CONFIGURATION -> "Configure · choose a location on this Mac"
        LocalFolderBindingState.CONFIGURED -> folder.localPath ?: "Configured on this Mac"
        LocalFolderBindingState.DECLINED -> "Declined on this Mac"
    }
    ExpandableInfoCard(
        title = folder.displayName,
        summary = summary,
        expanded = expanded,
        onToggle = { expanded = !expanded },
    ) {
        val filters = buildList {
            if (folder.includePatterns.isNotEmpty()) add("Include: ${folder.includePatterns.joinToString()}")
            if (folder.excludePatterns.isNotEmpty()) add("Exclude: ${folder.excludePatterns.joinToString()}")
        }
        Text(
            filters.joinToString("\n").ifBlank { "All files are included." },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Button(onClick = { onConfigure(folder) }) {
                Text(if (folder.bindingState == LocalFolderBindingState.CONFIGURED) "Change folder" else "Configure")
            }
            if (folder.bindingState != LocalFolderBindingState.DECLINED) {
                OutlinedButton(onClick = { onDecline(folder) }) { Text("Decline") }
            }
            if (folder.bindingState == LocalFolderBindingState.CONFIGURED && folder.localPath != null) {
                OutlinedButton(onClick = { onOpenFolder(folder) }) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open in Finder")
                }
            }
        }
    }
}

@Composable
fun DevicesScreen(
    deviceName: String,
    peers: List<MeshPeer>,
    onStartMesh: () -> Unit,
    onJoinMesh: () -> Unit,
    onRenameDevice: () -> Unit,
    hasMesh: Boolean,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= WIDE_SCREEN_BREAKPOINT
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 26.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text("Devices", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Every trusted device is an equal peer.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                        LocalMeshView(deviceName, peers, onRenameDevice, Modifier.weight(1.25f))
                        Column(Modifier.weight(0.9f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            DeviceActions(onStartMesh, onJoinMesh, hasMesh)
                            EmptyStateCard("No trusted peers", "Start a mesh here or enter a six-digit code from an existing device.")
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        LocalMeshView(deviceName, peers, onRenameDevice)
                        DeviceActions(onStartMesh, onJoinMesh, hasMesh)
                        EmptyStateCard("No trusted peers", "Start a mesh here or enter a six-digit code from an existing device.")
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceActions(onStartMesh: () -> Unit, onJoinMesh: () -> Unit, hasMesh: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = onStartMesh, modifier = Modifier.weight(1f)) {
            Icon(Icons.Rounded.Devices, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
            Text(if (hasMesh) "Add a device" else "Start a mesh")
        }
        OutlinedButton(onClick = onJoinMesh, enabled = !hasMesh, modifier = Modifier.weight(1f)) {
            Text("Join with code")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    messages: List<MeshChatMessage>,
    currentDeviceId: String,
    deviceNames: Map<String, String>,
    meshAvailable: Boolean,
    onSend: (String) -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    var copyToken by remember { mutableIntStateOf(0) }
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val ordered = messages.sortedWith(compareBy(MeshChatMessage::createdAtMillis, MeshChatMessage::messageId))

    fun sendDraft() {
        val body = draft.trim()
        if (meshAvailable && body.isNotEmpty()) {
            onSend(body)
            draft = ""
        }
    }

    LaunchedEffect(ordered.lastOrNull()?.messageId) {
        if (ordered.isNotEmpty()) listState.animateScrollToItem(ordered.lastIndex)
    }
    LaunchedEffect(copyToken) {
        if (copied) {
            val expected = copyToken
            delay(1_000)
            if (expected == copyToken) copied = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = 26.dp, vertical = 24.dp)) {
                Text("Mesh chat", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Private group chat for trusted devices on this mesh.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (ordered.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(26.dp), contentAlignment = Alignment.Center) {
                    EmptyStateCard(
                        "Start the conversation",
                        if (meshAvailable) "Messages sync through any trusted device that comes online."
                        else "Join a mesh to exchange signed messages with trusted devices.",
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(17.dp),
                ) {
                    items(ordered, key = MeshChatMessage::messageId) { message ->
                        val ours = message.authorDeviceId == currentDeviceId
                        val author = if (ours) "You" else deviceNames[message.authorDeviceId]
                            ?: message.authorDeviceId.take(8)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (ours) Arrangement.End else Arrangement.Start,
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(0.82f),
                                horizontalAlignment = if (ours) Alignment.End else Alignment.Start,
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Text(author, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        CHAT_TIME_FORMAT.format(
                                            Instant.ofEpochMilli(message.createdAtMillis).atZone(ZoneId.systemDefault()),
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.height(5.dp))
                                Surface(
                                    modifier = Modifier.combinedClickable(
                                        onClick = {},
                                        onLongClick = {
                                            clipboard.setText(AnnotatedString(message.body))
                                            copied = true
                                            copyToken++
                                        },
                                    ),
                                    color = if (ours) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (ours) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(
                                        topStart = if (ours) 20.dp else 6.dp,
                                        topEnd = if (ours) 6.dp else 20.dp,
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
                }
            }
            Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 5.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { if (it.toByteArray(Charsets.UTF_8).size <= 4_000) draft = it },
                        enabled = meshAvailable,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(if (meshAvailable) "Message the mesh" else "Join a mesh to send messages") },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                        singleLine = true,
                    )
                    Button(onClick = ::sendDraft, enabled = meshAvailable && draft.isNotBlank()) { Text("Send") }
                }
            }
        }
        if (copied) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 86.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                shadowElevation = 5.dp,
            ) {
                Text("Copied text.", modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp))
            }
        }
    }
}

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onOpenPowerSettings: () -> Unit,
    onOpenFileHistory: () -> Unit,
    onFeatureRequested: (String) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= WIDE_SCREEN_BREAKPOINT
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 26.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text("Settings", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Shape how SyncTosh behaves on this Mac.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                val appearance: @Composable () -> Unit = {
                    Column {
                        SectionLabel("APPEARANCE")
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeMode.entries.forEach { mode ->
                                SelectablePill(
                                    label = mode.name,
                                    selected = themeMode == mode,
                                    onClick = { onThemeModeChanged(mode) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                val featureSettings: @Composable () -> Unit = {
                    SettingsCard {
                SettingsActionRow(
                    icon = Icons.Rounded.Cloud,
                    title = "Cloud sync",
                    detail = "Google Drive and OneDrive",
                    onClick = { onFeatureRequested("Cloud sync") },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsActionRow(
                    icon = Icons.Rounded.History,
                    title = "File history",
                    detail = "Updates, sync activity and 30-day recovery",
                    onClick = onOpenFileHistory,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsActionRow(
                    icon = Icons.Rounded.BatterySaver,
                    title = "Power & discovery",
                    detail = "Wi-Fi rules, schedules and discovery windows",
                    onClick = onOpenPowerSettings,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsActionRow(
                    icon = Icons.Rounded.Computer,
                    title = "Background operation",
                    detail = "Menu bar and launch-at-login",
                    onClick = { onFeatureRequested("Background operation") },
                )
                    }
                }
                val about: @Composable () -> Unit = {
                    SettingsCard {
                        SettingsActionRow(
                            icon = Icons.Rounded.Info,
                            title = "About SyncTosh",
                            detail = "Local-first · version 0.1.2",
                            onClick = { onFeatureRequested("About SyncTosh") },
                        )
                    }
                }
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(0.85f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            appearance()
                            about()
                        }
                        Box(Modifier.weight(1.15f)) { featureSettings() }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        appearance()
                        featureSettings()
                        about()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileHistoryScreen(
    events: List<FileHistoryEvent>,
    folders: List<MeshFolder>,
    deviceNames: Map<String, String>,
    busy: Boolean,
    error: String?,
    onRecover: (FileHistoryEvent) -> Unit,
    onBack: () -> Unit,
) {
    val nowMillis = System.currentTimeMillis()
    val folderNames = folders.associate { it.folderId to it.displayName }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("File history") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 26.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Files deleted through mesh synchronization are retained on this Mac for 30 days. Recovering one creates a new file version for the mesh.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            error?.let { message ->
                item {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
                        Text(
                            message,
                            modifier = Modifier.padding(14.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            if (events.isEmpty()) {
                item { EmptyStateCard("No file activity yet", "Added, updated, synced, deleted and recovered files will appear here.") }
            } else {
                items(events, key = FileHistoryEvent::eventId) { event ->
                    val recoverable = event.action == FileHistoryAction.DELETED && event.recoveredAtMillis == null &&
                        event.recoveryPath != null && (event.recoverableUntilMillis ?: 0) > nowMillis
                    Surface(color = MaterialTheme.colorScheme.surface, shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(11.dp),
                                ) {
                                    Icon(
                                        when (event.action) {
                                            FileHistoryAction.DELETED -> Icons.Rounded.Delete
                                            FileHistoryAction.RECOVERED -> Icons.Rounded.Restore
                                            FileHistoryAction.SYNCED -> Icons.Rounded.Sync
                                            else -> Icons.Rounded.History
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.padding(9.dp).size(19.dp),
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(event.relativePath.substringAfterLast('/'), style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${event.action.historyLabel()} · ${formatHistoryTime(event.createdAtMillis)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(Modifier.height(9.dp))
                            val folderName = folderNames[event.folderId] ?: "Unknown folder"
                            val deviceName = deviceNames[event.sourceDeviceId] ?: event.sourceDeviceId.take(8)
                            Text(
                                "$folderName · $deviceName${event.sizeBytes?.let { " · ${formatFileSize(it)}" }.orEmpty()}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            event.relativePath.takeIf { it.contains('/') }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (event.action == FileHistoryAction.DELETED) {
                                Spacer(Modifier.height(8.dp))
                                when {
                                    event.recoveredAtMillis != null -> Text(
                                        "Recovered ${formatHistoryTime(event.recoveredAtMillis)}",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    recoverable -> Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "Recoverable until ${formatHistoryTime(requireNotNull(event.recoverableUntilMillis))}",
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        OutlinedButton(onClick = { onRecover(event) }, enabled = !busy) {
                                            Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(17.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(if (busy) "Working…" else "Recover")
                                        }
                                    }
                                    (event.recoverableUntilMillis ?: 0) <= nowMillis -> Text(
                                        "30-day recovery window expired",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    else -> Text(
                                        "Recovery copy unavailable on this Mac",
                                        style = MaterialTheme.typography.bodySmall,
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

private fun FileHistoryAction.historyLabel(): String = when (this) {
    FileHistoryAction.ADDED -> "Added"
    FileHistoryAction.UPDATED -> "Updated"
    FileHistoryAction.SYNCED -> "Synced"
    FileHistoryAction.DELETED -> "Deleted"
    FileHistoryAction.RECOVERED -> "Recovered"
}

private fun formatHistoryTime(timestamp: Long): String = Instant.ofEpochMilli(timestamp)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"))

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PowerDiscoveryScreen(
    intervalMinutes: Int,
    windowSeconds: Long,
    onIntervalChanged: (Int) -> Unit,
    onWindowChanged: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val scheduleNow = LocalDateTime.now()
    val upcoming = remember(scheduleNow.minute, intervalMinutes, windowSeconds) {
        upcomingDiscoveryWindows(scheduleNow, intervalMinutes, windowSeconds, 3)
    }
    val includeDate = intervalMinutes >= 24 * 60 || upcoming.any { it.start.toLocalDate() != scheduleNow.toLocalDate() }

    Column(Modifier.fillMaxSize()) {
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
            contentPadding = PaddingValues(horizontal = 26.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            item {
                Text(
                    "Coordinate short discovery windows so sleeping devices still have time to meet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                SectionLabel("DISCOVERY INTERVAL")
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SUPPORTED_DISCOVERY_INTERVALS.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { minutes ->
                                SelectablePill(
                                    label = discoveryIntervalLabel(minutes, compact = true),
                                    selected = minutes == intervalMinutes,
                                    onClick = { onIntervalChanged(minutes) },
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
                    SUPPORTED_DISCOVERY_WINDOWS.forEach { seconds ->
                        SelectablePill(
                            label = if (seconds < 60) "${seconds}s" else "${seconds / 60} min",
                            selected = seconds == windowSeconds,
                            onClick = { onWindowChanged(seconds) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            item {
                SectionLabel("UPCOMING WINDOWS")
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    upcoming.forEachIndexed { index, window ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(15.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    modifier = Modifier.size(28.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("${index + 1}", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                                Spacer(Modifier.width(11.dp))
                                Text(windowLabel(window.start, window.end, includeDate), style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "active ${discoveryWindowLabel(windowSeconds)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Schedule, contentDescription = null)
                        Spacer(Modifier.width(11.dp))
                        Text(
                            "All devices use the same midnight-based schedule. In the foreground, discovery remains continuously available on connected Wi-Fi.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

private fun windowLabel(start: LocalDateTime, end: LocalDateTime, includeDate: Boolean): String {
    val startFormatter = if (includeDate) DATE_TIME_FORMAT else TIME_FORMAT
    val endFormatter = if (includeDate && start.toLocalDate() != end.toLocalDate()) DATE_TIME_FORMAT else TIME_FORMAT
    return "${start.format(startFormatter)}–${end.format(endFormatter)}"
}

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")
private val CHAT_TIME_FORMAT = DateTimeFormatter.ofPattern("d MMM, HH:mm")
private val WIDE_SCREEN_BREAKPOINT = 900.dp
