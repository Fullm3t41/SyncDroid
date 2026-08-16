package com.syncdows.app.ui

import java.awt.Dimension
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals

class TrayPanelTest {
    @Test
    fun positionsPanelAboveBottomRightTrayAnchor() {
        val location = trayPanelLocation(
            anchor = Point(1900, 1040),
            screenBounds = Rectangle(0, 0, 1920, 1080),
            screenInsets = Insets(0, 0, 40, 0),
            panelSize = Dimension(376, 500),
        )

        assertEquals(Point(1532, 528), location)
    }

    @Test
    fun keepsPanelInsideOffsetMonitorWorkArea() {
        val location = trayPanelLocation(
            anchor = Point(-1910, 10),
            screenBounds = Rectangle(-1920, 0, 1920, 1080),
            screenInsets = Insets(32, 0, 0, 0),
            panelSize = Dimension(376, 500),
        )

        assertEquals(Point(-1908, 44), location)
    }
}
