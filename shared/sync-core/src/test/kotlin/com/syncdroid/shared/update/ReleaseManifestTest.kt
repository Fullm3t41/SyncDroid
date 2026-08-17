package com.syncdroid.shared.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReleaseManifestTest {
    @Test
    fun manifestRoundTripsAllPlatforms() {
        val manifest = ReleaseManifest(
            version = "0.2.0",
            publishedAt = "2026-08-16T12:00:00Z",
            notesUrl = "https://github.com/Fullm3t41/SyncDroid-Mesh/releases/tag/v0.2.0",
            assets = UpdatePlatform.entries.map { platform ->
                ReleaseAsset(
                    platform,
                    "${platform.id}.bin",
                    "https://github.com/Fullm3t41/SyncDroid-Mesh/releases/download/v0.2.0/${platform.id}.bin",
                    "a".repeat(64),
                    123L,
                )
            },
        )

        assertEquals(manifest, ReleaseManifest.parse(manifest.encode()))
    }

    @Test
    fun semanticVersionsCompareWithoutLexicographicErrors() {
        assertTrue(isNewerVersion("0.10.0", "0.9.9"))
        assertTrue(isNewerVersion("1.0.0", "1.0.0-beta.2"))
        assertFalse(isNewerVersion("1.0.0-beta.2", "1.0.0"))
        assertFalse(isNewerVersion("1.0.0", "1.0.0"))
    }
}
