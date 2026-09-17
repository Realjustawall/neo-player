package com.neoplayer.app.visual

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.graphics.ImageDecoder
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


data class ExtractedTrackTheme(
    val accentArgb: Int,
    val backgroundArgb: Int,
    val secondaryArgb: Int
)

class ArtworkThemeExtractor(private val context: Context) {
    suspend fun extract(uri: Uri): ExtractedTrackTheme? = withContext(Dispatchers.IO) {
        val bitmap = loadBitmap(uri) ?: return@withContext null
        try {
            val palette = Palette.from(bitmap).maximumColorCount(24).generate()
            val dominant = palette.getDominantColor(AndroidColor.rgb(30, 30, 30))
            val accent = palette.vibrantSwatch?.rgb
                ?: palette.lightVibrantSwatch?.rgb
                ?: palette.mutedSwatch?.rgb
                ?: dominant
            val background = palette.darkMutedSwatch?.rgb
                ?: palette.darkVibrantSwatch?.rgb
                ?: darken(dominant, .38f)
            val secondary = palette.lightMutedSwatch?.rgb
                ?: palette.mutedSwatch?.rgb
                ?: lighten(accent, .24f)
            ExtractedTrackTheme(accent, background, secondary)
        } finally {
            bitmap.recycle()
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val maxSide = maxOf(info.size.width, info.size.height).coerceAtLeast(1)
                decoder.setTargetSampleSize((maxSide / 1024).coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
        }
    }.getOrNull()

    private fun darken(color: Int, factor: Float): Int = AndroidColor.rgb(
        (AndroidColor.red(color) * factor).toInt().coerceIn(0, 255),
        (AndroidColor.green(color) * factor).toInt().coerceIn(0, 255),
        (AndroidColor.blue(color) * factor).toInt().coerceIn(0, 255)
    )

    private fun lighten(color: Int, amount: Float): Int = AndroidColor.rgb(
        (AndroidColor.red(color) + (255 - AndroidColor.red(color)) * amount).toInt().coerceIn(0, 255),
        (AndroidColor.green(color) + (255 - AndroidColor.green(color)) * amount).toInt().coerceIn(0, 255),
        (AndroidColor.blue(color) + (255 - AndroidColor.blue(color)) * amount).toInt().coerceIn(0, 255)
    )
}
