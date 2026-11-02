package com.harsharede.ember

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

private data class CardChoice(val item: String, val title: String, val detail: String, val icon: ImageVector)

private val CARDS = listOf(
    CardChoice("card:weather", "Weather", "Now and the next hours. Uses the internet (Open-Meteo) and your location or a city you choose.", Icons.Rounded.WbSunny),
    CardChoice("card:people", "People", "Recent chats from your notifications; with Contacts allowed, your starred contacts too.", Icons.Rounded.People),
    CardChoice("card:media", "Now playing", "Whatever's playing (YouTube, Spotify…), with controls. Shows only while something plays.", Icons.Rounded.MusicNote),
    CardChoice("card:calendar", "Up next", "Your next events. Needs Calendar.", Icons.Rounded.CalendarMonth),
    CardChoice("card:suggestions", "App suggestions", "The apps you open most.", Icons.Rounded.Lightbulb),
    CardChoice("card:screentime", "Screen time", "Today's time per app; set limits in Settings › Screen time. Needs usage access.", Icons.Rounded.HourglassBottom),
)

/**
 * Choose what's in the Smart Stack: Ember's cards, app feeds (from apps' notifications) and widgets. Switch each on or
 * off, and move the ones that are on up or down. A card that needs a permission asks for it when it's switched on.
 */
@Composable
fun StackEditor(onAddWidget: () -> Unit, onAutoPick: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val prefs = EmberApp.instance.prefs
    val items by prefs.stackItems.collectAsStateWithLifecycle()
    val feeds by Feeds.posts.collectAsStateWithLifecycle()
    val apps by EmberApp.instance.apps.apps.collectAsStateWithLifecycle()
    var askWeather by remember { mutableStateOf(false) }

    val contacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // People works without it (recent chats); with it, starred contacts join in.
        prefs.addStackItem("card:people"); CardData.refresh(context)
    }
    val calendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { prefs.addStackItem("card:calendar"); CardData.refresh(context) }
        else Toast.makeText(context, "Up next needs Calendar", Toast.LENGTH_SHORT).show()
    }
    val location = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { prefs.setWeatherPlace(null); prefs.addStackItem("card:weather"); WeatherStore.refresh(context, force = true) }
        else Toast.makeText(context, "Choose a city instead", Toast.LENGTH_SHORT).show()
    }

    fun turnOn(item: String) {
        when (item) {
            "card:weather" -> askWeather = true
            "card:people" -> if (CardData.granted(context, Manifest.permission.READ_CONTACTS)) prefs.addStackItem(item) else contacts.launch(Manifest.permission.READ_CONTACTS)
            "card:calendar" -> if (CardData.granted(context, Manifest.permission.READ_CALENDAR)) { prefs.addStackItem(item); CardData.refresh(context) }
                else calendar.launch(Manifest.permission.READ_CALENDAR)
            "card:screentime" -> {
                prefs.addStackItem(item)
                if (ScreenTime.hasAccess(context)) ScreenTime.refresh(context) else runCatching { context.startActivity(ScreenTime.accessIntent()) }
            }
            else -> prefs.addStackItem(item)
        }
    }
    fun turnOff(item: String) {
        prefs.removeStackItem(item)
        // A widget leaves for good: give its id back to Android.
        item.removePrefix("widget:").takeIf { item.startsWith("widget:") }?.toIntOrNull()?.let { Widgets.remove(it) }
    }

    // Apps that can have a feed: known feed apps that are installed, and any app that has posted something.
    val installed = remember(apps) { apps.map { it.packageName }.toSet() }
    val feedApps = remember(installed, feeds) {
        (Feeds.KNOWN_FEEDS.filter { it in installed } + feeds.filter { (_, posts) -> posts.any { !it.conversation } }.keys)
            .distinct().sortedBy { EmberApp.instance.apps.labelForPackage(it).lowercase() }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = .98f)).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Smart Stack", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TextButton(onClose) { Text("Done") }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { Section("In the stack, in order") }
            if (items.isEmpty()) item { Text("Nothing yet: switch something on below.", color = EmberColors.Secondary, modifier = Modifier.padding(8.dp)) }
            items(items, key = { "in-$it" }) { item ->
                Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    ItemIcon(item)
                    Text(stackItemTitle(item), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 12.dp), maxLines = 2)
                    IconButton({ prefs.moveStackItem(item, -1) }) { Icon(Icons.Rounded.KeyboardArrowUp, "Move up") }
                    IconButton({ prefs.moveStackItem(item, 1) }) { Icon(Icons.Rounded.KeyboardArrowDown, "Move down") }
                    IconButton({ turnOff(item) }) { Icon(Icons.Rounded.Close, "Take out of the stack") }
                }
            }

            item { Section("Ember cards") }
            items(CARDS, key = { it.item }) { card ->
                Toggle(card.title, card.detail, card.icon, card.item in items) { on -> if (on) turnOn(card.item) else turnOff(card.item) }
            }
            item {
                if ("card:weather" in items) {
                    val place by prefs.weatherPlace.collectAsStateWithLifecycle()
                    TextButton({ askWeather = true }) { Text("Weather for: ${place?.first ?: "my location"} (change)") }
                }
            }

            item { Section("App feeds") }
            item { Text("Posts from apps' notifications, such as new YouTube videos or Reddit replies, kept even after you clear them. " +
                "A feed appears in the stack once the app has posted something.", color = EmberColors.Secondary, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
            items(feedApps, key = { "feed-$it" }) { pkg ->
                val item = "feed:$pkg"
                val count = feeds[pkg]?.count { !it.conversation } ?: 0
                Toggle(EmberApp.instance.apps.labelForPackage(pkg), if (count > 0) "$count recent posts" else "No posts yet",
                    null, item in items, leading = { PackageIcon(pkg, 32.dp) }) { on -> if (on) turnOn(item) else turnOff(item) }
            }

            item { Section("Widgets") }
            item {
                Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onAddWidget) { Icon(Icons.Rounded.Widgets, null, Modifier.size(18.dp)); Text("  Add a widget") }
                    FilledTonalButton(onAutoPick) { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp)); Text("  Pick for me") }
                }
            }
        }
    }

    if (askWeather) WeatherPlaceDialog(
        onLocation = {
            askWeather = false
            if (CardData.granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)) {
                prefs.setWeatherPlace(null); prefs.addStackItem("card:weather"); WeatherStore.refresh(context, force = true)
            } else location.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        },
        onCity = { place -> askWeather = false; prefs.setWeatherPlace(place); prefs.addStackItem("card:weather"); WeatherStore.refresh(context, force = true) },
        onDismiss = { askWeather = false },
    )
}

@Composable
private fun Section(title: String) =
    Text(title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 4.dp))

@Composable
private fun ItemIcon(item: String) {
    val card = CARDS.firstOrNull { it.item == item }
    when {
        card != null -> Icon(card.icon, null, tint = MaterialTheme.colorScheme.primary)
        item.startsWith("feed:") -> PackageIcon(item.removePrefix("feed:"), 28.dp)
        else -> Icon(Icons.Rounded.Widgets, null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Toggle(title: String, detail: String, icon: ImageVector?, on: Boolean, leading: (@Composable () -> Unit)? = null, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(detail) },
        leadingContent = if (leading != null) leading else if (icon != null) ({ Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }) else null,
        trailingContent = { Switch(on, onChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable { onChange(!on) },
    )
}

/** Weather for where you are (asks for location) or for a city you search (no location needed). */
@Composable
fun WeatherPlaceDialog(onLocation: () -> Unit, onCity: (Triple<String, Double, Double>) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<Triple<String, Double, Double>>()) }
    LaunchedEffect(query) { delay(350); results = WeatherStore.searchCity(query) }
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onLocation) { Text("Use my location") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
        title = { Text("Weather for") },
        text = {
            Column {
                Text("Your approximate location, or a city (no location permission needed).", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(top = 12.dp), placeholder = { Text("Search a city") }, singleLine = true)
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    items(results) { place ->
                        Text(place.first, fontWeight = FontWeight.Medium, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable { onCity(place) }.padding(vertical = 10.dp, horizontal = 4.dp))
                    }
                }
            }
        })
}
