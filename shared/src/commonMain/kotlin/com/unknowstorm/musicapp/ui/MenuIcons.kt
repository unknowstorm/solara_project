package com.unknowstorm.musicapp.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

private fun menuIcon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(pathData).toNodes(),
              fill = SolidColor(Color.Black),
    ).build()

    internal val PlaylistAddVector: ImageVector by lazy {
        menuIcon(
            "PlaylistAdd",
            "M14,10H3v2h11V10zM14,6H3v2h11V6zM18,14v-4h-2v4h-4v2h4v4h2v-4h4v-2H18zM3,16h7v-2H3V16z",
        )
    }

    internal val InfoVector: ImageVector by lazy {
        menuIcon(
            "TrackInfo",
            "M11,15h2v2h-2zM11,7h2v6h-2zM11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2zM12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8z",
        )
    }
