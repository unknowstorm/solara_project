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
        val toolkit = java.awt.Toolkit.getDefaultToolkit()
        val field = Class.forName("sun.awt.X11.XToolkit").getDeclaredField("awtAppClassName")
        field.isAccessible = true
        field.set(toolkit, "solara-Solara")
    } catch (_: Throwable) {
        // Not X11 or no access: the default class name stays, the app still works
    }
}

private val prefs = Preferences.userRoot().node("com/unknowstorm/solara")
private const val ZOOM_KEY = "uiZoomMul"
private val zoomSteps = listOf(0.75f, 0.9f, 1f, 1.1f, 1.25f, 1.4f, 1.5f, 1.75f, 2f)

/**
 * Java on X11 / XWayland often does not pick up the desktop scale (for example 125 %), so everything looks tiny.
 * If the JVM was not already told a scale, read the desktop DPI (Xft.dpi, 96 = 100 %) and use it as the base scale.
 */
private fun detectSystemScale(): Float {
    if (!System.getProperty("os.name").orEmpty().contains("linux", ignoreCase = true)) return 1f
    if (System.getProperty("sun.java2d.uiScale") != null || System.getenv("GDK_SCALE") != null) return 1f
    return try {
        val process = ProcessBuilder("xrdb", "-query").redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().readText()
        process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
        val dpi = Regex("""Xft\.dpi:\s*([0-9.]+)""").find(text)?.groupValues?.get(1)?.toFloatOrNull()
        if (dpi == null) 1f else (Math.round(dpi / 96f * 20f) / 20f).coerceIn(1f, 3f)
    } catch (_: Throwable) {
        1f
    }
}

/** The user's extra zoom on top of the system scale: Ctrl + / Ctrl - / Ctrl 0, remembered between launches. */
private fun loadZoomMul(): Float = prefs.getFloat(ZOOM_KEY, 1f).coerceIn(zoomSteps.first(), zoomSteps.last())

fun main() {
    setLinuxWindowClass()
    application {
        val systemScale = remember { detectSystemScale() }
        var zoom by remember { mutableStateOf(loadZoomMul()) }
        fun setZoom(v: Float) {
            zoom = v
            runCatching { prefs.putFloat(ZOOM_KEY, v) }
        }
        // The window size is measured at the system density, so scale it to get a comfortable size on any screen
        val windowState = rememberWindowState(size = DpSize((1100 * systemScale).dp, (760 * systemScale).dp))
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
                        Key.Zero, Key.NumPad0 -> { setZoom(1f); true }
                        else -> false
                    }
                } else false
            },
        ) {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density * systemScale * zoom, base.fontScale)) {
                App()
            }
        }
    }
}
