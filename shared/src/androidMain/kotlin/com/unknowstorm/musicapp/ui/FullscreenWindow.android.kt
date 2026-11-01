package com.unknowstorm.musicapp.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

private tailrec fun Context.activityOrNull(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activityOrNull()
    else -> null
}

@Composable
actual fun FullscreenWindowEffect(active: Boolean) {
    val context = LocalContext.current
    DisposableEffect(active) {
        val activity = context.activityOrNull()
        if (active && activity != null) {
            val previous = activity.requestedOrientation
            // Альбом (любая из двух сторон), системные панели прячутся и выезжают по свайпу от края
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            onDispose {
                activity.requestedOrientation = previous
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        } else {
            onDispose { }
        }
    }
}
