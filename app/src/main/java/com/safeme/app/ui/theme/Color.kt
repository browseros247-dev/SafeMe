package com.safeme.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppColors(
    val brand: Color,
    val brandDark: Color,
    val brandSoft: Color,
    val brandMist: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val background: Color,
    val surface: Color,
    val line: Color,
    val success: Color,
    val successBg: Color,
    val warning: Color,
    val warningBg: Color,
    val danger: Color,
    val dangerBg: Color,
    val iconGreenBg: Color,
    val iconAmberBg: Color,
    val iconDarkBg: Color,
    val iconDarkFg: Color,
    val swOff: Color,
    val cardDark1: Color,
    val cardDark2: Color,
    val previewLabel: Color,
    val previewMsg: Color,
    val toastBg: Color,
    val toastFg: Color,
)

val LightAppColors = AppColors(
    brand = Color(0xFFD97757),
    brandDark = Color(0xFFB45A3B),
    brandSoft = Color(0xFFFBEFE8),
    brandMist = Color(0xFFFDF8F4),
    ink = Color(0xFF1F1A16),
    ink2 = Color(0xFF6B625A),
    ink3 = Color(0xFFA89E94),
    background = Color(0xFFFAF7F3),
    surface = Color(0xFFFFFFFF),
    line = Color(0xFFEAE3DB),
    success = Color(0xFF2E7D5B),
    successBg = Color(0xFFE7F0EC),
    warning = Color(0xFFC0822B),
    warningBg = Color(0xFFF7EDDD),
    danger = Color(0xFFC4453C),
    dangerBg = Color(0xFFF9E7E5),
    iconGreenBg = Color(0xFFE6F4EA),
    iconAmberBg = Color(0xFFF7EDDD),
    iconDarkBg = Color(0xFF3A2C1F),
    iconDarkFg = Color(0xFFE8B78F),
    swOff = Color(0xFFDDD5CC),
    cardDark1 = Color(0xFF171310),
    cardDark2 = Color(0xFF2A211B),
    previewLabel = Color(0xFF9A8D80),
    previewMsg = Color(0xFFC9BEB3),
    toastBg = Color(0xFF3A332C),
    toastFg = Color(0xFFFFFFFF),
)

val DarkAppColors = AppColors(
    brand = Color(0xFFE08A68),
    brandDark = Color(0xFFC97A5A),
    brandSoft = Color(0xFF3B2A21),
    brandMist = Color(0xFF241E19),
    ink = Color(0xFFF2ECE4),
    ink2 = Color(0xFFC6BCB0),
    ink3 = Color(0xFF8A8077),
    background = Color(0xFF15120E),
    surface = Color(0xFF211C16),
    line = Color(0xFF332B22),
    success = Color(0xFF5FBF92),
    successBg = Color(0xFF22332B),
    warning = Color(0xFFD89A4C),
    warningBg = Color(0xFF3A2E1E),
    danger = Color(0xFFE2685F),
    dangerBg = Color(0xFF3B2321),
    iconGreenBg = Color(0xFF22332B),
    iconAmberBg = Color(0xFF3A2E1E),
    iconDarkBg = Color(0xFF3A2C1F),
    iconDarkFg = Color(0xFFE8B78F),
    swOff = Color(0xFF3A3228),
    cardDark1 = Color(0xFF171310),
    cardDark2 = Color(0xFF2A211B),
    previewLabel = Color(0xFF9A8D80),
    previewMsg = Color(0xFFC9BEB3),
    toastBg = Color(0xFFF2ECE4),
    toastFg = Color(0xFF211C16),
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

val Brand = Color(0xFFD97757)
val BrandDark = Color(0xFFB45A3B)
val BrandSoft = Color(0xFFFBEFE8)

val Ink = Color(0xFF1F1A16)
val Ink2 = Color(0xFF6B625A)
val Ink3 = Color(0xFFA89E94)

val Background = Color(0xFFFAF7F3)
val Surface = Color(0xFFFFFFFF)
val Line = Color(0xFFEAE3DB)

val Success = Color(0xFF2E7D5B)
val Danger = Color(0xFFC4453C)