package com.safeme.app.ui.screens.vpn

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
        name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )

private val stroke = SolidColor(Color.Black)

private fun ImageVector.Builder.strokePath(
    pathBuilder: PathBuilder.() -> Unit,
) {
    path(
        fill = null,
        stroke = stroke,
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = pathBuilder,
    )
}

private fun ImageVector.Builder.fillPath(pathBuilder: PathBuilder.() -> Unit) {
    path(fill = stroke, pathBuilder = pathBuilder)
}

val VpnBackIcon: ImageVector by lazy {
    iconBuilder("VpnBackIcon").apply {
        strokePath {
            moveTo(15f, 5f)
            lineTo(8f, 12f)
            lineTo(15f, 19f)
        }
    }.build()
}

val VpnCheckIcon: ImageVector by lazy {
    iconBuilder("VpnCheckIcon").apply {
        strokePath {
            moveTo(5f, 12f)
            lineTo(10f, 17f)
            lineTo(20f, 6f)
        }
    }.build()
}

val VpnShieldIcon: ImageVector by lazy {
    iconBuilder("VpnShieldIcon").apply {
        strokePath {
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

val VpnCustomSquareIcon: ImageVector by lazy {
    iconBuilder("VpnCustomSquareIcon").apply {
        strokePath {
            moveTo(8f, 4f)
            horizontalLineTo(16f)
            arcToRelative(4f, 4f, 0f, false, true, 4f, 4f)
            verticalLineToRelative(8f)
            arcToRelative(4f, 4f, 0f, false, true, -4f, 4f)
            horizontalLineTo(8f)
            arcToRelative(4f, 4f, 0f, false, true, -4f, -4f)
            verticalLineTo(8f)
            arcToRelative(4f, 4f, 0f, false, true, 4f, -4f)
            close()
            moveTo(8f, 12f)
            horizontalLineTo(16f)
        }
    }.build()
}

val VpnSwapIcon: ImageVector by lazy {
    iconBuilder("VpnSwapIcon").apply {
        strokePath {
            moveTo(17f, 7f)
            lineTo(22f, 12f)
            lineTo(17f, 17f)
            moveTo(7f, 17f)
            lineTo(2f, 12f)
            lineTo(7f, 7f)
        }
    }.build()
}

val VpnBellIcon: ImageVector by lazy {
    iconBuilder("VpnBellIcon").apply {
        strokePath {
            moveTo(12f, 3f)
            arcToRelative(6f, 6f, 0f, false, true, 6f, 6f)
            verticalLineToRelative(5f)
            lineToRelative(2f, 2f)
            horizontalLineTo(4f)
            lineToRelative(2f, -2f)
            verticalLineTo(9f)
            arcToRelative(6f, 6f, 0f, false, true, 6f, -6f)
            close()
            moveTo(10f, 20f)
            horizontalLineToRelative(4f)
        }
    }.build()
}

val VpnInfoIcon: ImageVector by lazy {
    iconBuilder("VpnInfoIcon").apply {
        strokePath {
            moveTo(12f, 12f)
            moveToRelative(-9f, 0f)
            arcToRelative(9f, 9f, 0f, true, false, 18f, 0f)
            arcToRelative(9f, 9f, 0f, true, false, -18f, 0f)
            moveTo(12f, 8f)
            horizontalLineToRelative(0.01f)
            moveTo(11f, 12f)
            horizontalLineToRelative(1f)
            verticalLineToRelative(4f)
            horizontalLineToRelative(1f)
        }
    }.build()
}

val VpnFieldShieldIcon: ImageVector by lazy {
    iconBuilder("VpnFieldShieldIcon").apply {
        strokePath {
            moveTo(12f, 3f)
            lineTo(19f, 6f)
            verticalLineToRelative(5f)
            curveToRelative(0f, 4.5f, -3f, 7.5f, -7f, 9f)
            curveToRelative(-4f, -1.5f, -7f, -4.5f, -7f, -9f)
            verticalLineTo(6f)
            close()
        }
    }.build()
}

val VpnSearchIcon: ImageVector by lazy {
    iconBuilder("VpnSearchIcon").apply {
        strokePath {
            moveTo(11f, 11f)
            moveToRelative(-7f, 0f)
            arcToRelative(7f, 7f, 0f, true, false, 14f, 0f)
            arcToRelative(7f, 7f, 0f, true, false, -14f, 0f)
            moveTo(21f, 21f)
            lineTo(16.7f, 16.7f)
        }
    }.build()
}
