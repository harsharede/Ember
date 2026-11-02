package com.harsharede.ember

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Starred contacts and upcoming events, read when Home comes back (only with permission). */
object CardData {
    data class Event(val id: Long, val title: String, val begin: Long, val end: Long, val allDay: Boolean, val color: Int, val location: String)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _starred = MutableStateFlow<List<PersonEntry>>(emptyList())
    val starred: StateFlow<List<PersonEntry>> = _starred.asStateFlow()
    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events.asStateFlow()

    fun refresh(context: Context) {
        val items = EmberApp.instance.prefs.stackItems.value
        val app = context.applicationContext
        scope.launch {
            if ("card:people" in items && granted(app, Manifest.permission.READ_CONTACTS)) runCatching { _starred.value = starredContacts(app) }
            if ("card:calendar" in items && granted(app, Manifest.permission.READ_CALENDAR)) runCatching { _events.value = upcoming(app) }
        }
        WeatherStore.refresh(context)
        if ("card:screentime" in items) ScreenTime.refresh(context)
    }

    fun granted(context: Context, permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun starredContacts(context: Context): List<PersonEntry> {
        val out = ArrayList<PersonEntry>()
        context.contentResolver.query(ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_THUMBNAIL_URI),
            "${ContactsContract.Contacts.STARRED}=1", null, "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC")?.use { c ->
            while (c.moveToNext() && out.size < 8) {
                val uri = ContactsContract.Contacts.getLookupUri(c.getLong(0), c.getString(1))
                val photo = c.getString(3)?.let { p ->
                    runCatching { context.contentResolver.openInputStream(Uri.parse(p))?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
                }
                out += PersonEntry(c.getString(2).orEmpty(), photo, null, null, uri, 0)
            }
        }
        return out
    }

    private fun upcoming(context: Context): List<Event> {
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now - 6 * 60 * 60_000L); ContentUris.appendId(it, now + 2 * 24 * 60 * 60_000L)
        }.build()
        val out = ArrayList<Event>()
        context.contentResolver.query(uri, arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR, CalendarContract.Instances.EVENT_LOCATION),
            "${CalendarContract.Instances.VISIBLE}=1", null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                val end = c.getLong(3)
                if (end < now) continue
                out += Event(c.getLong(0), c.getString(1).orEmpty().ifBlank { "Event" }, c.getLong(2), end, c.getInt(4) == 1, c.getInt(5), c.getString(6).orEmpty())
                if (out.size >= 4) break
            }
        }
        return out
    }
}

private fun openApp(context: Context, packageName: String) {
    runCatching { context.startActivity(context.packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** The card frame every Ember card shares, so they sit in the stack like widgets do. */
@Composable
private fun CardFrame(title: String, icon: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().glass().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.invoke()
            Text(title, style = MaterialTheme.typography.labelLarge, color = EmberColors.Secondary,
                modifier = Modifier.padding(start = if (icon != null) 8.dp else 0.dp))
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

fun weatherIcon(code: Int, day: Boolean): ImageVector = when (code) {
    0, 1 -> if (day) Icons.Rounded.WbSunny else Icons.Rounded.Bedtime
    2 -> Icons.Rounded.WbCloudy
    3, 45, 48 -> Icons.Rounded.Cloud
    51, 53, 55, 56, 57 -> Icons.Rounded.Grain
    61, 63, 65, 66, 67, 80, 81, 82 -> Icons.Rounded.Umbrella
    71, 73, 75, 77, 85, 86 -> Icons.Rounded.AcUnit
    95, 96, 99 -> Icons.Rounded.FlashOn
    else -> Icons.Rounded.Cloud
}

@Composable
fun WeatherCard() {
    val now by WeatherStore.now.collectAsStateWithLifecycle()
    val problem by WeatherStore.problem.collectAsStateWithLifecycle()
    val w = now
    CardFrame(w?.place ?: "Weather") {
        if (w == null) { Text(problem ?: "Getting the forecast…", style = MaterialTheme.typography.bodyMedium); return@CardFrame }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(weatherIcon(w.code, w.isDay), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
            Text("${w.temp}°", fontSize = 44.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
            Column(Modifier.padding(start = 12.dp)) {
                Text(weatherLabel(w.code), style = MaterialTheme.typography.titleSmall)
                Text("H ${w.high}°  L ${w.low}°", color = EmberColors.Secondary, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            w.hours.forEach { (time, temp, code) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(time, color = EmberColors.Secondary, fontSize = 11.sp)
                    Icon(weatherIcon(code, true), null, Modifier.size(18.dp).padding(vertical = 2.dp))
                    Text("$temp°", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun PeopleCard() {
    val context = LocalContext.current
    val posts by Feeds.posts.collectAsStateWithLifecycle()
    val starred by CardData.starred.collectAsStateWithLifecycle()
    val people = remember2(posts, starred) { (recentPeople(posts, 8) + starred).distinctBy { it.name.lowercase() }.take(8) }
    CardFrame("People") {
        if (people.isEmpty()) { Text("People you chat with will show here.", style = MaterialTheme.typography.bodyMedium); return@CardFrame }
        people.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                row.forEach { p ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable {
                        when {
                            p.intent != null -> runCatching { p.intent.send() }.onFailure { p.packageName?.let { openApp(context, it) } }
                            p.contactUri != null -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, p.contactUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            p.packageName != null -> openApp(context, p.packageName)
                        }
                    }.padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box {
                            Avatar(p.avatar, p.name, 46)
                            p.packageName?.let { PackageIcon(it, 16.dp, Modifier.align(Alignment.BottomEnd).clip(CircleShape)) }
                        }
                        Text(p.name.substringBefore(' '), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Avatar(image: ImageBitmap?, name: String, sizeDp: Int) {
    if (image != null) Image(image, null, contentScale = ContentScale.Crop, modifier = Modifier.size(sizeDp.dp).clip(CircleShape))
    else Box(Modifier.size(sizeDp.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = (sizeDp / 2.4).sp)
    }
}

@Composable
fun NowPlayingCard(info: NowPlaying.Info) {
    val context = LocalContext.current
    Row(Modifier.fillMaxSize().glass().clickable { openApp(context, info.packageName) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.fillMaxHeight().width(120.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center) {
            if (info.art != null) Image(info.art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else PackageIcon(info.packageName, 48.dp)
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PackageIcon(info.packageName, 16.dp)
                Text(if (info.playing) "  Now playing" else "  Paused", style = MaterialTheme.typography.labelMedium, color = EmberColors.Secondary)
            }
            Text(info.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            Text(info.artist, color = EmberColors.Secondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val controls = info.controller.transportControls
            Row(Modifier.padding(top = 4.dp)) {
                IconButton({ controls.skipToPrevious() }) { Icon(Icons.Rounded.SkipPrevious, "Previous") }
                IconButton({ if (info.playing) controls.pause() else controls.play() }) {
                    Icon(if (info.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (info.playing) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                }
                IconButton({ controls.skipToNext() }) { Icon(Icons.Rounded.SkipNext, "Next") }
            }
        }
    }
}

@Composable
fun CalendarCard() {
    val context = LocalContext.current
    val events by CardData.events.collectAsStateWithLifecycle()
    CardFrame("Up next") {
        if (events.isEmpty()) { Text("Nothing in the next two days.", style = MaterialTheme.typography.bodyMedium); return@CardFrame }
        events.take(3).forEach { e ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(4.dp).height(34.dp).clip(RoundedCornerShape(2.dp)).background(if (e.color != 0) Color(e.color) else MaterialTheme.colorScheme.primary))
                Column(Modifier.padding(start = 10.dp)) {
                    Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(eventTime(e), color = EmberColors.Secondary, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

private fun eventTime(e: CardData.Event): String {
    val day = SimpleDateFormat("EEE", Locale.getDefault())
    val time = SimpleDateFormat("HH:mm", Locale.getDefault())
    val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).let { it.format(Date()) == it.format(Date(e.begin)) }
    val prefix = if (today) "Today" else day.format(Date(e.begin))
    val whenText = if (e.allDay) "$prefix · all day" else "$prefix · ${time.format(Date(e.begin))}–${time.format(Date(e.end))}"
    return if (e.location.isNotBlank()) "$whenText · ${e.location}" else whenText
}

/** An app's recent posts: the newest large, with its picture (a video thumbnail, a photo), and the next two below. */
@Composable
fun FeedCard(packageName: String, posts: List<FeedPost>) {
    val context = LocalContext.current
    val label = EmberApp.instance.apps.labelForPackage(packageName)
    val open: (FeedPost) -> Unit = { p -> runCatching { p.intent!!.send() }.onFailure { openApp(context, packageName) } }
    val top = posts.first()
    Box(Modifier.fillMaxSize().glass().clickable { open(top) }) {
        top.picture?.let { pic ->
            Image(pic, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = .15f), .5f to Color.Black.copy(alpha = .35f), 1f to Color.Black.copy(alpha = .85f))))
        }
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PackageIcon(packageName, 20.dp)
                Text("  $label", style = MaterialTheme.typography.labelLarge, color = if (top.picture != null) Color.White else EmberColors.Secondary)
                Spacer(Modifier.weight(1f))
                Text(relativeTime(top.time, System.currentTimeMillis()), fontSize = 12.sp, color = if (top.picture != null) Color.White.copy(alpha = .8f) else EmberColors.Secondary)
            }
            Spacer(Modifier.weight(1f))
            Text(top.title.ifBlank { top.text }, style = MaterialTheme.typography.titleMedium, color = if (top.picture != null) Color.White else MaterialTheme.colorScheme.onSurface,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (top.title.isNotBlank() && top.text.isNotBlank()) Text(top.text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = if (top.picture != null) Color.White.copy(alpha = .85f) else EmberColors.Secondary)
            if (top.picture == null) posts.drop(1).take(2).forEach { p ->
                Text("• " + p.title.ifBlank { p.text }, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp).clickable { open(p) })
            }
        }
    }
}

@Composable
private fun <T> remember2(a: Any?, b: Any?, calc: () -> T): T = androidx.compose.runtime.remember(a, b, calc)
