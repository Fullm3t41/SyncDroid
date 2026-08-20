package com.syncdroid.shared.update

import kotlin.test.Test
import kotlin.test.assertEquals

class ReleaseUpdateServiceTest {
    @Test
    fun `signature URL preserves the manifest query`() {
        assertEquals(
            "https://api.github.com/repos/Fullm3t41/SyncDroid-Mesh/contents/" +
                "syncdroid-update.properties.sig?ref=updates",
            releaseSignatureUrl(DEFAULT_RELEASE_MANIFEST_URL),
        )
    }

    @Test
    fun `signature URL keeps a fragment after the file suffix`() {
        assertEquals(
            "https://updates.example/manifest.properties.sig#stable",
            releaseSignatureUrl("https://updates.example/manifest.properties#stable"),
        )
    }

    @Test
    fun `offline bundle URL uses the signed release asset directory`() {
        val manifest = ReleaseManifest(
            version = "1.2.5",
            publishedAt = "2026-08-20T00:00:00Z",
            notesUrl = "https://github.com/Fullm3t41/SyncDroid-Mesh/releases/tag/v1.2.5",
            assets = listOf(
                ReleaseAsset(
                    UpdatePlatform.Android,
                    "SyncDroid-Mesh-1.2.5-Android.apk",
                    "https://github.com/Fullm3t41/SyncDroid-Mesh/releases/download/v1.2.5/SyncDroid-Mesh-1.2.5-Android.apk",
                    "a".repeat(64),
                    1,
                ),
            ),
        )
        assertEquals(
            "https://github.com/Fullm3t41/SyncDroid-Mesh/releases/download/v1.2.5/SyncDroid-Mesh-1.2.5-offline.sdu",
            offlineBundleDownloadUrl(manifest),
        )
    }
}
