package com.safeme.app.ui.screens.profile

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal val ProfShareIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ProfShare",
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
            moveTo(6f, 12f)
            arcToRelative(3f, 3f, 0f, false, true, 0f, 6f)
            arcToRelative(3f, 3f, 0f, false, true, 0f, -6f)
            moveTo(18f, 6f)
            arcToRelative(3f, 3f, 0f, false, true, 0f, 6f)
            arcToRelative(3f, 3f, 0f, false, true, 0f, -6f)
            moveTo(18f, 18f)
            arcToRelative(3f, 3f, 0f, false, true, 0f, 6f)
            arcToRelative(3f, 3f, 0f, false, true, 0f, -6f)
            moveTo(8.7f, 10.7f)
            lineTo(15.3f, 7.3f)
            moveTo(8.7f, 13.3f)
            lineTo(15.3f, 16.7f)
        }
    }.build()
}

internal val ProfMoonIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ProfMoon",
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
            moveTo(21f, 12.8f)
            arcTo(8.5f, 8.5f, 0f, false, true, 11.2f, 3f)
            arcTo(6.5f, 6.5f, 0f, false, false, 21f, 12.8f)
            close()
        }
    }.build()
}

internal val ProfLockIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ProfLock",
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
            moveTo(7f, 11f)
            horizontalLineTo(17f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
            verticalLineTo(19f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
            horizontalLineTo(7f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
            verticalLineTo(13f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
            close()
            moveTo(8f, 11f)
            verticalLineTo(7f)
            arcToRelative(4f, 4f, 0f, false, true, 8f, 0f)
            verticalLineToRelative(4f)
        }
    }.build()
}

internal val ProfPersonIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ProfPerson",
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
            moveTo(12f, 12f)
            arcToRelative(4f, 4f, 0f, false, true, 0f, -8f)
            arcToRelative(4f, 4f, 0f, false, true, 0f, 8f)
            close()
            moveTo(4f, 21f)
            verticalLineTo(20f)
            arcToRelative(7f, 7f, 0f, false, true, 14f, 0f)
            verticalLineToRelative(1f)
        }
    }.build()
}

internal val ProfDownloadIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ProfDownload",
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
            verticalLineTo(19f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
            horizontalLineTo(5f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
            verticalLineTo(15f)
            moveTo(7f, 10f)
            lineTo(12f, 15f)
            lineTo(17f, 10f)
            moveTo(12f, 15f)
            verticalLineTo(3f)
        }
    }.build()
}

internal val ProfShieldIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ProfShield",
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
            verticalLineTo(10f)
            curveToRelative(0f, 4.5f, -3f, 7.5f, -7f, 9f)
            curveToRelative(-4f, -1.5f, -7f, -4.5f, -7f, -9f)
            verticalLineTo(6f)
            close()
        }
    }.build()
}

internal val ProfShieldCheckIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ProfShieldCheck",
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
            verticalLineTo(10f)
            curveToRelative(0f, 4.5f, -3f, 7.5f, -7f, 9f)
            curveToRelative(-4f, -1.5f, -7f, -4.5f, -7f, -9f)
            verticalLineTo(6f)
            close()
            moveTo(9f, 12f)
            lineTo(11f, 14f)
            lineTo(15f, 10f)
        }
    }.build()
}

internal val ProfChevIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ProfChev",
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

internal val ProfPlayCircleIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ProfPlayCircle",
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
            moveTo(10f, 8f)
            lineTo(16f, 12f)
            lineTo(10f, 16f)
            close()
        }
    }.build()
}

internal val ProfCrashIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ProfCrash",
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
            moveTo(8f, 2f)
            lineTo(9.88f, 3.88f)
            moveTo(14.12f, 3.88f)
            lineTo(16f, 2f)
            moveTo(9f, 7.13f)
            verticalLineTo(6.13f)
            arcToRelative(3.003f, 3.003f, 0f, false, true, 6f, 0f)
            verticalLineTo(7.13f)
            moveTo(12f, 20f)
            curveToRelative(-3.3f, 0f, -6f, -2.7f, -6f, -6f)
            verticalLineTo(11f)
            arcToRelative(4f, 4f, 0f, false, true, 4f, -4f)
            horizontalLineTo(14f)
            arcToRelative(4f, 4f, 0f, false, true, 4f, 4f)
            verticalLineTo(14f)
            curveToRelative(0f, 3.3f, -2.7f, 6f, -6f, 6f)
            moveTo(12f, 20f)
            verticalLineTo(11f)
        }
    }.build()
}

internal val ProfInfoIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ProfInfo",
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
            moveTo(12f, 11f)
            verticalLineTo(16f)
            moveTo(12f, 8f)
            horizontalLineTo(12.01f)
        }
    }.build()
}

internal val ProfChatIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ProfChat",
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
            horizontalLineTo(20f)
            verticalLineTo(19f)
            horizontalLineTo(4f)
            close()
            moveTo(4f, 6f)
            lineTo(12f, 13f)
            lineTo(20f, 6f)
        }
    }.build()
}

internal val ProfLinkIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ProfLink",
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
            moveTo(10f, 13f)
            arcToRelative(5f, 5f, 0f, false, false, 7.5f, 0.5f)
            lineToRelative(3f, -3f)
            arcToRelative(5f, 5f, 0f, false, false, -7f, -7f)
            lineToRelative(-1.7f, 1.7f)
            moveTo(14f, 11f)
            arcToRelative(5f, 5f, 0f, false, false, -7.5f, -0.5f)
            lineToRelative(-3f, 3f)
            arcToRelative(5f, 5f, 0f, false, false, 7f, 7f)
            lineToRelative(1.7f, -1.7f)
        }
    }.build()
}

internal val ProfGlobeIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ProfGlobe",
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
            moveTo(12f, 3f)
            arcToRelative(14f, 14f, 0f, false, false, 0f, 18f)
            moveTo(12f, 3f)
            arcToRelative(14f, 14f, 0f, false, true, 0f, 18f)
            moveTo(3f, 12f)
            horizontalLineTo(21f)
        }
    }.build()
}
