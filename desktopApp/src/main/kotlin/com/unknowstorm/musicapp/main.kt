package com.unknowstorm.musicapp

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

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

fun main() {
    setLinuxWindowClass()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Solara",
            icon = painterResource("icon.png"),
        ) {
            App()
        }
    }
}
