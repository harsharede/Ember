package com.harsharede.ember

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/** Notifications that arrived since the notification page was last opened, newest first. */
fun unreadOf(items: List<NotifItem>, lastSeen: Long): List<NotifItem> = items.filter { it.postTime > lastSeen }

/**
 * The unread ticker under the date: one line at a time rolls up through what arrived since you last looked ("WhatsApp ·
 * Asha: on my way"), with a count. Tap it to open the notification page. It only moves while Home is on screen.
 */
@Composable
fun UnreadTicker(unread: List<NotifItem>, onOpen: () -> Unit) {
    if (unread.isEmpty()) return
    var index by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(unread.size) {
        index = 0
        if (unread.size < 2) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { while (true) { delay(3_500); index = (index + 1) % unread.size } }
    }
    val item = unread[index.coerceIn(0, unread.lastIndex)]
    Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).clickable(onClick = onOpen).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        AnimatedContent(item, Modifier.weight(1f), transitionSpec = {
            (slideInVertically(tween(350)) { it } + fadeIn(tween(350))) togetherWith (slideOutVertically(tween(350)) { -it } + fadeOut(tween(250)))
        }, label = "unread") { n ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                PackageIcon(n.packageName, 20.dp)
                Text(listOf(n.title, n.text).filter { it.isNotBlank() }.joinToString(": "), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp))
            }
        }
        Text(if (unread.size > 1) "${index + 1}/${unread.size}" else "new", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
    }
}
