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
    private val icons = LruCache<String, ImageBitmap>(600)
    private val infos = HashMap<String, LauncherActivityInfo>()

    private val _iconVersion = MutableStateFlow(0)
    /** Goes up when every icon has to be drawn again (a new icon pack). */
    val iconVersion: StateFlow<Int> = _iconVersion.asStateFlow()

    /** Forget every cached icon, so the next draw uses the chosen icon pack (or the apps' own). */
    fun reloadIcons() { icons.evictAll(); _iconVersion.value++ }

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
        icons.get("${app.key}@$sizePx")?.let { return it }
        val info = synchronized(infos) { infos[app.key] } ?: return null
        // The icon pack's drawing where it has one (still badged for work apps), else the app's own icon.
        val pack = EmberApp.instance.prefs.iconPack.value
        val fromPack = pack?.let { IconPacks.drawable(context, it, app.component) }
            ?.let { if (app.work) context.packageManager.getUserBadgedIcon(it, app.user) else it }
        val bitmap = runCatching { (fromPack ?: info.getBadgedIcon(android.util.DisplayMetrics.DENSITY_XXXHIGH)).toBitmap(sizePx, sizePx).asImageBitmap() }.getOrNull() ?: return null
        icons.put("${app.key}@$sizePx", bitmap)
        return bitmap
    }

    /** The icon of a package's main app, for notifications (which only name a package). */
    fun iconForPackage(packageName: String, sizePx: Int): ImageBitmap? {
        val app = _apps.value.firstOrNull { it.packageName == packageName && !it.work } ?: _apps.value.firstOrNull { it.packageName == packageName }
        if (app != null) return icon(app, sizePx)
        icons.get("pkg:$packageName@$sizePx")?.let { return it }
        val bitmap = runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(sizePx, sizePx).asImageBitmap() }.getOrNull() ?: return null
        icons.put("pkg:$packageName@$sizePx", bitmap)
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

/** The drawer's automatic groups. The user can rename them, make their own, and move any app (see Prefs.appGroups). */
enum class AppGroup(val title: String) {
    SOCIAL("Social"), MESSAGING("Messaging"), PRODUCTIVITY("Work & Productivity"), FINANCE("Money"), SHOPPING("Shopping"),
    ENTERTAINMENT("Entertainment"), MUSIC("Music & Audio"), PHOTOS("Photo & Video"), GAMES("Games"),
    TRAVEL("Travel & Maps"), NEWS("News & Reading"), HEALTH("Health & Fitness"), FOOD("Food & Delivery"),
    UTILITIES("Utilities"), SYSTEM("System"), OTHER("Other");
}

/** Well-known apps by exact package name: the surest signal there is. */
private val KNOWN_APPS: Map<String, AppGroup> = mapOf(
    "com.whatsapp" to AppGroup.MESSAGING, "com.whatsapp.w4b" to AppGroup.MESSAGING, "org.telegram.messenger" to AppGroup.MESSAGING,
    "com.google.android.apps.messaging" to AppGroup.MESSAGING, "com.samsung.android.messaging" to AppGroup.MESSAGING,
    "com.facebook.orca" to AppGroup.MESSAGING, "org.thoughtcrime.securesms" to AppGroup.MESSAGING, "com.discord" to AppGroup.MESSAGING,
    "com.instagram.android" to AppGroup.SOCIAL, "com.facebook.katana" to AppGroup.SOCIAL, "com.twitter.android" to AppGroup.SOCIAL,
    "com.reddit.frontpage" to AppGroup.SOCIAL, "com.snapchat.android" to AppGroup.SOCIAL, "com.linkedin.android" to AppGroup.SOCIAL,
    "com.instagram.barcelona" to AppGroup.SOCIAL, "com.pinterest" to AppGroup.SOCIAL,
    "com.microsoft.teams" to AppGroup.PRODUCTIVITY, "com.microsoft.office.outlook" to AppGroup.PRODUCTIVITY, "com.google.android.gm" to AppGroup.PRODUCTIVITY,
    "com.google.android.calendar" to AppGroup.PRODUCTIVITY, "com.google.android.keep" to AppGroup.PRODUCTIVITY, "com.google.android.apps.docs" to AppGroup.PRODUCTIVITY,
    "com.slack" to AppGroup.PRODUCTIVITY, "us.zoom.videomeetings" to AppGroup.PRODUCTIVITY, "com.notion.id" to AppGroup.PRODUCTIVITY,
    "com.samsung.android.app.notes" to AppGroup.PRODUCTIVITY, "com.samsung.android.calendar" to AppGroup.PRODUCTIVITY,
    "com.anthropic.claude" to AppGroup.PRODUCTIVITY, "com.openai.chatgpt" to AppGroup.PRODUCTIVITY, "com.google.android.apps.bard" to AppGroup.PRODUCTIVITY,
    "com.google.android.apps.nbu.paisa.user" to AppGroup.FINANCE, "net.one97.paytm" to AppGroup.FINANCE, "com.phonepe.app" to AppGroup.FINANCE,
    "com.samsung.android.spay" to AppGroup.FINANCE, "com.google.android.apps.walletnfcrel" to AppGroup.FINANCE, "com.americanexpress.android.acctsvcs.in" to AppGroup.FINANCE,
    "in.amazon.mShop.android.shopping" to AppGroup.SHOPPING, "com.amazon.mShop.android.shopping" to AppGroup.SHOPPING, "com.flipkart.android" to AppGroup.SHOPPING,
    "com.myntra.android" to AppGroup.SHOPPING, "com.android.vending" to AppGroup.SHOPPING, "com.sec.android.app.samsungapps" to AppGroup.SHOPPING,
    "com.google.android.youtube" to AppGroup.ENTERTAINMENT, "com.netflix.mediaclient" to AppGroup.ENTERTAINMENT, "in.startv.hotstar" to AppGroup.ENTERTAINMENT,
    "com.amazon.avod.thirdpartyclient" to AppGroup.ENTERTAINMENT, "com.airtel.xstream" to AppGroup.ENTERTAINMENT,
    "com.spotify.music" to AppGroup.MUSIC, "com.google.android.apps.youtube.music" to AppGroup.MUSIC, "com.jbl.oneapp" to AppGroup.MUSIC,
    "com.harman.jblconnectplus" to AppGroup.MUSIC, "com.audible.application" to AppGroup.MUSIC,
    "com.sec.android.app.camera" to AppGroup.PHOTOS, "com.sec.android.gallery3d" to AppGroup.PHOTOS, "com.google.android.apps.photos" to AppGroup.PHOTOS,
    "com.google.android.GoogleCamera" to AppGroup.PHOTOS, "com.samsung.android.video" to AppGroup.PHOTOS,
    "com.google.android.apps.maps" to AppGroup.TRAVEL, "com.ubercab" to AppGroup.TRAVEL, "com.olacabs.customer" to AppGroup.TRAVEL,
    "com.makemytrip" to AppGroup.TRAVEL, "com.airbnb.android" to AppGroup.TRAVEL,
    "com.google.android.apps.magazines" to AppGroup.NEWS, "com.nis.app" to AppGroup.NEWS, "com.amazon.kindle" to AppGroup.NEWS, "flipboard.app" to AppGroup.NEWS,
    "com.sec.android.app.shealth" to AppGroup.HEALTH, "com.google.android.apps.fitness" to AppGroup.HEALTH, "com.strava" to AppGroup.HEALTH,
    "com.application.zomato" to AppGroup.FOOD, "in.swiggy.android" to AppGroup.FOOD, "com.ubercab.eats" to AppGroup.FOOD,
    "com.android.chrome" to AppGroup.UTILITIES, "com.sec.android.app.sbrowser" to AppGroup.UTILITIES, "com.duckduckgo.mobile.android" to AppGroup.UTILITIES,
    "com.microsoft.emmx" to AppGroup.UTILITIES, "com.google.android.dialer" to AppGroup.UTILITIES, "com.samsung.android.dialer" to AppGroup.UTILITIES,
    "com.sec.android.app.clockpackage" to AppGroup.UTILITIES, "com.sec.android.app.popupcalculator" to AppGroup.UTILITIES,
    "com.sec.android.app.myfiles" to AppGroup.UTILITIES, "com.samsung.android.app.contacts" to AppGroup.UTILITIES,
    "com.android.settings" to AppGroup.SYSTEM,
)

/** Words in the package or app name, most specific first. */
private val HINTS: List<Pair<String, AppGroup>> = listOf(
    "whatsapp" to AppGroup.MESSAGING, "telegram" to AppGroup.MESSAGING, "messag" to AppGroup.MESSAGING, "signal" to AppGroup.MESSAGING,
    "chat" to AppGroup.MESSAGING, "mms" to AppGroup.MESSAGING, "sms" to AppGroup.MESSAGING,
    "facebook" to AppGroup.SOCIAL, "instagram" to AppGroup.SOCIAL, "twitter" to AppGroup.SOCIAL, "reddit" to AppGroup.SOCIAL, "social" to AppGroup.SOCIAL,
    "bank" to AppGroup.FINANCE, "wallet" to AppGroup.FINANCE, "upi" to AppGroup.FINANCE, "finance" to AppGroup.FINANCE, "invest" to AppGroup.FINANCE,
    "trading" to AppGroup.FINANCE, "credit" to AppGroup.FINANCE, "card" to AppGroup.FINANCE, "pay" to AppGroup.FINANCE, "money" to AppGroup.FINANCE,
    "shop" to AppGroup.SHOPPING, "store" to AppGroup.SHOPPING, "amazon" to AppGroup.SHOPPING,
    "food" to AppGroup.FOOD, "eats" to AppGroup.FOOD, "pizza" to AppGroup.FOOD, "restaurant" to AppGroup.FOOD, "grocer" to AppGroup.FOOD,
    "health" to AppGroup.HEALTH, "fit" to AppGroup.HEALTH, "workout" to AppGroup.HEALTH, "yoga" to AppGroup.HEALTH, "medic" to AppGroup.HEALTH,
    "game" to AppGroup.GAMES, "puzzle" to AppGroup.GAMES, "chess" to AppGroup.GAMES,
    "music" to AppGroup.MUSIC, "podcast" to AppGroup.MUSIC, "radio" to AppGroup.MUSIC, "audio" to AppGroup.MUSIC, "sound" to AppGroup.MUSIC,
    "camera" to AppGroup.PHOTOS, "gallery" to AppGroup.PHOTOS, "photo" to AppGroup.PHOTOS, "video" to AppGroup.PHOTOS, "editor" to AppGroup.PHOTOS,
    "tv" to AppGroup.ENTERTAINMENT, "movie" to AppGroup.ENTERTAINMENT, "stream" to AppGroup.ENTERTAINMENT, "cinema" to AppGroup.ENTERTAINMENT,
    "map" to AppGroup.TRAVEL, "travel" to AppGroup.TRAVEL, "flight" to AppGroup.TRAVEL, "hotel" to AppGroup.TRAVEL, "cab" to AppGroup.TRAVEL,
    "rail" to AppGroup.TRAVEL, "metro" to AppGroup.TRAVEL, "ride" to AppGroup.TRAVEL,
    "news" to AppGroup.NEWS, "reader" to AppGroup.NEWS, "book" to AppGroup.NEWS, "magazine" to AppGroup.NEWS,
    "mail" to AppGroup.PRODUCTIVITY, "office" to AppGroup.PRODUCTIVITY, "docs" to AppGroup.PRODUCTIVITY, "calendar" to AppGroup.PRODUCTIVITY,
    "notes" to AppGroup.PRODUCTIVITY, "teams" to AppGroup.PRODUCTIVITY, "meet" to AppGroup.PRODUCTIVITY, "drive" to AppGroup.PRODUCTIVITY,
    "task" to AppGroup.PRODUCTIVITY, "todo" to AppGroup.PRODUCTIVITY, "scan" to AppGroup.PRODUCTIVITY,
    "browser" to AppGroup.UTILITIES, "clock" to AppGroup.UTILITIES, "calculator" to AppGroup.UTILITIES, "files" to AppGroup.UTILITIES,
    "contacts" to AppGroup.UTILITIES, "dialer" to AppGroup.UTILITIES, "phone" to AppGroup.UTILITIES, "vpn" to AppGroup.UTILITIES,
    "authenticator" to AppGroup.UTILITIES, "weather" to AppGroup.UTILITIES, "translate" to AppGroup.UTILITIES, "recorder" to AppGroup.UTILITIES,
    "settings" to AppGroup.SYSTEM,
)

/** Phone-maker and Android apps nobody would file under "productivity" (which many of them claim to be). */
private val SYSTEM_PREFIXES = listOf("com.samsung.", "com.sec.", "com.android.", "com.google.android.gms", "com.google.android.as",
    "com.google.android.apps.wellbeing", "com.osp.", "com.monotype.", "com.diotek.")

fun groupOf(app: AppEntry): AppGroup = groupFor(app.packageName, app.category, app.label)

/**
 * The automatic group: a well-known app's own entry first, then words in its package or name, then the category the
 * app declares (except "productivity", which so many claim that it says little), then System for phone-maker apps, and
 * only then the declared productivity.
 */
fun groupFor(packageName: String, category: Int, label: String = ""): AppGroup {
    KNOWN_APPS[packageName]?.let { return it }
    val pkg = packageName.lowercase()
    val name = label.lowercase()
    // The app's own name is matched by whole word ("Fit" but not "Profit"); the package by part.
    val words = name.split(' ', '-', '_', '.', '&').filter { it.isNotBlank() }
    val hint = HINTS.firstOrNull { (key, _) -> words.any { it == key || it.startsWith(key) } }?.second
        ?: HINTS.firstOrNull { (key, _) -> key.length >= 4 && pkg.contains(key) }?.second
    if (hint != null) return hint
    when (category) {
        android.content.pm.ApplicationInfo.CATEGORY_GAME -> return AppGroup.GAMES
        android.content.pm.ApplicationInfo.CATEGORY_AUDIO -> return AppGroup.MUSIC
        android.content.pm.ApplicationInfo.CATEGORY_VIDEO -> return AppGroup.ENTERTAINMENT
        android.content.pm.ApplicationInfo.CATEGORY_IMAGE -> return AppGroup.PHOTOS
        android.content.pm.ApplicationInfo.CATEGORY_SOCIAL -> return AppGroup.SOCIAL
        android.content.pm.ApplicationInfo.CATEGORY_NEWS -> return AppGroup.NEWS
        android.content.pm.ApplicationInfo.CATEGORY_MAPS -> return AppGroup.TRAVEL
        android.content.pm.ApplicationInfo.CATEGORY_ACCESSIBILITY -> return AppGroup.UTILITIES
    }
    if (SYSTEM_PREFIXES.any { pkg.startsWith(it) }) return AppGroup.SYSTEM
    if (category == android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY) return AppGroup.PRODUCTIVITY
    return AppGroup.OTHER
}

/** The group the drawer shows an app in: the user's own choice (by hand or from AI) if there is one, else automatic. */
fun groupNameOf(app: AppEntry, overrides: Map<String, String>): String = overrides[app.packageName] ?: groupOf(app).title
