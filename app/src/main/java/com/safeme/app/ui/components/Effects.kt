package com.safeme.app.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Dp

fun Modifier.blurredShadow(
    cornerRadius: Dp,
    color: Color,
    blurRadius: Dp,
    offsetY: Dp,
): Modifier {
    // Paint is built lazily on the first draw (Density is only available in
    // the DrawScope) and reused for every subsequent frame — the drawBehind
    // lambda runs per frame, and allocating a Paint + BlurMaskFilter inside
    // it would churn the GC on every redraw of shadowed cards. A fresh
    // modifier instance (recomposition with changed params) rebuilds it.
    var paint: Paint? = null
    return drawBehind {
        val p = paint ?: Paint().apply {
            this.color = color
            isAntiAlias = true
            asFrameworkPaint().maskFilter =
                BlurMaskFilter(blurRadius.toPx(), BlurMaskFilter.Blur.NORMAL)
        }.also { paint = it }
        val corner = cornerRadius.toPx()
        drawIntoCanvas { canvas ->
            canvas.drawRoundRect(
                left = 0f,
                top = offsetY.toPx(),
                right = size.width,
                bottom = size.height + offsetY.toPx(),
                radiusX = corner,
                radiusY = corner,
                paint = p,
            )
        }
    }
}
