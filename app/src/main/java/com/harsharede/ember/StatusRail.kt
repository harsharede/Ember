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
import androidx.compose.material.icons.rounded.AirplanemodeActive
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
    onOpenNotifications: (String?) -> Unit,
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val hide: (StatusIcon) -> Unit = { icon ->
        EmberApp.instance.prefs.hideStatus(icon.id)
        Toast.makeText(context, "${icon.label} hidden. Bring it back in Ember settings.", Toast.LENGTH_SHORT).show()
    }
    fun shown(icon: StatusIcon) = icon.id !in hidden
    Column(modifier.glass(RoundedCornerShape(36.dp)).padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally,
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

/** The dock under the capsule. Tap to open; hold to pick a different app for that place. */
@Composable
fun DockColumn(slots: List<AppEntry?>, modifier: Modifier = Modifier, onLaunch: (AppEntry) -> Unit, onChoose: (Int) -> Unit) {
    Column(modifier.glass(RoundedCornerShape(36.dp)).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        slots.forEachIndexed { index, app ->
            Box(Modifier.clip(CircleShape).combinedClickable(onLongClick = { onChoose(index) }, onClick = { if (app != null) onLaunch(app) else onChoose(index) })
                .padding(2.dp), contentAlignment = Alignment.Center) {
                if (app != null) AppIcon(app, 52.dp)
                else Box(Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = .06f)), contentAlignment = Alignment.Center) {
                    Text("+", color = EmberColors.Secondary, fontSize = 22.sp)
                }
            }
        }
    }
}

