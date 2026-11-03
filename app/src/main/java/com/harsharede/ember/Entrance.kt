package com.harsharede.ember

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** How things come back into place on Home. */
enum class EntranceStyle(val label: String) {
    /** Falls straight into its place and lands with a spring. */
    DROP("Drop"),
    /** Starts a little scattered and tilted above, then falls and straightens into line. */
    SCATTER("Scatter"),
    /** Floats up gently from just below. */
    FLOAT("Float"),
    /** Grows from small with a springy pop. */
    POP("Pop"),
}

/**
 * Home's welcome back: when Home comes back into view, the dock's icons and Home's cards return to their places one
 * after another, each moving like a real spring (weight, overshoot, settle). One clock drives everything and each item
 * works out its own position from it in a draw lambda, so the animation never recomposes or relays out Home.
 */
class Entrance {
    /** 0: everything parked (Home is out of sight). 1: everything at rest in its place. */
    val clock = Animatable(1f)
    /** Read at draw time, set from Settings. */
    var iconStyle = EntranceStyle.SCATTER
    var cardStyle = EntranceStyle.FLOAT

    /** Park everything while Home can't be seen, so it's ready to come back when Home does. */
    suspend fun park() = clock.snapTo(0f)

    suspend fun play() { if (clock.value < 1f) clock.animateTo(1f, tween(TOTAL_MS, easing = LinearEasing)) }

    /** Icons are light and bouncy; cards are heavier and settle sooner. */
    enum class Kind(val staggerMs: Int, val stiffness: Float, val damping: Float) {
        ICON(40, 260f, .42f),
        CARD(65, 200f, .62f),
    }

    companion object {
        /** Long enough for the last staggered item to come to rest (18 × 65 ms, then about half a second of spring). */
        const val TOTAL_MS = 2000
        private const val MAX_STAGGERED = 18

        /** Seconds since item [index] set off, at [clock]; zero until its turn comes. */
        fun elapsed(clock: Float, index: Int, kind: Kind): Float =
            ((clock * TOTAL_MS - minOf(index, MAX_STAGGERED) * kind.staggerMs) / 1000f).coerceAtLeast(0f)

        /**
         * A damped spring let go from its start at rest: 0 at first, past 1 on the first swing (the overshoot), settling
         * at 1. [stiffness] sets how quick it is, [damping] (below 1) how much it bounces.
         */
        fun spring(t: Float, stiffness: Float, damping: Float): Float {
            val w0 = sqrt(stiffness)
            val wd = w0 * sqrt(1f - damping * damping)
            val x = exp(-damping * w0 * t) * (cos(wd * t) + (damping * w0 / wd) * sin(wd * t))
            return 1f - x
        }

        /** A steady -1..1 value per item, so a scattered icon always starts from the same spot and tilt. */
        fun jitter(index: Int, seed: Int): Float = (((index + 1) * 73856093 xor seed * 19349663) and 0xFFFF) / 32767.5f - 1f
    }
}

/** Item [index] comes back into place with [entrance]; with [enabled] off it simply stays put. */
fun Modifier.fallIn(entrance: Entrance, index: Int, enabled: Boolean, kind: Entrance.Kind): Modifier =
    if (!enabled) this else graphicsLayer {
        val clock = entrance.clock.value
        if (clock >= 1f) return@graphicsLayer
        val t = Entrance.elapsed(clock, index, kind)
        val p = Entrance.spring(t, kind.stiffness, kind.damping)
        val rest = 1f - p // 1 at the start, 0 at rest, a little below 0 on the overshoot
        alpha = (t * 8f).coerceIn(0f, 1f)
        val icon = kind == Entrance.Kind.ICON
        when (if (icon) entrance.iconStyle else entrance.cardStyle) {
            EntranceStyle.DROP -> translationY = -rest * (if (icon) 64.dp else 36.dp).toPx()
            EntranceStyle.SCATTER -> {
                translationY = -rest * (if (icon) 72.dp else 40.dp).toPx()
                translationX = rest * Entrance.jitter(index, 1) * (if (icon) 14.dp else 8.dp).toPx()
                rotationZ = rest * Entrance.jitter(index, 2) * (if (icon) 18f else 4f)
            }
            EntranceStyle.FLOAT -> {
                translationY = rest * (if (icon) 40.dp else 28.dp).toPx()
                val s = 1f - .06f * rest; scaleX = s; scaleY = s
            }
            EntranceStyle.POP -> {
                val s = (if (icon) p else .5f + .5f * p).coerceAtLeast(0f); scaleX = s; scaleY = s
            }
        }
    }

/**
 * A small live preview of the welcome-back animation for Settings: a mini rail of icons and two cards that play the
 * chosen styles every time a style is picked, or on Replay.
 */
@androidx.compose.runtime.Composable
fun EntrancePreview(iconStyle: EntranceStyle, cardStyle: EntranceStyle, icons: Boolean, cards: Boolean) {
    val entrance = androidx.compose.runtime.remember { Entrance() }
    entrance.iconStyle = iconStyle; entrance.cardStyle = cardStyle
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val replay: () -> Unit = { scope.launch { entrance.park(); entrance.play() } }
    androidx.compose.runtime.LaunchedEffect(iconStyle, cardStyle, icons, cards) { entrance.park(); kotlinx.coroutines.delay(150); entrance.play() }
    val accent = androidx.compose.material3.MaterialTheme.colorScheme.primary
    androidx.compose.foundation.layout.Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().height(150.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = .45f)).padding(12.dp)) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                repeat(3) { i ->
                    androidx.compose.foundation.layout.Box(Modifier.fallIn(entrance, i, cards, Entrance.Kind.CARD).fillMaxWidth().height(34.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(androidx.compose.ui.graphics.Color.White.copy(alpha = .12f)))
                }
            }
            androidx.compose.foundation.layout.Column(Modifier.padding(start = 12.dp).width(40.dp).fillMaxHeight()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(androidx.compose.ui.graphics.Color.White.copy(alpha = .08f)).padding(vertical = 6.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                repeat(5) { i ->
                    androidx.compose.foundation.layout.Box(Modifier.fallIn(entrance, i, icons, Entrance.Kind.ICON).size(20.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape).background(accent.copy(alpha = .35f + .13f * i)))
                }
            }
        }
        androidx.compose.material3.TextButton(replay) { androidx.compose.material3.Text("Replay") }
    }
}
