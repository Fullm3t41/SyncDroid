package com.synctosh.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.synctosh.app.platform.AppPreferences
import com.synctosh.app.platform.MacDeviceName
import com.synctosh.app.mesh.MeshRuntime
import com.synctosh.app.mesh.SUPPORTED_DISCOVERY_INTERVALS
import com.synctosh.app.mesh.SUPPORTED_DISCOVERY_WINDOWS
import com.synctosh.app.mesh.discoveryIntervalLabel
import com.synctosh.app.mesh.discoveryWindowLabel
import com.synctosh.app.ui.SyncToshApp

fun main() = application {
    val preferences = remember { AppPreferences() }
    val meshRuntime = remember {
        MeshRuntime(preferences, deviceName = { preferences.deviceName ?: MacDeviceName.current() })
    }
    val meshState by meshRuntime.state.collectAsState()
    var windowVisible by remember { mutableStateOf(true) }
    var discoveryInterval by remember { mutableIntStateOf(preferences.discoveryIntervalMinutes) }
    var discoveryWindow by remember { mutableLongStateOf(preferences.discoveryWindowSeconds) }
    val windowState = rememberWindowState(
        position = WindowPosition.Aligned(androidx.compose.ui.Alignment.Center),
        width = preferences.windowWidth.dp,
        height = preferences.windowHeight.dp,
    )
    val appIcon = painterResource("icons/synctosh.png")

    fun saveWindowState() {
        preferences.windowWidth = windowState.size.width.value
        preferences.windowHeight = windowState.size.height.value
    }

    fun showWindow() {
        windowVisible = true
        meshRuntime.setWindowForeground(true)
    }

    fun closeToNotificationBar() {
        saveWindowState()
        windowVisible = false
        meshRuntime.setWindowForeground(false)
    }

    fun updateDiscoveryInterval(minutes: Int) {
        discoveryInterval = minutes
        preferences.discoveryIntervalMinutes = minutes
        meshRuntime.discoveryScheduleChanged()
    }

    fun updateDiscoveryWindow(seconds: Long) {
        discoveryWindow = seconds
        preferences.discoveryWindowSeconds = seconds
        meshRuntime.discoveryScheduleChanged()
    }

    Tray(
        icon = appIcon,
        tooltip = "SyncTosh · ${meshState.status}",
        onAction = ::showWindow,
        menu = {
            Item("Open SyncTosh", onClick = ::showWindow)
            Separator()
            Item("Status: ${meshState.status}", enabled = false) {}
            Separator()
            val onlinePeers = meshState.peers.count { it.online }
            Item("Devices · $onlinePeers/${meshState.peers.size} online", enabled = false) {}
            if (meshState.profile == null) {
                Item("No mesh connected", enabled = false) {}
            } else if (meshState.peers.isEmpty()) {
                Item("No other mesh devices", enabled = false) {}
            } else {
                meshState.peers
                    .sortedWith(
                        compareByDescending<com.synctosh.app.model.MeshPeer> { it.online }
                            .thenBy { it.name.lowercase() },
                    )
                    .forEach { peer ->
                        Item(
                            text = if (peer.online) "🟢 ${peer.name}" else "● ${peer.name}",
                            enabled = peer.online,
                            onClick = ::showWindow,
                        )
                    }
            }
            Separator()
            Menu("Sync interval · ${discoveryIntervalLabel(discoveryInterval)}") {
                SUPPORTED_DISCOVERY_INTERVALS.forEach { minutes ->
                    RadioButtonItem(
                        text = discoveryIntervalLabel(minutes),
                        selected = discoveryInterval == minutes,
                        onClick = { updateDiscoveryInterval(minutes) },
                    )
                }
            }
            Menu("Discovery duration · ${discoveryWindowLabel(discoveryWindow)}") {
                SUPPORTED_DISCOVERY_WINDOWS.forEach { seconds ->
                    RadioButtonItem(
                        text = discoveryWindowLabel(seconds),
                        selected = discoveryWindow == seconds,
                        onClick = { updateDiscoveryWindow(seconds) },
                    )
                }
            }
            Separator()
            Item("Quit") {
                saveWindowState()
                meshRuntime.close()
                exitApplication()
            }
        },
    )

    if (windowVisible) {
        Window(
            title = "SyncTosh",
            icon = appIcon,
            state = windowState,
            onCloseRequest = ::closeToNotificationBar,
        ) {
            SyncToshApp(
                preferences = preferences,
                runtime = meshRuntime,
                discoveryInterval = discoveryInterval,
                discoveryWindow = discoveryWindow,
                onDiscoveryIntervalChanged = ::updateDiscoveryInterval,
                onDiscoveryWindowChanged = ::updateDiscoveryWindow,
                onCloseToNotificationBar = ::closeToNotificationBar,
            )
        }
    }
}
