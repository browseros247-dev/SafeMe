package com.safeme.app.ui.screens.blockscreen

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private fun iconBuilder(name: String): ImageVector.Builder =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )

private val stroke = SolidColor(Color.Black)

private fun ImageVector.Builder.strokePath(pathBuilder: PathBuilder.() -> Unit) {
    path(
        fill = null,
        stroke = stroke,
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = pathBuilder,
    )
}

val ImageIcon: ImageVector by lazy {
    iconBuilder("Image").apply {
        strokePath {
            moveTo(6f, 5f)
            horizontalLineToRelative(12f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
            verticalLineToRelative(10f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
            horizontalLineTo(6f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
            verticalLineTo(7f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
            close()
        }
        strokePath {
            moveTo(9f, 10f)
            moveToRelative(-2f, 0f)
            arcToRelative(2f, 2f, 0f, true, false, 4f, 0f)
            arcToRelative(2f, 2f, 0f, true, false, -4f, 0f)
        }
        strokePath {
            moveTo(4f, 17f)
            lineToRelative(5f, -4f)
            lineToRelative(4f, 3f)
            lineToRelative(3f, -2f)
            lineToRelative(4f, 3f)
        }
    }.build()
}

val MessageIcon: ImageVector by lazy {
    iconBuilder("Message").apply {
        strokePath {
            moveTo(4f, 7f)
            horizontalLineTo(20f)
        }
        strokePath {
            moveTo(4f, 12f)
            horizontalLineTo(20f)
        }
        strokePath {
            moveTo(4f, 17f)
            horizontalLineTo(14f)
        }
    }.build()
}

val LinkIcon: ImageVector by lazy {
    iconBuilder("Link").apply {
        strokePath {
            moveTo(17f, 7f)
            lineToRelative(5f, 5f)
            lineToRelative(-5f, 5f)
        }
        strokePath {
            moveTo(7f, 17f)
            lineToRelative(-5f, -5f)
            lineToRelative(5f, -5f)
        }
    }.build()
}

val InfoIcon: ImageVector by lazy {
    iconBuilder("Info").apply {
        strokePath {
            moveTo(12f, 12f)
            moveToRelative(-9f, 0f)
            arcToRelative(9f, 9f, 0f, true, false, 18f, 0f)
            arcToRelative(9f, 9f, 0f, true, false, -18f, 0f)
        }
        strokePath {
            moveTo(12f, 8f)
            horizontalLineToRelative(0.01f)
        }
        strokePath {
            moveTo(11f, 12f)
            horizontalLineToRelative(1f)
            verticalLineToRelative(4f)
            horizontalLineToRelative(1f)
        }
    }.build()
}

val ShieldCheckIcon: ImageVector by lazy {
    iconBuilder("ShieldCheck").apply {
        strokePath {
            moveTo(12f, 3f)
            lineToRelative(7f, 3f)
            verticalLineToRelative(5f)
            curveToRelative(0f, 4.5f, -3f, 7.5f, -7f, 9f)
            curveToRelative(-4f, -1.5f, -7f, -4.5f, -7f, -9f)
            verticalLineTo(6f)
            close()
        }
        strokePath {
            moveTo(9f, 12f)
            lineTo(11f, 14f)
            lineTo(15f, 10f)
        }
    }.build()
}
