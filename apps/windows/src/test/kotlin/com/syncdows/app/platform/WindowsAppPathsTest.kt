package com.syncdows.app.platform

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowsAppPathsTest {
    @Test
    fun persistentDataIsSeparateFromThePerUserInstallDirectory() {
        val data = WindowsAppPaths.applicationDataPath("C:\\Users\\James\\AppData\\Local", "ignored")

        assertEquals(Path.of("C:\\Users\\James\\AppData\\Local", "Fullm3t41", "SyncDows"), data)
        assertEquals(false, data.startsWith(Path.of("C:\\Users\\James\\AppData\\Local", "Programs", "SyncDows")))
    }

    @Test
    fun missingLocalAppDataUsesTheUserProfileFallback() {
        assertEquals(
            Path.of("C:\\Users\\James", "AppData", "Local", "Fullm3t41", "SyncDows"),
            WindowsAppPaths.applicationDataPath(null, "C:\\Users\\James"),
        )
    }
}
