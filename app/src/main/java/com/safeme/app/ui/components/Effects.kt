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
): Modifier = drawBehind {
    val corner = cornerRadius.toPx()
    val paint = Paint().apply {
        this.color = color
        isAntiAlias = true
        asFrameworkPaint().maskFilter = BlurMaskFilter(blurRadius.toPx(), BlurMaskFilter.Blur.NORMAL)
    }
    drawIntoCanvas { canvas ->
        canvas.drawRoundRect(
            left = 0f,
            top = offsetY.toPx(),
            right = size.width,
            bottom = size.height + offsetY.toPx(),
            radiusX = corner,
            radiusY = corner,
            paint = paint,
        )
    }
}
