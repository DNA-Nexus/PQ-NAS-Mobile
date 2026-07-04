package com.pqnas.mobile.files

import androidx.core.graphics.createBitmap
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import com.caverock.androidsvg.SVG

object SvgIconLoader {
    fun loadBitmapFromAssets(
        context: Context,
        assetPath: String,
        sizePx: Int
    ): Bitmap? {
        return try {
            context.assets.open(assetPath).use { input ->
                val svg = SVG.getFromInputStream(input)
                val bitmap = createBitmap(sizePx, sizePx)
                val canvas = Canvas(bitmap)

                svg.setDocumentWidth(sizePx.toFloat())
                svg.setDocumentHeight(sizePx.toFloat())
                svg.renderToCanvas(canvas)

                bitmap
            }
        } catch (_: Exception) {
            null
        }
    }
}
