package com.harsharede.ember

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** One notification, flattened to what Ember shows, plus what it needs to open, reply and snooze. */
data class NotifItem(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val color: Int,
    val clearable: Boolean,
    val contentIntent: PendingIntent?,
    val reply: ReplyAction?,
) {
    data class ReplyAction(val label: String, val intent: PendingIntent, val inputs: Array<RemoteInput>) {
        override fun equals(other: Any?) = other is ReplyAction && other.intent == intent
        override fun hashCode() = intent.hashCode()
    }
}

/** The live list of notifications, shared by Home, the notification page and the pill. */
object NotificationStore {
    private val _items = MutableStateFlow<List<NotifItem>>(emptyList())
    /** Newest first. Ongoing ones (music, navigation, downloads) and group summaries are left out. */
    val items: StateFlow<List<NotifItem>> = _items.asStateFlow()

    private val _connected = MutableStateFlow(false)
    /** Whether Ember has notification access right now. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _arrivals = MutableSharedFlow<NotifItem>(extraBufferCapacity = 16)
    /** A notification that should alert: new, or updated by its app to alert again. Drives the pill. */
    val arrivals: SharedFlow<NotifItem> = _arrivals.asSharedFlow()

    internal fun setConnected(on: Boolean) { _connected.value = on; if (!on) _items.value = emptyList() }

    internal fun replaceAll(list: List<NotifItem>) { _items.value = list.sortedByDescending { it.postTime } }

    internal fun upsert(item: NotifItem, alerts: Boolean) {
        _items.value = (listOf(item) + _items.value.filter { it.key != item.key }).sortedByDescending { it.postTime }
        if (alerts) _arrivals.tryEmit(item)
    }

    internal fun remove(key: String) { _items.value = _items.value.filter { it.key != key } }

    fun dismiss(key: String) { EmberNotificationListener.current?.dismiss(key) }

    /** Hide it for [minutes]; Android brings it back (and Ember alerts again) when the time is up. */
    fun snooze(key: String, minutes: Int) { EmberNotificationListener.current?.snooze(key, minutes * 60_000L) }

    /** Open what the notification points at. Returns false if the app gave it nothing to open. */
    fun open(context: Context, item: NotifItem): Boolean {
        val intent = item.contentIntent ?: return false
        return runCatching { intent.send(context, 0, null, null, null, null, sendOptions()); true }.getOrDefault(false)
    }

    /** Reply from Ember: fills in the app's own reply field and fires its reply action, like the shade's inline reply. */
    fun reply(context: Context, item: NotifItem, text: String): Boolean {
        val reply = item.reply ?: return false
        val results = Bundle()
        reply.inputs.forEach { results.putCharSequence(it.resultKey, text) }
        val fill = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        RemoteInput.addResultsToIntent(reply.inputs, fill, results)
        return runCatching { reply.intent.send(context, 0, fill, null, null, null, sendOptions()); true }.getOrDefault(false)
    }

    /** Android 14+ wants the sender to say it may start the app's screen; Ember is in front when the user taps. */
    private fun sendOptions(): Bundle? =
        if (Build.VERSION.SDK_INT >= 34) ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle()
        else null
}

/** Turns Android's notification into Ember's, or null for ones Ember doesn't list. */
internal fun StatusBarNotification.toItem(selfPackage: String, label: (String) -> String): NotifItem? {
    val n = notification
    if (packageName == selfPackage) return null
    if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
    if (isOngoing && n.category != Notification.CATEGORY_CALL) return null
    val extras = n.extras
    val title = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
        ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG) ?: extras.getCharSequence(Notification.EXTRA_TITLE)
    val text = lastMessage(extras) ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT)
        ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
    if (title.isNullOrBlank() && text.isNullOrBlank()) return null
    val reply = n.actions?.firstNotNullOfOrNull { action ->
        val inputs = action.remoteInputs?.filter { it.allowFreeFormInput }?.toTypedArray()
        if (inputs.isNullOrEmpty() || action.actionIntent == null) null
        else NotifItem.ReplyAction(action.title?.toString() ?: "Reply", action.actionIntent, inputs)
    }
    return NotifItem(
        key = key, packageName = packageName, appLabel = label(packageName),
        title = title?.toString().orEmpty(), text = text?.toString().orEmpty(),
        postTime = postTime, color = n.color, clearable = isClearable,
        contentIntent = n.contentIntent, reply = reply,
    )
}

/** Chat apps put the conversation in EXTRA_MESSAGES; the last one, with its sender, is the useful line. */
private fun lastMessage(extras: Bundle): CharSequence? {
    @Suppress("DEPRECATION")
    val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return null
    val last = messages.lastOrNull() as? Bundle ?: return null
    val text = last.getCharSequence("text") ?: return null
    val isGroup = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION)
    val sender = last.getCharSequence("sender")
    return if (isGroup && !sender.isNullOrBlank()) "$sender: $text" else text
}
