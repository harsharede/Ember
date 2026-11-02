package com.harsharede.ember

import android.app.Activity
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.LauncherApps
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * How much a widget belongs in the Smart Stack: feeds and news first, then social, video, weather, calendar and mail.
 * Search bars, shortcuts and tiny widgets are pushed down, since they show nothing new. Pure, so it's tested.
 */
fun stackScore(packageName: String, label: String, minWidthDp: Int, minHeightDp: Int): Int {
    val pkg = packageName.lowercase()
    val name = label.lowercase()
    var score = when {
        listOf("news", "magazines", "inshorts", "flipboard", "bbc", "cnn", "ndtv", "timesofindia", "upday", "feedly", "guardian").any { it in pkg } -> 10
        "reddit" in pkg -> 9
        listOf("instagram", "facebook", "twitter", "threads", "linkedin").any { it in pkg } -> 8
        "youtube" in pkg -> 7
        "weather" in pkg -> 6
        "calendar" in pkg -> 6
        "googlequicksearchbox" in pkg -> 5
        listOf("android.gm", "outlook", "mail").any { it in pkg } -> 5
        listOf("spotify", "music", "podcast").any { it in pkg } -> 4
        else -> 1
    }
    if (listOf("feed", "headline", "stories", "news", "posts", "popular", "trending", "inbox", "for you", "forecast", "upcoming", "agenda").any { it in name }) score += 3
    if (listOf("search", "shortcut", "quick", "button", "1x1", "bookmark").any { it in name }) score -= 6
    if (minWidthDp < 110 || minHeightDp < 70) score -= 4
    return score
}

object SmartStack {
    private const val MAX = 6

    /** The best widget from each app, best first, leaving out ones that need a setup screen (they can't be added unattended). */
    suspend fun candidates(context: Context, exclude: Set<String>): List<AppWidgetProviderInfo> = withContext(Dispatchers.IO) {
        val density = context.resources.displayMetrics.density
        val pm = context.packageManager
        val me = android.os.Process.myUserHandle()
        val profiles = context.getSystemService(LauncherApps::class.java).profiles
        profiles.flatMap { Widgets.manager.getInstalledProvidersForProfile(it) }
            .filter { it.profile == me && it.provider.packageName !in exclude && it.provider.packageName != context.packageName }
            .filter { it.configure == null || it.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0 }
            .map { it to stackScore(it.provider.packageName, it.loadLabel(pm), (it.minWidth / density).toInt(), (it.minHeight / density).toInt()) }
            .filter { it.second >= 4 }
            .groupBy { it.first.provider.packageName }
            .map { (_, list) -> list.maxBy { it.second } }
            .sortedByDescending { it.second }
            .take(MAX).map { it.first }
    }

    /** Fill the stack with what's installed. Apps already in it are skipped, so running it again only adds new ones. */
    fun autoPick(activity: Activity, scope: CoroutineScope) {
        scope.launch {
            val inStack = EmberApp.instance.prefs.stackWidgetIds.mapNotNull {
                runCatching { Widgets.manager.getAppWidgetInfo(it)?.provider?.packageName }.getOrNull()
            }.toSet()
            val picks = candidates(activity, inStack).take((MAX - inStack.size).coerceAtLeast(0))
            if (picks.isEmpty()) android.widget.Toast.makeText(activity, "No more widgets with updates found in your apps", android.widget.Toast.LENGTH_LONG).show()
            else Widgets.addAll(activity, picks, Widgets.Target.STACK)
        }
    }
}

/** Human names for stack items, for the editor and for TalkBack. */
fun stackItemTitle(item: String): String = when {
    item == "card:weather" -> "Weather"
    item == "card:people" -> "People"
    item == "card:media" -> "Now playing"
    item == "card:calendar" -> "Up next"
    item == "card:suggestions" -> "App suggestions"
    item == "card:screentime" -> "Screen time"
    item.startsWith("feed:") -> EmberApp.instance.apps.labelForPackage(item.removePrefix("feed:"))
    item.startsWith("widget:") -> item.removePrefix("widget:").toIntOrNull()?.let { id ->
        runCatching { Widgets.manager.getAppWidgetInfo(id)?.let { info ->
            "${EmberApp.instance.apps.labelForPackage(info.provider.packageName)} · ${info.loadLabel(EmberApp.instance.packageManager)}" } }.getOrNull()
    } ?: "Widget"
    else -> item
}

/**
 * The Smart Stack: Ember's cards, app feeds and widgets in one card that moves on every few seconds while Home is on
 * screen and nobody's touching it. Cards with nothing to show (nothing playing, no posts yet) step out of the rotation
 * until they do. Swipe sideways to flip by hand; the pencil opens the editor.
 */
@Composable
fun SmartStackCard(
    widthDp: Int,
    active: Boolean,
    editing: Boolean,
    suggestions: @Composable () -> Unit,
    onEdit: () -> Unit,
) {
    val prefs = EmberApp.instance.prefs
    val items by prefs.stackItems.collectAsStateWithLifecycle()
    val height by prefs.stackHeight.collectAsStateWithLifecycle()
    val seconds by prefs.stackSeconds.collectAsStateWithLifecycle()
    val media by NowPlaying.info.collectAsStateWithLifecycle()
    val feeds by Feeds.posts.collectAsStateWithLifecycle()
    val starred by CardData.starred.collectAsStateWithLifecycle()
    val events by CardData.events.collectAsStateWithLifecycle()
    val pages = remember(items, media != null, feeds, starred.isNotEmpty(), events.isNotEmpty()) {
        items.filter { item ->
            when {
                item == "card:media" -> media != null
                item == "card:people" -> starred.isNotEmpty() || recentPeople(feeds, 1).isNotEmpty()
                item == "card:calendar" -> events.isNotEmpty()
                item.startsWith("feed:") -> feeds[item.removePrefix("feed:")]?.any { !it.conversation } == true
                else -> true
            }
        }
    }

    if (pages.isEmpty()) {
        Column(Modifier.fillMaxWidth().glass().padding(18.dp)) {
            Text("Smart Stack", style = MaterialTheme.typography.titleSmall, color = EmberColors.Secondary)
            Text("Weather, people, what's playing, your next event, YouTube or Reddit posts and widgets, in one card that flips every few seconds.",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
            FilledTonalButton(onEdit) { Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp)); Text("  Set it up") }
        }
        return
    }

    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    var touchedAt by remember { mutableLongStateOf(0L) }
    val isActive by rememberUpdatedState(active && !editing)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(pages.size, seconds) {
        if (pages.size < 2) return@LaunchedEffect
        // Only while Home is in front (RESUMED): with the screen off or another app open, nothing moves.
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(seconds * 1000L)
                val idle = System.currentTimeMillis() - touchedAt > 12_000
                if (isActive && idle && !pager.isScrollInProgress) {
                    val next = (pager.currentPage + 1) % pages.size
                    if (next == 0) pager.scrollToPage(0) else pager.animateScrollToPage(next)
                }
            }
        }
    }
    val animatedHeight by animateDpAsState(height.dp, label = "stack height")
    Column(Modifier.fillMaxWidth()) {
        HorizontalPager(pager, Modifier.fillMaxWidth().height(animatedHeight).clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp))
            .stackSwipe(pager, scope) { touchedAt = System.currentTimeMillis() },
            userScrollEnabled = false, beyondViewportPageCount = pages.size, pageSpacing = 12.dp, key = { pages[it] }) { page ->
            val item = pages[page]
            Box(Modifier.fillMaxSize()) {
                when {
                    item == "card:weather" -> WeatherCard()
                    item == "card:people" -> PeopleCard()
                    item == "card:media" -> media?.let { NowPlayingCard(it) }
                    item == "card:calendar" -> CalendarCard()
                    item == "card:suggestions" -> suggestions()
                    item == "card:screentime" -> ScreenTimeCard()
                    item.startsWith("feed:") -> feeds[item.removePrefix("feed:")]?.filter { !it.conversation }?.takeIf { it.isNotEmpty() }
                        ?.let { FeedCard(item.removePrefix("feed:"), it) }
                    item.startsWith("widget:") -> item.removePrefix("widget:").toIntOrNull()?.let { WidgetFrame(it, widthDp, height) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                if (pages.size > 1) repeat(pages.size) { i ->
                    val selected = i == pager.currentPage
                    val w by animateDpAsState(if (selected) 16.dp else 6.dp, label = "dot")
                    Box(Modifier.padding(horizontal = 3.dp).size(width = w, height = 6.dp).clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .35f)))
                }
            }
            IconButton(onEdit, Modifier.size(40.dp)) { Icon(Icons.Rounded.Edit, "Edit the stack", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f), modifier = Modifier.size(18.dp)) }
        }
        if (editing) Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
            FilledTonalIconButton({ prefs.setStackHeight((height - 30).coerceAtLeast(110)) }) { Icon(Icons.Rounded.UnfoldLess, "Shorter") }
            FilledTonalIconButton({ prefs.setStackHeight((height + 30).coerceAtMost(420)) }) { Icon(Icons.Rounded.UnfoldMore, "Taller") }
            FilledTonalIconButton(onEdit) { Icon(Icons.Rounded.Edit, "Edit the stack") }
        }
    }
}

/**
 * Sideways swipes flip the stack even over a widget's own buttons (which would otherwise keep the touch). Taps still reach
 * the widget; an up-or-down drag is left alone for Home and its list.
 */
private fun Modifier.stackSwipe(pager: PagerState, scope: CoroutineScope, onTouch: () -> Unit): Modifier = pointerInput(pager) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onTouch()
        val slop = viewConfiguration.touchSlop
        val tracker = VelocityTracker()
        var dragging = false
        var dx = 0f; var dy = 0f
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            val delta = change.positionChange()
            dx += delta.x; dy += delta.y
            if (!dragging) {
                if (abs(dy) > slop && abs(dy) > abs(dx)) return@awaitEachGesture
                if (abs(dx) > slop && abs(dx) > abs(dy)) dragging = true else continue
            }
            tracker.addPosition(change.uptimeMillis, Offset(dx, 0f))
            pager.dispatchRawDelta(-delta.x)
            change.consume()
        }
        if (dragging) {
            val velocity = tracker.calculateVelocity().x
            val position = pager.currentPage + pager.currentPageOffsetFraction
            val target = when {
                velocity < -700 -> floor(position).toInt() + 1
                velocity > 700 -> floor(position).toInt()
                else -> position.roundToInt()
            }.coerceIn(0, pager.pageCount - 1)
            scope.launch { pager.animateScrollToPage(target) }
            onTouch()
        }
    }
}
