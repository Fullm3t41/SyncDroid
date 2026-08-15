package com.syncdroid.app.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestContentTest {
    @Test fun timestampOnlyDifferenceDoesNotCreateContentChange() {
        val before = FileManifestEntry("save.sav", 8, 100, "abc")
        val afterCopy = before.copy(modifiedAtMillis = 999)

        assertTrue(manifestsHaveSameContent(listOf(before), listOf(afterCopy)))
    }

    @Test fun changedHashStillCreatesContentChangeWithSameTimestamp() {
        val before = FileManifestEntry("save.sav", 8, 100, "abc")
        val edited = before.copy(sha256 = "def")

        assertFalse(manifestsHaveSameContent(listOf(before), listOf(edited)))
    }
}
