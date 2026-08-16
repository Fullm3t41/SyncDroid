package com.syncdroid.app.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncFilterRulesTest {
    @Test
    fun savOnlyMatchesSaveFilesIgnoringCase() {
        val rules = SyncFilterRules(includes = listOf("*.sav"))

        assertTrue(rules.shouldSync("slot1.sav"))
        assertTrue(rules.shouldSync("profiles/SLOT2.SAV"))
        assertFalse(rules.shouldSync("screenshot.png"))
    }

    @Test
    fun excludesOverrideIncludes() {
        val rules = SyncFilterRules(
            includes = listOf("*.sav"),
            excludes = listOf("autosave-*"),
        )

        assertTrue(rules.shouldSync("manual.sav"))
        assertFalse(rules.shouldSync("autosave-1.sav"))
    }

    @Test
    fun directoriesRemainTraversable() {
        val rules = SyncFilterRules(includes = listOf("*.sav"))
        assertTrue(rules.shouldSync("profiles", isDirectory = true))
    }
}
