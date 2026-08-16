package com.syncdows.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import java.awt.Point
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

@Composable
fun StyledSystemTrayIcon(
    tooltip: String,
    onMenuRequested: (Point) -> Unit,
    onOpenRequested: () -> Unit,
) {
    val currentMenuRequest = rememberUpdatedState(onMenuRequested)
    val currentOpenRequest = rememberUpdatedState(onOpenRequested)
    val trayIcon = remember {
        if (!SystemTray.isSupported()) {
            null
        } else {
            val iconUrl = Thread.currentThread().contextClassLoader.getResource("syncdows-icon-source.png")
            iconUrl?.let(ImageIO::read)?.let { image ->
                TrayIcon(image).apply { isImageAutoSize = true }
            }
        }
    }

    SideEffect {
        trayIcon?.toolTip = tooltip
    }

    DisposableEffect(trayIcon) {
        if (trayIcon == null) return@DisposableEffect onDispose {}

        val listener = object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (!SwingUtilities.isLeftMouseButton(event)) return
                val anchor = Point(event.xOnScreen, event.yOnScreen)
                if (event.clickCount >= 2) {
                    currentOpenRequest.value()
                } else {
                    currentMenuRequest.value(anchor)
                }
            }

            override fun mouseReleased(event: MouseEvent) {
                if (event.isPopupTrigger) {
                    currentMenuRequest.value(Point(event.xOnScreen, event.yOnScreen))
                }
            }
        }
        trayIcon.addMouseListener(listener)
        val systemTray = SystemTray.getSystemTray()
        val added = runCatching { systemTray.add(trayIcon) }.isSuccess

        onDispose {
            trayIcon.removeMouseListener(listener)
            if (added) systemTray.remove(trayIcon)
        }
    }
}
