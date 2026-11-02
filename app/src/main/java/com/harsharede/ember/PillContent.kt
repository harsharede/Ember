package com.harsharede.ember

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** The pill window's content: the glowing light, or the pop-up with the messages it's holding. */
@Composable
internal fun PillContent(overlay: PillOverlay) {
    EmberTheme {
        AnimatedContent(overlay.expanded.value, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) }, label = "pill") { open ->
            if (open) PillPopup(overlay) else PillLight(overlay)
        }
    }
}

@Composable
private fun PillLight(overlay: PillOverlay) {
    val newest = overlay.pending.firstOrNull() ?: return
    // The app's own notification color when it set one, otherwise Ember's.
    val glow = Color(lightColor(newest.color, EmberColors.Ember.toArgb()))
    val pulse by rememberInfiniteTransition(label = "glow").animateFloat(.35f, 1f,
        infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val density = LocalDensity.current
    val count = overlay.pending.size
    Box(Modifier
        .padding(start = 18.dp, top = 6.dp)
        .semantics { contentDescription = "$count new notification${if (count == 1) "" else "s"} from ${newest.appLabel}. Tap to read." }
        .pointerInput(Unit) {
            var total = Offset.Zero
            detectDragGestures(onDragStart = { total = Offset.Zero }, onDragEnd = {
                val threshold = with(density) { 28.dp.toPx() }
                when {
                    total.y > threshold -> overlay.setExpanded(true)        // pull down: read
                    kotlin.math.abs(total.x) > threshold -> overlay.clear()  // flick aside: put the light out
                }
            }) { change, drag -> change.consume(); total += drag }
        }
        .clickable { overlay.setExpanded(true) }
        .padding(8.dp)) {
        Row(Modifier
            .drawBehind {
                // The glow: soft halos that breathe, under a bright core.
                for (i in 3 downTo 1) {
                    val grow = i * 4.dp.toPx()
                    drawRoundRect(glow.copy(alpha = .16f * pulse / i), topLeft = Offset(-grow, -grow),
                        size = androidx.compose.ui.geometry.Size(size.width + grow * 2, size.height + grow * 2),
                        cornerRadius = CornerRadius(size.height / 2 + grow))
                }
            }
            .clip(RoundedCornerShape(50))
            .background(glow.copy(alpha = .55f + .45f * pulse))
            .height(16.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            PackageIcon(newest.packageName, 12.dp, Modifier.clip(RoundedCornerShape(50)))
            if (count > 1) Text("$count", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 3.dp, end = 2.dp))
            else Box(Modifier.width(14.dp))
        }
    }
}

@Composable
private fun PillPopup(overlay: PillOverlay) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 10.dp, vertical = 6.dp)
        .glass(RoundedCornerShape(30.dp), alpha = .94f).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(EmberColors.Ember))
            Text("New", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                modifier = Modifier.padding(start = 8.dp).weight(1f))
            TextButton({ overlay.clear() }) { Text("Clear", color = EmberColors.EmberSoft) }
            TextButton({ overlay.setExpanded(false) }) { Text("Hide", color = EmberColors.EmberSoft) }
        }
        LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(overlay.pending, key = { it.key }) { item ->
                NotificationCard(item, now, maxLines = 6, onDone = {
                    // Acted on: it leaves the pill, and the pill goes out when nothing is left.
                    overlay.pending.removeAll { it.key == item.key }
                    if (overlay.pending.isEmpty()) overlay.clear()
                })
            }
        }
    }
}
