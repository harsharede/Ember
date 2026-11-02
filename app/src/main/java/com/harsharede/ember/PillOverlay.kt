package com.harsharede.ember

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The notification light: when something arrives while the phone is in use, a small pill glows at the top left instead of
 * a banner covering the screen. Tap or swipe it down to open the messages as a pop-up (reply, snooze, open); swipe it
 * sideways to put it out. It goes out by itself once its notifications are gone.
 *
 * Drawn in an overlay window, so it needs "Display over other apps". Without it Ember simply doesn't show a pill.
 */
class PillOverlay(private val context: Context) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** What the pill is holding: notifications that arrived and haven't been seen yet, newest first. */
    internal val pending = mutableStateListOf<NotifItem>()
    internal val expanded = mutableStateOf(false)

    private var root: OverlayRoot? = null
    private var owner: OverlayOwner? = null

    private val screenReceiver = object : BroadcastReceiver() {
        // Something arrived with the screen off: the pill is waiting when the phone is unlocked.
        override fun onReceive(c: Context, intent: Intent) { if (pending.isNotEmpty() && !HomeActivity.resumed) show() }
    }

    fun start() {
        context.registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_USER_PRESENT), Context.RECEIVER_EXPORTED)
        scope.launch {
            NotificationStore.arrivals.collect { item ->
                if (!EmberApp.instance.prefs.pillEnabled.value) return@collect
                pending.removeAll { it.key == item.key }
                pending.add(0, item)
                // On Ember's own Home the rail already shows it; elsewhere the pill lights up.
                if (power.isInteractive && !HomeActivity.resumed && !Light.showing) show()
            }
        }
        scope.launch {
            NotificationStore.items.collect { live ->
                val keys = live.associateBy { it.key }
                // Keep the pill's copies current, and let go of ones dismissed or read elsewhere.
                val updated = pending.mapNotNull { keys[it.key] }
                if (updated != pending.toList()) { pending.clear(); pending.addAll(updated) }
                if (pending.isEmpty()) hide()
            }
        }
    }

    fun stop() {
        runCatching { context.unregisterReceiver(screenReceiver) }
        hide()
        scope.cancel()
    }

    /** Put the light out without touching the notifications themselves. */
    fun clear() { pending.clear(); hide() }

    fun setExpanded(open: Boolean) {
        expanded.value = open
        val view = root ?: return
        runCatching { wm.updateViewLayout(view, params(open)) }
    }

    private fun show() {
        if (root != null || pending.isEmpty()) return
        if (!Settings.canDrawOverlays(context)) return
        expanded.value = false
        val owner = OverlayOwner().also { this.owner = it }
        val compose = ComposeView(context).apply {
            setContent { PillContent(this@PillOverlay) }
        }
        val view = OverlayRoot(context, onOutside = { if (expanded.value) setExpanded(false) },
            onBack = { if (expanded.value) { setExpanded(false); true } else false })
        view.addView(compose)
        view.setViewTreeLifecycleOwner(owner)
        view.setViewTreeSavedStateRegistryOwner(owner)
        owner.resume()
        if (runCatching { wm.addView(view, params(false)) }.isSuccess) root = view else owner.destroy()
    }

    private fun hide() {
        val view = root ?: return
        root = null
        runCatching { wm.removeView(view) }
        owner?.destroy(); owner = null
        expanded.value = false
    }

    private fun params(open: Boolean) = WindowManager.LayoutParams(
        if (open) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // Collapsed it never takes focus, so typing in the app underneath carries on. Open, it can (for the reply field),
        // but a touch outside still goes to the app and folds the pop-up away.
        (if (open) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        // Allowed into the status bar strip, beside the camera, where an old phone's notification light sat.
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        title = "Ember pill"
    }

    private class OverlayRoot(context: Context, val onOutside: () -> Unit, val onBack: () -> Boolean) : FrameLayout(context) {
        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) { onOutside(); return false }
            return super.dispatchTouchEvent(ev)
        }
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP && onBack()) return true
            return super.dispatchKeyEvent(event)
        }
    }

    /** Compose needs a lifecycle and saved state to run in a window that isn't an Activity's. */
    private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
        init { saved.performRestore(null) }
        fun resume() { registry.currentState = Lifecycle.State.RESUMED }
        fun destroy() { registry.currentState = Lifecycle.State.DESTROYED }
    }
}
