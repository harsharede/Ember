package com.harsharede.ember

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Notifications kept as things to do. A swiped-away notification is gone for good; one added here stays, with its app,
 * title and text, until it's ticked off. While Ember is running it also remembers how to open the exact message.
 */
object Todos {
    /** The notification's own "open" action, kept in memory only (Android doesn't let it be saved). */
    private val intents = HashMap<Long, PendingIntent>()

    fun add(item: NotifItem) {
        val todo = EmberApp.instance.prefs.addTodo(item.packageName, item.title, item.text, item.postTime)
        item.contentIntent?.let { intents[todo.id] = it }
    }

    /** Open what the to-do is about: the message itself if Ember still knows how, otherwise its app. */
    fun open(context: Context, todo: Prefs.Todo) {
        // Android 14+ silently drops an app start from a PendingIntent unless the sender (Ember, in front) allows it.
        val sent = intents[todo.id]?.let { intent ->
            runCatching { intent.send(context, 0, null, null, null, null, NotificationStore.sendOptions()); true }.getOrDefault(false)
        } ?: false
        if (sent) return
        // Otherwise the app itself: through Ember's own list first (that covers Work profile apps), then Android's.
        val apps = EmberApp.instance.apps
        val entry = apps.apps.value.firstOrNull { it.packageName == todo.packageName }
        val opened = if (entry != null) runCatching { apps.launch(entry); true }.getOrDefault(false)
        else context.packageManager.getLaunchIntentForPackage(todo.packageName)?.let { launch ->
            runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
        } ?: false
        if (!opened) android.widget.Toast.makeText(context, "${apps.labelForPackage(todo.packageName)} can't be opened from here",
            android.widget.Toast.LENGTH_SHORT).show()
    }
}

/** The to-do list: open, tick off, remove. Used on the notification page and as a Smart Stack card. */
@Composable
fun TodoList(compact: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = EmberApp.instance.prefs
    val todos by prefs.todos.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp)) {
        if (todos.isEmpty()) {
            Text("Nothing to do. On any notification, tap To-do to keep it here after it's gone.",
                color = EmberColors.Secondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(8.dp))
            return@Column
        }
        val shown = if (compact) todos.filter { !it.done }.take(3) else todos
        shown.forEach { todo ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).then(if (compact) Modifier else Modifier.glass(RoundedCornerShape(18.dp)))
                .clickable { Todos.open(context, todo) }.padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Checkbox(todo.done, { prefs.setTodoDone(todo.id, it) })
                PackageIcon(todo.packageName, if (compact) 18.dp else 22.dp)
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(todo.title.ifBlank { todo.text }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall,
                        textDecoration = if (todo.done) TextDecoration.LineThrough else null)
                    if (!compact && todo.title.isNotBlank() && todo.text.isNotBlank())
                        Text(todo.text, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = EmberColors.Secondary)
                    if (!compact) Text("${EmberApp.instance.apps.labelForPackage(todo.packageName)} · ${relativeTime(todo.time, now)}",
                        fontSize = 12.sp, color = EmberColors.Secondary)
                }
                if (!compact) IconButton({ prefs.removeTodo(todo.id) }) { Icon(Icons.Rounded.Close, "Remove", tint = EmberColors.Secondary) }
            }
        }
        if (compact && todos.count { !it.done } > 3) Text("+${todos.count { !it.done } - 3} more", fontSize = 12.sp, color = EmberColors.Secondary,
            modifier = Modifier.padding(start = 12.dp))
        if (!compact && todos.any { it.done }) TextButton({ prefs.clearDoneTodos() }) { Text("Clear done") }
    }
}

/** The To-do card for the Smart Stack. */
@Composable
fun TodoCard() {
    Column(Modifier.fillMaxSize().glass().padding(14.dp)) {
        val open = EmberApp.instance.prefs.todos.collectAsStateWithLifecycle().value.count { !it.done }
        Text(if (open == 0) "To-do" else "To-do · $open", style = MaterialTheme.typography.labelLarge, color = EmberColors.Secondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        TodoList(compact = true)
    }
}
