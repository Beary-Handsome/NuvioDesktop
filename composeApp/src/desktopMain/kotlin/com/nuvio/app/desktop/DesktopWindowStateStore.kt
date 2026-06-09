package com.nuvio.app.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition

/**
 * Persists main window size, position, and maximized state between sessions.
 */
internal object DesktopWindowStateStore {
    private const val namespace = "nuvio_desktop_window"
    private const val keyWidthDp = "width_dp"
    private const val keyHeightDp = "height_dp"
    private const val keyMaximized = "maximized"
    private const val keyPositionX = "position_x"
    private const val keyPositionY = "position_y"

    data class Saved(
        val widthDp: Int,
        val heightDp: Int,
        val maximized: Boolean,
        val positionX: Int? = null,
        val positionY: Int? = null,
    )

    fun load(): Saved? {
        val w = DesktopPreferences.getInt(namespace, keyWidthDp) ?: return null
        val h = DesktopPreferences.getInt(namespace, keyHeightDp) ?: return null
        if (w < MinWidthDp || h < MinHeightDp) return null
        val maximized = DesktopPreferences.getBoolean(namespace, keyMaximized) ?: false
        val x = DesktopPreferences.getInt(namespace, keyPositionX)
        val y = DesktopPreferences.getInt(namespace, keyPositionY)
        return Saved(w, h, maximized, x, y)
    }

    /**
     * Skips fullscreen so we do not persist fullscreen bounds as the next floating size.
     */
    fun save(size: DpSize, placement: WindowPlacement, position: WindowPosition? = null) {
        if (placement == WindowPlacement.Fullscreen) return

        val maximized = placement == WindowPlacement.Maximized
        val w = size.width.value.toInt().coerceAtLeast(MinWidthDp)
        val h = size.height.value.toInt().coerceAtLeast(MinHeightDp)
        DesktopPreferences.putInt(namespace, keyWidthDp, w)
        DesktopPreferences.putInt(namespace, keyHeightDp, h)
        DesktopPreferences.putBoolean(namespace, keyMaximized, maximized)

        // Persist window position for multi-monitor support
        if (!maximized && position is WindowPosition.Absolute) {
            val px = position.x.value.toInt()
            val py = position.y.value.toInt()
            // Only save reasonable positions (not off-screen)
            if (px > -5000 && py > -5000 && px < 20000 && py < 20000) {
                DesktopPreferences.putInt(namespace, keyPositionX, px)
                DesktopPreferences.putInt(namespace, keyPositionY, py)
            }
        }
    }

    private const val MinWidthDp = 400
    private const val MinHeightDp = 300
}
