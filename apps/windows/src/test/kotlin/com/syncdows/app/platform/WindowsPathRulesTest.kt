package com.syncdows.app.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WindowsPathRulesTest {
    @Test
    fun acceptsPortableNestedPaths() {
        assertEquals("Saves/profile.sav", WindowsPathRules.validateRelativePath("Saves/profile.sav"))
        assertEquals("Movies/video.mkv", WindowsPathRules.validateRelativePath("/Movies\\video.mkv/"))
    }

    @Test
    fun rejectsReservedNamesAndIllegalCharacters() {
        listOf("CON", "con.txt", "aux/save.sav", "folder/NUL.bin", "LPT9/log.txt").forEach { path ->
            assertFailsWith<IllegalArgumentException>(path) { WindowsPathRules.validateRelativePath(path) }
        }
        listOf("save?.sav", "bad:name/file", "folder/trailing. ", "../escape.txt").forEach { path ->
            assertFailsWith<IllegalArgumentException>(path) { WindowsPathRules.validateRelativePath(path) }
        }
    }

    @Test
    fun recognizesWindowsDeviceNamesCaseInsensitively() {
        assertTrue(WindowsPathRules.isReservedName("Com1.txt"))
        assertTrue(WindowsPathRules.isReservedName("clock$"))
    }
}
