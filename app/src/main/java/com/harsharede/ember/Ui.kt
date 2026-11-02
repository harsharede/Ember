package com.harsharede.ember

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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

object EmberColors {
    val Ember = Color(0xFFFF7A3D)
    val EmberSoft = Color(0xFFFFB38F)
    val Glass = Color(0xFF2A2A2E)
    val GlassStrong = Color(0xFF1C1C1F)
    val Secondary = Color(0xFFB4B4BA)
    val Red = Color(0xFFFF453A)
}

@Composable
fun EmberTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = EmberColors.Ember, onPrimary = Color.Black,
        secondary = EmberColors.EmberSoft, surface = EmberColors.GlassStrong, onSurface = Color.White,
        surfaceVariant = EmberColors.Glass, onSurfaceVariant = EmberColors.Secondary,
        background = Color.Black, onBackground = Color.White,
    ), content = content)
}

/** Ember's glass: a dark translucent fill, a soft top sheen and a lit hairline edge. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(28.dp), alpha: Float = .78f): Modifier = this
    .clip(shape)
    .background(EmberColors.Glass.copy(alpha = alpha))
    .drawBehind {
        drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = .10f), .35f to Color.Transparent, 1f to Color.Transparent))
    }
    .border(1.dp, Brush.linearGradient(0f to Color.White.copy(alpha = .30f), .5f to Color.White.copy(alpha = .06f),
        1f to Color.White.copy(alpha = .14f)), shape)

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
