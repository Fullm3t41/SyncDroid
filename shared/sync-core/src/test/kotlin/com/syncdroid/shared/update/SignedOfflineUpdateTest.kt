package com.syncdroid.shared.update

import com.syncdroid.shared.protocol.MeshSessionMessage
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignedOfflineUpdateTest {
    @Test
    fun importedBundleSeedsManifestAndPlatformAssetWithoutGitHub() = runBlocking {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val publicKey = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        val files = mapOf(
            UpdatePlatform.Android to ("SyncDroid-Mesh-0.2.1-Android.apk" to byteArrayOf(1, 2, 3)),
            UpdatePlatform.MacOsArm64 to ("SyncTosh-0.2.1-macOS-arm64.dmg" to byteArrayOf(4, 5, 6, 7)),
            UpdatePlatform.WindowsX64 to ("SyncDows-0.2.1-Windows-x64.exe" to byteArrayOf(8, 9)),
        )
        val manifest = ReleaseManifest(
            version = "0.2.1",
            publishedAt = "2026-08-16T00:00:00Z",
            notesUrl = "https://example.test/releases/0.2.1",
            assets = files.map { (platform, namedBytes) ->
                ReleaseAsset(
                    platform,
                    namedBytes.first,
                    "https://example.test/${namedBytes.first}",
                    sha256(namedBytes.second),
                    namedBytes.second.size.toLong(),
                )
            },
        ).encode()
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(keyPair.private)
            update(manifest.toByteArray(StandardCharsets.UTF_8))
            Base64.getEncoder().encodeToString(sign())
        }
        val root = Files.createTempDirectory("signed-offline-update")
        try {
            val bundle = root.resolve("SyncDroid-Mesh-0.2.1-offline.sdu")
            ZipOutputStream(Files.newOutputStream(bundle)).use { zip ->
                fun add(name: String, bytes: ByteArray) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
                add("syncdroid-update.properties", manifest.toByteArray(StandardCharsets.UTF_8))
                add(RELEASE_SIGNATURE_FILE, signature.toByteArray(StandardCharsets.UTF_8))
                files.values.forEach { (name, bytes) -> add(name, bytes) }
            }

            val source = service(root.resolve("source"), UpdatePlatform.WindowsX64, publicKey)
            val target = service(root.resolve("target"), UpdatePlatform.Android, publicKey)
            source.importOfflineBundle(bundle)

            val sourceToTarget = Channel<MeshSessionMessage>(Channel.UNLIMITED)
            val targetToSource = Channel<MeshSessionMessage>(Channel.UNLIMITED)
            val sourceJob = async {
                MeshUpdateExchange(source).run("device-b", "device-a", sourceToTarget::send, targetToSource::receive)
            }
            val targetJob = async {
                MeshUpdateExchange(target).run("device-a", "device-b", targetToSource::send, sourceToTarget::receive)
            }
            sourceJob.await()
            targetJob.await()

            val ready = assertIs<UpdateState.Ready>(target.state.value)
            assertEquals("0.2.1", ready.manifest.version)
            assertEquals(UpdateSource.Mesh, ready.source)
            assertContentEquals(files.getValue(UpdatePlatform.Android).second, ready.installer.readBytes())
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun alteredManifestIsRejected() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val publicKey = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        val original = "signed content"
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(keyPair.private)
            update(original.toByteArray(StandardCharsets.UTF_8))
            Base64.getEncoder().encodeToString(sign())
        }
        assertFailsWith<IllegalArgumentException> {
            SignedReleaseManifest.verify("altered content", signature, publicKey)
        }
    }

    @Test
    fun importedSignedBundleOlderThanTheInstalledAppIsDeleted() = runBlocking {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val publicKey = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        val root = Files.createTempDirectory("outdated-offline-update")
        try {
            val bundle = signedBundle(root, "1.2.3", keyPair)
            val error = assertFailsWith<OutdatedOfflineBundleException> {
                service(root.resolve("cache"), UpdatePlatform.Android, publicKey, currentVersion = "1.2.4")
                    .importOfflineBundle(bundle)
            }
            assertTrue(error.sourceDeleted)
            assertFalse(Files.exists(bundle))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun bundleMatchingTheInstalledVersionRemainsAvailableForSeeding() = runBlocking {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val publicKey = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        val root = Files.createTempDirectory("same-version-offline-update")
        try {
            val bundle = signedBundle(root, "1.2.5", keyPair)
            val source = service(root.resolve("cache"), UpdatePlatform.Android, publicKey, currentVersion = "1.2.5")
            assertEquals("1.2.5", source.importOfflineBundle(bundle))
            assertTrue(Files.exists(bundle))
            assertEquals(4, source.availableAssets().size)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun service(
        directory: java.nio.file.Path,
        platform: UpdatePlatform,
        publicKey: String,
        currentVersion: String = "0.2.0",
    ) = ReleaseUpdateService(
        currentVersion = currentVersion,
        platform = platform,
        cacheDirectory = directory,
        lastCheck = { 0L },
        lastCheckStore = LastUpdateCheckStore {},
        manifestUrl = "https://127.0.0.1/unavailable",
        trustedPublicKeyBase64 = publicKey,
    )

    private fun signedBundle(
        root: java.nio.file.Path,
        version: String,
        keyPair: java.security.KeyPair,
    ): java.nio.file.Path {
        val files = mapOf(
            UpdatePlatform.Android to ("SyncDroid-Mesh-$version-Android.apk" to byteArrayOf(1)),
            UpdatePlatform.MacOsArm64 to ("SyncTosh-$version-macOS-arm64.dmg" to byteArrayOf(2)),
            UpdatePlatform.WindowsX64 to ("SyncDows-$version-Windows-x64.exe" to byteArrayOf(3)),
        )
        val manifest = ReleaseManifest(
            version = version,
            publishedAt = "2026-08-20T00:00:00Z",
            notesUrl = "https://example.test/releases/tag/v$version",
            assets = files.map { (platform, namedBytes) ->
                ReleaseAsset(
                    platform,
                    namedBytes.first,
                    "https://example.test/releases/download/v$version/${namedBytes.first}",
                    sha256(namedBytes.second),
                    namedBytes.second.size.toLong(),
                )
            },
        ).encode()
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(keyPair.private)
            update(manifest.toByteArray(StandardCharsets.UTF_8))
            Base64.getEncoder().encodeToString(sign())
        }
        return root.resolve("SyncDroid-Mesh-$version-offline.sdu").also { bundle ->
            ZipOutputStream(Files.newOutputStream(bundle)).use { zip ->
                fun add(name: String, bytes: ByteArray) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
                add("syncdroid-update.properties", manifest.toByteArray(StandardCharsets.UTF_8))
                add(RELEASE_SIGNATURE_FILE, signature.toByteArray(StandardCharsets.UTF_8))
                files.values.forEach { (name, bytes) -> add(name, bytes) }
            }
        }
    }

    private fun sha256(bytes: ByteArray): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
