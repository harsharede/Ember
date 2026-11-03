package com.harsharede.ember

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.concurrent.thread

/**
 * Ember's own Material You: the accent comes straight from the current wallpaper's colours.
 *
 * Android's dynamic colours come from the system palette, and some phones (Samsung's One UI with "Colour palette" off,
 * or when an app rather than the user sets the wallpaper) leave that palette on its default blue. Reading the
 * wallpaper's colours here means the accent follows every new wallpaper on every phone.
 */
object WallpaperTheme {
    private val _seed = MutableStateFlow<Int?>(null)
    /** The wallpaper's most colourful main colour (ARGB), or null before it's been read. */
    val seed: StateFlow<Int?> = _seed.asStateFlow()

    private const val STORE = "wallpaper_theme"
    private const val KEY_SEED = "seed"

    fun init(context: Context) {
        val store = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        // The last known colour first, so Ember opens in the right colours straight away.
        if (store.contains(KEY_SEED)) _seed.value = store.getInt(KEY_SEED, 0)
        val wm = WallpaperManager.getInstance(context)
        runCatching {
            wm.addOnColorsChangedListener({ colors, which ->
                if (which and WallpaperManager.FLAG_SYSTEM != 0) colors?.let { set(context, pick(it), "changed") }
            }, Handler(Looper.getMainLooper()))
        }
        // The first read can be slow on some phones; keep it off the main thread. Samsung files the home screen's
        // wallpaper under its own extra flags, so ask with those too when the plain question gets no answer.
        thread(name = "wallpaper-colours") {
            val colors = listOf(WallpaperManager.FLAG_SYSTEM, WallpaperManager.FLAG_SYSTEM or 4, WallpaperManager.FLAG_SYSTEM or 8)
                .firstNotNullOfOrNull { which -> runCatching { wm.getWallpaperColors(which) }.getOrNull() }
            if (colors != null) set(context, pick(colors), "read")
            else if (_seed.value == null) {
                // Ember keeps its own copy of the photo it set (for parallax): read the colours from that.
                val file = Wallpapers.wideFile(context)
                val bitmap = if (file.exists()) runCatching {
                    android.graphics.BitmapFactory.decodeFile(file.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 })
                }.getOrNull() else null
                if (bitmap != null) { fromBitmap(context, bitmap); bitmap.recycle() }
                else android.util.Log.i("EmberTheme", "No wallpaper colours yet; they come with the next photo")
            }
        }
    }

    /** Ember just set this photo as the wallpaper: take the colours from it directly. */
    fun fromBitmap(context: Context, bitmap: android.graphics.Bitmap) {
        runCatching {
            val small = android.graphics.Bitmap.createScaledBitmap(bitmap, 112, (112f * bitmap.height / bitmap.width).toInt().coerceAtLeast(1), true)
            set(context, pick(WallpaperColors.fromBitmap(small)), "photo")
            if (small !== bitmap) small.recycle()
        }
    }

    private fun set(context: Context, seed: Int, from: String) {
        android.util.Log.i("EmberTheme", "Accent seed ${Integer.toHexString(seed)} from $from")
        _seed.value = seed
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().putInt(KEY_SEED, seed).apply()
    }

    /** Of the wallpaper's main colours, the most colourful one, so a grey sky doesn't win over the red door below it. */
    private fun pick(colors: WallpaperColors): Int {
        val all = listOfNotNull(colors.primaryColor, colors.secondaryColor, colors.tertiaryColor).map { it.toArgb() }
        return all.maxByOrNull { argb ->
            val hsl = FloatArray(3).also { ColorUtils.colorToHSL(argb, it) }
            // Saturation counts most; very dark or very light colours carry little of it to the eye.
            hsl[1] * (1f - kotlin.math.abs(hsl[2] - .5f) * 1.4f)
        } ?: all.first()
    }

    /** A dark scheme in the wallpaper's hue: a light, clear accent for text and buttons, deep containers behind them. */
    fun scheme(seed: Int): ColorScheme {
        val hsl = FloatArray(3).also { ColorUtils.colorToHSL(seed, it) }
        val h = hsl[0]
        // A nearly grey wallpaper still gets a gentle tint rather than none.
        val s = hsl[1].coerceIn(.35f, .80f)
        fun c(hue: Float, sat: Float, light: Float) = Color.hsl((hue % 360f + 360f) % 360f, sat.coerceIn(0f, 1f), light.coerceIn(0f, 1f))
        val t = h + 60f
        return darkColorScheme(
            primary = c(h, s, .72f), onPrimary = c(h, s * .8f, .14f),
            primaryContainer = c(h, s * .6f, .26f), onPrimaryContainer = c(h, s, .90f),
            inversePrimary = c(h, s, .40f),
            secondary = c(h, s * .45f, .76f), onSecondary = c(h, s * .4f, .15f),
            secondaryContainer = c(h, s * .3f, .24f), onSecondaryContainer = c(h, s * .5f, .90f),
            tertiary = c(t, s * .6f, .76f), onTertiary = c(t, s * .5f, .15f),
            tertiaryContainer = c(t, s * .4f, .26f), onTertiaryContainer = c(t, s * .6f, .90f),
            surfaceTint = c(h, s, .72f),
        )
    }
}
