@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.harsharede.ember

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

/**
 * The app drawer, two pages side by side: every app A to Z, and the same apps in groups. Typing in the search field
 * always shows the A to Z results. [open] is true once the drawer has finished opening (that's when the keyboard comes up).
 */
@Composable
fun AppDrawer(apps: List<AppEntry>, open: Boolean, layer: Layer?, onLaunch: (AppEntry) -> Unit, onAddToDock: (AppEntry) -> Unit,
              title: String? = null, onClose: () -> Unit, lettersLeft: Boolean = false) {
    val prefs = EmberApp.instance.prefs
    val context = androidx.compose.ui.platform.LocalContext.current
    val autoKeyboard by prefs.drawerKeyboard.collectAsStateWithLifecycle()
    val savedTab by prefs.drawerTab.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var showHidden by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = if (title != null) 0 else savedTab) { if (title != null) 1 else 2 }
    LaunchedEffect(pager.settledPage) { if (title == null) prefs.setDrawerTab(pager.settledPage) }
    LaunchedEffect(open) {
        if (open && autoKeyboard && pager.currentPage == 0) { runCatching { focus.requestFocus() }; keyboard?.show() }
        if (!open) { query = ""; focusManager.clearFocus(); keyboard?.hide() }
    }
    LaunchedEffect(query) { if (query.isNotEmpty() && pager.currentPage != 0) pager.scrollToPage(0) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = .96f))
        .windowInsetsPadding(WindowInsets.safeDrawing).imePadding()
        // The search field and tabs aren't a list, so a drag there moves the drawer directly.
        .then(if (layer != null) Modifier.dragToMove(layer) else Modifier)) {
        // A grabber says the drawer pulls down to close.
        Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(36.dp).padding(vertical = 2.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .4f))
                .then(Modifier.padding(vertical = 2.dp)))
        }
        if (title != null) Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp, top = 8.dp))
        TextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).clip(RoundedCornerShape(28.dp)).focusRequester(focus),
            placeholder = { Text("Search apps") }, singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Rounded.Close, "Clear") }
                else if (title == null) IconButton({
                    (context as? android.app.Activity)?.let { unlockHidden(it) { showHidden = true } }
                }) { Icon(Icons.Rounded.Lock, "Hidden apps") }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                val first = apps.firstOrNull { drawerMatches(it.label, query) }
                if (first != null) onLaunch(first) else if (query.isNotBlank()) webSearch(context, query)
            }),
            colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
        if (title == null) SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            listOf("A to Z", "Groups").forEachIndexed { i, label ->
                SegmentedButton(pager.currentPage == i, { scope.launch { pager.animateScrollToPage(i) } },
                    SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
            }
        }
        HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            if (page == 0) AlphabeticalApps(apps, query, layer, onLaunch, onAddToDock, lettersLeft)
            else GroupedApps(apps, layer, onLaunch, onAddToDock)
        }
    }
    if (showHidden) HiddenAppsSheet(onLaunch = onLaunch, onClose = { showHidden = false })
}

@Composable
private fun AlphabeticalApps(apps: List<AppEntry>, query: String, layer: Layer?, onLaunch: (AppEntry) -> Unit, onAddToDock: (AppEntry) -> Unit,
                             lettersLeft: Boolean = false) {
    val visible = remember(apps, query) { apps.filter { drawerMatches(it.label, query) } }
    val grouped = remember(visible) { visible.groupBy { drawerLetter(it.label) }.toSortedMap(compareBy<String> { it == "#" }.thenBy { it }) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Where each letter's heading sits in the list, for the letter strip.
    val headingIndex = remember(grouped) {
        var i = 0
        grouped.mapValues { (_, list) -> i.also { i += list.size + 1 } }
    }
    LaunchedEffect(query) { listState.scrollToItem(0) }
    val strip: @Composable () -> Unit = {
        if (query.isEmpty()) LetterStrip(headingIndex.keys.toList(), onLeft = lettersLeft) { letter ->
            headingIndex[letter]?.let { scope.launch { listState.scrollToItem(it) } }
        }
    }
    Row(Modifier.fillMaxSize()) {
        // The letters sit on the same side as the rail, under the thumb that's using the phone.
        if (lettersLeft) strip()
        LazyColumn(Modifier.weight(1f).fillMaxHeight().then(if (layer != null) Modifier.nestedScroll(layer.closeConnection) else Modifier), state = listState) {
            // Typing also gives sums, conversions, people and a web search.
            if (query.isNotBlank()) item(key = "smart") { SmartResults(query) }
            grouped.forEach { (letter, list) ->
                stickyHeader(key = "h-$letter") {
                    Text(letter, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(start = 22.dp, top = 10.dp, bottom = 4.dp))
                }
                items(list, key = { it.key }) { app -> DrawerRow(app, onLaunch, onAddToDock) }
            }
            if (visible.isEmpty()) item { Text("No apps match \"$query\"", color = EmberColors.Secondary, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) }
        }
        if (!lettersLeft) strip()
    }
}

@Composable
private fun DrawerRow(app: AppEntry, onLaunch: (AppEntry) -> Unit, onAddToDock: (AppEntry) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        ListItem(
            headlineContent = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingContent = { AppIcon(app, 44.dp) },
            trailingContent = if (app.work) ({ Text("Work", color = EmberColors.Secondary, fontSize = 12.sp) }) else null,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.combinedClickable(onLongClick = { menu = true }) { onLaunch(app) },
        )
        AppMenu(app, menu, { menu = false }, onAddToDock)
    }
}

@Composable
private fun AppMenu(app: AppEntry, open: Boolean, onDismiss: () -> Unit, onAddToDock: (AppEntry) -> Unit, onMove: (() -> Unit)? = null) {
    val repo = EmberApp.instance.apps
    DropdownMenu(open, onDismiss) {
        DropdownMenuItem({ Text("Add to dock") }, { onDismiss(); onAddToDock(app) })
        if (onMove != null) DropdownMenuItem({ Text("Move to group…") }, { onDismiss(); onMove() })
        val prefs = EmberApp.instance.prefs
        val context = androidx.compose.ui.platform.LocalContext.current
        val inFocus = app.packageName in prefs.focusApps.collectAsStateWithLifecycle().value
        DropdownMenuItem({ Text(if (inFocus) "Keep out during Focus: on" else "Put away during Focus") }, { onDismiss(); prefs.setFocusApp(app.packageName, !inFocus) })
        DropdownMenuItem({ Text("Hide") }, {
            onDismiss(); prefs.hideApp(app.packageName)
            android.widget.Toast.makeText(context, "${app.label} hidden. See it under Hidden apps in the drawer.", android.widget.Toast.LENGTH_LONG).show()
        })
        DropdownMenuItem({ Text("App info") }, { onDismiss(); repo.openAppInfo(app) })
        DropdownMenuItem({ Text("Uninstall") }, { onDismiss(); repo.uninstall(app) })
    }
}

/**
 * Apps in groups, as cards; tap a card to open the whole group. Groups come from the user's own choices first (moved
 * by hand or sorted by AI), automatic otherwise. Hold a card to rename or ungroup it; hold an app to move it.
 */
@Composable
private fun GroupedApps(apps: List<AppEntry>, layer: Layer?, onLaunch: (AppEntry) -> Unit, onAddToDock: (AppEntry) -> Unit) {
    val prefs = EmberApp.instance.prefs
    val overrides by prefs.appGroups.collectAsStateWithLifecycle()
    val order by prefs.groupOrder.collectAsStateWithLifecycle()
    val groups = remember(apps, overrides, order) {
        val auto = AppGroup.entries.map { it.title }
        apps.groupBy { groupNameOf(it, overrides) }.entries
            .sortedWith(compareBy({ it.key == AppGroup.OTHER.title }, { order.indexOf(it.key).let { i -> if (i < 0) 1000 + auto.indexOf(it.key) else i } }, { it.key }))
            .associate { it.key to it.value }
    }
    var openGroup by remember { mutableStateOf<String?>(null) }
    var moving by remember { mutableStateOf<AppEntry?>(null) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var aiSheet by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize().then(if (layer != null) Modifier.nestedScroll(layer.closeConnection) else Modifier),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item(key = "ai", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.FilledTonalButton({ aiSheet = true }) {
                        Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp)); Text("  Sort with AI")
                    }
                    if (overrides.isNotEmpty()) androidx.compose.material3.TextButton({ prefs.resetAppGroups() }) { Text("Automatic") }
                }
            }
            items(groups.entries.toList(), key = { "g-" + it.key }) { (group, list) ->
                var menu by remember { mutableStateOf(false) }
                Box {
                    Column(Modifier.clip(RoundedCornerShape(28.dp)).combinedClickable(onLongClick = { menu = true }) { openGroup = group }) {
                        Column(Modifier.fillMaxWidth().aspectRatio(1f).glass().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            list.take(4).chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    pair.forEach { app ->
                                        Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(50)).clickable { onLaunch(app) }, contentAlignment = Alignment.Center) {
                                            AppIcon(app, 56.dp)
                                        }
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                        Text("$group  ·  ${list.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Rename") }, { menu = false; renaming = group })
                        if (overrides.containsValue(group)) DropdownMenuItem({ Text("Ungroup (back to automatic)") }, { menu = false; prefs.ungroup(group) })
                    }
                }
            }
        }
        val shown = openGroup
        BackHandler(shown != null) { openGroup = null }
        AnimatedVisibility(shown != null, enter = fadeIn() + scaleIn(initialScale = .92f), exit = fadeOut() + scaleOut(targetScale = .92f)) {
            val group = shown ?: return@AnimatedVisibility
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f))
                .clickable(remember { MutableInteractionSource() }, null) { openGroup = null }, contentAlignment = Alignment.Center) {
                Column(Modifier.padding(20.dp).glass(RoundedCornerShape(32.dp), alpha = .97f).clickable(remember { MutableInteractionSource() }, null) {}
                    .padding(18.dp)) {
                    Text(group, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 12.dp))
                    LazyVerticalGrid(GridCells.Fixed(4), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(groups[group].orEmpty(), key = { it.key }) { app -> GridApp(app, onLaunch, onAddToDock, onMove = { moving = app }) }
                    }
                }
            }
        }
    }
    moving?.let { app ->
        GroupNameDialog("Move ${app.label} to", groups.keys.toList(), current = groupNameOf(app, overrides),
            onPick = { name -> prefs.setAppGroup(app.packageName, name); moving = null }, onDismiss = { moving = null })
    }
    renaming?.let { old ->
        GroupNameDialog("Rename \"$old\"", emptyList(), current = old, onPick = { name ->
            // An automatic group gets its apps written down under the new name; a user group is simply renamed.
            if (overrides.containsValue(old)) prefs.renameGroup(old, name)
            else prefs.setAppGroups(groups[old].orEmpty().map { it.packageName }, name)
            if (openGroup == old) openGroup = name
            renaming = null
        }, onDismiss = { renaming = null })
    }
    if (aiSheet) AiGroupsSheet(apps, onClose = { aiSheet = false })
}

/** Choose an existing group or type a new name. */
@Composable
private fun GroupNameDialog(title: String, existing: List<String>, current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(if (existing.isEmpty()) current else "") }
    androidx.compose.material3.AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        confirmButton = { androidx.compose.material3.TextButton({ if (name.isNotBlank()) onPick(name.trim()) }, enabled = name.isNotBlank()) {
            Text(if (existing.isEmpty()) "Rename" else "New group") } },
        dismissButton = { androidx.compose.material3.TextButton(onDismiss) { Text("Cancel") } },
        text = {
            Column {
                if (existing.isNotEmpty()) LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items(existing.filter { it != current }) { g ->
                        Text(g, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable { onPick(g) }.padding(vertical = 10.dp, horizontal = 4.dp))
                    }
                }
                androidx.compose.material3.OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true,
                    placeholder = { Text(if (existing.isEmpty()) "Group name" else "Or a new group") })
            }
        })
}

@Composable
private fun GridApp(app: AppEntry, onLaunch: (AppEntry) -> Unit, onAddToDock: (AppEntry) -> Unit, onMove: (() -> Unit)? = null) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(Modifier.clip(RoundedCornerShape(16.dp)).combinedClickable(onLongClick = { menu = true }) { onLaunch(app) }.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            AppIcon(app, 52.dp)
            Text(app.label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp))
        }
        AppMenu(app, menu, { menu = false }, onAddToDock, onMove)
    }
}

/**
 * A–Z down the edge: tap or slide a finger along it to jump. While a finger is on it the letters near the finger swell
 * and lean out like a wave, a large bubble shows the letter, and each new letter gives a light tick.
 */
@Composable
private fun LetterStrip(letters: List<String>, onLeft: Boolean = false, onLetter: (String) -> Unit) {
    if (letters.size < 2) return
    var height by remember { mutableStateOf(1) }
    var last by remember { mutableStateOf("") }
    var fingerY by remember { mutableFloatStateOf(-1f) }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    fun pick(y: Float) {
        fingerY = y
        val i = (y / height * letters.size).toInt().coerceIn(0, letters.lastIndex)
        if (letters[i] != last) {
            last = letters[i]; onLetter(letters[i])
            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
        }
    }
    Box(Modifier.width(30.dp).fillMaxHeight().padding(vertical = 8.dp)) {
        Column(Modifier.fillMaxSize().onSizeChanged { height = it.height.coerceAtLeast(1) }
            .pointerInput(letters) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.firstOrNull()?.let { if (it.pressed) { pick(it.position.y); it.consume() } else { last = ""; fingerY = -1f } }
                    }
                }
            }, horizontalAlignment = Alignment.CenterHorizontally) {
            letters.forEachIndexed { i, letter ->
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(letter, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.graphicsLayer {
                            val y = fingerY
                            if (y < 0f) return@graphicsLayer
                            // Distance from the finger in letters; the wave falls off smoothly over about two letters each way.
                            val slot = height.toFloat() / letters.size
                            val d = (y - (i + .5f) * slot) / slot
                            val wave = kotlin.math.exp(-(d * d) / (2f * 1.8f * 1.8f))
                            scaleX = 1f + 1.4f * wave; scaleY = scaleX
                            translationX = (if (onLeft) 1f else -1f) * wave * 30.dp.toPx()
                        })
                }
            }
        }
        // The bubble: the current letter, large, beside the finger.
        if (fingerY >= 0f && last.isNotEmpty()) {
            val y = with(density) { fingerY.toDp() }
            Box(Modifier.offset(x = if (onLeft) 62.dp else (-96).dp, y = y - 32.dp).size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center) {
                Text(last, color = MaterialTheme.colorScheme.onPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
