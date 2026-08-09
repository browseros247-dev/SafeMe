package com.safeme.app.ui.screens.antitamper

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
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

private fun ImageVector.Builder.strokePath(pathBuilder: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) {
    path(
        fill = null,
        stroke = stroke,
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = pathBuilder,
    )
}

/** Trash icon from the Anti-Tamper prototype card. */
val AtTrashIcon: ImageVector by lazy {
    iconBuilder("AtTrash").apply {
        strokePath {
            moveTo(4f, 7f)
            horizontalLineTo(20f)
            moveTo(9f, 7f)
            verticalLineTo(5f)
            horizontalLineTo(15f)
            verticalLineTo(7f)
            moveTo(7f, 7f)
            lineToRelative(1f, 13f)
            horizontalLineTo(16f)
            lineToRelative(1f, -13f)
        }
    }.build()
}

/** Shield plus icon (prototype protect-btn). */
val AtShieldPlusIcon: ImageVector by lazy {
    iconBuilder("AtShieldPlus").apply {
        strokePath {
            moveTo(12f, 2f)
            lineTo(19f, 5f)
            verticalLineTo(10f)
            curveToRelative(0f, 4.5f, -3f, 7.5f, -7f, 9f)
            curveToRelative(-4f, -1.5f, -7f, -4.5f, -7f, -9f)
            verticalLineTo(6f)
            close()
        }
        strokePath {
            moveTo(12f, 9f)
            verticalLineTo(15f)
            moveTo(9f, 12f)
            horizontalLineTo(15f)
        }
    }.build()
}

/** Chevron-right from the prototype protect-btn. */
val AtChevronRightIcon: ImageVector by lazy {
    iconBuilder("AtChevronRight").apply {
        strokePath {
            moveTo(9f, 5f)
            lineTo(16f, 12f)
            lineTo(9f, 19f)
        }
    }.build()
}
