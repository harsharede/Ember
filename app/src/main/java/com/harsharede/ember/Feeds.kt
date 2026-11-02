package com.harsharede.ember

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One post an app announced in a notification: a new video, a Reddit reply, a headline, a message. */
data class FeedPost(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val time: Long,
    val picture: ImageBitmap?,
    val avatar: ImageBitmap?,
    val intent: PendingIntent?,
    val conversation: Boolean,
)

/**
 * App feeds for the Smart Stack, built from apps' notifications, so YouTube, Instagram, Reddit or a news app can have a
 * card even without a widget. Ember keeps the last few posts per app (also after the notification is dismissed), in
 * memory only. Pictures are kept only for apps whose feed is in the stack, and only the newest few, to stay light.
 */
object Feeds {
    private const val PER_APP = 12
    private const val PICTURES_PER_APP = 3
    private const val MAX_APPS = 40

    private val _posts = MutableStateFlow<Map<String, List<FeedPost>>>(emptyMap())
    /** Newest first, per package. */
    val posts: StateFlow<Map<String, List<FeedPost>>> = _posts.asStateFlow()

    /** Apps that are good feed material even before they've posted anything. */
    val KNOWN_FEEDS = listOf(
        "com.google.android.youtube", "com.instagram.android", "com.facebook.katana", "com.reddit.frontpage",
        "com.twitter.android", "com.instagram.barcelona", "com.linkedin.android", "com.google.android.apps.magazines",
        "com.nis.app", "com.sec.android.app.samsungnews", "flipboard.app", "com.google.android.apps.youtube.music",
    )

    fun record(context: Context, sbn: StatusBarNotification, item: NotifItem) {
        val n = sbn.notification
        val extras = n.extras
        val conversation = n.category == Notification.CATEGORY_MESSAGE || extras.containsKey(Notification.EXTRA_MESSAGES)
        val inStack = "feed:${sbn.packageName}" in EmberApp.instance.prefs.stackItems.value
        val picture = if (inStack && !conversation) runCatching { bigPicture(context, extras) }.getOrNull() else null
        val avatar = runCatching { n.getLargeIcon()?.loadDrawable(context)?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull()
        val post = FeedPost(sbn.key, sbn.packageName, item.title, item.text, sbn.postTime, picture, avatar, n.contentIntent, conversation)
        val map = _posts.value.toMutableMap()
        val list = (listOf(post) + map[sbn.packageName].orEmpty().filter { it.key != post.key })
            .take(PER_APP)
            .mapIndexed { i, p -> if (i >= PICTURES_PER_APP && p.picture != null) p.copy(picture = null) else p }
        map[sbn.packageName] = list
        if (map.size > MAX_APPS) map.entries.sortedBy { it.value.firstOrNull()?.time ?: 0 }.take(map.size - MAX_APPS).forEach { map.remove(it.key) }
        _posts.value = map
    }

    @Suppress("DEPRECATION")
    private fun bigPicture(context: Context, extras: android.os.Bundle): ImageBitmap? {
        val bitmap = (extras.get(Notification.EXTRA_PICTURE) as? Bitmap)
            ?: (extras.getParcelable<Icon>(Notification.EXTRA_PICTURE_ICON))?.loadDrawable(context)?.toBitmap()
            ?: return null
        // A thumbnail is plenty for a card.
        val w = 480
        val scaled = if (bitmap.width > w) Bitmap.createScaledBitmap(bitmap, w, (bitmap.height * w / bitmap.width).coerceAtLeast(1), true) else bitmap
        return scaled.asImageBitmap()
    }
}

/** Someone the user talks to: from recent chat notifications, or a starred contact. */
data class PersonEntry(val name: String, val avatar: ImageBitmap?, val packageName: String?, val intent: PendingIntent?, val contactUri: android.net.Uri?, val time: Long)

/** Recent chats, newest first, one per person and app. */
fun recentPeople(posts: Map<String, List<FeedPost>>, limit: Int = 8): List<PersonEntry> =
    posts.values.flatten().filter { it.conversation && it.title.isNotBlank() }
        .sortedByDescending { it.time }
        .distinctBy { it.packageName + "|" + it.title.lowercase() }
        .take(limit)
        .map { PersonEntry(it.title, it.avatar, it.packageName, it.intent, null, it.time) }
