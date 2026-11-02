package com.harsharede.ember

import android.app.Notification
import android.app.NotificationManager
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Ember's notification access. Android binds this once the user allows it in Settings; it feeds [NotificationStore]
 * and the pill, and does the dismissing and snoozing (only a listener may).
 */
class EmberNotificationListener : NotificationListenerService() {
    private var pill: PillOverlay? = null
    /** Plugged in with the screen off: the charging light, if it's switched on. Unplugged: it goes out. */
    private val power = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            when (intent.action) {
                android.content.Intent.ACTION_POWER_CONNECTED -> if (EmberApp.instance.prefs.chargingLight.value &&
                    !getSystemService(PowerManager::class.java).isInteractive) Light.showCharging(context)
                android.content.Intent.ACTION_POWER_DISCONNECTED -> Light.stopCharging()
            }
        }
    }
    private val ranking = Ranking()

    override fun onListenerConnected() {
        current = this
        NotificationStore.setConnected(true)
        pill = PillOverlay(this).also { it.start() }
        NowPlaying.start(this, android.content.ComponentName(this, EmberNotificationListener::class.java))
        runCatching {
            registerReceiver(power, android.content.IntentFilter().apply {
                addAction(android.content.Intent.ACTION_POWER_CONNECTED); addAction(android.content.Intent.ACTION_POWER_DISCONNECTED)
            }, android.content.Context.RECEIVER_EXPORTED)
        }
        val apps = EmberApp.instance.apps
        val map = runCatching { currentRanking }.getOrNull()
        val list = runCatching { activeNotifications }.getOrNull().orEmpty()
            .mapNotNull { it.toItem(packageName, apps::labelForPackage, rankingFor(it.key, map)) }
        NotificationStore.replaceAll(list)
        runCatching { activeNotifications }.getOrNull().orEmpty().sortedBy { it.postTime }.forEach { sbn ->
            list.firstOrNull { it.key == sbn.key }?.let { item -> runCatching { Feeds.record(this, sbn, item) } }
        }
    }

    override fun onListenerDisconnected() {
        if (current === this) current = null
        pill?.stop(); pill = null
        NowPlaying.stop()
        runCatching { unregisterReceiver(power) }
        NotificationStore.setConnected(false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        val ranking = rankingFor(sbn.key, rankingMap)
        val item = sbn.toItem(packageName, EmberApp.instance.apps::labelForPackage, ranking)
        if (item == null) { NotificationStore.remove(sbn.key); return }
        runCatching { Feeds.record(this, sbn, item) }
        val existing = NotificationStore.items.value.firstOrNull { it.key == item.key }
        val alerting = !item.silent && !item.persistent && alerts(sbn, existing, rankingMap)
        NotificationStore.upsert(item, alerts = alerting)
        // Screen off (or the light already on): the lock-screen light, unless Do Not Disturb is holding this one back.
        if (alerting && !focusSilences(item.packageName) && EmberApp.instance.prefs.lightEnabled.value && ranking?.matchesInterruptionFilter() != false &&
            (Light.showing || !getSystemService(PowerManager::class.java).isInteractive)) Light.show(this, item)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        NotificationStore.remove(sbn.key)
    }

    /** Like the system: a new notification alerts, an update alerts again unless the app asked it to stay quiet. */
    private fun alerts(sbn: StatusBarNotification, existing: NotifItem?, rankingMap: RankingMap?): Boolean {
        if (existing != null && sbn.notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0) return false
        if (existing != null && existing.postTime == sbn.postTime) return false
        if (rankingMap != null && rankingMap.getRanking(sbn.key, ranking)) {
            if (ranking.importance < NotificationManager.IMPORTANCE_DEFAULT) return false
            if (ranking.isSuspended) return false
        }
        return true
    }

    /** A fresh Ranking per notification: the item keeps reading its channel after the map moves on. */
    private fun rankingFor(key: String, map: RankingMap?): Ranking? {
        if (map == null) return null
        val r = Ranking()
        return if (map.getRanking(key, r)) r else null
    }

    fun dismiss(key: String) { runCatching { cancelNotification(key) } }
    fun snooze(key: String, millis: Long) { runCatching { snoozeNotification(key, millis) } }

    companion object {
        @Volatile var current: EmberNotificationListener? = null
            private set
    }
}
