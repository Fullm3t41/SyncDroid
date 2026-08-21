package com.syncdows.app.ui

import java.awt.Dimension
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals

class TrayPanelTest {
    @Test
    fun constrainsPanelHeightToScaledWorkArea() {
        assertEquals(
            556,
            trayPanelHeightDp(
                desiredHeightDp = 584,
                workAreaHeightPixels = 720,
                displayScale = 1.25,
            ),
        )
    }

    @Test
    fun keepsDesiredPanelHeightWhenItFits() {
        assertEquals(
            482,
            trayPanelHeightDp(
                desiredHeightDp = 482,
                workAreaHeightPixels = 1_040,
                displayScale = 1.5,
            ),
        )
    }

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
