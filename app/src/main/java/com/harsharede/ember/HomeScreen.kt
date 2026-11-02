@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.harsharede.ember

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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

/** Which full-screen layer is open over Home. Home's own button closes any of them. */
class HomeLayers {
    var drawer by mutableStateOf(false)
    var notifications by mutableStateOf(false)
    var notificationFilter by mutableStateOf<String?>(null)
    var pickDockSlot by mutableStateOf<Int?>(null)
    fun closeAll() { drawer = false; notifications = false; pickDockSlot = null }
}

@Composable
fun HomeScreen(layers: HomeLayers) {
    val context = LocalContext.current
    val app = EmberApp.instance
    val apps by app.apps.apps.collectAsStateWithLifecycle()
    val dockKeys by app.prefs.dock.collectAsStateWithLifecycle()
    val launches by app.prefs.launches.collectAsStateWithLifecycle()
    val hidden by app.prefs.hiddenStatus.collectAsStateWithLifecycle()
    val notifications by NotificationStore.items.collectAsStateWithLifecycle()
    val connected by NotificationStore.connected.collectAsStateWithLifecycle()
    val status by rememberDeviceStatus()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); delay(60_000 - now % 60_000 + 50) }
    }
    val dock = remember(apps, dockKeys) { dockKeys.map { key -> apps.firstOrNull { it.key == key } } }
    val suggestions = remember(apps, launches, dockKeys, now / 3_600_000) { suggest(apps, launches, dockKeys.toSet(), now) }
    val launch: (AppEntry) -> Unit = { app.apps.launch(it); layers.closeAll() }
    val addToDock: (AppEntry) -> Unit = { entry ->
        val slot = dockKeys.indexOfFirst { it.isEmpty() }.takeIf { it >= 0 } ?: (Prefs.DOCK_SLOTS - 1)
        app.prefs.setDockSlot(slot, entry.key)
    }

    BackHandler(layers.drawer || layers.notifications || layers.pickDockSlot != null) { layers.closeAll() }

    val density = LocalDensity.current
    var menu by remember { mutableStateOf(false) }
    // The layers sit beside Home's gesture area, not inside it, so scrolling a list never opens another layer.
    Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize()
        // A light shade at the top and bottom keeps text readable on any wallpaper.
        .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = .45f), .5f to Color.Black.copy(alpha = .15f), 1f to Color.Black.copy(alpha = .45f)))
        .pointerInput(Unit) {
            val threshold = with(density) { 60.dp.toPx() }
            var total = 0f
            detectVerticalDragGestures(onDragStart = { total = 0f }, onDragEnd = {
                if (total < -threshold) layers.drawer = true                                    // swipe up: apps
                else if (total > threshold) { layers.notificationFilter = null; layers.notifications = true } // swipe down: notifications
            }) { change, amount -> change.consume(); total += amount }
        }
        .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
            onLongClick = { menu = true }, onClick = {})) {
        Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(start = 18.dp, end = 10.dp, top = 8.dp)) {
            Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DateHeader(now)
                SuggestionsCard(suggestions, apps.isEmpty(), launch)
                ClockCard(now)
                if (!connected) AccessCard()
            }
            Column(Modifier.width(92.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusCapsule(status, now, notifications, hidden, Modifier.fillMaxWidth()) { pkg ->
                    layers.notificationFilter = pkg; layers.notifications = true
                }
                DockColumn(dock, Modifier.fillMaxWidth(), onLaunch = launch, onChoose = { layers.pickDockSlot = it })
            }
        }
        // A small handle says there's more below.
        Box(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 10.dp)
            .clip(RoundedCornerShape(50)).clickable { layers.drawer = true }.padding(horizontal = 24.dp, vertical = 10.dp)) {
            Box(Modifier.width(44.dp).height(5.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .45f)))
        }
        Box(Modifier.align(Alignment.Center)) {
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text("Wallpaper") }, { menu = false
                    runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper")) } })
                DropdownMenuItem({ Text("Ember settings") }, { menu = false
                    context.startActivity(Intent(context, SettingsActivity::class.java)) })
            }
        }

    }
        AnimatedVisibility(layers.notifications, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
            NotificationsScreen(notifications, connected, now, layers.notificationFilter, onFilter = { layers.notificationFilter = it },
                onClose = { layers.notifications = false })
        }
        AnimatedVisibility(layers.drawer, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            AppDrawer(apps, onLaunch = launch, onAddToDock = addToDock, onClose = { layers.drawer = false })
        }
        val slot = layers.pickDockSlot
        AnimatedVisibility(slot != null, enter = fadeIn(), exit = fadeOut()) {
            AppDrawer(apps, onLaunch = { entry -> if (slot != null) app.prefs.setDockSlot(slot, entry.key); layers.pickDockSlot = null },
                onAddToDock = { entry -> if (slot != null) app.prefs.setDockSlot(slot, entry.key); layers.pickDockSlot = null },
                onClose = { layers.pickDockSlot = null })
        }
    }
}

@Composable
private fun DateHeader(now: Long) {
    val date = Date(now)
    Column(Modifier.padding(top = 4.dp)) {
        Text(SimpleDateFormat("EEEE", Locale.getDefault()).format(date).uppercase(), color = EmberColors.Ember,
            fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(SimpleDateFormat("MMMM d", Locale.getDefault()).format(date), color = Color.White, fontSize = 40.sp,
            fontWeight = FontWeight.Bold, lineHeight = 44.sp)
    }
}

@Composable
private fun SuggestionsCard(apps: List<AppEntry>, loading: Boolean, onLaunch: (AppEntry) -> Unit) {
    Column(Modifier.fillMaxWidth().glass().padding(18.dp)) {
        Text("Suggestions", color = EmberColors.Secondary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(14.dp))
        if (apps.isEmpty()) Text(if (loading) "Loading…" else "Apps you open often will show up here. Swipe up for all apps.",
            color = EmberColors.Secondary, fontSize = 14.sp)
        else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            apps.forEach { entry ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { onLaunch(entry) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    AppIcon(entry, 54.dp)
                    Text(entry.label, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp))
                }
            }
            repeat(4 - apps.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun ClockCard(now: Long) {
    val date = Date(now)
    Column(Modifier.size(150.dp).glass().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Text("LOCAL TIME", color = EmberColors.Secondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(date), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Text(SimpleDateFormat("EEE", Locale.getDefault()).format(date), color = EmberColors.Secondary, fontSize = 14.sp)
    }
}

@Composable
private fun AccessCard() {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().glass().clickable { context.startActivity(Intent(context, SettingsActivity::class.java)) }.padding(16.dp)) {
        Text("Finish setting up Ember", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text("Allow notification access for the rail, the notification page and the glowing pill.", color = EmberColors.Secondary,
            fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
