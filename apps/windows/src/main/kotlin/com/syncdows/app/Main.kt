package com.syncdows.app

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.syncdows.app.mesh.MeshRuntime
import com.syncdows.app.platform.AppPreferences
import com.syncdows.app.platform.SingleInstanceGuard
import com.syncdows.app.platform.StyledSystemTrayIcon
import com.syncdows.app.platform.WindowsDeviceName
import com.syncdows.app.platform.UpdateConfig
import com.syncdows.app.platform.WindowsAppPaths
import com.syncdows.app.platform.WindowsUpdateInstaller
import com.syncdows.app.ui.SyncDowsApp
import com.syncdows.app.ui.TrayPanelWindow
import java.awt.Point
import com.syncdroid.shared.update.LastUpdateCheckStore
import com.syncdroid.shared.update.ReleaseUpdateService
import com.syncdroid.shared.update.UpdatePlatform

fun main(args: Array<String>) {
    val instanceGuard = SingleInstanceGuard.acquire() ?: return
    try {
        runApplication(instanceGuard, backgroundRequested = "--background" in args)
    } finally {
        instanceGuard.close()
    }
}

private fun runApplication(instanceGuard: SingleInstanceGuard, backgroundRequested: Boolean) = application {
    val preferences = remember { AppPreferences() }
    val startHidden = backgroundRequested && !preferences.noBackgroundService
    val updateService = remember {
        ReleaseUpdateService(
            currentVersion = UpdateConfig.CURRENT_VERSION,
            platform = UpdatePlatform.WindowsX64,
            cacheDirectory = WindowsAppPaths.updates,
            lastCheck = { preferences.lastUpdateCheckMillis },
            lastCheckStore = LastUpdateCheckStore { preferences.lastUpdateCheckMillis = it },
        )
    }
    val meshRuntime = remember {
        MeshRuntime(
            preferences,
            deviceName = { preferences.deviceName ?: WindowsDeviceName.current() },
            updateCache = updateService,
        ).also {
            if (startHidden) it.setWindowForeground(false)
        }
    }
    LaunchedEffect(updateService) { updateService.runDailyChecks() }
    val meshState by meshRuntime.state.collectAsState()
    var windowVisible by remember { mutableStateOf(!startHidden) }
    var trayPanelVisible by remember { mutableStateOf(false) }
    var trayPanelAnchor by remember { mutableStateOf(Point()) }
    var discoveryInterval by remember { mutableIntStateOf(preferences.discoveryIntervalMinutes) }
    var discoveryWindow by remember { mutableLongStateOf(preferences.discoveryWindowSeconds) }
    var alwaysOnDiscovery by remember { mutableStateOf(preferences.alwaysOnDiscovery) }
    val windowState = rememberWindowState(
        position = WindowPosition.Aligned(androidx.compose.ui.Alignment.Center),
        width = preferences.windowWidth.dp,
        height = preferences.windowHeight.dp,
    )
    val appIcon = painterResource("syncdows-icon-source.png")

    fun saveWindowState() {
        preferences.windowWidth = windowState.size.width.value
        preferences.windowHeight = windowState.size.height.value
    }

    fun showWindow() {
        trayPanelVisible = false
        windowVisible = true
        meshRuntime.setWindowForeground(true)
    }

    instanceGuard.onActivate(::showWindow)

    fun quitApplication() {
        saveWindowState()
        meshRuntime.close()
        exitApplication()
    }

    fun closeToNotificationBar() {
        if (preferences.noBackgroundService) {
            quitApplication()
        } else {
            saveWindowState()
            windowVisible = false
            meshRuntime.setWindowForeground(false)
        }
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

    fun updateAlwaysOnDiscovery(enabled: Boolean) {
        alwaysOnDiscovery = enabled
        preferences.alwaysOnDiscovery = enabled
        meshRuntime.discoveryScheduleChanged()
    }

    StyledSystemTrayIcon(
        tooltip = "SyncDows \u00B7 ${meshState.status}",
        onMenuRequested = { anchor ->
            trayPanelAnchor = anchor
            trayPanelVisible = !trayPanelVisible
        },
        onOpenRequested = ::showWindow,
    )

    if (trayPanelVisible) {
        TrayPanelWindow(
            anchor = trayPanelAnchor,
            themeMode = preferences.themeMode,
            meshState = meshState,
            discoveryInterval = discoveryInterval,
            discoveryWindow = discoveryWindow,
            alwaysOnDiscovery = alwaysOnDiscovery,
            onOpen = ::showWindow,
            onDiscoveryIntervalChanged = ::updateDiscoveryInterval,
            onDiscoveryWindowChanged = ::updateDiscoveryWindow,
            onAlwaysOnDiscoveryChanged = ::updateAlwaysOnDiscovery,
            onDismiss = { trayPanelVisible = false },
            onQuit = ::quitApplication,
        )
    }

    if (windowVisible) {
        Window(
            title = "SyncDows",
            icon = appIcon,
            state = windowState,
            onCloseRequest = ::closeToNotificationBar,
        ) {
            SyncDowsApp(
                preferences = preferences,
                runtime = meshRuntime,
                discoveryInterval = discoveryInterval,
                discoveryWindow = discoveryWindow,
                alwaysOnDiscovery = alwaysOnDiscovery,
                onDiscoveryIntervalChanged = ::updateDiscoveryInterval,
                onDiscoveryWindowChanged = ::updateDiscoveryWindow,
                onAlwaysOnDiscoveryChanged = ::updateAlwaysOnDiscovery,
                onCloseToNotificationBar = ::closeToNotificationBar,
                updateService = updateService,
                onInstallUpdate = { installer ->
                    WindowsUpdateInstaller.launch(installer)
                    quitApplication()
                },
            )
        }
    }
}
