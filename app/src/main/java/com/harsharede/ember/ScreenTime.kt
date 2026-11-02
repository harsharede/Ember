package com.harsharede.ember

import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Screen time: how long each app has been on screen today, from Android's own usage records (with usage access, given
 * once in Android's settings). Limits are the user's own; passing one sends a single gentle notification that day.
 */
object ScreenTime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _today = MutableStateFlow<Map<String, Long>>(emptyMap())
    /** Milliseconds on screen today, per package, most first. */
    val today: StateFlow<Map<String, Long>> = _today.asStateFlow()
    private const val WORK = "ember-screen-time"
    private const val CHANNEL = "nudges"

    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }

    fun accessIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun refresh(context: Context) {
        if (!hasAccess(context)) return
        val app = context.applicationContext
        scope.launch { runCatching { _today.value = usageToday(app) } }
    }

    fun usageToday(context: Context): Map<String, Long> {
        val now = System.currentTimeMillis()
        val start = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val events = context.getSystemService(UsageStatsManager::class.java).queryEvents(start, now)
        val list = ArrayList<Triple<Long, String, Int>>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val type = when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> 1
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> 2
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> 3
                else -> 0
            }
            if (type != 0) list += Triple(e.timeStamp, e.packageName.orEmpty(), type)
        }
        return foregroundTime(list, start, now).filterKeys { it != context.packageName }
    }

    /** Starts the hourly-ish check when there are limits, stops it when there are none. */
    fun schedule(context: Context) {
        val work = WorkManager.getInstance(context)
        if (EmberApp.instance.prefs.limits.value.isEmpty()) { work.cancelUniqueWork(WORK); return }
        work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<ScreenTimeWorker>(15, TimeUnit.MINUTES).build())
    }

    /** One quiet notification per app per day, the first time its limit is passed. */
    fun nudge(context: Context) {
        val prefs = EmberApp.instance.prefs
        val limits = prefs.limits.value
        if (limits.isEmpty() || !hasAccess(context)) return
        val usage = usageToday(context)
        _today.value = usage
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Screen time nudges", NotificationManager.IMPORTANCE_DEFAULT))
        limits.forEach { (pkg, minutes) ->
            val used = usage[pkg] ?: return@forEach
            val key = "$day|$pkg"
            if (used >= minutes * 60_000L && !prefs.nudged(key)) {
                prefs.markNudged(key)
                val label = EmberApp.instance.apps.labelForPackage(pkg)
                runCatching {
                    nm.notify(pkg.hashCode(), NotificationCompat.Builder(context, CHANNEL)
                        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                        .setContentTitle("${formatDuration(used)} on $label today")
                        .setContentText("That's past the ${formatDuration(minutes * 60_000L)} you set. Maybe a break?")
                        .setAutoCancel(true).build())
                }
            }
        }
    }
}

/**
 * Time on screen per app from a day's events (time, package, 1 resumed / 2 paused / 3 screen off). An app counts from
 * when it came to the front until something else did, it paused, or the screen went off.
 */
fun foregroundTime(events: List<Triple<Long, String, Int>>, start: Long, end: Long): Map<String, Long> {
    val out = HashMap<String, Long>()
    var current: String? = null
    var since = start
    for ((time, pkg, type) in events.sortedBy { it.first }) {
        when (type) {
            1 -> {
                current?.let { out[it] = (out[it] ?: 0L) + (time - since) }
                current = pkg; since = time
            }
            2 -> if (pkg == current) { out[pkg] = (out[pkg] ?: 0L) + (time - since); current = null }
            3 -> { current?.let { out[it] = (out[it] ?: 0L) + (time - since) }; current = null }
        }
    }
    current?.let { out[it] = (out[it] ?: 0L) + (end - since) }
    return out.filterValues { it > 0 }.entries.sortedByDescending { it.value }.associate { it.key to it.value }
}

/** "2 h 14 min", "45 min", "under a minute". */
fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes < 1 -> "under a minute"
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0L -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}

class ScreenTimeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result { runCatching { ScreenTime.nudge(applicationContext) }; return Result.success() }
}

/** The Screen time card: today's total, and the apps that took most of it, with their limits marked. */
@Composable
fun ScreenTimeCard() {
    val context = LocalContext.current
    val usage by ScreenTime.today.collectAsStateWithLifecycle()
    val limits by EmberApp.instance.prefs.limits.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().glass().padding(16.dp)) {
        Text("Screen time", style = MaterialTheme.typography.labelLarge, color = EmberColors.Secondary)
        if (!ScreenTime.hasAccess(context)) {
            Text("See how long you spend in each app today. Needs usage access.", style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp))
            FilledTonalButton({ runCatching { context.startActivity(ScreenTime.accessIntent()) } }) { Text("Allow usage access") }
            return@Column
        }
        Text(formatDuration(usage.values.sum()) + " today", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
        val top = usage.entries.take(4)
        val max = (top.firstOrNull()?.value ?: 1L).coerceAtLeast(1L)
        top.forEach { (pkg, ms) ->
            val limit = limits[pkg]
            val over = limit != null && ms >= limit * 60_000L
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                PackageIcon(pkg, 22.dp)
                Box(Modifier.weight(1f).padding(horizontal = 8.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(ms.toFloat() / max).clip(RoundedCornerShape(4.dp))
                        .background(if (over) Fixed.Red else MaterialTheme.colorScheme.primary))
                }
                Text(formatDuration(ms), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(78.dp))
            }
        }
        if (top.isEmpty()) Text("Nothing yet today.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(2.dp))
    }
}
