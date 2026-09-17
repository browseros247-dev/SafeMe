package com.safeme.app.ui.screens.socialblocking

import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

/**
 * Bounded icon cache — 48 entries covers the default rows plus a long picker
 * scroll without re-decoding; thread-safe (android.util.LruCache). Entries are
 * small (density-scaled ~96 px ARGB_8888 ≈ 36 KB each → ~1.7 MB ceiling).
 */
private val appIconCache = LruCache<String, ImageBitmap>(48)

/** Packages already known to be uninstalled/resolved-to-nothing — avoids repeat binder calls per recomposition. */
private val appIconMisses = java.util.Collections.synchronizedSet(mutableSetOf<String>())

/**
 * Real launcher icon of an installed app, with a graceful fallback.
 *
 * Tries each package in [packageNames] in order and renders the icon of the
 * FIRST installed one (family support: TikTok/Instagram ship multiple
 * packages per logical app). Decoding runs on IO via [produceState] and goes
 * through `Drawable.toBitmap`, which — unlike a `BitmapDrawable` cast —
 * handles adaptive icons (most modern apps), plain bitmaps and vectors
 * uniformly.
 *
 * When no listed package is installed (or the icon can't be decoded) the
 * [fallback] slot renders instead, so rows keep their previous pastel-letter
 * look on devices without those apps. Pure rendering — no state, no prefs.
 */
@Composable
fun InstalledAppIcon(
    packageNames: List<String>,
    size: Dp,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val cacheKey = packageNames.joinToString(",")
    val icon by produceState<ImageBitmap?>(initialValue = appIconCache.get(cacheKey), key1 = cacheKey) {
        if (value == null && !appIconMisses.contains(cacheKey)) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val pm = context.packageManager
                    val px = with(context.resources.displayMetrics) {
                        (size.value * density * 2f).toInt().coerceIn(48, 256)
                    }
                    var decoded: ImageBitmap? = null
                    for (pkg in packageNames) {
                        val drawable = runCatching { pm.getApplicationIcon(pkg) }.getOrNull() ?: continue
                        decoded = runCatching { drawable.toBitmap(px, px).asImageBitmap() }.getOrNull()
                        if (decoded != null) break
                    }
                    if (decoded != null) appIconCache.put(cacheKey, decoded) else appIconMisses.add(cacheKey)
                    decoded
                }.getOrNull()
            }
        }
    }
    val current = icon
    if (current != null) {
        Image(bitmap = current, contentDescription = null, modifier = modifier.size(size))
    } else {
        fallback()
    }
}
