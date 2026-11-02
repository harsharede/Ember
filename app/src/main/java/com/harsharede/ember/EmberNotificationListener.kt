package com.harsharede.ember

import android.app.Notification
import android.app.NotificationManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Ember's notification access. Android binds this once the user allows it in Settings; it feeds [NotificationStore]
 * and the pill, and does the dismissing and snoozing (only a listener may).
 */
class EmberNotificationListener : NotificationListenerService() {
    private var pill: PillOverlay? = null
    private val ranking = Ranking()

    override fun onListenerConnected() {
        current = this
        NotificationStore.setConnected(true)
        pill = PillOverlay(this).also { it.start() }
        val apps = EmberApp.instance.apps
        val map = runCatching { currentRanking }.getOrNull()
        val list = runCatching { activeNotifications }.getOrNull().orEmpty()
            .mapNotNull { it.toItem(packageName, apps::labelForPackage, rankingFor(it.key, map)) }
        NotificationStore.replaceAll(list)
    }

    override fun onListenerDisconnected() {
        if (current === this) current = null
        pill?.stop(); pill = null
        NotificationStore.setConnected(false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        val item = sbn.toItem(packageName, EmberApp.instance.apps::labelForPackage, rankingFor(sbn.key, rankingMap))
        if (item == null) { NotificationStore.remove(sbn.key); return }
        val existing = NotificationStore.items.value.firstOrNull { it.key == item.key }
        NotificationStore.upsert(item, alerts = !item.silent && !item.persistent && alerts(sbn, existing, rankingMap))
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
