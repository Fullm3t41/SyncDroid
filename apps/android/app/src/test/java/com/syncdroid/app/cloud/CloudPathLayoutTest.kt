package com.syncdroid.app.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CloudPathLayoutTest {
    @Test fun filesAlwaysLiveBelowSyncDroidAndNamedFolder() {
        assertEquals(
            "SyncDroid/Stardew Valley/Saves/slot1.sav",
            CloudPathLayout.file("Stardew Valley", "Saves/slot1.sav").displayPath,
        )
    }

    @Test fun folderRootKeepsTheOriginalSafeName() {
        assertEquals("SyncDroid/My Saves", CloudPathLayout.folderRoot("My Saves").displayPath)
    }

    @Test fun traversalAndCrossProviderInvalidNamesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            CloudPathLayout.file("My Saves", "../outside.sav")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CloudPathLayout.folderRoot("Game/Saves")
        }
    }
}
