package com.syncdows.app.platform

import java.nio.file.Path

object WindowsStartupManager {
    fun setEnabled(enabled: Boolean) {
        require(System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            "Launch at login can be changed from a packaged Windows build"
        }
        val command = if (enabled) {
            val executable = packagedExecutable()
            listOf(
                "reg.exe", "add", RUN_KEY,
                "/v", VALUE_NAME,
                "/t", "REG_SZ",
                "/d", "\"$executable\" --background",
                "/f",
            )
        } else {
            listOf("reg.exe", "delete", RUN_KEY, "/v", VALUE_NAME, "/f")
        }
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText().trim() }
        require(process.waitFor() == 0 || (!enabled && output.contains("unable to find", ignoreCase = true))) {
            output.ifBlank { "Windows could not update the launch-at-login setting" }
        }
    }

    private fun packagedExecutable(): Path {
        val appPath = System.getProperty("jpackage.app-path")?.takeIf(String::isNotBlank)
            ?: ProcessHandle.current().info().command().orElse(null)
        val path = requireNotNull(appPath) { "SyncDows executable path is unavailable" }
            .let(Path::of)
            .toAbsolutePath()
            .normalize()
        require(path.fileName.toString().endsWith(".exe", ignoreCase = true)) {
            "Launch at login is available after SyncDows is installed"
        }
        return path
    }

    private const val RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val VALUE_NAME = "SyncDows"
}
