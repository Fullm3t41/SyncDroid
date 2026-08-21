package com.syncdows.app.platform

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong

object WindowsTouchKeyboard {
    private val lastLaunchMillis = AtomicLong(0)

    /** Opens the Windows touch keyboard, falling back to the accessibility keyboard. */
    fun show() {
        val now = System.currentTimeMillis()
        val previous = lastLaunchMillis.getAndSet(now)
        if (now - previous < LAUNCH_COOLDOWN_MILLIS) return

        touchKeyboardCandidates(System.getenv())
            .firstOrNull(Files::isRegularFile)
            ?.let { executable -> runCatching { ProcessBuilder(executable.toString()).start() } }
    }

    private const val LAUNCH_COOLDOWN_MILLIS = 750L
}

internal fun touchKeyboardCandidates(environment: Map<String, String>): List<Path> {
    val commonFiles = listOfNotNull(
        environment["CommonProgramW6432"],
        environment["CommonProgramFiles"],
        environment["CommonProgramFiles(x86)"],
    )
    val windowsDirectory = environment["WINDIR"] ?: environment["SystemRoot"]

    return buildList {
        commonFiles.forEach { root ->
            add(Path.of(root, "microsoft shared", "ink", "TabTip.exe"))
        }
        windowsDirectory?.let { add(Path.of(it, "System32", "osk.exe")) }
    }.distinct()
}
