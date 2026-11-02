@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.harsharede.ember

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.Normalizer

/** The heading an app is filed under: its first letter without accents, or # for digits and symbols. */
fun drawerLetter(label: String): String {
    val first = Normalizer.normalize(label.trim().take(1), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").uppercase()
    return if (first.isNotEmpty() && first[0].isLetter()) first else "#"
}

/** Search matches the start of any word in the name ("tea" finds Microsoft Teams), ignoring case and accents. */
fun drawerMatches(label: String, query: String): Boolean {
    if (query.isBlank()) return true
    fun plain(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()
    val q = plain(query.trim())
    val name = plain(label)
    return name.startsWith(q) || name.split(' ', '-', '.', '_').any { it.startsWith(q) } || (q.length >= 3 && q in name)
}

/** The app drawer: every app A to Z, with a search field and a letter strip to jump through the list. */
@Composable
fun AppDrawer(apps: List<AppEntry>, onLaunch: (AppEntry) -> Unit, onAddToDock: (AppEntry) -> Unit, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val visible = remember(apps, query) { apps.filter { drawerMatches(it.label, query) } }
    val grouped = remember(visible) { visible.groupBy { drawerLetter(it.label) }.toSortedMap(compareBy<String> { it == "#" }.thenBy { it }) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Where each letter's heading sits in the list, for the letter strip.
    val headingIndex = remember(grouped) {
        var i = 0
        grouped.mapValues { (_, list) -> i.also { i += list.size + 1 } }
    }
    Column(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .86f)).windowInsetsPadding(WindowInsets.safeDrawing)) {
        TextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).clip(RoundedCornerShape(18.dp)),
            placeholder = { Text("Search apps") }, singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { visible.firstOrNull()?.let(onLaunch) }),
            colors = TextFieldDefaults.colors(focusedContainerColor = EmberColors.Glass, unfocusedContainerColor = EmberColors.Glass,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
        Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.ime)) {
            LazyColumn(Modifier.weight(1f).fillMaxHeight(), state = listState) {
                grouped.forEach { (letter, list) ->
                    stickyHeader(key = "h-$letter") {
                        Text(letter, color = EmberColors.Ember, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                            modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .7f)).padding(start = 22.dp, top = 10.dp, bottom = 4.dp))
                    }
                    items(list, key = { it.key }) { app -> DrawerRow(app, onLaunch, onAddToDock) }
                }
                if (visible.isEmpty()) item { Text("No apps match \"$query\"", color = EmberColors.Secondary, modifier = Modifier.padding(24.dp)) }
            }
            LetterStrip(headingIndex.keys.toList()) { letter ->
                headingIndex[letter]?.let { scope.launch { listState.scrollToItem(it) } }
            }
        }
    }
}

@Composable
private fun DrawerRow(app: AppEntry, onLaunch: (AppEntry) -> Unit, onAddToDock: (AppEntry) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val repo = EmberApp.instance.apps
    Box {
        Row(Modifier.fillMaxWidth().combinedClickable(onLongClick = { menu = true }) { onLaunch(app) }
            .padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app, 44.dp)
            Spacer(Modifier.width(14.dp))
            Text(app.label, color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
            if (app.work) Text("Work", color = EmberColors.Secondary, fontSize = 12.sp)
        }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem({ Text("Add to dock") }, { menu = false; onAddToDock(app) })
            DropdownMenuItem({ Text("App info") }, { menu = false; repo.openAppInfo(app) })
            DropdownMenuItem({ Text("Uninstall") }, { menu = false; repo.uninstall(app) })
        }
    }
}

/** A–Z down the edge: tap or slide a finger along it to jump. */
@Composable
private fun LetterStrip(letters: List<String>, onLetter: (String) -> Unit) {
    if (letters.size < 2) return
    var height by remember { mutableStateOf(1) }
    var last by remember { mutableStateOf("") }
    fun pick(y: Float) {
        val i = (y / height * letters.size).toInt().coerceIn(0, letters.lastIndex)
        if (letters[i] != last) { last = letters[i]; onLetter(letters[i]) }
    }
    Column(Modifier.width(28.dp).fillMaxHeight().padding(vertical = 8.dp).onSizeChanged { height = it.height.coerceAtLeast(1) }
        .pointerInput(letters) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    event.changes.firstOrNull()?.let { if (it.pressed) { pick(it.position.y); it.consume() } else last = "" }
                }
            }
        }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly) {
        letters.forEach { Text(it, color = EmberColors.EmberSoft, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
    }
}

