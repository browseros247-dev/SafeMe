package com.safeme.app.ui.screens.socialblocking

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Minimal icons for Social Media Blocking — reused where BlockingIcons would
 * bloat coupling. Brand tint comes from LocalAppColors at call-site.
 */

val SocialShieldIcon: ImageVector = ImageVector.Builder(
    name = "SocialShield", defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(12f, 2f); lineTo(20f, 5f); verticalLineTo(11f); curveTo(20f, 16f, 17f, 20f, 12f, 22f); curveTo(7f, 20f, 4f, 16f, 4f, 11f); verticalLineTo(5f); lineTo(12f, 2f); close()
        moveTo(9f, 12f); lineTo(11f, 14f); lineTo(15f, 10f)
    }
}.build()

val SocialBackIcon: ImageVector = ImageVector.Builder(
    name = "SocialBack", defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) { moveTo(15f, 5f); lineTo(7f, 12f); lineTo(15f, 19f) }
}.build()
