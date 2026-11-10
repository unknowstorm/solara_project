package com.unknowstorm.musicapp.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Navigation to the home "Favorites". Screens that draw the services bar themselves (YouTube)
 * take the handler from here so that the heart stands in the same place on all chips.
 */
val LocalOpenLikes = staticCompositionLocalOf<(() -> Unit)?> { null }
