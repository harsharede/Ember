package com.harsharede.ember

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A full-screen layer over Home (the app drawer, the notification page) that follows the finger: [progress] goes from 0
 * (hidden) to 1 (open) as it's dragged, then springs to whichever end the release points at.
 *
 * [sign] is the direction that opens it: -1 for a layer pulled up from the bottom, +1 for one pulled down from the top.
 * Read [progress] in draw lambdas (graphicsLayer), so a drag redraws without recomposing Home.
 */
class Layer(private val scope: CoroutineScope, val sign: Float) {
    var progress by mutableFloatStateOf(0f)
        private set
    /** The distance a full open takes, in pixels: the screen's height, set by Home. */
    var heightPx = 2000f
    private var job: Job? = null

    val visible: Boolean get() = progress > 0f

    fun dragBy(deltaPx: Float) {
        job?.cancel()
        progress = (progress + sign * deltaPx / heightPx).coerceIn(0f, 1f)
    }

    /** Release: a quick flick decides; otherwise past 40% opens and short of it closes. */
    fun settle(velocityPx: Float) {
        val v = sign * velocityPx / heightPx
        animateTo(when {
            v > 1.1f -> 1f
            v < -1.1f -> 0f
            progress > .4f -> 1f
            else -> 0f
        }, v)
    }

    fun show() = animateTo(1f)
    fun hide() = animateTo(0f)

    private fun animateTo(target: Float, velocity: Float = 0f) {
        job?.cancel()
        if (progress == target) return
        job = scope.launch {
            animate(progress, target, velocity, spring(dampingRatio = .92f, stiffness = Spring.StiffnessMediumLow)) { value, _ ->
                progress = value.coerceIn(0f, 1f)
            }
        }
    }

    /**
     * For the layer's own scrolling list: a pull past the list's end (down at the top of the drawer, up at the bottom of
     * the notifications) moves the layer to close it, and a flick finishes the job.
     */
    val closeConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // Part-way closed: the finger moves the layer, not the list, until it's back at either end.
            if (progress > 0f && progress < 1f && source == NestedScrollSource.UserInput) { dragBy(available.y); return available }
            return Offset.Zero
        }
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
            val before = progress
            dragBy(available.y)
            return if (progress != before) available else Offset.Zero
        }
        override suspend fun onPreFling(available: Velocity): Velocity {
            if (progress < 1f) { settle(available.y); return available }
            return Velocity.Zero
        }
    }
}

/** Home's pulls: up opens the drawer, down opens notifications, whichever way the drag starts. */
class HomePull(private val drawer: Layer, private val notifications: Layer) {
    private var active: Layer? = null

    fun drag(dy: Float): Boolean {
        val layer = active ?: when {
            dy < 0 && !notifications.visible -> drawer
            dy > 0 && !drawer.visible -> notifications
            else -> null
        }?.also { active = it } ?: return false
        layer.dragBy(dy)
        return true
    }

    fun release(velocityY: Float) { active?.settle(velocityY); active = null }

    /** Home's scrolling column hands over what it can't use: pulling past its top or bottom moves a layer. */
    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val layer = active ?: return Offset.Zero
            if (source == NestedScrollSource.UserInput && layer.visible) { drag(available.y); return available }
            return Offset.Zero
        }
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
            return if (drag(available.y)) available else Offset.Zero
        }
        override suspend fun onPreFling(available: Velocity): Velocity {
            if (active == null) return Velocity.Zero
            release(available.y)
            return available
        }
    }
}

/**
 * Dragging this area moves [layer] directly (for the parts of a layer that aren't a scrolling list). Velocity is tracked
 * from the summed deltas, which stay true as the layer moves under the finger.
 */
fun androidx.compose.ui.Modifier.dragToMove(layer: Layer): androidx.compose.ui.Modifier = pointerInput(layer) {
    val tracker = VelocityTracker()
    var travelled = 0f
    detectVerticalDragGestures(
        onDragStart = { tracker.resetTracking(); travelled = 0f },
        onDragEnd = { layer.settle(tracker.calculateVelocity().y) },
        onDragCancel = { layer.settle(0f) },
    ) { change, dy ->
        change.consume()
        travelled += dy
        tracker.addPosition(change.uptimeMillis, Offset(0f, travelled))
        layer.dragBy(dy)
    }
}
