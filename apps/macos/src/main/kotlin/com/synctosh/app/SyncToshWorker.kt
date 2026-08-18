package com.synctosh.app

import com.syncdroid.shared.update.LastUpdateCheckStore
import com.syncdroid.shared.update.ReleaseUpdateService
import com.syncdroid.shared.update.UpdatePlatform
import com.synctosh.app.mesh.MeshRuntime
import com.synctosh.app.mesh.MeshRuntimeState
import com.synctosh.app.mesh.SUPPORTED_DISCOVERY_INTERVALS
import com.synctosh.app.mesh.SUPPORTED_DISCOVERY_WINDOWS
import com.synctosh.app.mesh.discoveryIntervalLabel
import com.synctosh.app.mesh.discoveryWindowLabel
import com.synctosh.app.platform.AppPreferences
import com.synctosh.app.platform.MacAppPaths
import com.synctosh.app.platform.MacDeviceName
import com.synctosh.app.platform.UpdateConfig
import java.awt.CheckboxMenuItem
import java.awt.Desktop
import java.awt.EventQueue
import java.awt.Menu
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.desktop.AppReopenedListener
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import javax.imageio.ImageIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal class SyncToshWorker(private val arguments: Array<String>) : Closeable {
    private val preferences = AppPreferences()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stopped = AtomicBoolean(false)
    private val uiTransition = AtomicBoolean(false)
    private val finished = CountDownLatch(1)
    private val backgroundRequested = "--background" in arguments
    private val updateService = ReleaseUpdateService(
        currentVersion = UpdateConfig.CURRENT_VERSION,
        platform = UpdatePlatform.MacOsArm64,
        cacheDirectory = MacAppPaths.updates,
        lastCheck = { preferences.lastUpdateCheckMillis },
        lastCheckStore = LastUpdateCheckStore { preferences.lastUpdateCheckMillis = it },
    )
    private var controlServer: WorkerControlServer? = null
    private var runtime: MeshRuntime? = null
    private var runtimeStateJob: Job? = null
    private var latestState = MeshRuntimeState(status = "Starting background service…")
    private var trayIcon: TrayIcon? = null
    private var uiProcess: Process? = null

    fun run() {
        val existing = WorkerEndpoint.load()
        if (existing?.send(WorkerCommand.PING) == true) {
            if (!backgroundRequested) existing.send(WorkerCommand.SHOW)
            return
        }
        existing?.deleteIfCurrent()

        val server = WorkerControlServer(::handleCommand)
        controlServer = server
        server.endpoint.save()
        installTray()
        scope.launch { updateService.runDailyChecks() }

        if (backgroundRequested && !preferences.noBackgroundService) startBackgroundRuntime()
        else showUi()

        Runtime.getRuntime().addShutdownHook(Thread { close() })
        finished.await()
        kotlin.system.exitProcess(0)
    }

    @Synchronized
    private fun handleCommand(command: WorkerCommand) {
        when (command) {
            WorkerCommand.PING, WorkerCommand.UI_STARTED -> Unit
            WorkerCommand.SHOW -> showUi()
            WorkerCommand.UI_CLOSED -> onUiClosed()
            WorkerCommand.QUIT -> close()
        }
    }

    private fun showUi() {
        if (stopped.get()) return
        val activeUi = uiProcess?.takeIf(Process::isAlive)
        if (activeUi != null) {
            activate(activeUi.pid())
            return
        }
        if (!uiTransition.compareAndSet(false, true)) return
        scope.launch {
            try {
                latestState = latestState.copy(status = "Opening SyncTosh…")
                updateTray()
                val oldRuntime = synchronized(this@SyncToshWorker) {
                    runtime.also {
                        runtime = null
                        runtimeStateJob?.cancel()
                        runtimeStateJob = null
                    }
                }
                runCatching { oldRuntime?.closeAfterActiveTransfers() }
                    .onFailure { oldRuntime?.close() }
                if (stopped.get()) return@launch
                val endpoint = checkNotNull(controlServer?.endpoint) { "Background worker is unavailable" }
                val process = launchUiProcess(endpoint)
                synchronized(this@SyncToshWorker) { uiProcess = process }
                latestState = latestState.copy(status = "SyncTosh is open")
                updateTray()
                scope.launch {
                    process.waitFor()
                    synchronized(this@SyncToshWorker) {
                        if (uiProcess === process) onUiClosed()
                    }
                }
            } catch (error: Throwable) {
                latestState = latestState.copy(status = error.message ?: "Could not open SyncTosh")
                updateTray()
                if (!stopped.get()) startBackgroundRuntime()
            } finally {
                uiTransition.set(false)
            }
        }
    }

    @Synchronized
    private fun onUiClosed() {
        uiProcess = null
        uiTransition.set(false)
        if (stopped.get()) return
        if (preferences.noBackgroundService) close()
        else startBackgroundRuntime()
    }

    @Synchronized
    private fun startBackgroundRuntime() {
        if (stopped.get() || runtime != null || uiProcess?.isAlive == true) return
        val next = MeshRuntime(
            preferences,
            deviceName = { preferences.deviceName ?: MacDeviceName.current() },
            updateCache = updateService,
            initiallyForeground = false,
        )
        runtime = next
        runtimeStateJob = scope.launch {
            next.state.collectLatest { state ->
                latestState = state
                updateTray()
            }
        }
    }

    private fun installTray() {
        if (!SystemTray.isSupported()) return
        EventQueue.invokeLater {
            if (Desktop.isDesktopSupported()) {
                val desktop = Desktop.getDesktop()
                runCatching { desktop.addAppEventListener(AppReopenedListener { showUi() }) }
                runCatching {
                    desktop.setQuitHandler { _, response ->
                        close()
                        response.performQuit()
                    }
                }
            }
            val image = javaClass.classLoader.getResourceAsStream("icons/synctosh-tray.png")?.use(ImageIO::read)
                ?: return@invokeLater
            trayIcon = TrayIcon(image, "SyncTosh").apply {
                isImageAutoSize = true
                addActionListener { showUi() }
                popupMenu = trayMenu()
                SystemTray.getSystemTray().add(this)
            }
        }
    }

    private fun updateTray() {
        EventQueue.invokeLater {
            trayIcon?.apply {
                toolTip = "SyncTosh · ${latestState.status}"
                popupMenu = trayMenu()
            }
        }
    }

    private fun trayMenu(): PopupMenu = PopupMenu().apply {
        add(MenuItem("Open SyncTosh").apply { addActionListener { showUi() } })
        addSeparator()
        add(MenuItem("Status: ${latestState.status}").apply { isEnabled = false })
        addSeparator()
        val peers = latestState.peers
        add(MenuItem("Devices · ${peers.count { it.online }}/${peers.size} online").apply { isEnabled = false })
        if (latestState.profile == null) {
            add(MenuItem("No mesh connected").apply { isEnabled = false })
        } else if (peers.isEmpty()) {
            add(MenuItem("No other mesh devices").apply { isEnabled = false })
        } else {
            peers.sortedWith(compareByDescending<com.synctosh.app.model.MeshPeer> { it.online }.thenBy { it.name.lowercase() })
                .forEach { peer ->
                    add(MenuItem("${if (peer.online) "🟢" else "●"} ${peer.name}").apply {
                        isEnabled = peer.online
                        addActionListener { showUi() }
                    })
                }
        }
        addSeparator()
        add(CheckboxMenuItem("Always-on discovery", preferences.alwaysOnDiscovery).apply {
            addItemListener {
                preferences.alwaysOnDiscovery = state
                runtime?.discoveryScheduleChanged()
                updateTray()
            }
        })
        add(Menu("Sync interval · ${discoveryIntervalLabel(preferences.discoveryIntervalMinutes)}").apply {
            isEnabled = !preferences.alwaysOnDiscovery
            SUPPORTED_DISCOVERY_INTERVALS.forEach { minutes ->
                add(CheckboxMenuItem(discoveryIntervalLabel(minutes), preferences.discoveryIntervalMinutes == minutes).apply {
                    addItemListener {
                        preferences.discoveryIntervalMinutes = minutes
                        runtime?.discoveryScheduleChanged()
                        updateTray()
                    }
                })
            }
        })
        add(Menu("Discovery duration · ${discoveryWindowLabel(preferences.discoveryWindowSeconds)}").apply {
            isEnabled = !preferences.alwaysOnDiscovery
            SUPPORTED_DISCOVERY_WINDOWS.forEach { seconds ->
                add(CheckboxMenuItem(discoveryWindowLabel(seconds), preferences.discoveryWindowSeconds == seconds).apply {
                    addItemListener {
                        preferences.discoveryWindowSeconds = seconds
                        runtime?.discoveryScheduleChanged()
                        updateTray()
                    }
                })
            }
        })
        addSeparator()
        add(MenuItem("Quit").apply { addActionListener { close() } })
    }

    private fun launchUiProcess(endpoint: WorkerEndpoint): Process {
        Files.createDirectories(MacAppPaths.workerLog.parent)
        return ProcessBuilder(AppProcessLauncher.command(UI_ARGUMENT))
            .apply {
                environment()[WORKER_PORT_ENV] = endpoint.port.toString()
                environment()[WORKER_TOKEN_ENV] = endpoint.token
            }
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(MacAppPaths.workerLog.toFile()))
            .start()
    }

    private fun activate(pid: Long) {
        runCatching {
            ProcessBuilder(
                "/usr/bin/osascript",
                "-e",
                "tell application \"System Events\" to set frontmost of first process whose unix id is $pid to true",
            ).start()
        }
    }

    @Synchronized
    override fun close() {
        if (!stopped.compareAndSet(false, true)) return
        try {
            runCatching { uiProcess?.destroy() }
            uiProcess = null
            runtimeStateJob?.cancel()
            runtimeStateJob = null
            runCatching { runtime?.close() }
            runtime = null
            trayIcon?.let { icon -> EventQueue.invokeLater { runCatching { SystemTray.getSystemTray().remove(icon) } } }
            trayIcon = null
            runCatching { controlServer?.endpoint?.deleteIfCurrent() }
            runCatching { controlServer?.close() }
            controlServer = null
            scope.cancel()
        } finally {
            finished.countDown()
        }
    }
}

internal object AppProcessLauncher {
    fun command(vararg arguments: String): List<String> {
        val packaged = System.getProperty("jpackage.app-path")
            ?.takeIf(String::isNotBlank)
            ?.let(Path::of)
            ?: ProcessHandle.current().info().command().orElse(null)?.let(Path::of)
        if (packaged != null && packaged.fileName.toString() == "SyncTosh") {
            return listOf(packaged.toString()) + arguments
        }
        val java = Path.of(System.getProperty("java.home"), "bin", "java")
        return listOf(java.toString(), "-cp", System.getProperty("java.class.path"), "com.synctosh.app.MainKt") + arguments
    }
}
