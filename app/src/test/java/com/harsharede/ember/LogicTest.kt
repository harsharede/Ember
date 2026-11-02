package com.harsharede.ember

import android.content.ComponentName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class LogicTest {
    private val minute = 60_000L

    private fun at(hour: Int, min: Int = 0, dayOffset: Int = 0): Long = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 2, hour, min, 0); set(Calendar.MILLISECOND, 0); add(Calendar.DAY_OF_YEAR, dayOffset)
    }.timeInMillis

    @Test fun `relative time reads like the shade`() {
        val now = at(18, 0)
        assertEquals("now", relativeTime(now - 20_000, now))
        assertEquals("5m", relativeTime(now - 5 * minute, now))
        assertEquals("2h", relativeTime(now - 120 * minute, now))
        assertEquals("09:30", relativeTime(at(9, 30), now, Locale.UK))
        assertEquals("Yesterday", relativeTime(at(9, 30, dayOffset = -1), now))
    }

    @Test fun `timeline buckets`() {
        val now = at(18, 0)
        assertEquals(TimeBucket.NOW, bucketOf(now - 10 * minute, now))
        assertEquals(TimeBucket.TODAY, bucketOf(at(8), now))
        assertEquals(TimeBucket.YESTERDAY, bucketOf(at(23, dayOffset = -1), now))
        assertEquals(TimeBucket.OLDER, bucketOf(at(12, dayOffset = -3), now))
    }

    @Test fun `drawer files apps under their first letter`() {
        assertEquals("C", drawerLetter("chrome"))
        assertEquals("E", drawerLetter("Édition"))
        assertEquals("#", drawerLetter("1Password"))
    }

    @Test fun `drawer search finds the start of any word`() {
        assertTrue(drawerMatches("Microsoft Teams", "tea"))
        assertTrue(drawerMatches("YouTube Music", "you"))
        assertTrue(drawerMatches("Café Finder", "cafe"))
        assertFalse(drawerMatches("Outlook", "lo"))
        assertTrue(drawerMatches("Anything", ""))
    }

    @Test fun `suggestions favour frequent and recent apps and skip the dock`() {
        // Android classes are stubs in plain unit tests; any UserHandle object will do here.
        val user = android.os.UserHandle(null as android.os.Parcel?)
        fun app(name: String) = AppEntry(name, name, ComponentName("p.$name", "A"), user, work = false)
        val apps = listOf(app("a"), app("b"), app("c"), app("d"))
        val now = 100L * 24 * 60 * minute
        val launches = mapOf(
            "a" to Prefs.Launch(10, now - 30L * 24 * 60 * minute), // often, but a month ago
            "b" to Prefs.Launch(3, now - minute),                    // recently
            "c" to Prefs.Launch(5, now - minute),                    // recently and more often
            "d" to Prefs.Launch(50, now),                            // in the dock
        )
        assertEquals(listOf("c", "b", "a"), suggest(apps, launches, exclude = setOf("d"), now = now).map { it.key })
    }

    @Test fun `wifi bars`() {
        assertEquals(4, wifiBars(-50)); assertEquals(3, wifiBars(-60)); assertEquals(2, wifiBars(-70)); assertEquals(1, wifiBars(-90))
    }

    @Test fun `snooze offers the five choices`() {
        assertEquals(listOf(3, 5, 15, 30, 60), SNOOZE_MINUTES)
        assertEquals("1 hour", snoozeLabel(60)); assertEquals("15 minutes", snoozeLabel(15))
    }

    @Test fun `apps fall into sensible groups`() {
        assertEquals(AppGroup.MESSAGING, groupFor("com.whatsapp", android.content.pm.ApplicationInfo.CATEGORY_SOCIAL))
        assertEquals(AppGroup.GAMES, groupFor("com.example.puzzle", android.content.pm.ApplicationInfo.CATEGORY_GAME))
        assertEquals(AppGroup.PRODUCTIVITY, groupFor("com.microsoft.teams", -1))
        assertEquals(AppGroup.OTHER, groupFor("com.example.unknown", -1))
    }

    @Test fun `light keeps a colour that shows on black`() {
        assertEquals(0xFF2196F3.toInt(), lightColor(0xFF2196F3.toInt(), fallback = 1))
        assertEquals(1, lightColor(0xFF101010.toInt(), fallback = 1))  // too dark to see
        assertEquals(1, lightColor(0, fallback = 1))                    // app set none
    }

    @Test fun `smart stack prefers feeds over search bars and tiny widgets`() {
        val news = stackScore("com.google.android.apps.magazines", "Headlines", 250, 180)
        val reddit = stackScore("com.reddit.frontpage", "Popular posts", 250, 180)
        val youtubeSearch = stackScore("com.google.android.youtube", "Search", 250, 40)
        val clock = stackScore("com.sec.android.app.clockpackage", "Digital clock", 180, 100)
        assertTrue(news > reddit)
        assertTrue(reddit > youtubeSearch)
        assertTrue(youtubeSearch < 4)   // left out of "pick for me"
        assertTrue(clock < 4)
    }

    @Test fun `weather codes read as words`() {
        assertEquals("Clear", weatherLabel(0)); assertEquals("Rain", weatherLabel(63)); assertEquals("Thunderstorm", weatherLabel(95))
        assertEquals("—", weatherLabel(42))
    }

    @Test fun `recent people come from chats, newest first, one per person and app`() {
        fun post(key: String, pkg: String, who: String, time: Long, chat: Boolean = true) =
            FeedPost(key, pkg, who, "hi", time, null, null, null, chat)
        val posts = mapOf(
            "com.whatsapp" to listOf(post("1", "com.whatsapp", "Asha", 300), post("2", "com.whatsapp", "asha", 100), post("3", "com.whatsapp", "Ravi", 200)),
            "com.google.android.youtube" to listOf(post("4", "com.google.android.youtube", "New video", 400, chat = false)),
        )
        assertEquals(listOf("Asha", "Ravi"), recentPeople(posts).map { it.name })
    }

    @Test fun `automatic groups don't lump everything under productivity`() {
        val productivity = android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY
        assertEquals(AppGroup.SYSTEM, groupFor("com.samsung.android.app.galaxyfinder", productivity, "Finder"))
        assertEquals(AppGroup.HEALTH, groupFor("com.example.tracker", productivity, "Fit Tracker"))
        assertEquals(AppGroup.FINANCE, groupFor("com.example.bank", productivity, "My Bank"))
        assertEquals(AppGroup.PRODUCTIVITY, groupFor("com.example.thing", productivity, "Thing"))
        assertEquals(AppGroup.SOCIAL, groupFor("com.facebook.katana", -1, "Facebook"))
        assertEquals(AppGroup.FOOD, groupFor("in.swiggy.android", -1, "Swiggy"))
    }

    @Test fun `AI answers are read in the formats AIs actually use`() {
        val labels = mapOf("com.whatsapp" to "WhatsApp", "com.instagram.android" to "Instagram", "com.reddit.frontpage" to "Reddit",
            "com.spotify.music" to "Spotify", "com.android.chrome" to "Chrome")
        val plain = parseAiGroups("""
            Sure! Here you go:
            Messaging: com.whatsapp
            Social (2): com.instagram.android, com.reddit.frontpage
            **Music**: Spotify
        """.trimIndent(), labels)
        assertEquals(listOf("Messaging", "Social", "Music"), plain.order)
        assertEquals("Social", plain.groups["com.reddit.frontpage"])
        assertEquals("Music", plain.groups["com.spotify.music"])
        assertEquals(listOf("com.android.chrome"), plain.missing)

        val bullets = parseAiGroups("""
            ## Social
            - Instagram (com.instagram.android)
            - com.reddit.frontpage
            **Tools:**
            * Chrome
        """.trimIndent(), labels)
        assertEquals("Social", bullets.groups["com.instagram.android"])
        assertEquals("Social", bullets.groups["com.reddit.frontpage"])
        assertEquals("Tools", bullets.groups["com.android.chrome"])
        assertEquals(emptyMap<String, String>(), parseAiGroups("hello there", labels).groups)
    }

    @Test fun `drawer search does sums`() {
        assertEquals(216.0, evaluateExpression("12x18")!!, 1e-9)
        assertEquals(84.0, evaluateExpression("12*(3+4)")!!, 1e-9)
        assertEquals(1024.0, evaluateExpression("2^10")!!, 1e-9)
        assertEquals(36.0, evaluateExpression("15% of 240")!!, 1e-9)
        assertEquals(264.0, evaluateExpression("240 + 10%")!!, 1e-9)
        assertEquals(2.5, evaluateExpression("10 ÷ 4")!!, 1e-9)
        assertEquals(null, evaluateExpression("whatsapp"))
        assertEquals(null, evaluateExpression("2048"))
        assertEquals(null, evaluateExpression("7-zip"))
        assertEquals(null, evaluateExpression("1/0"))
    }

    @Test fun `drawer search converts units`() {
        assertEquals("10 km = 6.21 mi", convertUnits("10 km in mi"))
        assertEquals("100 f = 37.78 c", convertUnits("100 f to c"))
        assertEquals("1 kg = 2.2 lb", convertUnits("1 kg to lb"))
        assertEquals(null, convertUnits("10 km in kg"))
        assertEquals(null, convertUnits("spotify"))
    }

    @Test fun `on this day finds the same date in earlier years`() {
        fun at(y: Int, m: Int, d: Int) = Calendar.getInstance().apply { set(y, m, d, 12, 0, 0) }.timeInMillis
        assertEquals(3, yearsAgoToday(at(2023, Calendar.NOVEMBER, 2), at(2026, Calendar.NOVEMBER, 2)))
        assertEquals(0, yearsAgoToday(at(2023, Calendar.NOVEMBER, 3), at(2026, Calendar.NOVEMBER, 2)))
        assertEquals(0, yearsAgoToday(at(2026, Calendar.NOVEMBER, 2), at(2026, Calendar.NOVEMBER, 2)))
        assertEquals("1 year ago today", memoryLabel(1)); assertEquals(null, memoryLabel(0))
    }

    @Test fun `screen time adds up time in front, ending at pauses and screen off`() {
        val events = listOf(
            Triple(1_000L, "a", 1), Triple(61_000L, "b", 1),   // a: 60 s, then b comes to the front
            Triple(121_000L, "b", 2),                          // b: 60 s
            Triple(181_000L, "a", 1), Triple(241_000L, "", 3), // a: 60 s more, then the screen goes off
        )
        val t = foregroundTime(events, 0L, 300_000L)
        assertEquals(120_000L, t["a"]); assertEquals(60_000L, t["b"])
        assertEquals("2 h 14 min", formatDuration((2 * 60 + 14) * 60_000L)); assertEquals("45 min", formatDuration(45 * 60_000L))
    }

    @Test fun `weather effects match the forecast`() {
        assertEquals(WeatherKind.RAIN, weatherKind(63, true)); assertEquals(WeatherKind.STARS, weatherKind(0, false))
        assertEquals(WeatherKind.CLOUDS, weatherKind(3, true)); assertEquals(WeatherKind.STORM, weatherKind(95, true))
        assertEquals(WeatherKind.NONE, weatherKind(42, true))
    }
}
