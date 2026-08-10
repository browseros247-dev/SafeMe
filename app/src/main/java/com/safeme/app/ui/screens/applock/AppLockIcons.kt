package com.safeme.app.ui.screens.applock

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
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

/** Padlock (hero, setup CTA, unlock card, settings rows, password field). */
val AlLockIcon: ImageVector by lazy {
    iconBuilder("AlLock").apply {
        strokePath {
            moveTo(12f, 3f)
            curveToRelative(-2.21f, 0f, -4f, 1.79f, -4f, 4f)
            verticalLineTo(10f)
            horizontalLineTo(6f)
            curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
            verticalLineTo(20f)
            curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
            horizontalLineTo(18f)
            curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
            verticalLineTo(11f)
            curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
            horizontalLineTo(16f)
            verticalLineTo(7f)
            curveToRelative(0f, -2.21f, -1.79f, -4f, -4f, -4f)
            close()
        }
        strokePath {
            moveTo(12f, 14f)
            verticalLineTo(17f)
        }
    }.build()
}

/** Clock (auto-lock row). */
val AlClockIcon: ImageVector by lazy {
    iconBuilder("AlClock").apply {
        strokePath {
            moveTo(12f, 3f)
            curveToRelative(-4.97f, 0f, -9f, 4.03f, -9f, 9f)
            reflectiveCurveToRelative(4.03f, 9f, 9f, 9f)
            reflectiveCurveToRelative(9f, -4.03f, 9f, -9f)
            reflectiveCurveToRelative(-4.03f, -9f, -9f, -9f)
            close()
        }
        strokePath {
            moveTo(12f, 7f)
            verticalLineTo(12f)
            lineTo(15f, 14f)
        }
    }.build()
}

/** Backspace (keypad delete). */
val AlBackspaceIcon: ImageVector by lazy {
    iconBuilder("AlBackspace").apply {
        strokePath {
            moveTo(22f, 3f)
            horizontalLineTo(7f)
            curveToRelative(-0.69f, 0f, -1.23f, 0.35f, -1.59f, 0.88f)
            lineTo(0f, 12f)
            lineTo(5.41f, 20.11f)
            curveToRelative(0.36f, 0.53f, 0.9f, 0.89f, 1.59f, 0.89f)
            horizontalLineTo(22f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(5f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            close()
        }
        strokePath {
            moveTo(15f, 9f)
            lineTo(9f, 15f)
            moveTo(9f, 9f)
            lineTo(15f, 15f)
        }
    }.build()
}

/** Chevron right (setup CTA, settings rows). */
val AlChevronRightIcon: ImageVector by lazy {
    iconBuilder("AlChevronRight").apply {
        strokePath {
            moveTo(9f, 5f)
            lineTo(16f, 12f)
            lineTo(9f, 19f)
        }
    }.build()
}

/** Plain shield (note at the bottom of the screen). */
val AlShieldIcon: ImageVector by lazy {
    iconBuilder("AlShield").apply {
        strokePath {
            moveTo(12f, 2f)
            lineTo(19f, 5f)
            verticalLineTo(10f)
            curveToRelative(0f, 4.5f, -3f, 7.5f, -7f, 9f)
            curveToRelative(-4f, -1.5f, -7f, -4.5f, -7f, -9f)
            verticalLineTo(5f)
            close()
        }
    }.build()
}

/** Fingerprint (unlock gate biometric option). */
val AlFingerprintIcon: ImageVector by lazy {
    iconBuilder("AlFingerprint").apply {
        addPath(
            pathData = addPathNodes(
                "M17.81 4.47c-.08 0-.16-.02-.23-.06C15.66 3.42 14 3 12.01 3c-1.98 0-3.86.47-5.57 1.41-.24.13-.54.04-.68-.2-.13-.24-.04-.55.2-.68C7.82 2.52 9.86 2 12.01 2c2.13 0 3.99.47 6.03 1.52.25.13.34.44.21.67-.09.18-.26.28-.44.28zM3.5 9.72c-.1 0-.2-.03-.29-.09-.23-.16-.28-.47-.12-.7.99-1.4 2.25-2.5 3.75-3.27C9.98 4.04 14 4.03 17.15 5.65c1.5.77 2.76 1.86 3.75 3.26.16.23.11.54-.12.7-.23.16-.54.11-.7-.12-.9-1.26-2.04-2.25-3.39-2.94-2.87-1.47-6.54-1.47-9.4.01-1.36.7-2.5 1.7-3.4 2.96-.08.14-.23.2-.39.2zm-1.15 3.49c-.15 0-.3-.07-.39-.2-.15-.24-.08-.55.16-.7 1.27-.87 2.68-1.52 4.2-1.94 2.94-.82 6.09-.82 9.03 0 1.52.42 2.93 1.07 4.2 1.94.24.16.31.46.16.7-.16.24-.46.31-.7.16-1.14-.81-2.45-1.41-3.83-1.8-2.72-.76-5.64-.76-8.36 0-1.38.39-2.69.99-3.83 1.8-.1.07-.21.1-.32.1zm12.8 3.54c-.17 0-.33-.09-.42-.24-.36-.62-.84-1.16-1.39-1.6-.39-.32-.8-.58-1.21-.79-.08-.04-.16-.09-.24-.14-.15-.1-.33-.14-.51-.14-.05 0-.11.01-.16.02-.16.04-.31.09-.45.15-.61.27-1.14.66-1.58 1.17-.13.15-.36.17-.51.04-.15-.13-.17-.36-.04-.51.53-.6 1.17-1.07 1.9-1.38.16-.07.34-.13.51-.17.08-.02.16-.03.24-.04.12-.01.25-.01.37.01.09.01.18.03.27.07.28.12.54.28.79.47.62.5 1.16 1.11 1.56 1.79.13.25.04.55-.21.68-.08.05-.17.07-.26.07zm-5.16 6.16c-.13 0-.26-.05-.35-.15-.16-.17-.15-.43.01-.59.24-.23.42-.51.55-.83.08-.19.29-.28.48-.2.19.08.28.29.2.48-.17.42-.42.81-.74 1.12-.09.09-.1.17-.15.17zm4.55-2.37c-.2 0-.39-.12-.47-.31-.1-.25.02-.53.27-.63 1.16-.46 2.17-1.18 2.96-2.1.11-.13.31-.14.44-.03.13.11.14.31.03.44-.87 1.01-1.98 1.8-3.25 2.3-.05.02-.11.03-.17.03zm2.98-4.42c-.12 0-.24-.04-.33-.13-.15-.17-.13-.43.04-.58.51-.44 1.28-1.51 1.28-2.4 0-.28-.05-.56-.15-.83-.08-.21.03-.44.24-.52.21-.08.44.03.52.24.12.34.19.73.19 1.11 0 1.12-.9 2.36-1.56 2.92-.08.07-.17.11-.27.11zm-8.23 5.07c-.11 0-.22-.04-.3-.12-.39-.39-.89-.58-1.44-.58-.08 0-.16.01-.23.02-.22.03-.42.07-.61.12-.35.1-.72.15-1.1.15-1.06 0-2.09-.4-2.85-1.12-.81-.75-1.25-1.77-1.25-2.86 0-1.09.44-2.11 1.25-2.86.76-.71 1.79-1.12 2.85-1.12.37 0 .73.04 1.09.15.16.04.34.06.52.06.42 0 .82-.09 1.19-.27.21-.1.46-.02.56.19.1.21.02.46-.19.56-.47.23-1 .35-1.56.35-.27 0-.54-.03-.8-.09-.28-.08-.57-.12-.86-.12-.84 0-1.63.32-2.22.89-.59.57-.92 1.33-.92 2.14s.33 1.57.92 2.14c.59.57 1.38.89 2.22.89.29 0 .58-.04.86-.12.24-.07.49-.11.75-.11.44 0 .86.08 1.25.23.24.09.36.36.27.6-.06.18-.23.29-.4.29zm-1.77 3.31c-.17 0-.33-.08-.43-.23-.3-.44-.45-.95-.45-1.49 0-.27.03-.54.09-.81.05-.22.27-.36.49-.31.22.05.36.27.31.49-.05.19-.08.39-.08.63 0 .38.11.74.31 1.05.13.18.09.43-.1.56-.07.05-.15.08-.24.08z"
            ),
            fill = SolidColor(Color.Black),
        )
    }.build()
}

/** Check (radio checkbox + setup success). */
val AlCheckIcon: ImageVector by lazy {
    iconBuilder("AlCheck").apply {
        strokePath {
            moveTo(5f, 12f)
            lineTo(10f, 17f)
            lineTo(20f, 6f)
        }
    }.build()
}

/** Keyboard (password method row). */
val AlKeyboardIcon: ImageVector by lazy {
    iconBuilder("AlKeyboard").apply {
        strokePath {
            moveTo(3f, 6f)
            curveToRelative(0f, -1.1f, 0.9f, -2f, 2f, -2f)
            horizontalLineTo(19f)
            curveToRelative(1.1f, 0f, 2f, 0.9f, 2f, 2f)
            verticalLineTo(18f)
            curveToRelative(0f, 1.1f, -0.9f, 2f, -2f, 2f)
            horizontalLineTo(5f)
            curveToRelative(-1.1f, 0f, -2f, -0.9f, -2f, -2f)
            close()
        }
        strokePath {
            moveTo(7f, 10f)
            horizontalLineTo(7.01f)
            moveTo(11f, 10f)
            horizontalLineTo(11.01f)
            moveTo(15f, 10f)
            horizontalLineTo(15.01f)
            moveTo(7f, 14f)
            horizontalLineTo(17f)
        }
    }.build()
}

/** 3x3 dot grid (pattern method row). */
val AlPatternIcon: ImageVector by lazy {
    iconBuilder("AlPattern").apply {
        strokePath {
            moveTo(6f, 6f)
            horizontalLineTo(6.01f)
            moveTo(12f, 6f)
            horizontalLineTo(12.01f)
            moveTo(18f, 6f)
            horizontalLineTo(18.01f)
            moveTo(6f, 12f)
            horizontalLineTo(6.01f)
            moveTo(12f, 12f)
            horizontalLineTo(12.01f)
            moveTo(18f, 12f)
            horizontalLineTo(18.01f)
            moveTo(6f, 18f)
            horizontalLineTo(6.01f)
            moveTo(12f, 18f)
            horizontalLineTo(12.01f)
            moveTo(18f, 18f)
            horizontalLineTo(18.01f)
        }
    }.build()
}
