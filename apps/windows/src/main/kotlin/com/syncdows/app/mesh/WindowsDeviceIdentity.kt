package com.syncdows.app.mesh

import com.syncdows.app.platform.WindowsAppPaths
import java.math.BigInteger
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Security
import java.security.Signature
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.EnumSet
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

/**
 * The long-lived P-256 mesh identity is stored in an owner-only PKCS#12 file.
 * Windows protects the file with the current user's ACL, so SyncDows does not
 * require another password after the user signs in to Windows.
 */
class WindowsDeviceIdentity(
    private val alias: String = IDENTITY_ALIAS,
    private val identityPath: Path = defaultIdentityPath(),
    @Suppress("UNUSED_PARAMETER") legacyKeyStoreFactory: (() -> KeyStore)? = null,
) : DeviceSigner {
    private val entry: KeyStore.PrivateKeyEntry by lazy(::loadOrCreate)

    override val publicKey get() = entry.certificate.publicKey
    private val privateKey get() = entry.privateKey
    val certificate: X509Certificate get() = entry.certificate as X509Certificate
    override val deviceId get() = deviceIdFor(publicKey)
    val fingerprint get() = fingerprintFor(publicKey)

    override fun sign(payload: ByteArray): ByteArray = Signature.getInstance("SHA256withECDSA").run {
        initSign(privateKey)
        update(payload)
        sign()
    }

    private fun loadOrCreate(): KeyStore.PrivateKeyEntry {
        if (Files.exists(identityPath)) return loadFileEntry()
        require(!Files.isSymbolicLink(identityPath)) { "SyncDows identity path must not be a symbolic link" }

        val pair = KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
            generateKeyPair()
        }
        val created = KeyStore.PrivateKeyEntry(pair.private, arrayOf(selfSignedCertificate(pair)))
        saveFileEntry(created)
        return loadFileEntry()
    }

    private fun loadFileEntry(): KeyStore.PrivateKeyEntry {
        require(Files.isRegularFile(identityPath) && !Files.isSymbolicLink(identityPath)) {
            "SyncDows identity is not a regular file"
        }
        enforceOwnerOnlyPermissions(identityPath)
        val store = KeyStore.getInstance("PKCS12").apply {
            Files.newInputStream(identityPath).use { load(it, FILE_PASSWORD) }
        }
        return requireNotNull(validatedEntry(store, FILE_PASSWORD)) {
            "SyncDows identity file is invalid or does not contain its private key"
        }
    }

    private fun saveFileEntry(value: KeyStore.PrivateKeyEntry) {
        val parent = requireNotNull(identityPath.parent) { "SyncDows identity needs a parent directory" }
        Files.createDirectories(parent)
        val temporary = Files.createTempFile(parent, ".syncdows-identity-", ".tmp")
        try {
            enforceOwnerOnlyPermissions(temporary)
            val store = KeyStore.getInstance("PKCS12").apply {
                load(null, FILE_PASSWORD)
                setKeyEntry(alias, value.privateKey, FILE_PASSWORD, value.certificateChain)
            }
            Files.newOutputStream(temporary).use { store.store(it, FILE_PASSWORD) }
            enforceOwnerOnlyPermissions(temporary)
            moveAtomically(temporary, identityPath)
            enforceOwnerOnlyPermissions(identityPath)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun moveAtomically(source: Path, destination: Path) {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, destination)
        }
    }

    private fun validatedEntry(store: KeyStore, password: CharArray): KeyStore.PrivateKeyEntry? {
        val key = runCatching { store.getKey(alias, password) as? PrivateKey }.getOrNull() ?: return null
        val certificate = store.getCertificate(alias) ?: return null
        val challenge = "syncdows-file-identity-check-v1".toByteArray()
        val signature = runCatching {
            Signature.getInstance("SHA256withECDSA").run { initSign(key); update(challenge); sign() }
        }.getOrNull() ?: return null
        val matches = runCatching {
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(certificate.publicKey); update(challenge); verify(signature)
            }
        }.getOrDefault(false)
        if (!matches) return null
        return KeyStore.PrivateKeyEntry(key, arrayOf(certificate))
    }

    private fun selfSignedCertificate(pair: KeyPair): X509Certificate {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
        val now = Instant.now()
        val subject = X500Name("CN=SyncDows ${deviceIdFor(pair.public)}")
        val builder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger(160, SecureRandom()).abs(),
            Date.from(now.minus(1, ChronoUnit.DAYS)),
            Date.from(now.plus(20 * 365L, ChronoUnit.DAYS)),
            subject,
            pair.public,
        )
        val signer = JcaContentSignerBuilder("SHA256withECDSA")
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .build(pair.private)
        return JcaX509CertificateConverter()
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .getCertificate(builder.build(signer))
            .also { it.verify(pair.public) }
    }

    internal fun privateKey(): PrivateKey = privateKey

    companion object {
        private const val IDENTITY_ALIAS = "SyncDows Device Identity v1"
        private val FILE_PASSWORD = "SyncDows-Local-Identity-v1".toCharArray()
        private val OWNER_READ_WRITE = setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)

        fun defaultIdentityPath(): Path = WindowsAppPaths.identity

        private fun enforceOwnerOnlyPermissions(path: Path) {
            val windows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
            runCatching {
                if (windows) {
                    val view = requireNotNull(
                        Files.getFileAttributeView(
                            path,
                            AclFileAttributeView::class.java,
                            LinkOption.NOFOLLOW_LINKS,
                        ),
                    ) { "Windows ACLs are unavailable" }
                    val ownerOnly = AclEntry.newBuilder()
                        .setType(AclEntryType.ALLOW)
                        .setPrincipal(view.owner)
                        .setPermissions(EnumSet.allOf(AclEntryPermission::class.java))
                        .build()
                    view.acl = listOf(ownerOnly)
                } else {
                    Files.setPosixFilePermissions(path, OWNER_READ_WRITE)
                }
            }.getOrElse { error ->
                throw IllegalStateException("Could not secure SyncDows identity permissions", error)
            }
        }
    }
}
