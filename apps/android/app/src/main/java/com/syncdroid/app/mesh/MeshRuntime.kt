package com.syncdroid.app.mesh

import android.content.Context
import android.util.Log
import com.syncdroid.app.data.SyncDroidDatabase
import com.syncdroid.app.data.LocalFolderBindingEntity
import com.syncdroid.app.scheduling.millisUntilNextRendezvous
import com.syncdroid.app.sync.TransferRateSampler
import com.syncdroid.app.storage.StorageSyncWarning
import com.syncdroid.shared.sync.MeshRouteCandidate
import com.syncdroid.shared.sync.initialMeshFanoutTargets
import com.syncdroid.shared.sync.propagationFanoutTargets
import java.io.Closeable
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext

sealed interface MeshRuntimeEvent {
    data class DiscoveryWaiting(val nextWindowAtMillis: Long) : MeshRuntimeEvent
    data class DiscoveryActive(val windowEndsAtMillis: Long?) : MeshRuntimeEvent
    data class SyncStarted(val peerId: String, val peerName: String) : MeshRuntimeEvent
    data class TransferProgress(
        val peerId: String,
        val peerName: String,
        val bytesPerSecond: Long,
    ) : MeshRuntimeEvent
    data class SyncCompleted(
        val peerId: String,
        val peerName: String,
        val folderIds: Set<String>,
        val storageWarning: StorageSyncWarning? = null,
    ) : MeshRuntimeEvent
    data class SyncFailed(
        val peerId: String,
        val peerName: String,
        val reason: String,
        val storageWarning: StorageSyncWarning? = null,
    ) : MeshRuntimeEvent
    data class ChatMessagesReceived(
        val count: Int,
        val authorName: String,
        val preview: String,
    ) : MeshRuntimeEvent
}

class MeshRuntime(
    context: Context,
    private val database: SyncDroidDatabase,
    private val identity: AndroidDeviceIdentity,
    private val groupId: String,
    private val groupName: String,
    private val rendezvousIntervalMinutes: Int = 5,
    private val scheduledDiscoveryEnabled: Boolean = true,
    private val rendezvousWindowSeconds: Long = 30,
    private val discoverImmediately: Boolean = false,
    private val appInForeground: StateFlow<Boolean>,
    private val onEvent: (MeshRuntimeEvent) -> Unit = {},
) : Closeable {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activePeers = ConcurrentHashMap.newKeySet<String>()
    private val peerJobs = ConcurrentHashMap<String, Job>()
    private val lastSessionAtMillis = ConcurrentHashMap<String, Long>()
    private val propagationSignals = Channel<PropagationSignal>(Channel.BUFFERED)
    private val sessionStateLock = Any()
    private val closeRequested = AtomicBoolean(false)
    private var acceptingDiscoveredSessions = false
    private var server: MeshPeerServer? = null
    private var discovery: MeshNsdDiscovery? = null

    suspend fun start() {
        check(server == null) { "Mesh runtime is already active" }
        val trusted = database.meshDao().trustedDevices(groupId)
        val tls = DeviceTlsContext.create(identity, trusted, allowUnknownPeer = true)
        val peerServer = MeshPeerServer(tls) { connection -> runSessionOnce(connection) }
        val port = peerServer.start()
        server = peerServer
        val client = MeshPeerClient(tls)
        scope.launch {
            var initialDiscoveryPending = discoverImmediately
            appInForeground.collectLatest { foreground ->
                if (shouldRunContinuousDiscovery(foreground, scheduledDiscoveryEnabled)) {
                    initialDiscoveryPending = false
                    onEvent(MeshRuntimeEvent.DiscoveryActive(windowEndsAtMillis = null))
                    runDiscoveryWindow(client, port, null)
                } else {
                    // Background discovery remains aligned to the configured rendezvous grid.
                    // A window is allowed to drain if it already started a sync.
                    if (initialDiscoveryPending) {
                        initialDiscoveryPending = false
                        onEvent(MeshRuntimeEvent.DiscoveryActive(System.currentTimeMillis() + rendezvousWindowSeconds * 1_000))
                        runDiscoveryWindow(client, port, rendezvousWindowSeconds * 1_000)
                    }
                    while (isActive) {
                        val delayMillis = millisUntilNextRendezvous(ZonedDateTime.now(), rendezvousIntervalMinutes)
                        onEvent(MeshRuntimeEvent.DiscoveryWaiting(System.currentTimeMillis() + delayMillis))
                        delay(delayMillis)
                        onEvent(MeshRuntimeEvent.DiscoveryActive(System.currentTimeMillis() + rendezvousWindowSeconds * 1_000))
                        runDiscoveryWindow(client, port, rendezvousWindowSeconds * 1_000)
                    }
                }
            }
        }
    }

    private suspend fun runDiscoveryWindow(client: MeshPeerClient, port: Int, durationMillis: Long?) {
        val nsd = MeshNsdDiscovery(appContext, identity.deviceId)
        val trustedIds = database.meshDao().trustedDevices(groupId).mapTo(mutableSetOf()) { it.deviceId }
        val initialContacted = mutableSetOf<String>()
        synchronized(sessionStateLock) { acceptingDiscoveredSessions = true }
        discovery = nsd
        nsd.start(port)
        val collector = scope.launch {
            nsd.peers.collectLatest { peers ->
                delay(ROUTING_SETTLE_MILLIS)
                val removed = peerJobs.keys - peers.keys
                removed.forEach { id ->
                    if (id !in activePeers) peerJobs.remove(id)?.cancel()
                }
                val eligible = peers.filterValues { it.protocolMajor == 1 && it.deviceId in trustedIds }
                val remainingFanout = (ROUTING_FANOUT - initialContacted.size).coerceAtLeast(0)
                val targets = initialMeshFanoutTargets(identity.deviceId, eligible.keys, ROUTING_FANOUT)
                    .filterNot(initialContacted::contains)
                    .take(remainingFanout)
                targets.forEach { deviceId ->
                    initialContacted += deviceId
                    launchPeerConnection(client, requireNotNull(eligible[deviceId]), retry = true)
                }
            }
        }
        val propagationWorker = scope.launch {
            for (signal in propagationSignals) {
                delay(PROPAGATION_COALESCE_MILLIS)
                val available = nsd.peers.value.filterValues {
                    it.protocolMajor == 1 && it.deviceId in trustedIds
                }
                val targets = propagationFanoutTargets(
                    localDeviceId = identity.deviceId,
                    sourceDeviceId = signal.sourcePeerId,
                    peers = available.keys.map { deviceId ->
                        MeshRouteCandidate(
                            deviceId = deviceId,
                            lastSessionAtMillis = lastSessionAtMillis[deviceId] ?: 0L,
                            active = deviceId in activePeers || peerJobs[deviceId]?.isActive == true,
                        )
                    },
                    maxFanout = ROUTING_FANOUT,
                )
                targets.forEach { deviceId ->
                    available[deviceId]?.let { launchPeerConnection(client, it, retry = false) }
                }
            }
        }
        try {
            if (durationMillis == null) collector.join() else delay(durationMillis)
        } finally {
            // A timed window ending, or the app moving to the background, must not
            // tear down an established transfer. Keep NSD advertised until every
            // active session has drained, then atomically stop admitting new ones.
            withContext(NonCancellable) {
                awaitActiveSyncsBeforeDiscoveryShutdown()
                collector.cancelAndJoin()
                propagationWorker.cancelAndJoin()
                peerJobs.values.forEach(Job::cancel)
                peerJobs.clear()
                nsd.close()
                if (discovery === nsd) discovery = null
            }
        }
    }

    private fun launchPeerConnection(
        client: MeshPeerClient,
        peer: DiscoveredMeshPeer,
        retry: Boolean,
    ) {
        if (peer.deviceId in activePeers || peerJobs[peer.deviceId]?.isActive == true) return
        peerJobs[peer.deviceId] = scope.launch {
            do {
                val connected = runCatching {
                    client.connect(peer.address, peer.port).use { connection ->
                        StablePeerAuthenticator(database, identity, groupId).authenticate(connection)
                        require(connection.peer.deviceId == peer.deviceId) {
                            "NSD identity does not match mesh identity"
                        }
                        runSessionOnce(connection, alreadyAuthenticated = true)
                    }
                }.isSuccess
                if (connected || !retry) break
                delay(CONNECTION_RETRY_MILLIS)
            } while (isActive)
        }
    }

    private suspend fun awaitActiveSyncsBeforeDiscoveryShutdown() {
        while (true) {
            val keepDiscoveryActive = synchronized(sessionStateLock) {
                shouldKeepDiscoveryActiveWhileSyncing(activePeers.size, closeRequested.get()).also { keepActive ->
                    if (!keepActive) acceptingDiscoveredSessions = false
                }
            }
            if (!keepDiscoveryActive) return
            delay(ACTIVE_SYNC_DRAIN_POLL_MILLIS)
        }
    }

    private suspend fun runSessionOnce(
        connection: AuthenticatedPeerConnection,
        alreadyAuthenticated: Boolean = false,
    ) {
        if (!alreadyAuthenticated) StablePeerAuthenticator(database, identity, groupId).authenticate(connection)
        val admitted = synchronized(sessionStateLock) {
            acceptingDiscoveredSessions && activePeers.add(connection.peer.deviceId)
        }
        if (!admitted) return
        val peerName = database.meshDao().getDevice(groupId, connection.peer.deviceId)?.displayName
            ?: connection.peer.deviceId.take(8)
        try {
            onEvent(MeshRuntimeEvent.SyncStarted(connection.peer.deviceId, peerName))
            val rateSampler = TransferRateSampler { bytesPerSecond ->
                onEvent(MeshRuntimeEvent.TransferProgress(
                    connection.peer.deviceId,
                    peerName,
                    bytesPerSecond,
                ))
            }
            val result = MeshSyncSession(
                appContext,
                database,
                identity,
                groupId,
                groupName,
                rateSampler::record,
            ).run(connection)
            lastSessionAtMillis[connection.peer.deviceId] = System.currentTimeMillis()
            if (result.appliedChangeCount > 0 || result.replicatedStateChanged) {
                propagationSignals.trySend(PropagationSignal(connection.peer.deviceId))
            }
            if (result.newChatMessages.isNotEmpty()) {
                val latest = result.newChatMessages.maxWith(
                    compareBy(MeshChatMessage::createdAtMillis, MeshChatMessage::messageId),
                )
                val authorName = database.meshDao().getDevice(groupId, latest.authorDeviceId)?.displayName
                    ?: latest.authorDeviceId.take(8)
                onEvent(MeshRuntimeEvent.ChatMessagesReceived(
                    count = result.newChatMessages.size,
                    authorName = authorName,
                    preview = latest.body,
                ))
            }
            val syncedFolders = database.syncDao().configuredBindings(identity.deviceId, groupId)
                .map(LocalFolderBindingEntity::folderId)
                .filterTo(mutableSetOf()) {
                    it !in result.storageBlockedFolderIds && database.syncDao().unresolvedConflictCount(it) == 0
                }
            onEvent(MeshRuntimeEvent.SyncCompleted(
                connection.peer.deviceId,
                peerName,
                syncedFolders,
                result.storageWarning,
            ))
        } catch (error: Throwable) {
            Log.e(TAG, "Mesh sync with $peerName failed", error)
            onEvent(MeshRuntimeEvent.SyncFailed(
                connection.peer.deviceId,
                peerName,
                error.message ?: "Unknown sync error",
            ))
            throw error
        } finally {
            synchronized(sessionStateLock) { activePeers.remove(connection.peer.deviceId) }
        }
    }

    override fun close() {
        closeRequested.set(true)
        synchronized(sessionStateLock) { acceptingDiscoveredSessions = false }
        discovery?.close()
        server?.close()
        peerJobs.values.forEach(Job::cancel)
        peerJobs.clear()
        discovery = null
        server = null
        scope.cancel()
    }

    private companion object {
        const val ACTIVE_SYNC_DRAIN_POLL_MILLIS = 100L
        const val CONNECTION_RETRY_MILLIS = 5_000L
        const val PROPAGATION_COALESCE_MILLIS = 300L
        const val ROUTING_SETTLE_MILLIS = 750L
        const val ROUTING_FANOUT = 2
        const val TAG = "SyncDroidMesh"
    }
}

private data class PropagationSignal(val sourcePeerId: String)

internal fun shouldRunContinuousDiscovery(appInForeground: Boolean, scheduledDiscoveryEnabled: Boolean): Boolean =
    appInForeground || !scheduledDiscoveryEnabled

internal fun shouldKeepDiscoveryActiveWhileSyncing(activeSyncCount: Int, runtimeClosing: Boolean): Boolean =
    activeSyncCount > 0 && !runtimeClosing
