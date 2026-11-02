@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.harsharede.ember

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Every notification on a timeline, newest first, under Now / Earlier today / Yesterday / Older. Each card can be replied
 * to, snoozed (3, 5, 15, 30 minutes or an hour) or opened; swipe one away to dismiss it.
 */
@Composable
fun NotificationsScreen(items: List<NotifItem>, connected: Boolean, now: Long, filter: String?, onFilter: (String?) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val shown = if (filter == null) items else items.filter { it.packageName == filter }
    Column(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .88f)).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Notifications", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (shown.any { it.clearable }) TextButton({ shown.filter { it.clearable }.forEach { NotificationStore.dismiss(it.key) } }) {
                Text("Clear all", color = EmberColors.EmberSoft)
            }
        }
        if (filter != null) Row(Modifier.padding(horizontal = 16.dp)) {
            FilterChip(true, { onFilter(null) }, label = { Text("${EmberApp.instance.apps.labelForPackage(filter)}  ✕") },
                leadingIcon = { PackageIcon(filter, 18.dp) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = EmberColors.Glass, selectedLabelColor = Color.White))
        }
        when {
            !connected -> Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
                Text("Ember needs notification access", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("To list your notifications, let you reply and set reminders, and light the pill when something arrives. " +
                    "Nothing leaves your phone.", color = EmberColors.Secondary, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
                Button({ context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                    colors = ButtonDefaults.buttonColors(containerColor = EmberColors.Ember, contentColor = Color.Black)) { Text("Allow access") }
            }
            shown.isEmpty() -> Box(Modifier.fillMaxSize().padding(28.dp)) {
                Text("You're all caught up", color = EmberColors.Secondary, fontSize = 17.sp)
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                shown.groupBy { bucketOf(it.postTime, now) }.toSortedMap().forEach { (bucket, list) ->
                    stickyHeader(key = "b-${bucket.name}") {
                        Text(bucket.title, color = EmberColors.Ember, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .6f)).padding(start = 8.dp, top = 8.dp, bottom = 4.dp))
                    }
                    items(list, key = { it.key }) { item -> DismissibleCard(item, now) }
                }
            }
        }
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
