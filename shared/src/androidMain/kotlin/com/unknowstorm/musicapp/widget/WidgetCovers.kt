package com.unknowstorm.musicapp.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap

/** Loads the cover for the widgets: a square, centre-cropped bitmap with rounded corners. */
internal object WidgetCovers {
    private const val SIZE_PX = 400

    suspend fun load(context: Context, url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return try {
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(SIZE_PX, SIZE_PX)
                .allowHardware(false) // RemoteViews cannot carry hardware bitmaps
                .build()
            val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return null
            val source = result.image.toBitmap()
            roundedSquare(source)
        } catch (e: Exception) {
            android.util.Log.w("Solara", "widget cover failed: ${e.message}")
            null
        }
    }

    private fun roundedSquare(src: Bitmap): Bitmap {
        val side = minOf(src.width, src.height)
        val left = (src.width - side) / 2
        val top = (src.height - side) / 2
        val square = Bitmap.createBitmap(src, left, top, side, side)
        val out = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(
                Bitmap.createScaledBitmap(square, SIZE_PX, SIZE_PX, true),
                Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP,
            )
        }
        val radius = SIZE_PX * 0.12f
        Canvas(out).drawRoundRect(RectF(0f, 0f, SIZE_PX.toFloat(), SIZE_PX.toFloat()), radius, radius, paint)
        return out
    }
}
