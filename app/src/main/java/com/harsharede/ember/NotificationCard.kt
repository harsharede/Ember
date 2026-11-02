package com.harsharede.ember

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NotificationsPaused
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Snooze choices, in minutes. */
val SNOOZE_MINUTES = listOf(3, 5, 15, 30, 60)

fun snoozeLabel(minutes: Int) = if (minutes >= 60) "${minutes / 60} hour" else "$minutes minutes"

/**
 * One notification with its actions: reply right here (when the app allows it), snooze, open, dismiss.
 * [onDone] runs after an action that should close whatever is showing the card (the pill's pop-up).
 */
@Composable
fun NotificationCard(item: NotifItem, now: Long, modifier: Modifier = Modifier, maxLines: Int = 4, onDone: () -> Unit = {}) {
    val context = LocalContext.current
    var replying by rememberSaveable(item.key) { mutableStateOf(false) }
    var snoozeOpen by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp))
        .clickable { if (NotificationStore.open(context, item)) onDone() }
        .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PackageIcon(item.packageName, 22.dp)
            Spacer(Modifier.width(8.dp))
            Text(item.appLabel, color = EmberColors.Secondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            Text(relativeTime(item.postTime, now), color = EmberColors.Secondary, fontSize = 12.sp)
            if (item.clearable) IconButton({ NotificationStore.dismiss(item.key); onDone() }, Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Close, "Dismiss", tint = EmberColors.Secondary, modifier = Modifier.size(18.dp))
            } else Spacer(Modifier.width(10.dp))
        }
        if (item.title.isNotBlank()) Text(item.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, end = 8.dp))
        if (item.text.isNotBlank()) Text(item.text, color = Color.White.copy(alpha = .86f), fontSize = 14.sp,
            maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp, end = 8.dp))
        if (replying && item.reply != null) {
            ReplyField(item, onSent = { replying = false; onDone() })
        } else Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (item.reply != null) ActionChip(Icons.AutoMirrored.Rounded.Reply, "Reply") { replying = true }
            Box {
                ActionChip(Icons.Rounded.NotificationsPaused, "Remind me") { snoozeOpen = true }
                DropdownMenu(snoozeOpen, { snoozeOpen = false }) {
                    SNOOZE_MINUTES.forEach { minutes ->
                        DropdownMenuItem(text = { Text("In ${snoozeLabel(minutes)}") }, onClick = {
                            snoozeOpen = false
                            NotificationStore.snooze(item.key, minutes)
                            Toast.makeText(context, "I'll remind you in ${snoozeLabel(minutes)}", Toast.LENGTH_SHORT).show()
                            onDone()
                        })
                    }
                }
            }
            if (item.contentIntent != null) ActionChip(Icons.AutoMirrored.Rounded.OpenInNew, "Open") { if (NotificationStore.open(context, item)) onDone() }
        }
    }
}

@Composable
private fun ReplyField(item: NotifItem, onSent: () -> Unit) {
    val context = LocalContext.current
    var text by rememberSaveable(item.key + "reply") { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    fun send() {
        if (text.isBlank()) return
        if (NotificationStore.reply(context, item, text.trim())) {
            Toast.makeText(context, "Sent", Toast.LENGTH_SHORT).show(); text = ""; onSent()
        } else Toast.makeText(context, "${item.appLabel} didn't accept the reply", Toast.LENGTH_SHORT).show()
    }
    Row(Modifier.padding(top = 8.dp, end = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(text, { text = it }, Modifier.weight(1f).focusRequester(focus),
            placeholder = { Text(item.reply?.label ?: "Reply") }, maxLines = 4, shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { send() }),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmberColors.Ember, unfocusedBorderColor = Color.White.copy(alpha = .2f)))
        IconButton(::send, Modifier.padding(start = 4.dp).clip(CircleShape).background(EmberColors.Ember)) {
            Icon(Icons.AutoMirrored.Rounded.Send, "Send", tint = Color.Black)
        }
    }
}

@Composable
private fun ActionChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = EmberColors.EmberSoft, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = EmberColors.EmberSoft, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
