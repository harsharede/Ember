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
    /** App keys in the rail's dock, top to bottom, as many as the user likes. */
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

    /** A widget on Home: which page (0 is the Today page) and how tall. */
    data class HomeWidget(val id: Int, val heightDp: Int, val page: Int = 0)

    private val _pages = MutableStateFlow(sp.getInt(KEY_PAGES, 1).coerceAtLeast(1))
    /** Home pages: the Today page plus any blank pages added for widgets. */
    val pageCount: StateFlow<Int> = _pages.asStateFlow()
    fun addPage(): Int { _pages.value += 1; sp.edit().putInt(KEY_PAGES, _pages.value).apply(); return _pages.value - 1 }
    /** Remove page [page] (never the Today page); its widgets go with it, later pages move up. Returns the removed widget ids. */
    fun removePage(page: Int): List<Int> {
        if (page <= 0 || page >= _pages.value) return emptyList()
        val gone = _widgets.value.filter { it.page == page }.map { it.id }
        saveWidgets(_widgets.value.filter { it.page != page }.map { if (it.page > page) it.copy(page = it.page - 1) else it })
        _pages.value -= 1; sp.edit().putInt(KEY_PAGES, _pages.value).apply()
        return gone
    }

    // How Home looks, adjusted live from Home's "Customize look".
    private val _railWidth = MutableStateFlow(sp.getInt(KEY_RAIL_WIDTH, 92))
    /** Width of the right rail (status capsule and dock), in dp. Dock icons scale with it. */
    val railWidth: StateFlow<Int> = _railWidth.asStateFlow()
    private val _dockHeight = MutableStateFlow(sp.getFloat(KEY_DOCK_HEIGHT, 1f))
    /** The most of the rail's height the dock may take, from 0.3 to 1 (all that's left under the capsule). */
    val dockHeight: StateFlow<Float> = _dockHeight.asStateFlow()
    private val _railAlpha = MutableStateFlow(sp.getFloat(KEY_RAIL_ALPHA, .8f))
    /** How solid the rail's glass is (0 clear, 1 solid). */
    val railAlpha: StateFlow<Float> = _railAlpha.asStateFlow()
    private val _cardAlpha = MutableStateFlow(sp.getFloat(KEY_CARD_ALPHA, .8f))
    /** How solid Home's cards are (the Smart Stack, suggestions, Ember's cards). */
    val cardAlpha: StateFlow<Float> = _cardAlpha.asStateFlow()
    fun setRailWidth(dp: Int) { _railWidth.value = dp; sp.edit().putInt(KEY_RAIL_WIDTH, dp).apply() }
    fun setDockHeight(f: Float) { _dockHeight.value = f; sp.edit().putFloat(KEY_DOCK_HEIGHT, f).apply() }
    fun setRailAlpha(a: Float) { _railAlpha.value = a; sp.edit().putFloat(KEY_RAIL_ALPHA, a).apply() }
    fun setCardAlpha(a: Float) { _cardAlpha.value = a; sp.edit().putFloat(KEY_CARD_ALPHA, a).apply() }

    // The Smart Stack: Ember's own cards, app feeds and widgets in one card that flips every few seconds.
    // Items are "card:weather", "card:people", "card:media", "card:calendar", "card:suggestions", "feed:<package>", "widget:<id>".
    private val _stack = MutableStateFlow(readStack())
    val stackItems: StateFlow<List<String>> = _stack.asStateFlow()
    /** The widget ids in the stack. */
    val stackWidgetIds: List<Int> get() = _stack.value.filter { it.startsWith("widget:") }.mapNotNull { it.removePrefix("widget:").toIntOrNull() }
    private val _stackHeight = MutableStateFlow(sp.getInt(KEY_STACK_HEIGHT, 190))
    val stackHeight: StateFlow<Int> = _stackHeight.asStateFlow()
    private val _stackSeconds = MutableStateFlow(sp.getInt(KEY_STACK_SECONDS, 7))
    /** Seconds on each card before the stack moves on (only while Home is on screen). */
    val stackSeconds: StateFlow<Int> = _stackSeconds.asStateFlow()

    fun addStackItem(item: String) { if (item !in _stack.value) saveStack(_stack.value + item) }
    fun removeStackItem(item: String) { if (item in _stack.value) saveStack(_stack.value - item) }
    fun moveStackItem(item: String, by: Int) {
        val list = _stack.value.toMutableList()
        val i = list.indexOf(item); val j = i + by
        if (i < 0 || j !in list.indices) return
        list.add(j, list.removeAt(i)); saveStack(list)
    }
    private fun saveStack(list: List<String>) { _stack.value = list; sp.edit().putString(KEY_STACK_ITEMS, list.joinToString(",")).apply() }
    private fun readStack(): List<String> {
        sp.getString(KEY_STACK_ITEMS, null)?.let { saved -> return saved.split(',').filter { it.isNotBlank() } }
        // First run of this version: carry over the widgets (and Suggestions) from the older stack.
        val widgets = sp.getString(KEY_STACK, null).orEmpty().split(',').mapNotNull { it.toIntOrNull() }.map { "widget:$it" }
        val suggestions = if (sp.getBoolean(KEY_STACK_SUGGESTIONS, false) || widgets.isEmpty()) listOf("card:suggestions") else emptyList()
        return listOf("card:media", "card:people") + suggestions + widgets
    }

    // Weather card: a city the user chose, or their location.
    private val _weatherPlace = MutableStateFlow(readPlace())
    /** Name, latitude, longitude of the chosen city; null means "use my location". */
    val weatherPlace: StateFlow<Triple<String, Double, Double>?> = _weatherPlace.asStateFlow()
    fun setWeatherPlace(place: Triple<String, Double, Double>?) {
        _weatherPlace.value = place
        sp.edit().putString(KEY_WEATHER_PLACE, place?.let { "${it.first}\t${it.second}\t${it.third}" }).apply()
    }
    private fun readPlace(): Triple<String, Double, Double>? = sp.getString(KEY_WEATHER_PLACE, null)?.split('\t')?.let { p ->
        if (p.size == 3) Triple(p[0], p[1].toDoubleOrNull() ?: return@let null, p[2].toDoubleOrNull() ?: return@let null) else null
    }

    fun setStackHeight(dp: Int) { _stackHeight.value = dp; sp.edit().putInt(KEY_STACK_HEIGHT, dp).apply() }
    fun setStackSeconds(s: Int) { _stackSeconds.value = s; sp.edit().putInt(KEY_STACK_SECONDS, s).apply() }

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
    private val _wallpaperPlace = MutableStateFlow(sp.getString(KEY_WP_PLACE, null))
    /** Where the wallpaper photo was taken, if known. */
    val wallpaperPlace: StateFlow<String?> = _wallpaperPlace.asStateFlow()
    fun setWallpaperPlace(place: String?) { _wallpaperPlace.value = place; sp.edit().putString(KEY_WP_PLACE, place).apply() }
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
    /** Move a widget up or down among the widgets on its own page. */
    fun moveWidget(id: Int, by: Int) {
        val list = _widgets.value.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return
        val samePage = list.indices.filter { list[it].page == list[i].page }
        val at = samePage.indexOf(i)
        val j = samePage.getOrNull(at + by) ?: return
        val t = list[i]; list[i] = list[j]; list[j] = t
        saveWidgets(list)
    }
    private fun saveWidgets(list: List<HomeWidget>) {
        _widgets.value = list
        sp.edit().putString(KEY_WIDGETS, list.joinToString(SEP) { "${it.id}$FIELD${it.heightDp}$FIELD${it.page}" }).apply()
    }
    private fun readWidgets(): List<HomeWidget> = sp.getString(KEY_WIDGETS, null).orEmpty().split(SEP).mapNotNull { line ->
        val parts = line.split(FIELD)
        val id = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
        HomeWidget(id, parts.getOrNull(1)?.toIntOrNull() ?: 160, parts.getOrNull(2)?.toIntOrNull() ?: 0)
    }

    fun hideStatus(id: String) = setHidden(_hiddenStatus.value + id)
    fun showStatus(id: String) = setHidden(_hiddenStatus.value - id)
    private fun setHidden(set: Set<String>) {
        _hiddenStatus.value = set
        sp.edit().putStringSet(KEY_HIDDEN, set).apply()
    }

    /** Put [key] at [index] (replacing what's there), or at the end when [index] is past the last app. */
    fun setDockSlot(index: Int, key: String) {
        val list = _dock.value.filter { it != key }.toMutableList()
        if (index in list.indices) list[index] = key else list += key
        saveDock(list)
    }
    fun addToDock(key: String) { if (key !in _dock.value) saveDock(_dock.value + key) }
    fun removeFromDock(key: String) = saveDock(_dock.value - key)
    fun moveInDock(key: String, by: Int) {
        val list = _dock.value.toMutableList()
        val i = list.indexOf(key); val j = i + by
        if (i < 0 || j !in list.indices) return
        list.add(j, list.removeAt(i)); saveDock(list)
    }
    private fun saveDock(list: List<String>) { _dock.value = list; sp.edit().putString(KEY_DOCK, list.joinToString(SEP)).apply() }

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

    // Older versions kept four fixed places, with empty strings for empty ones.
    private fun readDock(): List<String> = sp.getString(KEY_DOCK, null).orEmpty().split(SEP).filter { it.isNotBlank() }

    private fun readLaunches(): Map<String, Launch> =
        sp.getString(KEY_LAUNCHES, null).orEmpty().split(SEP).mapNotNull { line ->
            val parts = line.split(FIELD)
            if (parts.size != 3) null else parts[0] to Launch(parts[1].toIntOrNull() ?: 0, parts[2].toLongOrNull() ?: 0L)
        }.toMap()

    companion object {
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
        private const val KEY_PAGES = "pages"
        private const val KEY_RAIL_WIDTH = "rail_width"
        private const val KEY_DOCK_HEIGHT = "dock_height"
        private const val KEY_RAIL_ALPHA = "rail_alpha"
        private const val KEY_CARD_ALPHA = "card_alpha"
        private const val KEY_LIGHT = "light"
        private const val KEY_STACK = "stack"
        private const val KEY_STACK_HEIGHT = "stack_height"
        private const val KEY_STACK_SECONDS = "stack_seconds"
        private const val KEY_STACK_SUGGESTIONS = "stack_suggestions"
        private const val KEY_STACK_ITEMS = "stack_items"
        private const val KEY_WEATHER_PLACE = "weather_place"
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
        private const val KEY_WP_PLACE = "wp_place"
        private const val KEY_LIGHT_MINUTES = "light_minutes"
        private const val KEY_LIGHT_ICON = "light_icon"
        private const val KEY_LIGHT_BRIGHTNESS = "light_brightness"
        private const val SEP = "\n"
        private const val FIELD = "\t"
    }
}
