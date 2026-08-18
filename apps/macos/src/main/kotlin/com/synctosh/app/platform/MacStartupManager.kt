package com.synctosh.app.platform

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object MacStartupManager {
    fun isEnabled(): Boolean = Files.isRegularFile(launchAgentPath(userHome()))

    fun setEnabled(enabled: Boolean) {
        require(System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) {
            "Launch at login can be changed from a packaged macOS build"
        }
        setEnabled(enabled, packagedExecutable(), userHome())
    }

    internal fun setEnabled(enabled: Boolean, executable: Path, home: Path) {
        val launchAgent = launchAgentPath(home)
        if (!enabled) {
            Files.deleteIfExists(launchAgent)
            return
        }

        val normalizedExecutable = executable.toAbsolutePath().normalize()
        require(normalizedExecutable.fileName.toString() == "SyncTosh") {
            "Launch at login is available after SyncTosh is installed"
        }
        Files.createDirectories(launchAgent.parent)
        val temporary = Files.createTempFile(launchAgent.parent, "com.synctosh.app", ".plist.tmp")
        try {
            Files.writeString(temporary, launchAgentContents(normalizedExecutable))
            try {
                Files.move(
                    temporary,
                    launchAgent,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, launchAgent, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    internal fun launchAgentPath(home: Path): Path =
        home.resolve("Library").resolve("LaunchAgents").resolve("com.synctosh.app.plist")

    internal fun launchAgentContents(executable: Path): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
        <plist version="1.0">
        <dict>
            <key>Label</key>
            <string>com.synctosh.app</string>
            <key>ProgramArguments</key>
            <array>
                <string>${xmlEscape(executable.toString())}</string>
                <string>--background</string>
            </array>
            <key>RunAtLoad</key>
            <true/>
            <key>ProcessType</key>
            <string>Interactive</string>
        </dict>
        </plist>
    """.trimIndent() + "\n"

    private fun packagedExecutable(): Path {
        val appPath = System.getProperty("jpackage.app-path")?.takeIf(String::isNotBlank)
            ?: ProcessHandle.current().info().command().orElse(null)
        return requireNotNull(appPath) { "SyncTosh executable path is unavailable" }
            .let(Path::of)
    }

    private fun userHome(): Path = Path.of(System.getProperty("user.home"))

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
