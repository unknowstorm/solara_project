package com.unknowstorm.musicapp

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.util.prefs.Preferences

/**
 * On Linux (X11) the window class defaults to the main class name ("com-unknowstorm-musicapp-MainKt"),
 * so the taskbar shows that text and no icon. We set it to the id of the installed menu entry
 * ("solara-Solara.desktop"), then the shell takes the name and the icon from it.
 */
private fun setLinuxWindowClass() {
    if (!System.getProperty("os.name").orEmpty().contains("linux", ignoreCase = true)) return
    try {
        val field = Class.forName("sun.awt.X11.XToolkit").getDeclaredField("awtAppClassName")
        field.isAccessible = true
        field.set(null, "solara-Solara")
    } catch (_: Throwable) {
        // Not X11 or no access: the default class name stays, the app still works
    }
}

private val prefs = Preferences.userRoot().node("com/unknowstorm/solara")
private const val ZOOM_KEY = "uiZoom"
private val zoomSteps = listOf(0.75f, 0.9f, 1f, 1.1f, 1.25f, 1.4f, 1.5f, 1.75f, 2f)

/** The interface scale: Ctrl + / Ctrl - / Ctrl 0, remembered between launches. Starts at 1.25 on the first launch. */
private fun loadZoom(): Float = prefs.getFloat(ZOOM_KEY, 1.25f).coerceIn(zoomSteps.first(), zoomSteps.last())

fun main() {
    setLinuxWindowClass()
    application {
        var zoom by remember { mutableStateOf(loadZoom()) }
        fun setZoom(v: Float) {
            zoom = v
            runCatching { prefs.putFloat(ZOOM_KEY, v) }
        }
        val windowState = rememberWindowState(size = DpSize(1100.dp, 760.dp))
        Window(
            onCloseRequest = ::exitApplication,
            title = "Solara",
            icon = painterResource("icon.png"),
            state = windowState,
            onPreviewKeyEvent = { e ->
                if (e.type == KeyEventType.KeyDown && e.isCtrlPressed) {
                    val idx = zoomSteps.indexOfFirst { it >= zoom - 0.001f }.coerceAtLeast(0)
                    when (e.key) {
                        Key.Equals, Key.Plus, Key.NumPadAdd -> { setZoom(zoomSteps[(idx + 1).coerceAtMost(zoomSteps.lastIndex)]); true }
                        Key.Minus, Key.NumPadSubtract -> { setZoom(zoomSteps[(idx - 1).coerceAtLeast(0)]); true }
                        Key.Zero, Key.NumPad0 -> { setZoom(1.25f); true }
                        else -> false
                    }
                } else false
            },
        ) {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density * zoom, base.fontScale)) {
                App()
            }
        }
    }
}
