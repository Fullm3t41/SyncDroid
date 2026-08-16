package com.synctosh.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
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
import com.synctosh.app.ui.SyncToshApp

fun main() = application {
    val preferences = remember { AppPreferences() }
    val meshRuntime = remember {
        MeshRuntime(preferences, deviceName = { preferences.deviceName ?: MacDeviceName.current() })
    }
    val meshState by meshRuntime.state.collectAsState()
    var windowVisible by remember { mutableStateOf(true) }
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

    Tray(
        icon = appIcon,
        tooltip = "SyncTosh · ${meshState.status}",
        onAction = ::showWindow,
        menu = {
            Item("Open SyncTosh", onClick = ::showWindow)
            Separator()
            Item("Status: ${meshState.status}", enabled = false) {}
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
            SyncToshApp(preferences, meshRuntime, ::closeToNotificationBar)
        }
    }
}
