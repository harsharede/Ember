@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.harsharede.ember

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
    onOpenNotifications: (String?) -> Unit,
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val hide: (StatusIcon) -> Unit = { icon ->
        EmberApp.instance.prefs.hideStatus(icon.id)
        Toast.makeText(context, "${icon.label} hidden. Bring it back in Ember settings.", Toast.LENGTH_SHORT).show()
    }
    fun shown(icon: StatusIcon) = icon.id !in hidden
    Column(modifier.glass(RoundedCornerShape(36.dp), alpha).padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)

        val icons = buildList {
            if (shown(StatusIcon.WIFI) && status.wifi) add(StatusIcon.WIFI to wifiIcon(status.wifiLevel))
            if (shown(StatusIcon.CELLULAR) && status.cellular && !status.airplane) add(StatusIcon.CELLULAR to cellIcon(status.cellLevel))
            if (shown(StatusIcon.VPN) && status.vpn) add(StatusIcon.VPN to Icons.Rounded.VpnKey)
            if (shown(StatusIcon.BLUETOOTH) && status.bluetooth) add(StatusIcon.BLUETOOTH to Icons.Rounded.Bluetooth)
            if (shown(StatusIcon.AIRPLANE) && status.airplane) add(StatusIcon.AIRPLANE to Icons.Rounded.AirplanemodeActive)
            if (shown(StatusIcon.DND) && status.dnd) add(StatusIcon.DND to Icons.Rounded.DoNotDisturbOn)
            if (shown(StatusIcon.RINGER) && (status.silent || status.vibrate))
                add(StatusIcon.RINGER to if (status.silent) Icons.Rounded.NotificationsOff else Icons.Rounded.Vibration)
            if (shown(StatusIcon.LOCATION) && status.location) add(StatusIcon.LOCATION to Icons.Rounded.LocationOn)
            if (shown(StatusIcon.POWER_SAVE) && status.powerSave) add(StatusIcon.POWER_SAVE to Icons.Rounded.EnergySavingsLeaf)
        }
        // Two to a row keeps the capsule narrow however many things are on.
        icons.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (id, vector) ->
                    SwipeAway(onGone = { hide(id) }) {
                        Icon(vector, id.label, tint = if (id == StatusIcon.DND || id == StatusIcon.RINGER) EmberColors.Ember else Color.White,
                            modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        if (shown(StatusIcon.BATTERY)) SwipeAway(onGone = { hide(StatusIcon.BATTERY) }) { BatteryRing(status) }

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
private fun BatteryRing(status: DeviceStatus) {
    val color = when {
        status.charging -> Color(0xFF34C759)
        status.battery <= 15 -> EmberColors.Red
        status.powerSave -> Color(0xFFFFD60A)
        else -> Color.White
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(38.dp)) {
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
               onLaunch: (AppEntry) -> Unit, onChoose: (Int) -> Unit) {
    val prefs = EmberApp.instance.prefs
    val scroll = rememberScrollState()
    Box(modifier.glass(RoundedCornerShape(36.dp), alpha)) {
        Column(Modifier.verticalScroll(scroll).padding(vertical = 12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            apps.forEachIndexed { index, app ->
                var menu by remember { mutableStateOf(false) }
                Box {
                    Box(Modifier.clip(CircleShape).combinedClickable(onLongClick = { menu = true }, onClick = { onLaunch(app) }).padding(2.dp)) {
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
            Box(Modifier.size(iconSize * .8f).clip(CircleShape).background(Color.White.copy(alpha = .06f)).clickable { onChoose(apps.size) },
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
 * The wallpaper control, tiny, in a corner: a small round button. Tap it and it opens into a bar with where and when
 * the photo was taken (scrolling past), like, new photo and dislike; it folds away again after a few seconds.
 */
@Composable
fun WallpaperMini(modifier: Modifier = Modifier) {
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
            c.place != null -> listOfNotNull(c.place, date).joinToString("  ·  ")
            !hasPlaceAccess -> listOfNotNull(date, "Tap to show where").joinToString("  ·  ")
            else -> date ?: "From your photos"
        }
    }
    val small = 30.dp
    Row(modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .55f))
        .animateContentSize(), verticalAlignment = Alignment.CenterVertically) {
        if (!open) {
            Box(Modifier.size(small).clickable { open = true; touched++ }, contentAlignment = Alignment.Center) {
                if (changing) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp)
                else Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.Image, "Wallpaper", Modifier.size(15.dp),
                    tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .8f))
            }
        } else {
            Row(Modifier.widthIn(max = 150.dp).clickable {
                touched++
                val c = current
                when {
                    c != null && c.place == null && !hasPlaceAccess -> placeAccess.launch(android.Manifest.permission.ACCESS_MEDIA_LOCATION)
                    c != null -> runCatching {
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(c.uri, "image/*")
                            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }.padding(start = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Place, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
                Text(line, fontSize = 11.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 3.dp).basicMarquee(iterations = 2, repeatDelayMillis = 1200, initialDelayMillis = 600))
            }
            MiniButton(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (liked) "Liked" else "Like this wallpaper",
                enabled = current != null && !changing, tint = if (liked) MaterialTheme.colorScheme.primary else null) { touched++; Wallpapers.like() }
            MiniButton(Icons.Rounded.Refresh, "New wallpaper", enabled = !changing) { touched++; Wallpapers.changeNow(context) }
            MiniButton(Icons.Rounded.ThumbDown, "Don't show this photo again", enabled = !changing) { touched++; Wallpapers.dislike(context) }
        }
    }
}

@Composable
private fun MiniButton(icon: ImageVector, label: String, enabled: Boolean, tint: Color? = null, onClick: () -> Unit) {
    Box(Modifier.size(30.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, Modifier.size(16.dp), tint = (tint ?: MaterialTheme.colorScheme.onSurface).copy(alpha = if (enabled) .9f else .35f))
    }
}
