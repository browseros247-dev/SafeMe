package com.safeme.app.ui.screens.focus

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal val FocPlayIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "FocPlay",
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
            moveTo(6f, 4f)
            lineTo(20f, 12f)
            lineTo(6f, 20f)
            close()
        }
    }.build()
}

internal val FocCalendarIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "FocCalendar",
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
            moveTo(7f, 5f)
            horizontalLineTo(17f)
            arcToRelative(3f, 3f, 0f, false, true, 3f, 3f)
            verticalLineTo(18f)
            arcToRelative(3f, 3f, 0f, false, true, -3f, 3f)
            horizontalLineTo(7f)
            arcToRelative(3f, 3f, 0f, false, true, -3f, -3f)
            verticalLineTo(8f)
            arcToRelative(3f, 3f, 0f, false, true, 3f, -3f)
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

internal val FocGridIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "FocGrid",
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
            moveTo(5f, 3f)
            horizontalLineTo(8f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
            verticalLineTo(8f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
            horizontalLineTo(5f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
            verticalLineTo(5f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
            close()
            moveTo(16f, 3f)
            horizontalLineTo(19f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
            verticalLineTo(8f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
            horizontalLineTo(16f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
            verticalLineTo(5f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
            close()
            moveTo(5f, 14f)
            horizontalLineTo(8f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
            verticalLineTo(19f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
            horizontalLineTo(5f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
            verticalLineTo(16f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
            close()
            moveTo(16f, 14f)
            horizontalLineTo(19f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
            verticalLineTo(19f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
            horizontalLineTo(16f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
            verticalLineTo(16f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
            close()
        }
    }.build()
}

internal val FocWidgetIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "FocWidget",
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
            moveTo(5f, 3f)
            horizontalLineTo(19f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
            verticalLineTo(9f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
            horizontalLineTo(5f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
            verticalLineTo(5f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
            close()
            moveTo(5f, 13f)
            horizontalLineTo(19f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
            verticalLineTo(19f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
            horizontalLineTo(5f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
            verticalLineTo(15f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
            close()
        }
    }.build()
}

internal val FocInfoIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "FocInfo",
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
            moveTo(12f, 8f)
            horizontalLineTo(12.01f)
            moveTo(11f, 12f)
            horizontalLineTo(12f)
            verticalLineTo(16f)
            horizontalLineTo(13f)
        }
    }.build()
}
