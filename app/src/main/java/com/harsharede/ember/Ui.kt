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
    val scheme = if (fromWallpaper) dynamicDarkColorScheme(context) else EmberScheme
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Ember's glass: the scheme's raised surface, a soft top sheen and a lit hairline edge. */
@Composable
fun Modifier.glass(shape: Shape = RoundedCornerShape(28.dp), alpha: Float = .80f): Modifier {
    val fill = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha)
    return this
        .clip(shape)
        .background(fill)
        .drawBehind {
            drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = .08f), .35f to Color.Transparent, 1f to Color.Transparent))
        }
        .border(1.dp, Brush.linearGradient(0f to Color.White.copy(alpha = .24f), .5f to Color.White.copy(alpha = .05f),
            1f to Color.White.copy(alpha = .12f)), shape)
}

@Composable
fun AppIcon(app: AppEntry, size: Dp, modifier: Modifier = Modifier) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val icon by produceState<ImageBitmap?>(null, app.key, px) {
        value = withContext(Dispatchers.IO) { EmberApp.instance.apps.icon(app, px) }
    }
    IconImage(icon, size, modifier)
}

@Composable
fun PackageIcon(packageName: String, size: Dp, modifier: Modifier = Modifier) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val icon by produceState<ImageBitmap?>(null, packageName, px) {
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
