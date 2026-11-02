package com.harsharede.ember

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Home's welcome back: when Home comes back into view, the dock's icons and Home's cards drop into place one after
 * another. One clock drives everything (each item reads its own slice of it in a draw lambda), so the animation moves
 * pixels without recomposing or relaying out Home.
 */
class Entrance {
    /** 0: everything parked above its place (Home is out of sight). 1: everything in place. */
    val clock = Animatable(1f)

    /** Park everything, while Home can't be seen, so it's ready to fall in when Home comes back. */
    suspend fun park() = clock.snapTo(0f)

    suspend fun play() { if (clock.value < 1f) clock.animateTo(1f, tween(TOTAL_MS, easing = LinearEasing)) }

    enum class Kind(val distance: Dp, val durationMs: Int, val staggerMs: Int) {
        /** Dock icons: a short fall with a bounce, like dropping into line. */
        ICON(56.dp, 560, 45),
        /** Cards: a smaller drop that overshoots a touch and settles. */
        CARD(28.dp, 480, 70),
    }

    companion object {
        // Long enough for the last staggered item of either kind to land (18 × 70 ms + 480 ms).
        const val TOTAL_MS = 1800
        private const val MAX_STAGGERED = 18

        /** How far item [index] of [kind] is into its fall at [clock] (0 to 1), before easing. */
        fun fraction(clock: Float, index: Int, kind: Kind): Float {
            val elapsed = clock * TOTAL_MS - minOf(index, MAX_STAGGERED) * kind.staggerMs
            return (elapsed / kind.durationMs).coerceIn(0f, 1f)
        }
    }
}

/** Item [index] falls into place with [entrance]; with [enabled] off it simply stays put. */
fun Modifier.fallIn(entrance: Entrance, index: Int, enabled: Boolean, kind: Entrance.Kind): Modifier =
    if (!enabled) this else graphicsLayer {
        val clock = entrance.clock.value
        if (clock >= 1f) return@graphicsLayer
        val f = Entrance.fraction(clock, index, kind)
        val eased = if (kind == Entrance.Kind.ICON) bounceOut(f) else backOut(f)
        translationY = -(1f - eased) * kind.distance.toPx()
        alpha = (f * 3f).coerceIn(0f, 1f)
        if (kind == Entrance.Kind.CARD) { scaleX = .96f + .04f * f; scaleY = scaleX }
    }

/** Falls, hits the floor, and bounces a few times, each smaller (the classic "bounce out"). */
fun bounceOut(t: Float): Float {
    val n = 7.5625f; val d = 2.75f
    return when {
        t < 1f / d -> n * t * t
        t < 2f / d -> { val x = t - 1.5f / d; n * x * x + .75f }
        t < 2.5f / d -> { val x = t - 2.25f / d; n * x * x + .9375f }
        else -> { val x = t - 2.625f / d; n * x * x + .984375f }
    }
}

/** Goes a little past the end and comes back. */
fun backOut(t: Float, overshoot: Float = 1.4f): Float { val x = t - 1f; return x * x * ((overshoot + 1f) * x + overshoot) + 1f }
