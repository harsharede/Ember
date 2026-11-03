package com.harsharede.ember

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Colours that don't come from the wallpaper. */
object Fixed {
    val Ember = Color(0xFFFF7A3D)
    val Green = Color(0xFF34C759)
    val Yellow = Color(0xFFFFD60A)
    val Red = Color(0xFFFF453A)
}

/**
 * Ember's palette, read from the Material colour scheme so it follows the wallpaper (Material You) or Ember's own
 * orange, whichever is chosen in Settings.
 */
object EmberColors {
    val Ember: Color @Composable get() = MaterialTheme.colorScheme.primary
    val EmberSoft: Color @Composable get() = MaterialTheme.colorScheme.primary.copy(alpha = .9f)
    val Glass: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
    val GlassStrong: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainer
    val Secondary: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val Red: Color get() = Fixed.Red
}

private val EmberScheme = darkColorScheme(
    primary = Fixed.Ember, onPrimary = Color.Black,
    primaryContainer = Color(0xFF5A2410), onPrimaryContainer = Color(0xFFFFDBCC),
    secondary = Color(0xFFFFB38F), onSecondary = Color.Black,
    secondaryContainer = Color(0xFF4A2A1C), onSecondaryContainer = Color(0xFFFFDBCC),
    surface = Color(0xFF121212), onSurface = Color.White, onSurfaceVariant = Color(0xFFB4B4BA),
    surfaceContainerLowest = Color(0xFF0B0B0C), surfaceContainerLow = Color(0xFF161618), surfaceContainer = Color(0xFF1C1C1F),
    surfaceContainerHigh = Color(0xFF2A2A2E), surfaceContainerHighest = Color(0xFF34343A),
    background = Color.Black, onBackground = Color.White, outlineVariant = Color(0xFF3A3A40),
)

/**
 * Dark Material 3. With "Wallpaper colours" on (the default), Android 12+ builds the scheme from the wallpaper, so the
 * accent, cards and highlights all change with it.
 */
@Composable
fun EmberTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val fromWallpaper by EmberApp.instance.prefs.wallpaperColors.collectAsState()
    val seed by WallpaperTheme.seed.collectAsState()
    // Built from the wallpaper itself; Android's dynamic scheme only until the wallpaper's colours have been read.
    val base = when {
        !fromWallpaper -> EmberScheme
        seed != null -> remember(seed) { WallpaperTheme.scheme(seed!!) }
        else -> dynamicDarkColorScheme(context)
    }
    // One glass for everything: every surface (cards, menus, dialogs, sheets, settings) is a near-black smoked glass with a
    // whisper of the accent, instead of Material's lighter grey-lilac containers, which looked milky over photos.
    val ink = Color(0xFF0D0E11)
    fun tone(lift: Float) = androidx.compose.ui.graphics.lerp(androidx.compose.ui.graphics.lerp(ink, Color.White, lift), base.primary, .05f)
    val scheme = base.copy(
        surface = tone(.02f), surfaceDim = tone(.0f), surfaceBright = tone(.10f),
        surfaceContainerLowest = tone(.0f), surfaceContainerLow = tone(.03f), surfaceContainer = tone(.05f),
        surfaceContainerHigh = tone(.08f), surfaceContainerHighest = tone(.12f),
        background = Color.Black, onSurface = Color.White, onSurfaceVariant = Color(0xFFB9BAC2),
        outline = Color.White.copy(alpha = .22f), outlineVariant = Color.White.copy(alpha = .10f),
    )
    val shapes = androidx.compose.material3.Shapes(
        extraSmall = RoundedCornerShape(14.dp), small = RoundedCornerShape(16.dp), medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(32.dp),
    )
    // Text with no colour of its own is light on Ember's dark surfaces (it was black, unreadable, outside a Surface).
    MaterialTheme(colorScheme = scheme, shapes = shapes) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides scheme.onSurface, content = content)
    }
}

/** How solid glass surfaces are; Home sets it from "Card transparency". */
val LocalGlassAlpha = androidx.compose.runtime.compositionLocalOf { .72f }

/**
 * Ember's glass: smoked, near-black glass (so photos behind stay rich, not washed out), a soft light falling from the
 * top, and a hairline edge that catches the light on the upper left like real glass.
 */
@Composable
fun Modifier.glass(shape: Shape = RoundedCornerShape(28.dp), alpha: Float = LocalGlassAlpha.current): Modifier {
    val fill = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = alpha)
    return this
        .clip(shape)
        .background(fill)
        .drawBehind {
            drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = .10f), .3f to Color.White.copy(alpha = .02f), 1f to Color.Transparent))
        }
        .border(1.dp, Brush.linearGradient(0f to Color.White.copy(alpha = .30f), .45f to Color.White.copy(alpha = .06f),
            1f to Color.White.copy(alpha = .14f)), shape)
}

@Composable
fun AppIcon(app: AppEntry, size: Dp, modifier: Modifier = Modifier) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val version by EmberApp.instance.apps.iconVersion.collectAsState()
    val icon by produceState<ImageBitmap?>(null, app.key, px, version) {
        value = withContext(Dispatchers.IO) { EmberApp.instance.apps.icon(app, px) }
    }
    IconImage(icon, size, modifier)
}

@Composable
fun PackageIcon(packageName: String, size: Dp, modifier: Modifier = Modifier) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val version by EmberApp.instance.apps.iconVersion.collectAsState()
    val icon by produceState<ImageBitmap?>(null, packageName, px, version) {
        value = withContext(Dispatchers.IO) { EmberApp.instance.apps.iconForPackage(packageName, px) }
    }
    IconImage(icon, size, modifier)
}

@Composable
private fun IconImage(icon: ImageBitmap?, size: Dp, modifier: Modifier) {
    if (icon != null) Image(icon, contentDescription = null, modifier = modifier.size(size))
    else Box(modifier.size(size).clip(RoundedCornerShape(size / 3)).background(Color.White.copy(alpha = .08f)))
}

/** "now", "5m", "2h", then the time today, "Yesterday", or the date. */
fun relativeTime(time: Long, now: Long, locale: Locale = Locale.getDefault()): String {
    val diff = (now - time).coerceAtLeast(0)
    val minute = 60_000L
    return when {
        diff < minute -> "now"
        diff < 60 * minute -> "${diff / minute}m"
        diff < 4 * 60 * minute -> "${diff / (60 * minute)}h"
        sameDay(time, now) -> SimpleDateFormat("HH:mm", locale).format(Date(time))
        sameDay(time, now - 24 * 60 * minute) -> "Yesterday"
        else -> SimpleDateFormat("d MMM", locale).format(Date(time))
    }
}

/** Timeline headings for the notification page. */
enum class TimeBucket(val title: String) { NOW("Now"), TODAY("Earlier today"), YESTERDAY("Yesterday"), OLDER("Older") }

fun bucketOf(time: Long, now: Long): TimeBucket = when {
    now - time < 30 * 60_000L -> TimeBucket.NOW
    sameDay(time, now) -> TimeBucket.TODAY
    sameDay(time, now - 24 * 60 * 60_000L) -> TimeBucket.YESTERDAY
    else -> TimeBucket.OLDER
}

private fun sameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

/**
 * Behind full-screen layers (app drawer, notifications, pickers): the wallpaper stays visible, darkened like smoked glass,
 * so they belong to the same glass as Home instead of being flat panels.
 */
fun Modifier.layerBackdrop(): Modifier = this.background(
    Brush.verticalGradient(0f to Color.Black.copy(alpha = .70f), .5f to Color.Black.copy(alpha = .62f), 1f to Color.Black.copy(alpha = .78f)))

/** Behind sticky headers in those layers: just enough to keep a heading readable over the list scrolling under it. */
val HeaderScrim = Color.Black.copy(alpha = .55f)
