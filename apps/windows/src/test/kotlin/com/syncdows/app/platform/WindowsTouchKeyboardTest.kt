package com.syncdows.app.platform

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowsTouchKeyboardTest {
    @Test
    fun prefersTouchKeyboardAndFallsBackToAccessibilityKeyboard() {
        val candidates = touchKeyboardCandidates(
            mapOf(
                "CommonProgramW6432" to "C:\\Program Files\\Common Files",
                "CommonProgramFiles" to "C:\\Program Files\\Common Files",
                "WINDIR" to "C:\\Windows",
            ),
        )

        assertEquals(
            listOf(
                Path.of("C:\\Program Files\\Common Files", "microsoft shared", "ink", "TabTip.exe"),
                Path.of("C:\\Windows", "System32", "osk.exe"),
            ),
            candidates,
        )
    }
}
