package com.syncdows.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.syncdows.app.mesh.MeshRuntimeState
import com.syncdows.app.mesh.SUPPORTED_DISCOVERY_INTERVALS
import com.syncdows.app.mesh.SUPPORTED_DISCOVERY_WINDOWS
import com.syncdows.app.mesh.discoveryIntervalLabel
import com.syncdows.app.mesh.discoveryWindowLabel
import com.syncdows.app.model.MeshPeer
import com.syncdows.app.model.ThemeMode
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import kotlin.math.max
import kotlin.math.min

@Composable
fun TrayPanelWindow(
    anchor: Point,
    themeMode: ThemeMode,
    meshState: MeshRuntimeState,
    discoveryInterval: Int,
    discoveryWindow: Long,
    alwaysOnDiscovery: Boolean,
    onOpen: () -> Unit,
    onDiscoveryIntervalChanged: (Int) -> Unit,
    onDiscoveryWindowChanged: (Long) -> Unit,
    onAlwaysOnDiscoveryChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onQuit: () -> Unit,
) {
    val visiblePeerCount = min(meshState.peers.size, 5)
    val desiredPanelHeight = 414 + visiblePeerCount * 34
    val configuration = screenConfigurationAt(anchor)
    val screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(configuration)
    val workAreaHeight = configuration.bounds.height - screenInsets.top - screenInsets.bottom
    val panelHeight = trayPanelHeightDp(
        desiredHeightDp = desiredPanelHeight,
        workAreaHeightPixels = workAreaHeight,
        displayScale = configuration.defaultTransform.scaleY,
    ).dp
    val windowState = rememberDialogState(width = 376.dp, height = panelHeight)
    var intervalMenuExpanded by remember { mutableStateOf(false) }
    var durationMenuExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    DialogWindow(
        title = "SyncDows status",
        state = windowState,
        onCloseRequest = onDismiss,
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
    ) {
        LaunchedEffect(anchor) {
            positionTrayPanel(window, anchor)
            window.toFront()
            window.requestFocus()
        }
        DisposableEffect(window, intervalMenuExpanded, durationMenuExpanded) {
            val listener = object : WindowAdapter() {
                override fun windowLostFocus(event: WindowEvent) {
                    if (!intervalMenuExpanded && !durationMenuExpanded) onDismiss()
                }
            }
            window.addWindowFocusListener(listener)
            onDispose { window.removeWindowFocusListener(listener) }
        }

        SyncDowsTheme(themeMode) {
            Surface(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.background,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shadowElevation = 16.dp,
            ) {
                Box(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .windowsTouchDragScroll(scrollState)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(11.dp),
                    ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(38.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Sync,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text("SyncDows", style = MaterialTheme.typography.titleLarge)
                            Text(
                                meshState.status,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close status panel")
                        }
                    }

                    Button(
                        onClick = onOpen,
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(Icons.Rounded.Computer, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open SyncDows")
                    }

                    OutlinedButton(
                        onClick = onQuit,
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Exit SyncDows")
                    }

                    DeviceStatusCard(meshState)

                    Text(
                        "BACKGROUND DISCOVERY",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(13.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Always on", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = alwaysOnDiscovery, onCheckedChange = onAlwaysOnDiscoveryChanged)
                        }
                    }
                    TraySelectionRow(
                        icon = Icons.Rounded.Schedule,
                        title = "Sync interval",
                        value = discoveryIntervalLabel(discoveryInterval),
                        enabled = !alwaysOnDiscovery,
                        expanded = intervalMenuExpanded,
                        onExpandedChange = {
                            durationMenuExpanded = false
                            intervalMenuExpanded = it
                        },
                        options = SUPPORTED_DISCOVERY_INTERVALS,
                        selected = discoveryInterval,
                        optionLabel = ::discoveryIntervalLabel,
                        onSelected = {
                            onDiscoveryIntervalChanged(it)
                            intervalMenuExpanded = false
                        },
                    )
                    TraySelectionRow(
                        icon = Icons.Rounded.Timer,
                        title = "Discovery duration",
                        value = discoveryWindowLabel(discoveryWindow),
                        enabled = !alwaysOnDiscovery,
                        expanded = durationMenuExpanded,
                        onExpandedChange = {
                            intervalMenuExpanded = false
                            durationMenuExpanded = it
                        },
                        options = SUPPORTED_DISCOVERY_WINDOWS,
                        selected = discoveryWindow,
                        optionLabel = ::discoveryWindowLabel,
                        onSelected = {
                            onDiscoveryWindowChanged(it)
                            durationMenuExpanded = false
                        },
                    )
                    }
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(scrollState),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(vertical = 14.dp, horizontal = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceStatusCard(meshState: MeshRuntimeState) {
    val sortedPeers = meshState.peers.sortedWith(compareByDescending<MeshPeer> { it.online }.thenBy { it.name.lowercase() })
    val onlinePeers = sortedPeers.count { it.online }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(meshState.profile?.groupName ?: "No mesh connected", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (meshState.profile == null) "Pair a device to begin" else "$onlinePeers/${sortedPeers.size} devices online",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    modifier = Modifier.size(9.dp),
                    shape = CircleShape,
                    color = if (onlinePeers > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                ) {}
            }
            sortedPeers.take(5).forEach { peer ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(8.dp),
                        shape = CircleShape,
                        color = if (peer.online) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                    ) {}
                    Spacer(Modifier.width(9.dp))
                    Text(
                        peer.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (peer.online) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (peer.online) "Online" else "Offline",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (sortedPeers.size > 5) {
                Text(
                    "+${sortedPeers.size - 5} more mesh members",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun <T> TraySelectionRow(
    icon: ImageVector,
    title: String,
    value: String,
    enabled: Boolean = true,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Box(Modifier.alpha(if (enabled) 1f else 0.38f)) {
        Surface(
            modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onExpandedChange(!expanded) },
            shape = RoundedCornerShape(13.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.size(17.dp))
            }
        }
        DropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.width(300.dp),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    leadingIcon = {
                        if (option == selected) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(17.dp))
                        } else {
                            Spacer(Modifier.size(17.dp))
                        }
                    },
                    onClick = { onSelected(option) },
                )
            }
        }
    }
}

internal fun trayPanelLocation(
    anchor: Point,
    screenBounds: Rectangle,
    screenInsets: Insets,
    panelSize: Dimension,
    margin: Int = 12,
): Point {
    val left = screenBounds.x + screenInsets.left + margin
    val top = screenBounds.y + screenInsets.top + margin
    val right = screenBounds.x + screenBounds.width - screenInsets.right - margin
    val bottom = screenBounds.y + screenBounds.height - screenInsets.bottom - margin
    val preferredX = anchor.x - panelSize.width + 28
    val preferredY = anchor.y - panelSize.height - margin
    return Point(
        preferredX.coerceIn(left, max(left, right - panelSize.width)),
        preferredY.coerceIn(top, max(top, bottom - panelSize.height)),
    )
}

internal fun trayPanelHeightDp(
    desiredHeightDp: Int,
    workAreaHeightPixels: Int,
    displayScale: Double,
    marginPixels: Int = 12,
): Int {
    val usablePixels = (workAreaHeightPixels - marginPixels * 2).coerceAtLeast(1)
    val safeScale = displayScale.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
    return min(desiredHeightDp, (usablePixels / safeScale).toInt().coerceAtLeast(1))
}

private fun screenConfigurationAt(anchor: Point): java.awt.GraphicsConfiguration =
    GraphicsEnvironment.getLocalGraphicsEnvironment()
        .screenDevices
        .asSequence()
        .map { it.defaultConfiguration }
        .firstOrNull { it.bounds.contains(anchor) }
        ?: GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration

private fun positionTrayPanel(window: java.awt.Window, anchor: Point) {
    val configuration = runCatching { screenConfigurationAt(anchor) }.getOrElse { window.graphicsConfiguration }
    val location = trayPanelLocation(
        anchor = anchor,
        screenBounds = configuration.bounds,
        screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(configuration),
        panelSize = window.size,
    )
    window.setLocation(location)
}
