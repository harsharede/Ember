package com.harsharede.ember

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Everything Ember remembers. Each value is a StateFlow so the screens update the moment it changes. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("ember", Context.MODE_PRIVATE)

    private val _hiddenStatus = MutableStateFlow(sp.getStringSet(KEY_HIDDEN, emptySet())!!.toSet())
    /** Status icons the user swiped away (ids from [StatusIcon]). */
    val hiddenStatus: StateFlow<Set<String>> = _hiddenStatus.asStateFlow()

    private val _dock = MutableStateFlow(readDock())
    /** App keys in the rail's dock, top to bottom. An empty string is an empty slot. */
    val dock: StateFlow<List<String>> = _dock.asStateFlow()

    private val _pill = MutableStateFlow(sp.getBoolean(KEY_PILL, true))
    /** Show the glowing pill over other apps when a notification arrives. */
    val pillEnabled: StateFlow<Boolean> = _pill.asStateFlow()

    private val _hideStatusBar = MutableStateFlow(sp.getBoolean(KEY_HIDE_BAR, true))
    /** Hide Android's own status bar on Home, since the rail shows the same things. */
    val hideStatusBar: StateFlow<Boolean> = _hideStatusBar.asStateFlow()

    private val _launches = MutableStateFlow(readLaunches())
    /** How often and how recently each app was opened from Ember, for Suggestions. */
    val launches: StateFlow<Map<String, Launch>> = _launches.asStateFlow()

    data class Launch(val count: Int, val last: Long)

    private val _wallpaperColors = MutableStateFlow(sp.getBoolean(KEY_WALLPAPER_COLORS, true))
    /** Material You: take the theme colours from the wallpaper. Off uses Ember's own orange. */
    val wallpaperColors: StateFlow<Boolean> = _wallpaperColors.asStateFlow()

    private val _drawerKeyboard = MutableStateFlow(sp.getBoolean(KEY_DRAWER_KEYBOARD, true))
    /** Open the keyboard in the search field as soon as the app drawer opens. */
    val drawerKeyboard: StateFlow<Boolean> = _drawerKeyboard.asStateFlow()

    private val _drawerTab = MutableStateFlow(sp.getInt(KEY_DRAWER_TAB, 0))
    /** The drawer page last used: 0 for A to Z, 1 for groups. */
    val drawerTab: StateFlow<Int> = _drawerTab.asStateFlow()

    private val _showPersistent = MutableStateFlow(sp.getBoolean(KEY_PERSISTENT, false))
    /** List notifications that can't be dismissed (system and "always on" ones) in their own section. */
    val showPersistent: StateFlow<Boolean> = _showPersistent.asStateFlow()

    private val _widgets = MutableStateFlow(readWidgets())
    /** Widgets on Home, top to bottom. */
    val widgets: StateFlow<List<HomeWidget>> = _widgets.asStateFlow()

    data class HomeWidget(val id: Int, val heightDp: Int)

    private val _light = MutableStateFlow(sp.getBoolean(KEY_LIGHT, true))
    /** The lock-screen light: wake the screen black with a glowing pill when something arrives with the screen off. */
    val lightEnabled: StateFlow<Boolean> = _light.asStateFlow()
    private val _lightMinutes = MutableStateFlow(sp.getInt(KEY_LIGHT_MINUTES, 60))
    /** How long the light keeps glowing before it lets the screen go off. */
    val lightMinutes: StateFlow<Int> = _lightMinutes.asStateFlow()
    private val _lightIcon = MutableStateFlow(sp.getBoolean(KEY_LIGHT_ICON, true))
    /** Show the apps' icons inside the light (off: just a coloured light). */
    val lightIcon: StateFlow<Boolean> = _lightIcon.asStateFlow()
    private val _lightBrightness = MutableStateFlow(sp.getInt(KEY_LIGHT_BRIGHTNESS, 0))
    /** 0 low, 1 medium, 2 high. */
    val lightBrightness: StateFlow<Int> = _lightBrightness.asStateFlow()

    // Wallpapers from the user's photos.
    private val _wallpaperRotate = MutableStateFlow(sp.getBoolean(KEY_WP_ROTATE, false))
    val wallpaperRotate: StateFlow<Boolean> = _wallpaperRotate.asStateFlow()
    private val _wallpaperEvery = MutableStateFlow(sp.getInt(KEY_WP_EVERY, 1))
    /** [Wallpapers.EVERY_30_MIN] or [Wallpapers.THREE_A_DAY]. */
    val wallpaperEvery: StateFlow<Int> = _wallpaperEvery.asStateFlow()
    private val _wallpaperLock = MutableStateFlow(sp.getBoolean(KEY_WP_LOCK, true))
    /** Put it on the lock screen too, not just Home. */
    val wallpaperLock: StateFlow<Boolean> = _wallpaperLock.asStateFlow()
    private val _wallpaperCameraOnly = MutableStateFlow(sp.getBoolean(KEY_WP_CAMERA, true))
    /** Only photos taken with the camera (no screenshots, downloads or chat images). */
    val wallpaperCameraOnly: StateFlow<Boolean> = _wallpaperCameraOnly.asStateFlow()
    private val _wallpaperCard = MutableStateFlow(sp.getBoolean(KEY_WP_CARD, true))
    /** The like / dislike / new-photo buttons on Home. */
    val wallpaperCard: StateFlow<Boolean> = _wallpaperCard.asStateFlow()
    private val _wallpaperPending = MutableStateFlow(sp.getBoolean(KEY_WP_PENDING, false))
    /** A timed change is waiting for the screen to go off or Home to come back. */
    val wallpaperPending: StateFlow<Boolean> = _wallpaperPending.asStateFlow()
    private val _wallpaperPhoto = MutableStateFlow(sp.getLong(KEY_WP_PHOTO, -1L))
    val wallpaperPhoto: StateFlow<Long> = _wallpaperPhoto.asStateFlow()
    private val _wallpaperTakenAt = MutableStateFlow(sp.getLong(KEY_WP_TAKEN, 0L))
    val wallpaperTakenAt: StateFlow<Long> = _wallpaperTakenAt.asStateFlow()
    private val _liked = MutableStateFlow(readIds(KEY_WP_LIKED))
    val likedPhotos: StateFlow<Set<Long>> = _liked.asStateFlow()
    private val _disliked = MutableStateFlow(readIds(KEY_WP_DISLIKED))
    val dislikedPhotos: StateFlow<Set<Long>> = _disliked.asStateFlow()
    private val _recent = MutableStateFlow(sp.getString(KEY_WP_RECENT, null).orEmpty().split(',').mapNotNull { it.toLongOrNull() })
    /** The last photos shown, newest first, so they don't come straight back. */
    val recentPhotos: StateFlow<List<Long>> = _recent.asStateFlow()

    fun setWallpaperRotate(on: Boolean) { _wallpaperRotate.value = on; sp.edit().putBoolean(KEY_WP_ROTATE, on).apply() }
    fun setWallpaperEvery(v: Int) { _wallpaperEvery.value = v; sp.edit().putInt(KEY_WP_EVERY, v).apply() }
    fun setWallpaperLock(on: Boolean) { _wallpaperLock.value = on; sp.edit().putBoolean(KEY_WP_LOCK, on).apply() }
    fun setWallpaperCameraOnly(on: Boolean) { _wallpaperCameraOnly.value = on; sp.edit().putBoolean(KEY_WP_CAMERA, on).apply() }
    fun setWallpaperCard(on: Boolean) { _wallpaperCard.value = on; sp.edit().putBoolean(KEY_WP_CARD, on).apply() }
    fun setWallpaperPending(on: Boolean) { if (_wallpaperPending.value != on) { _wallpaperPending.value = on; sp.edit().putBoolean(KEY_WP_PENDING, on).apply() } }
    fun setWallpaperPhoto(id: Long, takenAt: Long) {
        _wallpaperPhoto.value = id; _wallpaperTakenAt.value = takenAt
        sp.edit().putLong(KEY_WP_PHOTO, id).putLong(KEY_WP_TAKEN, takenAt).apply()
    }
    fun addRecentPhoto(id: Long, keep: Int) {
        val list = (listOf(id) + _recent.value.filter { it != id }).take(keep)
        _recent.value = list; sp.edit().putString(KEY_WP_RECENT, list.joinToString(",")).apply()
    }
    fun likePhoto(id: Long) { saveIds(KEY_WP_LIKED, _liked, _liked.value + id); saveIds(KEY_WP_DISLIKED, _disliked, _disliked.value - id) }
    fun unlikePhoto(id: Long) = saveIds(KEY_WP_LIKED, _liked, _liked.value - id)
    fun dislikePhoto(id: Long) { saveIds(KEY_WP_DISLIKED, _disliked, _disliked.value + id); saveIds(KEY_WP_LIKED, _liked, _liked.value - id) }
    fun forgetPhotoChoices() { saveIds(KEY_WP_LIKED, _liked, emptySet()); saveIds(KEY_WP_DISLIKED, _disliked, emptySet()) }
    private fun readIds(key: String): Set<Long> = sp.getStringSet(key, emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
    private fun saveIds(key: String, flow: MutableStateFlow<Set<Long>>, ids: Set<Long>) {
        flow.value = ids; sp.edit().putStringSet(key, ids.mapTo(HashSet()) { it.toString() }).apply()
    }

    fun setLight(on: Boolean) { _light.value = on; sp.edit().putBoolean(KEY_LIGHT, on).apply() }
    fun setLightMinutes(m: Int) { _lightMinutes.value = m; sp.edit().putInt(KEY_LIGHT_MINUTES, m).apply() }
    fun setLightIcon(on: Boolean) { _lightIcon.value = on; sp.edit().putBoolean(KEY_LIGHT_ICON, on).apply() }
    fun setLightBrightness(level: Int) { _lightBrightness.value = level; sp.edit().putInt(KEY_LIGHT_BRIGHTNESS, level).apply() }

    fun setWallpaperColors(on: Boolean) { _wallpaperColors.value = on; sp.edit().putBoolean(KEY_WALLPAPER_COLORS, on).apply() }
    fun setDrawerKeyboard(on: Boolean) { _drawerKeyboard.value = on; sp.edit().putBoolean(KEY_DRAWER_KEYBOARD, on).apply() }
    fun setDrawerTab(tab: Int) { if (_drawerTab.value != tab) { _drawerTab.value = tab; sp.edit().putInt(KEY_DRAWER_TAB, tab).apply() } }
    fun setShowPersistent(on: Boolean) { _showPersistent.value = on; sp.edit().putBoolean(KEY_PERSISTENT, on).apply() }

    fun addWidget(widget: HomeWidget) = saveWidgets(_widgets.value.filter { it.id != widget.id } + widget)
    fun removeWidget(id: Int) = saveWidgets(_widgets.value.filter { it.id != id })
    fun resizeWidget(id: Int, heightDp: Int) = saveWidgets(_widgets.value.map { if (it.id == id) it.copy(heightDp = heightDp) else it })
    fun moveWidget(id: Int, by: Int) {
        val list = _widgets.value.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + by
        if (i < 0 || j !in list.indices) return
        list.add(j, list.removeAt(i))
        saveWidgets(list)
    }
    private fun saveWidgets(list: List<HomeWidget>) {
        _widgets.value = list
        sp.edit().putString(KEY_WIDGETS, list.joinToString(SEP) { "${it.id}$FIELD${it.heightDp}" }).apply()
    }
    private fun readWidgets(): List<HomeWidget> = sp.getString(KEY_WIDGETS, null).orEmpty().split(SEP).mapNotNull { line ->
        val parts = line.split(FIELD)
        val id = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
        HomeWidget(id, parts.getOrNull(1)?.toIntOrNull() ?: 160)
    }

    fun hideStatus(id: String) = setHidden(_hiddenStatus.value + id)
    fun showStatus(id: String) = setHidden(_hiddenStatus.value - id)
    private fun setHidden(set: Set<String>) {
        _hiddenStatus.value = set
        sp.edit().putStringSet(KEY_HIDDEN, set).apply()
    }

    fun setDockSlot(index: Int, key: String) {
        val list = _dock.value.toMutableList()
        while (list.size <= index) list += ""
        // One app appears in the dock once: moving it clears its old slot.
        for (i in list.indices) if (list[i] == key && i != index) list[i] = ""
        list[index] = key
        _dock.value = list
        sp.edit().putString(KEY_DOCK, list.joinToString(SEP)).apply()
    }

    fun setPill(on: Boolean) { _pill.value = on; sp.edit().putBoolean(KEY_PILL, on).apply() }
    fun setHideStatusBar(on: Boolean) { _hideStatusBar.value = on; sp.edit().putBoolean(KEY_HIDE_BAR, on).apply() }

    fun recordLaunch(key: String, now: Long = System.currentTimeMillis()) {
        val old = _launches.value[key]
        val map = _launches.value + (key to Launch((old?.count ?: 0) + 1, now))
        // Keep the list small: the 60 most recently used apps are plenty for suggestions.
        val trimmed = if (map.size > 60) map.entries.sortedByDescending { it.value.last }.take(60).associate { it.key to it.value } else map
        _launches.value = trimmed
        sp.edit().putString(KEY_LAUNCHES, trimmed.entries.joinToString(SEP) { "${it.key}$FIELD${it.value.count}$FIELD${it.value.last}" }).apply()
    }

    private fun readDock(): List<String> {
        val raw = sp.getString(KEY_DOCK, null) ?: return List(DOCK_SLOTS) { "" }
        return raw.split(SEP).let { if (it.size < DOCK_SLOTS) it + List(DOCK_SLOTS - it.size) { "" } else it }
    }

    private fun readLaunches(): Map<String, Launch> =
        sp.getString(KEY_LAUNCHES, null).orEmpty().split(SEP).mapNotNull { line ->
            val parts = line.split(FIELD)
            if (parts.size != 3) null else parts[0] to Launch(parts[1].toIntOrNull() ?: 0, parts[2].toLongOrNull() ?: 0L)
        }.toMap()

    companion object {
        const val DOCK_SLOTS = 4
        private const val KEY_HIDDEN = "hidden_status"
        private const val KEY_DOCK = "dock"
        private const val KEY_PILL = "pill"
        private const val KEY_HIDE_BAR = "hide_status_bar"
        private const val KEY_LAUNCHES = "launches"
        private const val KEY_WALLPAPER_COLORS = "wallpaper_colors"
        private const val KEY_DRAWER_KEYBOARD = "drawer_keyboard"
        private const val KEY_DRAWER_TAB = "drawer_tab"
        private const val KEY_PERSISTENT = "show_persistent"
        private const val KEY_WIDGETS = "widgets"
        private const val KEY_LIGHT = "light"
        private const val KEY_WP_ROTATE = "wp_rotate"
        private const val KEY_WP_EVERY = "wp_every"
        private const val KEY_WP_LOCK = "wp_lock"
        private const val KEY_WP_CAMERA = "wp_camera_only"
        private const val KEY_WP_CARD = "wp_card"
        private const val KEY_WP_PENDING = "wp_pending"
        private const val KEY_WP_PHOTO = "wp_photo"
        private const val KEY_WP_TAKEN = "wp_taken"
        private const val KEY_WP_LIKED = "wp_liked"
        private const val KEY_WP_DISLIKED = "wp_disliked"
        private const val KEY_WP_RECENT = "wp_recent"
        private const val KEY_LIGHT_MINUTES = "light_minutes"
        private const val KEY_LIGHT_ICON = "light_icon"
        private const val KEY_LIGHT_BRIGHTNESS = "light_brightness"
        private const val SEP = "\n"
        private const val FIELD = "\t"
    }
}
