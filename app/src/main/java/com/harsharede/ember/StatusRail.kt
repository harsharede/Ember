@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.harsharede.ember

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.rounded.Close
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Image
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.basicMarquee
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.rounded.AirplanemodeInactive
import androidx.compose.material.icons.rounded.DoNotDisturbOff
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.EnergySavingsLeaf
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Wifi1Bar
import androidx.compose.material.icons.rounded.Wifi2Bar
import androidx.compose.material.icons.rounded.SignalCellular4Bar
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SignalCellularAlt1Bar
import androidx.compose.material.icons.rounded.SignalCellularAlt2Bar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The right-hand status capsule, standing in for Android's status bar: the time, and an icon for each thing that's
 * on. Swipe an icon sideways to hide it (Settings brings it back). The notification count opens to show which apps.
 */
@Composable
fun StatusCapsule(
    status: DeviceStatus,
    now: Long,
    notifications: List<NotifItem>,
    hidden: Set<String>,
    modifier: Modifier = Modifier,
    alpha: Float = .8f,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(36.dp),
    widthDp: Int = 92,
    onFlip: (() -> Unit)? = null,
    onOpenNotifications: (String?) -> Unit,
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val hide: (StatusIcon) -> Unit = { icon ->
        EmberApp.instance.prefs.hideStatus(icon.id)
        Toast.makeText(context, "${icon.label} hidden. Bring it back in Ember settings.", Toast.LENGTH_SHORT).show()
    }
    fun shown(icon: StatusIcon) = icon.id !in hidden
    val showOff by EmberApp.instance.prefs.statusShowOff.collectAsStateWithLifecycle()
    // Everything scales with the rail, so a narrow rail still fits the time.
    val timeSize = (widthDp * .22f).coerceIn(13f, 20f).sp
    val iconSize = (widthDp * .22f).coerceIn(15f, 20f).dp
    Column(modifier.glass(shape, alpha).padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Double-tap the time to move the rail to the other edge (the other thumb).
        val clockStyle by EmberApp.instance.prefs.clockStyle.collectAsStateWithLifecycle()
        Box(if (onFlip == null) Modifier else Modifier.clip(RoundedCornerShape(12.dp))
            .combinedClickable(onDoubleClick = onFlip, onClick = {}).padding(horizontal = 4.dp)) {
            RailClock(now, clockStyle, timeSize)
        }

        // What's on, and (if chosen) what's off, dimmed, so it can be tapped back on.
        val icons = buildList {
            fun put(id: StatusIcon, on: Boolean, onIcon: ImageVector, offIcon: ImageVector?) {
                if (!shown(id)) return
                if (on) add(Triple(id, onIcon, true)) else if (showOff && offIcon != null) add(Triple(id, offIcon, false))
            }
            put(StatusIcon.WIFI, status.wifi, wifiIcon(status.wifiLevel), Icons.Rounded.WifiOff)
            put(StatusIcon.CELLULAR, status.cellular && !status.airplane, cellIcon(status.cellLevel), null)
            put(StatusIcon.VPN, status.vpn, Icons.Rounded.VpnKey, null)
            put(StatusIcon.BLUETOOTH, status.bluetooth, Icons.Rounded.Bluetooth, Icons.Rounded.BluetoothDisabled)
            put(StatusIcon.AIRPLANE, status.airplane, Icons.Rounded.AirplanemodeActive, Icons.Rounded.AirplanemodeInactive)
            put(StatusIcon.DND, status.dnd, Icons.Rounded.DoNotDisturbOn, Icons.Rounded.DoNotDisturbOff)
            put(StatusIcon.RINGER, status.silent || status.vibrate, if (status.silent) Icons.Rounded.NotificationsOff else Icons.Rounded.Vibration,
                Icons.Rounded.NotificationsActive)
            put(StatusIcon.LOCATION, status.location, Icons.Rounded.LocationOn, Icons.Rounded.LocationOff)
            put(StatusIcon.POWER_SAVE, status.powerSave, Icons.Rounded.EnergySavingsLeaf, null)
        }
        // Two to a row keeps the capsule narrow however many things are on. Tap: its quick control; hold: its settings.
        icons.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (id, vector, on) ->
                    SwipeAway(onGone = { hide(id) }) {
                        Box(Modifier.clip(CircleShape).combinedClickable(onLongClick = { StatusActions.longPress(context, id) },
                            onClick = { StatusActions.tap(context, id, status) }).padding(3.dp)) {
                            Icon(vector, id.label, modifier = Modifier.size(iconSize),
                                tint = (if (on && (id == StatusIcon.DND || id == StatusIcon.RINGER)) EmberColors.Ember else Color.White)
                                    .copy(alpha = if (on) 1f else .4f))
                        }
                    }
                }
            }
        }
        if (shown(StatusIcon.BATTERY)) SwipeAway(onGone = { hide(StatusIcon.BATTERY) }) {
            Box(Modifier.clip(RoundedCornerShape(16.dp)).combinedClickable(onLongClick = { StatusActions.longPress(context, StatusIcon.BATTERY) },
                onClick = { StatusActions.tap(context, StatusIcon.BATTERY, status) })) { BatteryRing(status, (widthDp * .41f).coerceIn(26f, 38f).dp) }
        }

        if (shown(StatusIcon.NOTIFICATIONS) && notifications.isNotEmpty()) SwipeAway(onGone = { hide(StatusIcon.NOTIFICATIONS) }) {
            Row(Modifier.clip(RoundedCornerShape(50)).background(EmberColors.Ember.copy(alpha = .9f))
                .clickable { expanded = !expanded }.padding(horizontal = 9.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Notifications, "Notifications", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                Text("${notifications.size}", color = MaterialTheme.colorScheme.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 3.dp))
            }
        }
        // Expanded: one icon per app with notifications, newest first; tap one to read just that app's.
        AnimatedVisibility(expanded && notifications.isNotEmpty(), enter = expandVertically(), exit = shrinkVertically()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                notifications.map { it.packageName }.distinct().take(8).forEach { pkg ->
                    val count = notifications.count { it.packageName == pkg }
                    Box(Modifier.clip(CircleShape).clickable { expanded = false; onOpenNotifications(pkg) }.padding(2.dp)) {
                        PackageIcon(pkg, 32.dp)
                        if (count > 1) Text("$count", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.TopEnd).clip(CircleShape).background(Color.White).padding(horizontal = 4.dp))
                    }
                }
                Text("All", color = EmberColors.EmberSoft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { expanded = false; onOpenNotifications(null) }
                        .padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
    }
}

/** A sideways swipe past a short distance hides the item; a smaller one springs back. */
@Composable
private fun SwipeAway(onGone: () -> Unit, content: @Composable () -> Unit) {
    var dx by remember { mutableFloatStateOf(0f) }
    val limit = with(LocalDensity.current) { 36.dp.toPx() }
    Box(Modifier
        .offset { IntOffset(dx.roundToInt(), 0) }
        .graphicsLayer { alpha = 1f - (abs(dx) / (limit * 1.4f)).coerceIn(0f, .8f) }
        .pointerInput(Unit) {
            detectHorizontalDragGestures(onDragEnd = { if (abs(dx) > limit) onGone(); dx = 0f }, onDragCancel = { dx = 0f }) { change, amount ->
                change.consume(); dx += amount
            }
        }) { content() }
}

@Composable
private fun BatteryRing(status: DeviceStatus, ring: Dp = 38.dp) {
    val color = when {
        status.charging -> Color(0xFF34C759)
        status.battery <= 15 -> EmberColors.Red
        status.powerSave -> Color(0xFFFFD60A)
        else -> Color.White
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(ring)) {
            val stroke = 4.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(Color.White.copy(alpha = .15f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(color, -90f, 360f * status.battery / 100f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("${status.battery}%", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

private fun wifiIcon(level: Int): ImageVector = when (level) {
    1 -> Icons.Rounded.Wifi1Bar
    2 -> Icons.Rounded.Wifi2Bar
    else -> Icons.Rounded.Wifi
}

private fun cellIcon(level: Int): ImageVector = when {
    level <= 1 -> Icons.Rounded.SignalCellularAlt1Bar
    level == 2 -> Icons.Rounded.SignalCellularAlt2Bar
    level == 3 -> Icons.Rounded.SignalCellularAlt
    else -> Icons.Rounded.SignalCellular4Bar
}

/**
 * The dock under the capsule: as many apps as you like, top to bottom. It takes the room left between the capsule and
 * the wallpaper buttons; when the capsule grows (more status icons, notifications opened) the dock gets shorter and
 * scrolls, with a small arrow saying there's more. Tap to open; hold for change, move or remove; + adds one.
 */
@Composable
fun DockColumn(apps: List<AppEntry>, modifier: Modifier = Modifier, iconSize: Dp = 52.dp, alpha: Float = .8f,
               entrance: Entrance? = null, animate: Boolean = false,
               shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(36.dp),
               onLaunch: (AppEntry) -> Unit, onChoose: (Int) -> Unit) {
    val prefs = EmberApp.instance.prefs
    val scroll = rememberScrollState()
    Box(modifier.glass(shape, alpha)) {
        Column(Modifier.verticalScroll(scroll).padding(vertical = 12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            apps.forEachIndexed { index, app ->
                var menu by remember { mutableStateOf(false) }
                Box {
                    Box(Modifier.then(if (entrance != null) Modifier.fallIn(entrance, index, animate, Entrance.Kind.ICON) else Modifier)
                        .clip(CircleShape).combinedClickable(onLongClick = { menu = true }, onClick = { onLaunch(app) }).padding(2.dp)) {
                        AppIcon(app, iconSize)
                    }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Change app") }, { menu = false; onChoose(index) })
                        if (index > 0) DropdownMenuItem({ Text("Move up") }, { menu = false; prefs.moveInDock(app.key, -1) })
                        if (index < apps.lastIndex) DropdownMenuItem({ Text("Move down") }, { menu = false; prefs.moveInDock(app.key, 1) })
                        DropdownMenuItem({ Text("Remove from dock") }, { menu = false; prefs.removeFromDock(app.key) })
                    }
                }
            }
            Box(Modifier.then(if (entrance != null) Modifier.fallIn(entrance, apps.size, animate, Entrance.Kind.ICON) else Modifier)
                .size(iconSize * .8f).clip(CircleShape).background(Color.White.copy(alpha = .06f)).clickable { onChoose(apps.size) },
                contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, "Add an app to the dock", tint = EmberColors.Secondary)
            }
        }
        // More above or below than fits: a small arrow at that edge.
        if (scroll.canScrollBackward) MoreArrow(Icons.Rounded.KeyboardArrowUp, Modifier.align(Alignment.TopCenter))
        if (scroll.canScrollForward) MoreArrow(Icons.Rounded.KeyboardArrowDown, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun MoreArrow(icon: ImageVector, modifier: Modifier) {
    Box(modifier.padding(2.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = .9f)).padding(1.dp)) {
        Icon(icon, "More apps", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
    }
}

/**
 * The wallpaper control, tiny, in the bottom-left corner: a small round button. Tap it and it opens upwards (where and
 * when the photo was taken, then like, new photo and dislike), well clear of the drawer handle in the middle; it folds
 * away again a few seconds after the last touch.
 */
@Composable
fun WallpaperMini(modifier: Modifier = Modifier, alignEnd: Boolean = false) {
    val context = LocalContext.current
    val current by Wallpapers.current.collectAsStateWithLifecycle()
    val changing by Wallpapers.changing.collectAsStateWithLifecycle()
    val liked = current?.liked == true
    var open by remember { mutableStateOf(false) }
    var touched by remember { mutableIntStateOf(0) }
    var hasPlaceAccess by remember { mutableStateOf(Wallpapers.hasPlaceAccess(context)) }
    val placeAccess = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
        hasPlaceAccess = ok
        if (ok) Wallpapers.lookUpPlace(context)
    }
    // Folds away by itself a few seconds after the last touch.
    LaunchedEffect(open, touched, changing) { if (open && !changing) { kotlinx.coroutines.delay(6_000); open = false } }
    val line = remember(current, changing, hasPlaceAccess) {
        val c = current
        val date = c?.takenAt?.takeIf { it > 0 }?.let { SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(it)) }
        when {
            changing -> "Finding a photo…"
            c == null -> "From your photos"
            c.place != null -> listOfNotNull(memoryLabel(c.yearsAgo), c.place, date).joinToString("  ·  ")
            !hasPlaceAccess -> listOfNotNull(memoryLabel(c.yearsAgo), date, "Tap to show where").joinToString("  ·  ")
            else -> listOfNotNull(memoryLabel(c.yearsAgo), date).joinToString("  ·  ").ifEmpty { "From your photos" }
        }
    }
    val fill = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .75f)
    val side = if (alignEnd) Alignment.End else Alignment.Start
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = side) {
        AnimatedVisibility(open, enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = side) {
                Row(Modifier.widthIn(max = 180.dp).clip(RoundedCornerShape(50)).background(fill).clickable {
                    touched++
                    val c = current
                    when {
                        c != null && c.place == null && !hasPlaceAccess -> placeAccess.launch(android.Manifest.permission.ACCESS_MEDIA_LOCATION)
                        c != null -> runCatching {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(c.uri, "image/*")
                                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                }.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Place, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
                    Text(line, fontSize = 11.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 3.dp).basicMarquee(iterations = 2, repeatDelayMillis = 1200, initialDelayMillis = 600))
                }
                MiniButton(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (liked) "Liked" else "Like this wallpaper",
                    enabled = current != null && !changing, tint = if (liked) MaterialTheme.colorScheme.primary else null, fill = fill) { touched++; Wallpapers.like() }
                MiniButton(Icons.Rounded.Refresh, "New wallpaper", enabled = !changing, fill = fill) { touched++; Wallpapers.changeNow(context) }
                MiniButton(Icons.Rounded.ThumbDown, "Don't show this photo again", enabled = !changing, fill = fill) { touched++; Wallpapers.dislike(context) }
            }
        }
        // The button itself: shows a heart when this photo is liked, a spinner while a new one is found, × while open.
        Box(Modifier.size(34.dp).clip(CircleShape).background(fill).clickable { open = !open; touched++ }, contentAlignment = Alignment.Center) {
            when {
                changing -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp)
                open -> Icon(Icons.Rounded.Close, "Close", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface)
                else -> Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.Image, "Wallpaper", Modifier.size(16.dp),
                    tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .85f))
            }
        }
    }
}

@Composable
private fun MiniButton(icon: ImageVector, label: String, enabled: Boolean, tint: Color? = null, fill: Color = Color.Transparent, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).clip(CircleShape).background(fill).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, Modifier.size(16.dp), tint = (tint ?: MaterialTheme.colorScheme.onSurface).copy(alpha = if (enabled) .9f else .35f))
    }
}

/** "3 years ago today", for an On this day wallpaper. */
fun memoryLabel(yearsAgo: Int): String? = when {
    yearsAgo <= 0 -> null
    yearsAgo == 1 -> "1 year ago today"
    else -> "$yearsAgo years ago today"
}

/** The rail's time in one of three styles (Customize look › Clock style): Digital, Stacked, 12-hour. */
@Composable
fun RailClock(now: Long, style: Int, size: androidx.compose.ui.unit.TextUnit) {
    val date = Date(now)
    fun f(p: String) = SimpleDateFormat(p, Locale.getDefault()).format(date)
    when (style) {
        // Hours over minutes, large: reads at a glance from across the table.
        1 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(f("HH"), color = Color.White, fontSize = size * 1.5f, fontWeight = FontWeight.Black, lineHeight = size * 1.5f)
            Text(f("mm"), color = MaterialTheme.colorScheme.primary, fontSize = size * 1.5f, fontWeight = FontWeight.Black, lineHeight = size * 1.5f)
        }
        2 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(f("h:mm"), color = Color.White, fontSize = size, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(f("a").uppercase(), color = EmberColors.Secondary, fontSize = size * .55f, fontWeight = FontWeight.SemiBold)
        }
        else -> Text(f("HH:mm"), color = Color.White, fontSize = size, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
