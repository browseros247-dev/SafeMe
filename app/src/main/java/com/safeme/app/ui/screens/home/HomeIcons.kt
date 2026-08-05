package com.safeme.app.ui.screens.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal val HomeClockIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "HomeClock",
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
            moveTo(3f, 12f)
            arcToRelative(9f, 9f, 0f, false, true, 18f, 0f)
            arcToRelative(9f, 9f, 0f, false, true, -18f, 0f)
            moveTo(12f, 7f)
            verticalLineTo(12f)
            lineTo(15f, 14f)
        }
    }.build()
}

internal val HomeHashtagIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "HomeHashtag",
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
            moveTo(4f, 9f)
            horizontalLineTo(20f)
            moveTo(4f, 15f)
            horizontalLineTo(20f)
            moveTo(10f, 3f)
            lineTo(8f, 21f)
            moveTo(16f, 3f)
            lineTo(14f, 21f)
        }
    }.build()
}

internal val HomeCalendarIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "HomeCalendar",
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
            moveTo(4f, 5f)
            arcToRelative(3f, 3f, 0f, false, true, 3f, -3f)
            horizontalLineTo(17f)
            arcToRelative(3f, 3f, 0f, false, true, 3f, 3f)
            verticalLineTo(19f)
            arcToRelative(3f, 3f, 0f, false, true, -3f, 3f)
            horizontalLineTo(7f)
            arcToRelative(3f, 3f, 0f, false, true, -3f, -3f)
            close()
            moveTo(4f, 10f)
            horizontalLineTo(20f)
            moveTo(9f, 3f)
            verticalLineTo(7f)
            moveTo(15f, 3f)
            verticalLineTo(7f)
        }
    }.build()
}

internal val HomeDownloadIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "HomeDownload",
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
            moveTo(21f, 15f)
            verticalLineToRelative(4f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
            horizontalLineTo(5f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
            verticalLineToRelative(-4f)
            moveTo(7f, 10f)
            lineTo(12f, 15f)
            lineTo(17f, 10f)
            moveTo(12f, 15f)
            verticalLineTo(3f)
        }
    }.build()
}

internal val HomeAccessibilityIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "HomeAccessibility",
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
            moveTo(18f, 19f)
            lineTo(19f, 12f)
            lineTo(13f, 13f)
            moveTo(5f, 8f)
            lineTo(8f, 5f)
            lineTo(13.5f, 8f)
            lineTo(11.14f, 11.5f)
            moveTo(4.24f, 14.5f)
            arcToRelative(5f, 5f, 0f, false, true, 6.88f, 6f)
            moveTo(13.76f, 17.5f)
            arcToRelative(5f, 5f, 0f, false, false, -6.88f, -6f)
        }
    }.build()
}
