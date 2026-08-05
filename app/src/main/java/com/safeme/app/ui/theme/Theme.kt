package com.safeme.app.ui.theme

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import com.safeme.app.data.ThemePref
import com.safeme.app.data.themePref

private val SafeMeColorScheme = lightColorScheme(
    primary = Brand,
    onPrimary = Surface,
    primaryContainer = BrandSoft,
    onPrimaryContainer = Ink,
    secondary = BrandDark,
    onSecondary = Surface,
    background = Background,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = Line,
    onSurfaceVariant = Ink2,
    outline = Ink3,
    error = Danger,
    onError = Surface,
)

private val SafeMeDarkColorScheme = darkColorScheme(
    primary = DarkAppColors.brand,
    onPrimary = DarkAppColors.surface,
    primaryContainer = DarkAppColors.brandSoft,
    onPrimaryContainer = DarkAppColors.ink,
    secondary = DarkAppColors.brandDark,
    onSecondary = DarkAppColors.surface,
    background = DarkAppColors.background,
    onBackground = DarkAppColors.ink,
    surface = DarkAppColors.surface,
    onSurface = DarkAppColors.ink,
    surfaceVariant = DarkAppColors.line,
    onSurfaceVariant = DarkAppColors.ink2,
    outline = DarkAppColors.ink3,
    error = DarkAppColors.danger,
    onError = DarkAppColors.surface,
)

@Composable
fun SafeMeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val appColors = if (darkTheme) DarkAppColors else LightAppColors
    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) SafeMeDarkColorScheme else SafeMeColorScheme,
            typography = SafeMeTypography,
            content = content,
        )
    }
}

@Composable
fun SafeMeApp(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val themePref by context.themePref().collectAsState(initial = ThemePref.SYSTEM)
    val darkTheme = when (themePref) {
        ThemePref.SYSTEM -> isSystemInDarkTheme()
        ThemePref.DARK -> true
        ThemePref.LIGHT -> false
    }
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        // Resolve the host window safely. view.context is not guaranteed to be
        // an Activity (it can be a ContextThemeWrapper), so a direct cast would
        // crash with ClassCastException on some devices/themes.
        val activity = view.context.findActivity()
        if (activity != null) {
            SideEffect {
                val window = activity.window
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
            }
        }
    }
    SafeMeTheme(darkTheme = darkTheme, content = content)
}

/**
 * Walks the ContextWrapper chain to find the host [android.app.Activity].
 * Returns null when no activity is reachable (e.g. previews), so callers can
 * skip activity-only work instead of crashing.
 */
tailrec fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}