package com.synctosh.app.mesh

import com.syncdroid.shared.protocol.PairingCompletionMessage
import com.synctosh.app.model.MeshPeer
import com.synctosh.app.platform.AppPreferences
import java.io.Closeable
import java.nio.file.Path
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

data class VisiblePairingOffer(val code: String, val expiresAtMillis: Long)

data class MeshRuntimeState(
    val localDeviceId: String = "",
    val profile: MeshProfile? = null,
    val peers: List<MeshPeer> = emptyList(),
    val folders: List<MeshFolder> = emptyList(),
    val chatMessages: List<MeshChatMessage> = emptyList(),
    val fileHistory: List<FileHistoryEvent> = emptyList(),
    val status: String = "Ready to connect",
    val pairingOffer: VisiblePairingOffer? = null,
    val busy: Boolean = false,
    val attemptsRemaining: Int = 5,
    val error: String? = null,
)

class MeshRuntime(
    private val preferences: AppPreferences,
    private val deviceName: () -> String,
    private val store: MeshStore = MeshStore(),
    private val identity: MacDeviceIdentity = MacDeviceIdentity(),
) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lanDiscovery = PairingLanDiscovery(identity.deviceId)
    private val bonjour = BonjourDiscovery(identity.deviceId)
    private val mutableState = MutableStateFlow(MeshRuntimeState())
    val state: StateFlow<MeshRuntimeState> = mutableState.asStateFlow()
    private var pairingServer: MeshPeerServer? = null
    private var meshServer: MeshPeerServer? = null
    private val activeSessions = ConcurrentHashMap.newKeySet<String>()
    private val connectionJobs = mutableMapOf<String, Job>()
    private val syncMutex = Mutex()
    private val fileHistory = FileHistoryRepository(store, identity.deviceId)
    private var expiryJob: Job? = null
    private var backgroundScheduleJob: Job? = null
    @Volatile private var windowForeground = true
    @Volatile private var discoveryActive = true

    init {
        fileHistory.cleanupExpired()
        refresh("Ready to connect")
        store.profile()?.let(::startMeshNetworking)
        scope.launch {
            combine(bonjour.peers, bonjour.pairingOffers, lanDiscovery.offers) { peers, _, _ -> peers }
                .collect { peers ->
                    refresh(mutableState.value.status)
                    if (!discoveryActive) return@collect
                    val profile = store.profile() ?: return@collect
                    connectToAvailablePeers(profile, peers.values, initiatorOrdering = true)
                }
        }
    }

    fun setWindowForeground(foreground: Boolean) {
        if (windowForeground == foreground) return
        windowForeground = foreground
        if (foreground) {
            backgroundScheduleJob?.cancel()
            backgroundScheduleJob = null
            scope.launch {
                setDiscoveryActive(true)
                val profile = store.profile()
                refresh(if (profile == null) "Ready to connect" else "Discovery active while SyncTosh is open")
                if (profile != null) connectToAvailablePeers(profile, bonjour.peers.value.values, initiatorOrdering = false)
            }
        } else {
            restartBackgroundSchedule()
        }
    }

    fun discoveryScheduleChanged() {
        if (!windowForeground) restartBackgroundSchedule()
    }

    fun createMesh(groupName: String) = scope.launch {
        updateBusy("Creating encrypted mesh identity…")
        runCatching { store.createMesh(groupName, deviceName(), identity) }
            .onSuccess {
                startMeshNetworking(it)
                refresh("Mesh ready · add another device when you’re ready")
            }
            .onFailure(::report)
    }

    fun createPairingOffer() = scope.launch {
        val profile = store.profile()
        if (profile == null) {
            report(IllegalStateException("Start a mesh before adding another device"))
            return@launch
        }
        updateBusy("Opening a secure pairing offer…")
        runCatching {
            stopPairingOffer()
            val offer = PairingCodeOffer.create()
            val attempts = AtomicInteger(0)
            val server = MeshPeerServer(DeviceTlsContext(identity, allowUnknownPeer = true)) { connection ->
                require(!offer.expired() && attempts.incrementAndGet() <= 5) { "Pairing offer expired" }
                val handshake = PairingHandshake(
                    PairingRole.Inviter,
                    offer.invitationId,
                    offer.code,
                    PairingIdentity.from(identity, deviceName()),
                )
                val result = PairingConnectionProtocol(connection, handshake).run()
                completeInvitation(profile, connection, result)
            }
            val port = server.start()
            pairingServer = server
            lanDiscovery.advertise(port, offer.invitationId)
            bonjour.advertisePairing(port, offer.invitationId)
            mutableState.value = mutableState.value.copy(
                status = "Pairing code ready",
                pairingOffer = VisiblePairingOffer(offer.code, offer.expiresAtMillis),
                busy = false,
                error = null,
            )
            expiryJob = scope.launch {
                delay((offer.expiresAtMillis - System.currentTimeMillis()).coerceAtLeast(0))
                stopPairingOffer()
                refresh("Pairing code expired")
            }
        }.onFailure(::report)
    }

    fun joinMesh(code: String) = scope.launch {
        if (!code.matches(Regex("\\d{6}"))) {
            report(IllegalArgumentException("Enter all six digits")); return@launch
        }
        val limiter = preferences.pairingAttemptState().normalized()
        if (limiter.locked) {
            mutableState.value = mutableState.value.copy(
                attemptsRemaining = 0,
                error = "0 attempts remaining.",
            )
            return@launch
        }
        updateBusy("Searching this Wi-Fi for a matching pairing offer…")
        runCatching {
            val offers = withTimeout(12_000) {
                while (isActive) {
                    val found = (bonjour.pairingOffers.value + lanDiscovery.offers.value).values.distinctBy {
                        Triple(it.invitationId, it.address.hostAddress, it.port)
                    }
                    if (found.isNotEmpty()) return@withTimeout found
                    delay(150)
                }
                emptyList()
            }
            var lastError: Throwable? = null
            for (offer in offers) {
                val result = runCatching { withTimeout(35_000) { joinOffer(offer, code) } }
                if (result.isSuccess) {
                    preferences.clearPairingAttempts()
                    store.profile()?.let(::startMeshNetworking)
                    refresh("Joined ${store.profile()?.groupName.orEmpty()}")
                    return@launch
                }
                lastError = result.exceptionOrNull()
            }
            throw IllegalArgumentException("The code did not match a nearby pairing offer", lastError)
        }.onFailure {
            val next = preferences.recordPairingFailure()
            mutableState.value = mutableState.value.copy(
                busy = false,
                attemptsRemaining = next.attemptsRemaining,
                error = "${next.attemptsRemaining} attempts remaining.",
            )
        }
    }

    fun dismissError() { mutableState.value = mutableState.value.copy(error = null) }

    fun configureFolder(folderId: String, localPath: Path) = scope.launch {
        runCatching {
            store.configureFolder(folderId, identity.deviceId, localPath)
            refresh("Folder configured")
            store.profile()?.let { connectToAvailablePeers(it, bonjour.peers.value.values, initiatorOrdering = false) }
        }.onFailure(::report)
    }

    fun syncNow() = scope.launch {
        val profile = store.profile() ?: return@launch
        updateBusy("Looking for trusted devices…")
        connectToAvailablePeers(profile, bonjour.peers.value.values, initiatorOrdering = false)
        if (bonjour.peers.value.isEmpty()) refresh("No trusted devices are currently online")
    }

    fun sendChat(body: String) = scope.launch {
        val profile = store.profile()
        if (profile == null) {
            report(IllegalStateException("Join a mesh before sending a message"))
            return@launch
        }
        runCatching {
            val message = MeshChatMessage.create(profile.groupId, body, identity)
            check(store.applyChat(message)) { "This message is already in the mesh" }
            refresh("Message ready to sync")
            connectToAvailablePeers(profile, bonjour.peers.value.values, initiatorOrdering = false)
        }.onFailure(::report)
    }

    fun recoverFile(eventId: String) = scope.launch {
        val profile = store.profile() ?: return@launch
        updateBusy("Recovering file…")
        runCatching {
            fileHistory.recover(eventId, profile)
            FileSyncEngine(store, identity, profile).scanConfiguredFolders(recordHistory = false)
            refresh("File recovered · ready to sync")
            connectToAvailablePeers(profile, bonjour.peers.value.values, initiatorOrdering = false)
        }.onFailure(::report)
    }

    fun declineFolder(folderId: String) = scope.launch {
        runCatching {
            store.declineFolder(folderId, identity.deviceId)
            refresh("Folder declined on this Mac")
        }.onFailure(::report)
    }

    private suspend fun completeInvitation(
        profile: MeshProfile,
        connection: AuthenticatedPeerConnection,
        pairing: PairingResult,
    ) {
        val parents = store.membershipEvents(profile.groupId)
        val version = parents.fold(VersionVector()) { merged, event -> merged.merge(event.version) }.increment(identity.deviceId)
        val add = MembershipEvent.createAddDevice(
            profile.groupId,
            pairing.remoteIdentity.displayName,
            pairing.remoteIdentity.decodePublicKey(),
            identity,
            parents.map { it.eventId },
            version,
        )
        store.applyMembership(profile.groupName, add)
        store.recordTlsKey(profile.groupId, pairing.remoteIdentity.deviceId, connection.peerTlsIdentity.publicKeySpki)
        connection.send(
            PairingCompletionCodec.encode(
                PairingCompletionMessage.Complete(profile.groupId, profile.groupName, MeshWireCodec.encode(store.exportBundle())),
            ),
        )
        require(PairingCompletionCodec.decode(connection.receive()) == PairingCompletionMessage.Ack)
        stopPairingOffer()
        refresh("Paired with ${pairing.remoteIdentity.displayName}")
    }

    private suspend fun joinOffer(offer: PairingOffer, code: String) {
        MeshPeerClient(DeviceTlsContext(identity, allowUnknownPeer = true)).connect(offer.address, offer.port).use { connection ->
            val handshake = PairingHandshake(
                PairingRole.Joiner,
                offer.invitationId,
                code,
                PairingIdentity.from(identity, deviceName()),
            )
            val result = PairingConnectionProtocol(connection, handshake).run()
            require(result.remoteIdentity.deviceId == offer.deviceId) { "Pairing identity differs from discovery" }
            val completion = PairingCompletionCodec.decode(connection.receive()) as? PairingCompletionMessage.Complete
                ?: error("Existing device did not finish pairing")
            val bundle = MeshWireCodec.decode(completion.meshBundle)
            require(bundle.membershipEvents.any { it.subjectDeviceId == identity.deviceId }) {
                "Pairing response did not authorize this Mac"
            }
            val profile = store.importBundle(
                bundle,
                expectedOfferingIdentity = result.remoteIdentity,
                requiredLocalDeviceId = identity.deviceId,
            )
            require(profile.groupId == completion.groupId && profile.groupName == completion.groupName)
            store.recordTlsKey(profile.groupId, result.remoteIdentity.deviceId, connection.peerTlsIdentity.publicKeySpki)
            connection.send(PairingCompletionCodec.encode(PairingCompletionMessage.Ack))
        }
    }

    private fun refresh(status: String) {
        val profile = store.profile()
        val discovered = bonjour.peers.value
        val peers = profile?.let { mesh ->
            store.devices(mesh.groupId)
                .filter { it.trusted && it.deviceId != identity.deviceId }
                .map { device ->
                    val live = discovered[device.deviceId]
                    if (live != null) store.markSeen(mesh.groupId, device.deviceId, live.lastSeenAtMillis)
                    MeshPeer(
                        device.deviceId,
                        device.displayName,
                        live != null,
                        live?.lastSeenAtMillis ?: device.lastSeenAtMillis,
                    )
                }
        }.orEmpty()
        mutableState.value = mutableState.value.copy(
            localDeviceId = identity.deviceId,
            profile = profile,
            peers = peers,
            folders = profile?.let { store.folders(it.groupId, identity.deviceId) }.orEmpty(),
            chatMessages = profile?.let { store.chatMessages(it.groupId) }.orEmpty(),
            fileHistory = store.fileHistory(),
            status = status,
            busy = false,
            attemptsRemaining = preferences.pairingAttemptState().normalized().attemptsRemaining,
            error = null,
        )
    }

    private fun startMeshNetworking(profile: MeshProfile) {
        if (meshServer != null) return
        val server = MeshPeerServer(DeviceTlsContext(identity, allowUnknownPeer = true)) { connection ->
            if (!discoveryActive) return@MeshPeerServer
            runStableSession(connection, profile)
        }
        val port = server.start()
        meshServer = server
        bonjour.advertiseMesh(port)
    }

    private suspend fun runStableSession(connection: AuthenticatedPeerConnection, profile: MeshProfile) {
        val remoteId = StablePeerAuthenticator(store, identity, profile.groupId).authenticate(connection)
        if (!activeSessions.add(remoteId)) return
        try {
            syncMutex.withLock {
                var transferred = 0L
                val startedAt = System.nanoTime()
                mutableState.value = mutableState.value.copy(status = "Scanning configured folders…")
                MeshFileSyncSession(store, identity, profile) { bytes ->
                    transferred += bytes
                    val seconds = ((System.nanoTime() - startedAt) / 1_000_000_000.0).coerceAtLeast(0.1)
                    mutableState.value = mutableState.value.copy(
                        status = "Syncing files · ${formatTransferRate((transferred / seconds).toLong())}",
                    )
                }.run(connection, remoteId)
                val conflicts = store.unresolvedConflicts().size
                refresh(if (conflicts == 0) "Files synced" else "$conflicts file conflict${if (conflicts == 1) "" else "s"} need review")
            }
        } finally {
            activeSessions.remove(remoteId)
        }
    }

    private fun connectToAvailablePeers(
        profile: MeshProfile,
        peers: Collection<DiscoveredPeer>,
        initiatorOrdering: Boolean,
    ) {
        if (!discoveryActive) return
        peers.forEach { peer ->
            if (peer.protocolMajor != 1 || (initiatorOrdering && identity.deviceId >= peer.deviceId)) return@forEach
            if (store.devices(profile.groupId).none { it.trusted && it.deviceId == peer.deviceId }) return@forEach
            if (connectionJobs[peer.deviceId]?.isActive == true || peer.deviceId in activeSessions) return@forEach
            connectionJobs[peer.deviceId] = scope.launch {
                runCatching {
                    MeshPeerClient(DeviceTlsContext(identity, allowUnknownPeer = true))
                        .connect(peer.address, peer.port)
                        .use { runStableSession(it, profile) }
                }.onFailure { reportSessionFailure(peer.deviceId, it) }
            }
        }
    }

    private fun reportSessionFailure(peerId: String, error: Throwable) {
        activeSessions.remove(peerId)
        mutableState.value = mutableState.value.copy(
            status = "Waiting for trusted peers",
            error = error.message?.takeIf { it.isNotBlank() },
        )
    }

    private fun updateBusy(status: String) {
        mutableState.value = mutableState.value.copy(status = status, busy = true, error = null)
    }

    private fun report(error: Throwable) {
        mutableState.value = mutableState.value.copy(
            busy = false,
            error = error.message ?: "Mesh operation failed",
        )
    }

    private fun stopPairingOffer() {
        expiryJob?.cancel(); expiryJob = null
        pairingServer?.close(); pairingServer = null
        lanDiscovery.stopAdvertising(); bonjour.stopPairingAdvertisement()
        mutableState.value = mutableState.value.copy(pairingOffer = null)
    }

    private fun restartBackgroundSchedule() {
        backgroundScheduleJob?.cancel()
        backgroundScheduleJob = scope.launch {
            while (isActive && !windowForeground) {
                val now = LocalDateTime.now()
                val window = currentOrNextDiscoveryWindow(
                    now,
                    preferences.discoveryIntervalMinutes.coerceAtLeast(1),
                    preferences.discoveryWindowSeconds.coerceAtLeast(1),
                )
                val zone = ZoneId.systemDefault()
                val startMillis = window.start.atZone(zone).toInstant().toEpochMilli()
                val waitForStart = startMillis - System.currentTimeMillis()
                if (waitForStart > 0) {
                    waitForActiveWorkToFinish()
                    if (windowForeground) break
                    setDiscoveryActive(false)
                    refresh("Background sync scheduled · ${BACKGROUND_TIME_FORMAT.format(window.start)}")
                    val remainingToStart = startMillis - System.currentTimeMillis()
                    if (remainingToStart > 0) delay(remainingToStart)
                }
                if (windowForeground) break
                val endMillis = window.end.atZone(zone).toInstant().toEpochMilli()
                if (System.currentTimeMillis() >= endMillis) continue
                setDiscoveryActive(true)
                refresh("Background discovery window open")
                store.profile()?.let { profile ->
                    connectToAvailablePeers(profile, bonjour.peers.value.values, initiatorOrdering = false)
                }
                val remaining = endMillis - System.currentTimeMillis()
                if (remaining > 0) delay(remaining)
                waitForActiveWorkToFinish()
                if (!windowForeground) setDiscoveryActive(false)
            }
        }
    }

    private suspend fun waitForActiveWorkToFinish() {
        while (
            !windowForeground &&
            (activeSessions.isNotEmpty() || syncMutex.isLocked || connectionJobs.values.any(Job::isActive))
        ) delay(500)
    }

    private fun setDiscoveryActive(active: Boolean) {
        if (discoveryActive == active) return
        discoveryActive = active
        bonjour.setMeshEnabled(active)
    }

    override fun close() {
        backgroundScheduleJob?.cancel(); backgroundScheduleJob = null
        stopPairingOffer(); meshServer?.close(); meshServer = null
        connectionJobs.values.forEach(Job::cancel); connectionJobs.clear()
        lanDiscovery.close(); bonjour.close(); store.close(); scope.cancel()
    }
}

private data class PairingCodeOffer(
    val code: String,
    val invitationId: String,
    val expiresAtMillis: Long,
) {
    fun expired() = System.currentTimeMillis() >= expiresAtMillis

    companion object {
        fun create(): PairingCodeOffer {
            val random = SecureRandom()
            return PairingCodeOffer(
                random.nextInt(1_000_000).toString().padStart(6, '0'),
                ByteArray(16).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) },
                System.currentTimeMillis() + 5 * 60 * 1_000,
            )
        }
    }
}

private fun formatTransferRate(bytesPerSecond: Long): String = when {
    bytesPerSecond >= 1024L * 1024L * 1024L -> "%.1f GB/s".format(bytesPerSecond / (1024.0 * 1024 * 1024))
    bytesPerSecond >= 1024L * 1024L -> "%.1f MB/s".format(bytesPerSecond / (1024.0 * 1024))
    bytesPerSecond >= 1024L -> "%.0f KB/s".format(bytesPerSecond / 1024.0)
    else -> "$bytesPerSecond B/s"
}

private val BACKGROUND_TIME_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")
