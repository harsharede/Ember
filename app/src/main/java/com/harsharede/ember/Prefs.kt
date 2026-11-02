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
        private const val SEP = "\n"
        private const val FIELD = "\t"
    }
}
