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
                AppEntry(key, info.label.toString().trim(), info.componentName, user, work = user != me)
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
