package com.synctosh.app.platform

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacStartupManagerTest {
    @Test
    fun enabledLaunchAgentStartsSyncToshHidden() {
        val plist = MacStartupManager.launchAgentContents(
            Path.of("/Applications/SyncTosh & Tools.app/Contents/MacOS/SyncTosh"),
        )

        assertTrue(plist.contains("/Applications/SyncTosh &amp; Tools.app/Contents/MacOS/SyncTosh"))
        assertTrue(plist.contains("<string>--background</string>"))
        assertTrue(plist.contains("<key>RunAtLoad</key>"))
    }

    @Test
    fun launchAgentCanBeEnabledAndDisabled() {
        val home = createTempDirectory("synctosh-startup-test")
        val executable = home.resolve("SyncTosh")
        Files.writeString(executable, "test")

        MacStartupManager.setEnabled(true, executable, home)
        assertTrue(Files.isRegularFile(MacStartupManager.launchAgentPath(home)))

        MacStartupManager.setEnabled(false, executable, home)
        assertFalse(Files.exists(MacStartupManager.launchAgentPath(home)))
    }
}
