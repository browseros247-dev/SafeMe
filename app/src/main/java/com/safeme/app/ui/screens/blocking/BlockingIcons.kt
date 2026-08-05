package com.safeme.app.ui.screens.blocking

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal val BlkHelpIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "BlkHelp",
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
            moveTo(12f, 16f)
            verticalLineTo(12f)
            moveTo(12f, 8f)
            horizontalLineTo(12.01f)
        }
    }.build()
}

internal val BlkShieldCheckIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "BlkShieldCheck",
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
            moveTo(12f, 2f)
            lineTo(20f, 5f)
            verticalLineToRelative(5f)
            curveToRelative(0f, 5f, -3f, 9f, -8f, 12f)
            curveToRelative(-5f, -3f, -8f, -7f, -8f, -12f)
            verticalLineTo(5f)
            close()
            moveTo(9f, 12f)
            lineToRelative(2f, 2f)
            lineToRelative(4f, -4f)
        }
    }.build()
}

internal val BlkMenuIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "BlkMenu",
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
            moveTo(4f, 6f)
            horizontalLineTo(20f)
            moveTo(4f, 12f)
            horizontalLineTo(20f)
            moveTo(4f, 18f)
            horizontalLineTo(20f)
        }
    }.build()
}

internal val BlkChevronRightIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "BlkChevronRight",
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
            moveTo(9f, 5f)
            lineTo(16f, 12f)
            lineTo(9f, 19f)
        }
    }.build()
}

internal val BlkLayersIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "BlkLayers",
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
            moveTo(12f, 2f)
            lineTo(2f, 7f)
            lineToRelative(10f, 5f)
            lineToRelative(10f, -5f)
            lineToRelative(-10f, -5f)
            close()
            moveTo(2f, 17f)
            lineToRelative(10f, 5f)
            lineToRelative(10f, -5f)
            moveTo(2f, 12f)
            lineToRelative(10f, 5f)
            lineToRelative(10f, -5f)
        }
    }.build()
}

internal val BlkGlobeIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "BlkGlobe",
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
            moveTo(2f, 12f)
            arcToRelative(10f, 10f, 0f, false, true, 20f, 0f)
            arcToRelative(10f, 10f, 0f, false, true, -20f, 0f)
            moveTo(12f, 2f)
            arcToRelative(14.5f, 14.5f, 0f, false, true, 0f, 20f)
            arcToRelative(14.5f, 14.5f, 0f, false, true, 0f, -20f)
            moveTo(2f, 12f)
            horizontalLineTo(20f)
        }
    }.build()
}

internal val BlkSquarePlusIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "BlkSquarePlus",
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
            moveTo(4f, 8f)
            arcToRelative(4f, 4f, 0f, false, true, 4f, -4f)
            horizontalLineTo(16f)
            arcToRelative(4f, 4f, 0f, false, true, 4f, 4f)
            verticalLineTo(16f)
            arcToRelative(4f, 4f, 0f, false, true, -4f, 4f)
            horizontalLineTo(8f)
            arcToRelative(4f, 4f, 0f, false, true, -4f, -4f)
            close()
            moveTo(12f, 8f)
            verticalLineTo(16f)
            moveTo(8f, 12f)
            horizontalLineTo(16f)
        }
    }.build()
}

internal val BlkShieldIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "BlkShield",
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
            moveTo(12f, 2f)
            lineTo(20f, 5f)
            verticalLineToRelative(5f)
            curveToRelative(0f, 5f, -3f, 9f, -8f, 12f)
            curveToRelative(-5f, -3f, -8f, -7f, -8f, -12f)
            verticalLineTo(5f)
            close()
        }
    }.build()
}

internal val BlkShieldAlertIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "BlkShieldAlert",
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
            moveTo(12f, 2f)
            lineTo(19f, 5f)
            verticalLineToRelative(5f)
            curveToRelative(0f, 4.5f, -3f, 7.5f, -7f, 9f)
            curveToRelative(-4f, -1.5f, -7f, -4.5f, -7f, -9f)
            verticalLineTo(6f)
            close()
            moveTo(12f, 8f)
            verticalLineTo(12f)
            moveTo(12f, 16f)
            horizontalLineTo(12.01f)
        }
    }.build()
}

internal val BlkCrossIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "BlkCross",
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
            moveTo(5f, 5f)
            horizontalLineTo(19f)
            moveTo(12f, 5f)
            verticalLineTo(19f)
            moveTo(8f, 19f)
            horizontalLineTo(16f)
        }
    }.build()
}
