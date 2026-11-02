package com.harsharede.ember

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

/** One launchable app (one per profile: a work-profile copy is its own entry). */
data class AppEntry(
    val key: String,
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val work: Boolean,
    /** Android's ApplicationInfo.category, or -1 when the app doesn't say. */
    val category: Int = -1,
) {
    val packageName: String get() = component.packageName
}

/** The installed apps, kept current as apps are installed, updated and removed. */
class AppsRepository(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val icons = LruCache<String, ImageBitmap>(300)
    private val infos = HashMap<String, LauncherActivityInfo>()

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    /** Every launchable app, sorted A to Z the way the phone's language sorts. */
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    init {
        launcherApps.registerCallback(object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) = refresh()
            override fun onPackageAdded(packageName: String, user: UserHandle) = refresh()
            override fun onPackageChanged(packageName: String, user: UserHandle) = refresh()
            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
        }, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun refresh() {
        scope.launch {
            val list = withContext(Dispatchers.IO) { load() }
            _apps.value = list
        }
    }

    private fun load(): List<AppEntry> {
        val me = android.os.Process.myUserHandle()
        val collator = Collator.getInstance()
        val found = HashMap<String, LauncherActivityInfo>()
        val entries = launcherApps.profiles.flatMap { user ->
            // A paused work profile still lists its apps; they just won't open until it's turned on.
            launcherApps.getActivityList(null, user).filter { it.componentName.packageName != context.packageName }.map { info ->
                val key = keyFor(info.componentName, user)
                found[key] = info
                AppEntry(key, info.label.toString().trim(), info.componentName, user, work = user != me,
                    category = info.applicationInfo.category)
            }
        }
        synchronized(infos) { infos.clear(); infos.putAll(found) }
        return entries.sortedWith(compareBy(collator) { it.label })
    }

    fun byKey(key: String): AppEntry? = _apps.value.firstOrNull { it.key == key }

    /** The app's icon (with the work badge where it applies), loaded once and cached. Call off the main thread. */
    fun icon(app: AppEntry, sizePx: Int): ImageBitmap? {
        icons.get(app.key)?.let { return it }
        val info = synchronized(infos) { infos[app.key] } ?: return null
        val bitmap = runCatching { info.getBadgedIcon(0).toBitmap(sizePx, sizePx).asImageBitmap() }.getOrNull() ?: return null
        icons.put(app.key, bitmap)
        return bitmap
    }

    /** The icon of a package's main app, for notifications (which only name a package). */
    fun iconForPackage(packageName: String, sizePx: Int): ImageBitmap? {
        val app = _apps.value.firstOrNull { it.packageName == packageName && !it.work } ?: _apps.value.firstOrNull { it.packageName == packageName }
        if (app != null) return icon(app, sizePx)
        icons.get("pkg:$packageName")?.let { return it }
        val bitmap = runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(sizePx, sizePx).asImageBitmap() }.getOrNull() ?: return null
        icons.put("pkg:$packageName", bitmap)
        return bitmap
    }

    fun labelForPackage(packageName: String): String =
        _apps.value.firstOrNull { it.packageName == packageName }?.label
            ?: runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString() }.getOrDefault(packageName)

    fun launch(app: AppEntry, bounds: Rect? = null) {
        runCatching {
            launcherApps.startMainActivity(app.component, app.user, bounds, ActivityOptions.makeBasic().toBundle())
            EmberApp.instance.prefs.recordLaunch(app.key)
        }
    }

    fun openAppInfo(app: AppEntry) {
        runCatching { launcherApps.startAppDetailsActivity(app.component, app.user, null, null) }
    }

    fun uninstall(app: AppEntry) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
                .putExtra(Intent.EXTRA_USER, app.user).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    companion object {
        fun keyFor(component: ComponentName, user: UserHandle) = component.flattenToString() + "#" + user.hashCode()
    }
}

/**
 * Suggestions: apps scored by how often they're opened, with recent use counting most (a launch loses half its weight
 * every three days). Dock apps are left out, since they're already one tap away.
 */
fun suggest(apps: List<AppEntry>, launches: Map<String, Prefs.Launch>, exclude: Set<String>, now: Long, limit: Int = 4): List<AppEntry> {
    val halfLife = 3 * 24 * 60 * 60 * 1000.0
    return apps.asSequence()
        .filter { it.key !in exclude }
        .mapNotNull { app -> launches[app.key]?.let { l -> app to l.count * Math.pow(0.5, (now - l.last).coerceAtLeast(0) / halfLife) } }
        .sortedByDescending { it.second }
        .take(limit).map { it.first }.toList()
}

/** The drawer's groups. Apps say their own category; for the many that don't, well-known package names fill in. */
enum class AppGroup(val title: String) {
    SOCIAL("Social"), MESSAGING("Messaging"), PRODUCTIVITY("Productivity"), FINANCE("Finance & Shopping"),
    ENTERTAINMENT("Entertainment"), MUSIC("Music & Audio"), PHOTOS("Photo & Video"), GAMES("Games"),
    TRAVEL("Travel & Maps"), NEWS("News & Reading"), UTILITIES("Utilities"), OTHER("Other");
}

private val PACKAGE_HINTS: List<Pair<String, AppGroup>> = listOf(
    "whatsapp" to AppGroup.MESSAGING, "telegram" to AppGroup.MESSAGING, "messag" to AppGroup.MESSAGING, "signal" to AppGroup.MESSAGING,
    "android.mms" to AppGroup.MESSAGING, "skype" to AppGroup.MESSAGING, "discord" to AppGroup.MESSAGING, "slack" to AppGroup.PRODUCTIVITY,
    "facebook" to AppGroup.SOCIAL, "instagram" to AppGroup.SOCIAL, "twitter" to AppGroup.SOCIAL, "snapchat" to AppGroup.SOCIAL,
    "linkedin" to AppGroup.SOCIAL, "reddit" to AppGroup.SOCIAL, "threads" to AppGroup.SOCIAL,
    "teams" to AppGroup.PRODUCTIVITY, "outlook" to AppGroup.PRODUCTIVITY, "office" to AppGroup.PRODUCTIVITY, "gmail" to AppGroup.PRODUCTIVITY,
    "android.gm" to AppGroup.PRODUCTIVITY, "docs" to AppGroup.PRODUCTIVITY, "calendar" to AppGroup.PRODUCTIVITY, "notes" to AppGroup.PRODUCTIVITY,
    "drive" to AppGroup.PRODUCTIVITY, "zoom" to AppGroup.PRODUCTIVITY, "keep" to AppGroup.PRODUCTIVITY,
    "bank" to AppGroup.FINANCE, "pay" to AppGroup.FINANCE, "amex" to AppGroup.FINANCE, "wallet" to AppGroup.FINANCE, "upi" to AppGroup.FINANCE,
    "amazon" to AppGroup.FINANCE, "flipkart" to AppGroup.FINANCE, "shop" to AppGroup.FINANCE,
    "youtube" to AppGroup.ENTERTAINMENT, "netflix" to AppGroup.ENTERTAINMENT, "prime" to AppGroup.ENTERTAINMENT, "hotstar" to AppGroup.ENTERTAINMENT,
    "xstream" to AppGroup.ENTERTAINMENT, "jiocinema" to AppGroup.ENTERTAINMENT,
    "spotify" to AppGroup.MUSIC, "music" to AppGroup.MUSIC, "podcast" to AppGroup.MUSIC, "jbl" to AppGroup.MUSIC, "audio" to AppGroup.MUSIC,
    "camera" to AppGroup.PHOTOS, "gallery" to AppGroup.PHOTOS, "photos" to AppGroup.PHOTOS, "video" to AppGroup.PHOTOS,
    "maps" to AppGroup.TRAVEL, "uber" to AppGroup.TRAVEL, "olacabs" to AppGroup.TRAVEL, "travel" to AppGroup.TRAVEL,
    "news" to AppGroup.NEWS, "kindle" to AppGroup.NEWS, "reader" to AppGroup.NEWS,
    "settings" to AppGroup.UTILITIES, "clock" to AppGroup.UTILITIES, "calculator" to AppGroup.UTILITIES, "files" to AppGroup.UTILITIES,
    "myfiles" to AppGroup.UTILITIES, "dialer" to AppGroup.UTILITIES, "contacts" to AppGroup.UTILITIES, "chrome" to AppGroup.UTILITIES,
    "browser" to AppGroup.UTILITIES, "vpn" to AppGroup.UTILITIES, "authenticator" to AppGroup.UTILITIES, "phone" to AppGroup.UTILITIES,
)

fun groupOf(app: AppEntry): AppGroup {
    val byCategory = when (app.category) {
        android.content.pm.ApplicationInfo.CATEGORY_GAME -> AppGroup.GAMES
        android.content.pm.ApplicationInfo.CATEGORY_AUDIO -> AppGroup.MUSIC
        android.content.pm.ApplicationInfo.CATEGORY_VIDEO -> AppGroup.ENTERTAINMENT
        android.content.pm.ApplicationInfo.CATEGORY_IMAGE -> AppGroup.PHOTOS
        android.content.pm.ApplicationInfo.CATEGORY_SOCIAL -> AppGroup.SOCIAL
        android.content.pm.ApplicationInfo.CATEGORY_NEWS -> AppGroup.NEWS
        android.content.pm.ApplicationInfo.CATEGORY_MAPS -> AppGroup.TRAVEL
        android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppGroup.PRODUCTIVITY
        android.content.pm.ApplicationInfo.CATEGORY_ACCESSIBILITY -> AppGroup.UTILITIES
        else -> null
    }
    // A name hint wins over a vague category: chat apps often call themselves "social".
    val pkg = app.packageName.lowercase()
    val hint = PACKAGE_HINTS.firstOrNull { pkg.contains(it.first) }?.second
    return when {
        hint == AppGroup.MESSAGING -> hint
        byCategory != null -> byCategory
        hint != null -> hint
        else -> AppGroup.OTHER
    }
}
