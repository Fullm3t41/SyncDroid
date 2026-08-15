package com.syncdroid.app.mesh

import android.content.Context
import com.syncdroid.app.cloud.AndroidFolderKeyStore
import com.syncdroid.app.cloud.PairingFolderKeyWrapper
import com.syncdroid.app.cloud.WrappedFolderKeyTransfer
import com.syncdroid.app.data.SyncDroidDatabase
import com.syncdroid.app.sync.VersionVector
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.json.JSONArray

class PairingCoordinator(
    context: Context,
    private val database: SyncDroidDatabase,
    private val identity: AndroidDeviceIdentity,
    private val profileStore: LocalMeshProfileStore,
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val browser = PairingNsd(appContext, identity.deviceId)
    private val lanDiscovery = PairingLanDiscovery(identity.deviceId)
    private val deviceNameStore = LocalDeviceNameStore(appContext)
    private val mutableStatus = MutableStateFlow("Looking for nearby pairing offers…")
    val status: StateFlow<String> = mutableStatus.asStateFlow()
    private var offerNsd: PairingNsd? = null
    private var offerServer: MeshPeerServer? = null

    init {
        browser.discover()
    }

    suspend fun offer(offer: PairingCodeOffer, profile: LocalMeshProfile) {
        offerServer?.close()
        offerNsd?.close()
        val attempts = AtomicInteger(0)
        val tls = DeviceTlsContext.create(identity, emptyList(), allowUnknownPeer = true)
        val server = MeshPeerServer(tls) { connection ->
            val attempt = attempts.incrementAndGet()
            require(!offer.isExpired(System.currentTimeMillis()) && attempt <= offer.maxAttempts) {
                "Pairing offer expired or attempt limit reached"
            }
            val handshake = PairingHandshake(
                PairingRole.Inviter,
                offer.invitationId,
                offer.code,
                PairingIdentity.from(identity, deviceNameStore.load()),
            )
            val result = PairingConnectionProtocol(connection, handshake).run()
            val completion = createCompletion(profile, result)
            connection.send(PairingCompletionCodec.encode(completion))
            require(PairingCompletionCodec.decode(connection.receive()) is PairingCompletionMessage.Ack)
            mutableStatus.value = "Paired with ${result.remoteIdentity.displayName}"
        }
        val port = server.start()
        offerServer = server
        offerNsd = PairingNsd(appContext, identity.deviceId).also { it.advertise(port, offer.invitationId) }
        lanDiscovery.advertise(port, offer.invitationId)
        mutableStatus.value = "Code ready · listening for a nearby device"
    }

    suspend fun join(code: String): LocalMeshProfile {
        require(code.matches(Regex("\\d{6}"))) { "Enter all six digits" }
        mutableStatus.value = "Searching this Wi-Fi for the device showing that code…"
        val offers = withTimeout(DISCOVERY_TIMEOUT_MILLIS) {
            combine(browser.offers, lanDiscovery.offers) { nsdOffers, lanOffers ->
                (nsdOffers + lanOffers).values.toList()
            }.first { it.isNotEmpty() }
        }
        var lastFailure: Throwable? = null
        for (offer in offers) {
            val result = runCatching {
                withTimeout(PAIRING_ATTEMPT_TIMEOUT_MILLIS) { joinOffer(offer, code) }
            }
            result.getOrNull()?.let { return it }
            lastFailure = result.exceptionOrNull()
        }
        throw IllegalArgumentException("The code did not match a nearby pairing offer", lastFailure)
    }

    private suspend fun joinOffer(offer: DiscoveredPairingOffer, code: String): LocalMeshProfile {
        val tls = DeviceTlsContext.create(identity, emptyList(), allowUnknownPeer = true)
        MeshPeerClient(tls).connect(offer.address, offer.port).use { connection ->
            val handshake = PairingHandshake(
                PairingRole.Joiner,
                offer.invitationId,
                code,
                PairingIdentity.from(identity, deviceNameStore.load()),
            )
            val result = PairingConnectionProtocol(connection, handshake).run()
            require(result.remoteIdentity.deviceId == offer.deviceId) { "Pairing identity does not match discovery" }
            val completion = PairingCompletionCodec.decode(connection.receive()) as? PairingCompletionMessage.Complete
                ?: error("Existing device did not finish pairing")
            val bundle = MeshWireCodec.decode(completion.meshBundle)
            require(bundle.membershipEvents.any { it.subjectDeviceId == identity.deviceId }) {
                "Pairing response does not authorize this device"
            }
            MeshReplicationRepository(database, identity).receive(bundle)
            val keyStore = AndroidFolderKeyStore(appContext, database.syncDao())
            completion.folderKeys.forEach { wrapped ->
                val key = PairingFolderKeyWrapper.unwrap(wrapped, result.sessionKey)
                keyStore.import(key.folderId, key.keyId, key.bytes)
            }
            val profile = LocalMeshProfile(completion.groupId, completion.groupName)
            profileStore.save(profile)
            connection.send(PairingCompletionCodec.encode(PairingCompletionMessage.Ack))
            mutableStatus.value = "Joined ${profile.groupName}"
            return profile
        }
    }

    private suspend fun createCompletion(
        profile: LocalMeshProfile,
        pairing: PairingResult,
    ): PairingCompletionMessage.Complete {
        val meshDao = database.meshDao()
        val membership = MeshMembershipRepository(meshDao)
        val parents = meshDao.membershipEvents(profile.groupId)
        val mergedVersion = parents.fold(VersionVector()) { value, event ->
            value.merge(VersionVector.fromJson(event.versionVectorJson))
        }.increment(identity.deviceId)
        val add = MembershipEvent.createAddDevice(
            profile.groupId,
            pairing.remoteIdentity.displayName,
            pairing.remoteIdentity.decodePublicKey(),
            identity,
            parents.map { it.eventId },
            mergedVersion,
        )
        membership.apply(profile.groupName, add).getOrThrow()
        val bundle = MeshWireCodec.encode(MeshReplicationRepository(database, identity).export(profile.groupId, profile.groupName))
        val keyStore = AndroidFolderKeyStore(appContext, database.syncDao())
        val wrappedKeys = database.syncDao().folderKeys().map { entity ->
            PairingFolderKeyWrapper.wrap(keyStore.getOrCreate(entity.folderId), pairing.sessionKey)
        }
        return PairingCompletionMessage.Complete(profile.groupId, profile.groupName, bundle, wrappedKeys)
    }

    override fun close() {
        offerServer?.close()
        offerNsd?.close()
        browser.close()
        lanDiscovery.close()
        offerServer = null
        offerNsd = null
    }

    private companion object {
        const val DISCOVERY_TIMEOUT_MILLIS = 12_000L
        const val PAIRING_ATTEMPT_TIMEOUT_MILLIS = 30_000L
    }
}

sealed interface PairingCompletionMessage {
    data class Complete(
        val groupId: String,
        val groupName: String,
        val meshBundle: ByteArray,
        val folderKeys: List<WrappedFolderKeyTransfer>,
    ) : PairingCompletionMessage
    data object Ack : PairingCompletionMessage
}

object PairingCompletionCodec {
    fun encode(message: PairingCompletionMessage): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(MAGIC)
            when (message) {
                PairingCompletionMessage.Ack -> output.writeByte(ACK)
                is PairingCompletionMessage.Complete -> {
                    output.writeByte(COMPLETE)
                    output.writeString(message.groupId)
                    output.writeString(message.groupName)
                    output.writeData(message.meshBundle)
                    require(message.folderKeys.size <= MAX_KEYS)
                    output.writeInt(message.folderKeys.size)
                    message.folderKeys.forEach { key ->
                        output.writeString(key.folderId)
                        output.writeString(key.keyId)
                        output.writeData(key.nonce)
                        output.writeData(key.ciphertext)
                    }
                }
            }
        }
        bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): PairingCompletionMessage = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        require(input.readInt() == MAGIC)
        val message = when (input.readUnsignedByte()) {
            ACK -> PairingCompletionMessage.Ack
            COMPLETE -> PairingCompletionMessage.Complete(
                input.readString(),
                input.readString(),
                input.readData(),
                List(input.readInt().also { require(it in 0..MAX_KEYS) }) {
                    WrappedFolderKeyTransfer(input.readString(), input.readString(), input.readData(), input.readData())
                },
            )
            else -> error("Unknown pairing completion message")
        }
        require(input.available() == 0)
        message
    }

    private fun DataOutputStream.writeString(value: String) = writeData(value.toByteArray(StandardCharsets.UTF_8))
    private fun DataInputStream.readString() = String(readData(), StandardCharsets.UTF_8)
    private fun DataOutputStream.writeData(value: ByteArray) { require(value.size <= MAX_DATA); writeInt(value.size); write(value) }
    private fun DataInputStream.readData() = ByteArray(readInt().also { require(it in 0..MAX_DATA) }).also(::readFully)
    private const val MAGIC = 0x53445043
    private const val COMPLETE = 1
    private const val ACK = 2
    private const val MAX_KEYS = 10_000
    private const val MAX_DATA = 16 * 1024 * 1024
}
