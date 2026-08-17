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
}
