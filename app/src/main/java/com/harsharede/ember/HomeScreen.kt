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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.lazy.itemsIndexed
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
    /** Which Home page a Home widget goes on. */
    var pickPage by mutableIntStateOf(0)
    /** Open the notification page (from the lock-screen light's swipe up). */
    var openNotifications by mutableIntStateOf(0)
}

@Composable
fun HomeScreen(requests: HomeRequests, onAddWidget: (android.appwidget.AppWidgetProviderInfo, Widgets.Target, Int) -> Unit, onAutoPick: () -> Unit) {
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
    var stackEditor by remember { mutableStateOf(false) }
    var lookSheet by remember { mutableStateOf(false) }
    val pageCount by app.prefs.pageCount.collectAsStateWithLifecycle()
    val railWidth by app.prefs.railWidth.collectAsStateWithLifecycle()
    val dockHeight by app.prefs.dockHeight.collectAsStateWithLifecycle()
    val railAlpha by app.prefs.railAlpha.collectAsStateWithLifecycle()
    val cardAlpha by app.prefs.cardAlpha.collectAsStateWithLifecycle()
    val wallpaperPage by app.prefs.wallpaperPage.collectAsStateWithLifecycle()
    val animIcons by app.prefs.animIcons.collectAsStateWithLifecycle()
    val animCards by app.prefs.animCards.collectAsStateWithLifecycle()
    // Android's "Remove animations" wins over Ember's own switches.
    val systemAnimations = remember { android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
    val iconsFall = animIcons && systemAnimations
    val cardsFall = animCards && systemAnimations
    val entrance = remember { Entrance() }
    val iconStyle by app.prefs.iconStyle.collectAsStateWithLifecycle()
    val cardStyle by app.prefs.cardStyle.collectAsStateWithLifecycle()
    entrance.iconStyle = EntranceStyle.entries.getOrElse(iconStyle) { EntranceStyle.SCATTER }
    entrance.cardStyle = EntranceStyle.entries.getOrElse(cardStyle) { EntranceStyle.FLOAT }
    // The pages: Today, any widget pages, and last (if on) a page with nothing but the wallpaper.
    val homePager = rememberPagerState { pageCount + if (wallpaperPage) 1 else 0 }
    val totalPages = pageCount + if (wallpaperPage) 1 else 0
    val cleanIndex = pageCount
    /** How far the wallpaper page has come in, 0 to 1. Read in draw lambdas so swiping doesn't recompose Home. */
    val clean: () -> Float = {
        if (!wallpaperPage) 0f else (homePager.currentPage + homePager.currentPageOffsetFraction - (cleanIndex - 1)).coerceIn(0f, 1f)
    }
    val cleanNow by remember(wallpaperPage, cleanIndex) {
        derivedStateOf { wallpaperPage && homePager.currentPage == cleanIndex && kotlin.math.abs(homePager.currentPageOffsetFraction) < .05f }
    }
    // On the wallpaper page the navigation bar steps aside too; a swipe from the bottom edge brings it back.
    val view = androidx.compose.ui.platform.LocalView.current
    LaunchedEffect(cleanNow) {
        val window = (view.context as? android.app.Activity)?.window ?: return@LaunchedEffect
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
        if (cleanNow) controller.hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
        else controller.show(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
    }
    val notifications by NotificationStore.items.collectAsStateWithLifecycle()
    val connected by NotificationStore.connected.collectAsStateWithLifecycle()
    val status by rememberDeviceStatus()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); delay(60_000 - now % 60_000 + 50) }
    }
    val scope = rememberCoroutineScope()
    // Out of sight (an app opened, the screen went off): park the icons and cards. Back in sight: they fall into place.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) {
        if (iconsFall || cardsFall) scope.launch { entrance.park() }
    }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_START) { scope.launch { entrance.play() } }
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
    val dock = remember(apps, dockKeys) { dockKeys.mapNotNull { key -> apps.firstOrNull { it.key == key } } }
    val suggestions = remember(apps, launches, dockKeys, now / 3_600_000) { suggest(apps, launches, dockKeys.toSet(), now) }
    val launch: (AppEntry) -> Unit = { entry -> app.apps.launch(entry); drawer.hide(); notes.hide() }
    val addToDock: (AppEntry) -> Unit = { entry -> app.prefs.addToDock(entry.key) }
    LaunchedEffect(requests.openNotifications) { if (requests.openNotifications > 0) { drawer.hide(); notificationFilter = null; notes.show() } }
    LaunchedEffect(requests.closeAll) { if (requests.closeAll == 0) return@LaunchedEffect; drawer.hide(); notes.hide(); pickDockSlot = null; editing = false; requests.pickWidget = false; stackEditor = false; homePager.animateScrollToPage(0) }

    BackHandler(drawerShown || notesShown || pickDockSlot != null || editing || requests.pickWidget || stackEditor || homePager.currentPage > 0) {
        when {
            requests.pickWidget -> requests.pickWidget = false
            stackEditor -> stackEditor = false
            pickDockSlot != null -> pickDockSlot = null
            drawerShown -> drawer.hide()
            notesShown -> notes.hide()
            editing -> editing = false
            else -> scope.launch { homePager.animateScrollToPage(0) }
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
            // A light shade at the top and bottom keeps text readable; it fades out on the wallpaper page.
            .drawBehind {
                val a = 1f - clean()
                if (a > 0f) drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = .40f * a), .5f to Color.Black.copy(alpha = .12f * a), 1f to Color.Black.copy(alpha = .40f * a)))
            }
            // On the wallpaper page a sideways swipe anywhere goes back (the pager itself only spans the left column).
            .pointerInput(cleanNow) {
                if (!cleanNow) return@pointerInput
                var dx = 0f
                detectHorizontalDragGestures(onDragStart = { dx = 0f }, onDragEnd = {
                    if (dx > 60.dp.toPx()) scope.launch { homePager.animateScrollToPage(cleanIndex - 1) }
                }) { change, amount -> change.consume(); dx += amount }
            }
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
            CompositionLocalProvider(LocalGlassAlpha provides cardAlpha) {
            Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(start = 16.dp, end = 10.dp, top = 6.dp)) {
                BoxWithConstraints(Modifier.weight(1f).padding(end = 12.dp)) {
                    val columnWidth = maxWidth.value.toInt()
                    // Swipe sideways for more pages; the rail stays where it is.
                    HorizontalPager(homePager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, pageSpacing = 24.dp) { page ->
                        if (page == cleanIndex) { Box(Modifier.fillMaxSize()); return@HorizontalPager }
                        val pageWidgets = widgets.filter { it.page == page || (page == pageCount - 1 && it.page >= pageCount) }
                        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
                            if (page == 0) {
                                item("date") { Box(Modifier.fallIn(entrance, 0, cardsFall, Entrance.Kind.CARD)) { DateHeader(now) } }
                                item("stack") { Box(Modifier.fallIn(entrance, 1, cardsFall, Entrance.Kind.CARD)) {
                                    SmartStackCard(columnWidth, active = !drawerShown && !notesShown && !stackEditor && homePager.currentPage == 0, editing = editing,
                                        suggestions = { SuggestionsCard(suggestions, apps.isEmpty(), launch) },
                                        onEdit = { stackEditor = true })
                                } }
                                if (!connected) item("access") { AccessCard() }
                            } else if (pageWidgets.isEmpty()) item("empty") {
                                // A blank page: say what it's for.
                                Column(Modifier.fillMaxWidth().padding(top = 24.dp).glass().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Page ${page + 1}", style = MaterialTheme.typography.titleMedium)
                                    Text("A blank page for widgets.", color = EmberColors.Secondary, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                                    FilledTonalButton({ requests.pickTarget = Widgets.Target.HOME; requests.pickPage = page; requests.pickWidget = true }) { Text("Add widget") }
                                }
                            }
                            itemsIndexed(pageWidgets, key = { _, w -> "w${w.id}" }) { i, widget ->
                                HomeWidgetView(widget, editing, columnWidth, Modifier.fallIn(entrance, (if (page == 0) 2 else 0) + i, cardsFall, Entrance.Kind.CARD))
                            }
                            if (editing) item("edit-actions") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton({ requests.pickTarget = Widgets.Target.HOME; requests.pickPage = page; requests.pickWidget = true }) { Text("Add widget") }
                                    if (page > 0) FilledTonalButton({
                                        app.prefs.removePage(page).forEach { runCatching { Widgets.host.deleteAppWidgetId(it) } }
                                        scope.launch { homePager.animateScrollToPage((page - 1).coerceAtLeast(0)) }
                                    }) { Text("Remove page") }
                                    FilledTonalButton({ editing = false }) { Text("Done") }
                                }
                            }
                        }
                    }
                }
                // The rail: status on top, then the dock taking the room that's left (up to the height chosen in Customize
                // look). When the capsule grows, the dock gets shorter and scrolls, with an arrow saying there's more.
                BoxWithConstraints(Modifier.width(railWidth.dp).fillMaxHeight().padding(bottom = 8.dp)
                    .graphicsLayer { alpha = 1f - clean() }.then(if (cleanNow) Modifier.blockTouches() else Modifier)) {
                    val railHeight = maxHeight
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatusCapsule(status, now, counted, hidden, Modifier.fillMaxWidth(), alpha = railAlpha) { pkg -> notificationFilter = pkg; notes.show() }
                        DockColumn(dock, Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = railHeight * dockHeight),
                            iconSize = (railWidth * .57f).dp, alpha = railAlpha, entrance = entrance, animate = iconsFall,
                            onLaunch = launch, onChoose = { pickDockSlot = it })
                    }
                }
            }
            }
            // Page dots, when there's more than one page. Tap one to go there.
            if (totalPages > 1 && !cleanNow) Row(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 40.dp, end = railWidth.dp)
                .graphicsLayer { alpha = 1f - clean() }) {
                repeat(totalPages) { i ->
                    val selected = i == homePager.currentPage
                    Box(Modifier.clip(CircleShape).clickable { scope.launch { homePager.animateScrollToPage(i) } }.padding(5.dp)) {
                        Box(Modifier.size(if (selected) 8.dp else 6.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = if (selected) .9f else .4f)))
                    }
                }
            }
            // The wallpaper control: tiny, bottom-left.
            // A small handle says there's more below.
            if (!cleanNow) Box(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 10.dp)
                .graphicsLayer { alpha = 1f - clean() }
                .clip(RoundedCornerShape(50)).clickable { drawer.show() }.padding(horizontal = 24.dp, vertical = 10.dp)) {
                Box(Modifier.width(44.dp).height(5.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .45f)))
            }
            Box(Modifier.align(Alignment.Center)) {
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Add widget") }, { menu = false; requests.pickTarget = Widgets.Target.HOME; requests.pickPage = homePager.currentPage; requests.pickWidget = true })
                    DropdownMenuItem({ Text("Add page") }, { menu = false
                        val page = app.prefs.addPage()
                        scope.launch { delay(50); homePager.animateScrollToPage(page) } })
                    DropdownMenuItem({ Text("Customize look") }, { menu = false; lookSheet = true })
                    DropdownMenuItem({ Text("Edit Smart Stack") }, { menu = false; stackEditor = true })
                    DropdownMenuItem({ Text(if (editing) "Done editing" else "Edit Home") }, { menu = false; editing = !editing })
                    DropdownMenuItem({ Text("Wallpaper") }, { menu = false
                        runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper")) } })
                    DropdownMenuItem({ Text("Ember settings") }, { menu = false
                        context.startActivity(Intent(context, SettingsActivity::class.java)) })
                }
            }
            // The wallpaper control: tiny, bottom-left, opening upwards. Last, so it sits above everything else on Home
            // (the drawer handle used to cover its buttons).
            if (wallpaperCard && !cleanNow) WallpaperMini(Modifier.align(Alignment.BottomStart).windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 14.dp, bottom = 10.dp).graphicsLayer { alpha = 1f - clean() })
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
            AppDrawer(apps, open = slot != null, layer = null, title = if (slot != null && slot < dockKeys.size) "Change this dock app" else "Add an app to the dock",
                onLaunch = { entry -> if (slot != null) app.prefs.setDockSlot(slot, entry.key); pickDockSlot = null },
                onAddToDock = { entry -> if (slot != null) app.prefs.setDockSlot(slot, entry.key); pickDockSlot = null },
                onClose = { pickDockSlot = null })
        }
        AnimatedVisibility(stackEditor, enter = fadeIn(), exit = fadeOut()) {
            StackEditor(onAddWidget = { requests.pickTarget = Widgets.Target.STACK; requests.pickWidget = true },
                onAutoPick = onAutoPick, onClose = { stackEditor = false })
        }
        if (lookSheet) LookSheet(onClose = { lookSheet = false })
        AnimatedVisibility(requests.pickWidget, enter = fadeIn(), exit = fadeOut()) {
            WidgetPicker(onPick = { info -> requests.pickWidget = false; if (requests.pickTarget == Widgets.Target.HOME) editing = true
                onAddWidget(info, requests.pickTarget, requests.pickPage) },
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

@Composable
private fun AccessCard() {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().glass().clickable { context.startActivity(Intent(context, SettingsActivity::class.java)) }.padding(16.dp)) {
        Text("Finish setting up Ember", style = MaterialTheme.typography.titleSmall)
        Text("Allow notification access for the rail, the notification page and the glowing pill.", color = EmberColors.Secondary,
            fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Takes every touch so nothing under it can be tapped (the faded-out rail on the wallpaper page). */
private fun Modifier.blockTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial).changes.forEach { it.consume() }
    }
}
