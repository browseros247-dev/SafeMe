package com.safeme.app.ui.screens.keywords

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val KwSearchIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "KwSearch",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        val stroke = SolidColor(Color.Black)
        path(
            fill = null,
            stroke = stroke,
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(11f, 19f)
            arcToRelative(8f, 8f, 0f, false, true, 0f, -16f)
            arcToRelative(8f, 8f, 0f, false, true, 0f, 16f)
            moveTo(21f, 21f)
            lineTo(16.65f, 16.65f)
        }
    }.build()
}

val KwEditIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "KwEdit",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        val stroke = SolidColor(Color.Black)
        path(
            fill = null,
            stroke = stroke,
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(3f, 17.25f)
            verticalLineTo(21f)
            horizontalLineTo(6.75f)
            lineTo(17.81f, 9.94f)
            lineTo(14.06f, 6.19f)
            lineTo(3f, 17.25f)
            close()
            moveTo(20.71f, 7.04f)
            curveTo(21.1f, 6.65f, 21.1f, 6.02f, 20.71f, 5.63f)
            lineTo(18.37f, 3.29f)
            curveTo(17.98f, 2.9f, 17.35f, 2.9f, 16.96f, 3.29f)
            lineTo(15.13f, 5.12f)
            lineTo(18.88f, 8.87f)
            lineTo(20.71f, 7.04f)
            close()
        }
    }.build()
}

val KwTrashIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "KwTrash",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        val stroke = SolidColor(Color.Black)
        path(
            fill = null,
            stroke = stroke,
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(3f, 6f)
            horizontalLineTo(21f)
            moveTo(8f, 6f)
            verticalLineTo(4f)
            curveTo(8f, 3.45f, 8.45f, 3f, 9f, 3f)
            horizontalLineTo(15f)
            curveTo(15.55f, 3f, 16f, 3.45f, 16f, 4f)
            verticalLineTo(6f)
            moveTo(19f, 6f)
            verticalLineTo(20f)
            curveTo(19f, 20.55f, 18.55f, 21f, 18f, 21f)
            horizontalLineTo(6f)
            curveTo(5.45f, 21f, 5f, 20.55f, 5f, 20f)
            verticalLineTo(6f)
            moveTo(10f, 11f)
            verticalLineTo(17f)
            moveTo(14f, 11f)
            verticalLineTo(17f)
        }
    }.build()
}
