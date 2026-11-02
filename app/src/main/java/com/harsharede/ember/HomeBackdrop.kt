package com.harsharede.ember

import android.app.WallpaperManager
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Parallax: the wallpaper pans a little as Home's pages are swiped. With Ember's own photo wallpapers, Home draws a
 * slightly wider copy itself (the system wallpaper is its middle, so the two match on the Today page). With any other
 * wallpaper Home tells Android the scroll position, and the phone pans it if the wallpaper is wide enough.
 * [position] runs from 0 (the Today page) to 1 (the last page).
 */
@Composable
fun ParallaxWallpaper(position: () -> Float) {
    val context = LocalContext.current
    val prefs = EmberApp.instance.prefs
    val on by prefs.parallax.collectAsStateWithLifecycle()
    val rotating by prefs.wallpaperRotate.collectAsStateWithLifecycle()
    val version by Wallpapers.wideVersion.collectAsStateWithLifecycle()
    val wide by produceState<ImageBitmap?>(null, on, rotating, version) {
        value = if (!on || !rotating) null else withContext(Dispatchers.IO) {
            runCatching { Wallpapers.wideFile(context).takeIf { it.exists() }?.let { f -> BitmapFactory.decodeFile(f.path)?.asImageBitmap() } }.getOrNull()
        }
    }
    val image = wide
    if (on && image != null) {
        Canvas(Modifier.fillMaxSize()) {
            // Scaled to the screen's height, the copy is a little wider than the screen. Today shows its middle (as the
            // system wallpaper does); further pages pan towards its right edge.
            val scale = size.height / image.height
            val w = image.width * scale
            val extra = (w - size.width).coerceAtLeast(0f)
            val left = -(extra / 2f) * (1f + position().coerceIn(0f, 1f))
            drawImage(image, dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), 0),
                dstSize = androidx.compose.ui.unit.IntSize(w.toInt(), size.height.toInt()))
        }
    } else if (on) {
        // Someone else's wallpaper: let Android pan it.
        val view = LocalView.current
        LaunchedEffect(view) {
            val wm = WallpaperManager.getInstance(context)
            snapshotFlow { position() }.collect { p ->
                runCatching { view.windowToken?.let { wm.setWallpaperOffsets(it, p.coerceIn(0f, 1f), .5f) } }
            }
        }
    }
}

/**
 * Weather drawn over the wallpaper, to match the Weather card: rain (with the odd lightning flash in a storm), snow,
 * drifting fog, a soft sun glow on a clear day or a few twinkling stars on a clear night. It only moves while Home is
 * in front, at a gentle 30 frames a second.
 */
@Composable
fun WeatherEffects() {
    val prefs = EmberApp.instance.prefs
    val on by prefs.weatherEffects.collectAsStateWithLifecycle()
    val weather by WeatherStore.now.collectAsStateWithLifecycle()
    val w = weather
    if (!on || w == null) return
    val kind = weatherKind(w.code, w.isDay)
    if (kind == WeatherKind.NONE) return
    var frame by remember { mutableLongStateOf(0L) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(kind) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { while (true) { delay(33); frame += 33 } }
    }
    val drops = remember(kind) { List(if (kind == WeatherKind.SNOW) 55 else 70) { floatArrayOf(Random.nextFloat(), Random.nextFloat(), .6f + Random.nextFloat() * .8f) } }
    Canvas(Modifier.fillMaxSize()) {
        val t = frame / 1000f
        when (kind) {
            WeatherKind.RAIN, WeatherKind.STORM -> {
                drops.forEach { (x0, y0, speed) ->
                    val y = ((y0 + t * .9f * speed) % 1f) * size.height
                    val x = (x0 * size.width + y * .12f) % size.width
                    drawLine(Color.White.copy(alpha = .22f + .12f * speed), Offset(x, y), Offset(x + 5f, y + 26f * speed), strokeWidth = 2.2f, cap = StrokeCap.Round)
                }
                if (kind == WeatherKind.STORM) {
                    // A flash every ten seconds or so, bright for a moment and fading.
                    val phase = t % 9.7f
                    if (phase < .35f) drawRect(Color.White.copy(alpha = .22f * (1f - phase / .35f)))
                }
            }
            WeatherKind.SNOW -> drops.forEach { (x0, y0, speed) ->
                val y = ((y0 + t * .07f * speed) % 1f) * size.height
                val x = (x0 * size.width + sin((t * .8f + x0 * 10f).toDouble()).toFloat() * 18f + size.width) % size.width
                drawCircle(Color.White.copy(alpha = .55f), radius = 2.5f + 2.5f * speed, center = Offset(x, y))
            }
            WeatherKind.FOG -> for (i in 0 until 3) {
                val y = size.height * (.25f + .25f * i) + sin((t * .2f + i).toDouble()).toFloat() * 40f
                drawRect(Brush.verticalGradient(0f to Color.Transparent, .5f to Color.White.copy(alpha = .10f), 1f to Color.Transparent,
                    startY = y - 160f, endY = y + 160f))
            }
            WeatherKind.SUN -> {
                val pulse = .22f + .06f * sin((t * 2 * PI / 6).toDouble()).toFloat()
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE6A8).copy(alpha = pulse), Color.Transparent),
                    center = Offset(size.width * .85f, size.height * .06f), radius = size.width * .8f),
                    radius = size.width * .8f, center = Offset(size.width * .85f, size.height * .06f))
            }
            WeatherKind.CLOUDS -> for (i in 0 until 4) {
                // Big soft clouds drifting slowly across.
                val speed = .012f + i * .004f
                val x = ((drops[i][0] + t * speed) % 1.4f - .2f) * size.width
                val y = size.height * (.08f + .12f * i)
                val r = size.width * (.35f + .1f * (i % 2))
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = if (w.isDay) .10f else .06f), Color.Transparent), center = Offset(x, y), radius = r),
                    radius = r, center = Offset(x, y))
            }
            WeatherKind.STARS -> drops.take(30).forEachIndexed { i, (x0, y0, speed) ->
                val twinkle = .25f + .35f * ((sin((t * speed * 2f + i).toDouble()).toFloat() + 1f) / 2f)
                drawCircle(Color.White.copy(alpha = twinkle), radius = 1.6f + speed, center = Offset(x0 * size.width, y0 * size.height * .55f))
            }
            WeatherKind.NONE -> Unit
        }
    }
}

enum class WeatherKind { NONE, RAIN, STORM, SNOW, FOG, SUN, STARS, CLOUDS }

/** Which effect suits a WMO weather code. */
fun weatherKind(code: Int, day: Boolean): WeatherKind = when (code) {
    0, 1 -> if (day) WeatherKind.SUN else WeatherKind.STARS
    2, 3 -> WeatherKind.CLOUDS
    45, 48 -> WeatherKind.FOG
    51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> WeatherKind.RAIN
    71, 73, 75, 77, 85, 86 -> WeatherKind.SNOW
    95, 96, 99 -> WeatherKind.STORM
    else -> WeatherKind.NONE
}
