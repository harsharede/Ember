package com.harsharede.ember

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The lock-screen notification light, like the LED phones used to have. When something arrives with the screen off,
 * the screen wakes black at the lowest brightness and only a small pill glows at the top left, blinking slowly. Tap to
 * see the lock screen, swipe up to unlock straight into Ember's notifications. It goes dark again on its own once the
 * notifications are dealt with or the time set in Settings runs out, and the proximity sensor turns it off in a pocket.
 */
object Light {
    private val _items = MutableStateFlow<List<NotifItem>>(emptyList())
    /** What the light is showing, newest first. */
    val items: StateFlow<List<NotifItem>> = _items.asStateFlow()

    /** The light's screen is up (set by the Activity itself). */
    @Volatile var showing = false
        internal set
    @Volatile private var launchedAt = 0L

    /** Light up for [item], or add it to a light that's already on. */
    fun show(context: Context, item: NotifItem) {
        _items.value = listOf(item) + _items.value.filter { it.key != item.key }
        if (showing) return
        val now = System.currentTimeMillis()
        if (now - launchedAt < 4_000) return // already on its way
        launchedAt = now
        runCatching {
            context.startActivity(Intent(context, LightActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS))
        }
        // Some phones don't honour an Activity's "turn the screen on"; if it's still off shortly after, wake it directly.
        val power = context.getSystemService(PowerManager::class.java)
        Handler(Looper.getMainLooper()).postDelayed({
            if (!power.isInteractive && !showing) runCatching {
                @Suppress("DEPRECATION")
                power.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "ember:light-wake").acquire(2_000)
            }
        }, 1_500)
    }

    internal fun clear() { _items.value = emptyList() }
}

/** The app's notification colour, unless it set none or picked one too dark to glow on black. */
fun lightColor(argb: Int, fallback: Int): Int {
    if (argb == 0) return fallback
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    val luminance = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255
    return if (luminance < .18) fallback else argb or 0xFF000000.toInt()
}

class LightActivity : ComponentActivity() {
    private var proximity: PowerManager.WakeLock? = null
    private var dark by mutableStateOf(false)

    private val screenEvents = object : BroadcastReceiver() {
        // Power button, the screen timing out once dark, or the phone unlocked: the light's job is done.
        override fun onReceive(context: Context, intent: Intent) { finish() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Light.showing = true
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val prefs = EmberApp.instance.prefs
        window.attributes = window.attributes.apply {
            screenBrightness = LIGHT_BRIGHTNESS[prefs.lightBrightness.value.coerceIn(0, LIGHT_BRIGHTNESS.lastIndex)]
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            // The slowest refresh rate the screen offers: the light changes a few times a second at most.
            runCatching {
                val current = display?.mode
                display?.supportedModes?.filter { current == null || (it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight) }
                    ?.minByOrNull { it.refreshRate }?.let { preferredDisplayModeId = it.modeId }
            }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        registerReceiver(screenEvents, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT)
        }, Context.RECEIVER_EXPORTED)
        setContent { EmberTheme { LightScreen(dark, onTap = { finish() }, onSwipeUp = ::unlockToNotifications, onDark = ::goDark) } }
    }

    /** Nothing left to show: hide the pill and let the screen time out by itself (an app can't switch it off). */
    private fun goDark() {
        if (dark) return
        dark = true
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun unlockToNotifications() {
        val open = {
            startActivity(Intent(this, HomeActivity::class.java).setAction(HomeActivity.ACTION_OPEN_NOTIFICATIONS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        }
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard.isKeyguardLocked) keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() { open() }
        }) else open()
    }

    override fun onStart() {
        super.onStart()
        // In a pocket or face down, the proximity sensor switches the screen off and back on as it's uncovered.
        val power = getSystemService(PowerManager::class.java)
        if (power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) runCatching {
            proximity = power.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "ember:light-pocket").also { it.acquire(4 * 60 * 60_000L) }
        }
    }

    override fun onStop() {
        super.onStop()
        proximity?.let { if (it.isHeld) runCatching { it.release() } }
        proximity = null
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenEvents) }
        Light.showing = false
        Light.clear()
        super.onDestroy()
    }

    companion object {
        /** Low, medium and high, as window brightness (0 to 1). */
        val LIGHT_BRIGHTNESS = floatArrayOf(.01f, .06f, .2f)
    }
}

@Composable
private fun LightScreen(dark: Boolean, onTap: () -> Unit, onSwipeUp: () -> Unit, onDark: () -> Unit) {
    val prefs = EmberApp.instance.prefs
    val minutes by prefs.lightMinutes.collectAsStateWithLifecycle()
    val showIcon by prefs.lightIcon.collectAsStateWithLifecycle()
    val wanted by Light.items.collectAsStateWithLifecycle()
    val live by NotificationStore.items.collectAsStateWithLifecycle()
    // Only what's still there: one read on another device or dismissed elsewhere drops out of the light.
    val items = remember(wanted, live) { val keys = live.associateBy { it.key }; wanted.mapNotNull { keys[it.key] } }
    LaunchedEffect(items.isEmpty(), wanted.isEmpty()) { if (wanted.isNotEmpty() && items.isEmpty()) onDark() }
    LaunchedEffect(minutes) { delay(minutes * 60_000L); onDark() }

    // An LED's slow blink: a second on, a few off, with short fades, so the screen redraws only while it changes.
    var lit by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { while (true) { lit = true; delay(1_400); lit = false; delay(2_600) } }
    val glow by animateFloatAsState(if (lit) 1f else .1f, tween(500), label = "blink")
    // Move a few pixels every minute so the same pixels aren't lit for hours.
    var shift by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); shift = (shift + 1) % 4 } }

    val fallback = MaterialTheme.colorScheme.primary.toArgb()
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize().background(Color.Black)
        .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
        .pointerInput(Unit) {
            var travelled = 0f
            detectVerticalDragGestures(onDragStart = { travelled = 0f },
                onDragEnd = { if (travelled < -with(density) { 80.dp.toPx() }) onSwipeUp() }) { change, dy -> change.consume(); travelled += dy }
        }) {
        val newest = items.firstOrNull()
        if (!dark && newest != null) {
            val color = Color(lightColor(newest.color, fallback))
            Row(Modifier.align(Alignment.TopStart)
                .padding(start = 24.dp, top = 14.dp)
                .offset(x = (shift % 2 * 3).dp, y = (shift / 2 * 3).dp)
                .graphicsLayer { alpha = glow }
                .drawBehind {
                    for (i in 3 downTo 1) {
                        val grow = i * 5.dp.toPx()
                        drawRoundRect(color.copy(alpha = .18f / i), topLeft = Offset(-grow, -grow),
                            size = Size(size.width + grow * 2, size.height + grow * 2), cornerRadius = CornerRadius(size.height / 2 + grow))
                    }
                }
                .clip(RoundedCornerShape(50)).background(color).height(18.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (showIcon) items.map { it.packageName }.distinct().take(3).forEach { pkg ->
                    PackageIcon(pkg, 13.dp, Modifier.padding(horizontal = 1.dp).clip(RoundedCornerShape(50)))
                }
                Box(Modifier.width(if (showIcon) 6.dp else 26.dp))
            }
        }
    }
}
