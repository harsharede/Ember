@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.harsharede.ember

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the Activity asks of Home: close everything (the Home button), or start adding a widget. */
class HomeRequests {
    var closeAll by mutableIntStateOf(0)
    var pickWidget by mutableStateOf(false)
    /** Where the widget being picked goes. */
    var pickTarget by mutableStateOf(Widgets.Target.HOME)
    /** Open the notification page (from the lock-screen light's swipe up). */
    var openNotifications by mutableIntStateOf(0)
}

@Composable
fun HomeScreen(requests: HomeRequests, onAddWidget: (android.appwidget.AppWidgetProviderInfo, Widgets.Target) -> Unit, onAutoPick: () -> Unit) {
    val context = LocalContext.current
    val app = EmberApp.instance
    val apps by app.apps.apps.collectAsStateWithLifecycle()
    val dockKeys by app.prefs.dock.collectAsStateWithLifecycle()
    val launches by app.prefs.launches.collectAsStateWithLifecycle()
    val hidden by app.prefs.hiddenStatus.collectAsStateWithLifecycle()
    val widgets by app.prefs.widgets.collectAsStateWithLifecycle()
    val rotating by app.prefs.wallpaperRotate.collectAsStateWithLifecycle()
    val showWallpaperButtons by app.prefs.wallpaperCard.collectAsStateWithLifecycle()
    val wallpaperCard = rotating && showWallpaperButtons
    val stackIds by app.prefs.stackWidgets.collectAsStateWithLifecycle()
    val stackSuggestions by app.prefs.stackSuggestions.collectAsStateWithLifecycle()
    val notifications by NotificationStore.items.collectAsStateWithLifecycle()
    val connected by NotificationStore.connected.collectAsStateWithLifecycle()
    val status by rememberDeviceStatus()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); delay(60_000 - now % 60_000 + 50) }
    }
    val scope = rememberCoroutineScope()
    val drawer = remember { Layer(scope, sign = -1f) }
    val notes = remember { Layer(scope, sign = 1f) }
    val pull = remember { HomePull(drawer, notes) }
    var notificationFilter by remember { mutableStateOf<String?>(null) }
    var pickDockSlot by remember { mutableStateOf<Int?>(null) }
    var editing by remember { mutableStateOf(false) }
    val drawerOpen by remember { derivedStateOf { drawer.progress >= 1f } }
    val drawerShown by remember { derivedStateOf { drawer.visible } }
    val notesShown by remember { derivedStateOf { notes.visible } }

    // Only what's worth attention counts on the rail: not silent ones, not ones that can't be dismissed.
    val counted = remember(notifications) { notifications.filter { !it.silent && !it.persistent } }
    val dock = remember(apps, dockKeys) { dockKeys.map { key -> apps.firstOrNull { it.key == key } } }
    val suggestions = remember(apps, launches, dockKeys, now / 3_600_000) { suggest(apps, launches, dockKeys.toSet(), now) }
    val launch: (AppEntry) -> Unit = { entry -> app.apps.launch(entry); drawer.hide(); notes.hide() }
    val addToDock: (AppEntry) -> Unit = { entry ->
        val slot = dockKeys.indexOfFirst { it.isEmpty() }.takeIf { it >= 0 } ?: (Prefs.DOCK_SLOTS - 1)
        app.prefs.setDockSlot(slot, entry.key)
    }
    LaunchedEffect(requests.openNotifications) { if (requests.openNotifications > 0) { drawer.hide(); notificationFilter = null; notes.show() } }
    LaunchedEffect(requests.closeAll) { if (requests.closeAll == 0) return@LaunchedEffect; drawer.hide(); notes.hide(); pickDockSlot = null; editing = false; requests.pickWidget = false }

    BackHandler(drawerShown || notesShown || pickDockSlot != null || editing || requests.pickWidget) {
        when {
            requests.pickWidget -> requests.pickWidget = false
            pickDockSlot != null -> pickDockSlot = null
            drawerShown -> drawer.hide()
            notesShown -> notes.hide()
            else -> editing = false
        }
    }

    val density = LocalDensity.current
    var menu by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heightPx = with(density) { maxHeight.toPx() }
        drawer.heightPx = heightPx * .8f
        notes.heightPx = heightPx * .8f
        Box(Modifier.fillMaxSize()
            .graphicsLayer {
                // Home sinks back a little as a layer comes over it.
                val p = maxOf(drawer.progress, notes.progress)
                alpha = 1f - .5f * p
                scaleX = 1f - .05f * p; scaleY = scaleX
            }
            .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = .40f), .5f to Color.Black.copy(alpha = .12f), 1f to Color.Black.copy(alpha = .40f)))
            .nestedScroll(pull.connection)
            .pointerInput(Unit) {
                // Drags outside the scrolling column (the rail, empty space): up for apps, down for notifications.
                val tracker = VelocityTracker()
                var travelled = 0f
                detectVerticalDragGestures(
                    onDragStart = { tracker.resetTracking(); travelled = 0f },
                    onDragEnd = { pull.release(tracker.calculateVelocity().y) },
                    onDragCancel = { pull.release(0f) },
                ) { change, dy ->
                    change.consume(); travelled += dy
                    tracker.addPosition(change.uptimeMillis, Offset(0f, travelled))
                    pull.drag(dy)
                }
            }
            .combinedClickable(remember { MutableInteractionSource() }, null, onLongClick = { menu = true }, onClick = {})) {
            Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(start = 16.dp, end = 10.dp, top = 6.dp)) {
                BoxWithConstraints(Modifier.weight(1f).padding(end = 12.dp)) {
                    val columnWidth = maxWidth.value.toInt()
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                        item("date") { DateHeader(now) }
                        item("stack") {
                            SmartStackCard(columnWidth, active = !drawerShown && !notesShown, editing = editing,
                                suggestions = if (stackSuggestions || stackIds.isEmpty()) ({ SuggestionsCard(suggestions, apps.isEmpty(), launch) }) else null,
                                onAutoPick = onAutoPick,
                                onAdd = { requests.pickTarget = Widgets.Target.STACK; requests.pickWidget = true })
                        }
                        if (wallpaperCard) item("wallpaper") { WallpaperCard() }
                        if (!connected) item("access") { AccessCard() }
                        items(widgets, key = { "w${it.id}" }) { widget -> HomeWidgetView(widget, editing, columnWidth) }
                        if (editing) item("edit-actions") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton({ requests.pickTarget = Widgets.Target.HOME; requests.pickWidget = true }) { Text("Add widget") }
                                FilledTonalButton({ editing = false }) { Text("Done") }
                            }
                        }
                    }
                }
                Column(Modifier.width(92.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatusCapsule(status, now, counted, hidden, Modifier.fillMaxWidth()) { pkg -> notificationFilter = pkg; notes.show() }
                    DockColumn(dock, Modifier.fillMaxWidth(), onLaunch = launch, onChoose = { pickDockSlot = it })
                }
            }
            // A small handle says there's more below.
            Box(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 10.dp)
                .clip(RoundedCornerShape(50)).clickable { drawer.show() }.padding(horizontal = 24.dp, vertical = 10.dp)) {
                Box(Modifier.width(44.dp).height(5.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .45f)))
            }
            Box(Modifier.align(Alignment.Center)) {
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Add widget") }, { menu = false; requests.pickTarget = Widgets.Target.HOME; requests.pickWidget = true })
                    DropdownMenuItem({ Text("Fill Smart Stack for me") }, { menu = false; onAutoPick() })
                    DropdownMenuItem({ Text(if (editing) "Done editing" else "Edit Home") }, { menu = false; editing = !editing })
                    DropdownMenuItem({ Text("Wallpaper") }, { menu = false
                        runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper")) } })
                    DropdownMenuItem({ Text("Ember settings") }, { menu = false
                        context.startActivity(Intent(context, SettingsActivity::class.java)) })
                }
            }
        }

        // The layers stay composed, parked off screen, so they open on the first frame of a swipe with nothing to build.
        Box(Modifier.fillMaxSize().graphicsLayer {
            translationY = -(1f - notes.progress) * size.height
            alpha = if (notes.progress == 0f) 0f else 1f
        }) {
            if (notesShown) NotificationsScreen(notifications, connected, now, notificationFilter, notes, onFilter = { notificationFilter = it })
        }
        Box(Modifier.fillMaxSize().graphicsLayer {
            translationY = (1f - drawer.progress) * size.height
            alpha = if (drawer.progress == 0f) 0f else 1f
        }) {
            AppDrawer(apps, open = drawerOpen, layer = drawer, onLaunch = launch, onAddToDock = addToDock, onClose = { drawer.hide() })
        }
        val slot = pickDockSlot
        AnimatedVisibility(slot != null, enter = fadeIn(), exit = fadeOut()) {
            AppDrawer(apps, open = slot != null, layer = null, title = "Choose an app for the dock",
                onLaunch = { entry -> if (slot != null) app.prefs.setDockSlot(slot, entry.key); pickDockSlot = null },
                onAddToDock = { entry -> if (slot != null) app.prefs.setDockSlot(slot, entry.key); pickDockSlot = null },
                onClose = { pickDockSlot = null })
        }
        AnimatedVisibility(requests.pickWidget, enter = fadeIn(), exit = fadeOut()) {
            WidgetPicker(onPick = { info -> requests.pickWidget = false; editing = true; onAddWidget(info, requests.pickTarget) },
                onClose = { requests.pickWidget = false })
        }
    }
}

@Composable
private fun DateHeader(now: Long) {
    val date = Date(now)
    Column(Modifier.padding(top = 2.dp)) {
        Text(SimpleDateFormat("EEEE", Locale.getDefault()).format(date).uppercase(), color = MaterialTheme.colorScheme.primary,
            fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(SimpleDateFormat("MMMM d", Locale.getDefault()).format(date), color = Color.White, fontSize = 40.sp,
            fontWeight = FontWeight.Bold, lineHeight = 44.sp)
    }
}

@Composable
private fun SuggestionsCard(apps: List<AppEntry>, loading: Boolean, onLaunch: (AppEntry) -> Unit) {
    Column(Modifier.fillMaxWidth().glass().padding(18.dp)) {
        Text("Suggestions", color = EmberColors.Secondary, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(14.dp))
        if (apps.isEmpty()) Text(if (loading) "Loading…" else "Apps you open often will show up here. Swipe up for all apps.",
            color = EmberColors.Secondary, fontSize = 14.sp)
        else Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            apps.forEach { entry ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { onLaunch(entry) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    AppIcon(entry, 54.dp)
                    Text(entry.label, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                }
            }
            repeat(4 - apps.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/**
 * Today's wallpaper photo: when it was taken, and three buttons. Dislike swaps it at once and it never comes back,
 * the arrows put a new one on, the heart makes it come back more often.
 */
@Composable
private fun WallpaperCard() {
    val context = LocalContext.current
    val current by Wallpapers.current.collectAsStateWithLifecycle()
    val changing by Wallpapers.changing.collectAsStateWithLifecycle()
    Row(Modifier.fillMaxWidth().glass().padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("WALLPAPER", color = EmberColors.Secondary, style = MaterialTheme.typography.labelMedium)
            val taken = current?.takenAt?.takeIf { it > 0 }
            Text(when {
                changing -> "Finding a photo…"
                taken != null -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(taken))
                else -> "From your photos"
            }, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
        IconButton({ Wallpapers.dislike(context) }, enabled = !changing) {
            Icon(Icons.Rounded.ThumbDown, "Don't show this photo again", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton({ Wallpapers.changeNow(context) }, enabled = !changing) {
            if (changing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Rounded.Refresh, "New photo", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton({ Wallpapers.like() }, enabled = current != null && !changing) {
            Icon(if (current?.liked == true) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                if (current?.liked == true) "Liked" else "Like", tint = if (current?.liked == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AccessCard() {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().glass().clickable { context.startActivity(Intent(context, SettingsActivity::class.java)) }.padding(16.dp)) {
        Text("Finish setting up Ember", style = MaterialTheme.typography.titleSmall)
        Text("Allow notification access for the rail, the notification page and the glowing pill.", color = EmberColors.Secondary,
            fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
