@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.harsharede.ember

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Every notification on a timeline, newest first, under Now / Earlier today / Yesterday / Older. Silent ones and ones
 * that can't be dismissed get their own folded sections at the end. Push up past the end (or drag the header) to close.
 */
@Composable
fun NotificationsScreen(items: List<NotifItem>, connected: Boolean, now: Long, filter: String?, layer: Layer, onFilter: (String?) -> Unit) {
    val context = LocalContext.current
    val showPersistent by EmberApp.instance.prefs.showPersistent.collectAsStateWithLifecycle()
    var silentOpen by remember { mutableStateOf(false) }
    var persistentOpen by remember { mutableStateOf(false) }
    var showTodos by remember { mutableStateOf(false) }
    val shown = if (filter == null) items else items.filter { it.packageName == filter }
    val main = shown.filter { !it.silent && !it.persistent }
    val silent = shown.filter { it.silent && !it.persistent }
    val persistent = if (showPersistent || filter != null) shown.filter { it.persistent } else emptyList()

    Column(Modifier.fillMaxSize().layerBackdrop()
        .windowInsetsPadding(WindowInsets.safeDrawing).dragToMove(layer)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Notifications", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (shown.any { it.clearable }) TextButton({ shown.filter { it.clearable }.forEach { NotificationStore.dismiss(it.key) } }) { Text("Clear all") }
        }
        // Notifications or the to-do list kept from them.
        val todos by EmberApp.instance.prefs.todos.collectAsStateWithLifecycle()
        val openTodos = todos.count { !it.done }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!showTodos, { showTodos = false }, label = { Text("Notifications") })
            FilterChip(showTodos, { showTodos = true }, label = { Text(if (openTodos > 0) "To-do · $openTodos" else "To-do") })
            if (filter != null && !showTodos) FilterChip(true, { onFilter(null) }, label = { Text("${EmberApp.instance.apps.labelForPackage(filter)}  ✕") },
                leadingIcon = { PackageIcon(filter, 18.dp) })
        }
        // Whatever's playing stays pinned at the top, with its controls, until it stops.
        val playing by NowPlaying.info.collectAsStateWithLifecycle()
        playing?.let { info ->
            Box(Modifier.fillMaxWidth().height(88.dp).padding(horizontal = 12.dp, vertical = 6.dp)) { NowPlayingCard(info) }
        }
        if (showTodos) {
            LazyColumn(Modifier.fillMaxSize().nestedScroll(layer.closeConnection), contentPadding = PaddingValues(12.dp)) {
                item(key = "todos") { TodoList(compact = false) }
                item(key = "end") { Box(Modifier.height(120.dp)) }
            }
            return@Column
        }
        when {
            !connected -> Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
                Text("Ember needs notification access", style = MaterialTheme.typography.titleLarge)
                Text("To list your notifications, reply, set reminders and light the pill. Nothing leaves your phone.",
                    color = EmberColors.Secondary, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
                Button({ context.startActivity(Intent(context, SettingsActivity::class.java)) }) { Text("Set up") }
            }
            shown.isEmpty() -> Box(Modifier.fillMaxSize().padding(28.dp)) {
                Text("You're all caught up", color = EmberColors.Secondary, style = MaterialTheme.typography.titleMedium)
            }
            else -> LazyColumn(Modifier.fillMaxSize().nestedScroll(layer.closeConnection), contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                main.groupBy { bucketOf(it.postTime, now) }.toSortedMap().forEach { (bucket, list) ->
                    stickyHeader(key = "b-${bucket.name}") { SectionHeader(bucket.title) }
                    items(list, key = { it.key }) { item -> DismissibleCard(item, now) }
                }
                if (silent.isNotEmpty()) {
                    item(key = "silent") { FoldHeader("Silent", silent.size, silentOpen) { silentOpen = !silentOpen } }
                    if (silentOpen) items(silent, key = { it.key }) { item -> DismissibleCard(item, now) }
                }
                if (persistent.isNotEmpty()) {
                    item(key = "persistent") { FoldHeader("Can't be dismissed", persistent.size, persistentOpen) { persistentOpen = !persistentOpen } }
                    if (persistentOpen) items(persistent, key = { it.key }) { item -> NotificationCard(item, now) }
                }
                // Room at the end, so a push up from the last card closes the page rather than hitting the edge.
                item(key = "end") { Box(Modifier.height(120.dp)) }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.fillMaxWidth().background(HeaderScrim).padding(start = 8.dp, top = 10.dp, bottom = 4.dp))
}

@Composable
private fun FoldHeader(title: String, count: Int, open: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("$title  ·  $count", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun DismissibleCard(item: NotifItem, now: Long) {
    if (!item.clearable) { NotificationCard(item, now); return }
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
        if (value != SwipeToDismissBoxValue.Settled) NotificationStore.dismiss(item.key)
        value != SwipeToDismissBoxValue.Settled
    })
    SwipeToDismissBox(state, backgroundContent = {}) { NotificationCard(item, now) }
}

