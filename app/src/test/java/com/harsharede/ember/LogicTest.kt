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
}
