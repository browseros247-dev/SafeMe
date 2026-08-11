package com.safeme.app.ui.screens.backup

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

/** Feather "download" — save backup to a file. */
val BackupExportIcon: ImageVector by lazy {
    iconBuilder("BackupExport").apply {
        strokePath {
            moveTo(21f, 15f)
            lineToRelative(0f, 4f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
            lineTo(5f, 21f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
            lineToRelative(0f, -4f)
        }
        strokePath {
            moveTo(7f, 10f)
            lineTo(12f, 15f)
            lineTo(17f, 10f)
        }
        strokePath {
            moveTo(12f, 15f)
            verticalLineTo(3f)
        }
    }.build()
}

/** Feather "upload" — restore from a file. */
val BackupImportIcon: ImageVector by lazy {
    iconBuilder("BackupImport").apply {
        strokePath {
            moveTo(21f, 15f)
            lineToRelative(0f, 4f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
            lineTo(5f, 21f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
            lineToRelative(0f, -4f)
        }
        strokePath {
            moveTo(17f, 8f)
            lineTo(12f, 3f)
            lineTo(7f, 8f)
        }
        strokePath {
            moveTo(12f, 3f)
            verticalLineTo(15f)
        }
    }.build()
}
