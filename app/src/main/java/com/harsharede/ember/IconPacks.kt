package com.harsharede.ember

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Icon packs from the Play Store. A pack lists, in its appfilter.xml, which drawable to use for which app; Ember reads
 * that once and draws the pack's icon where it has one, and the app's own icon everywhere else.
 */
object IconPacks {
    data class Pack(val packageName: String, val label: String)

    /** What icon pack apps announce themselves with (Nova, ADW, Apex, Go and others all read the same format). */
    private val ACTIONS = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.teslacoilsw.launcher.THEME",
        "org.adw.ActivityStarter.THEMES", "com.anddoes.launcher.THEME", "com.gau.go.launcherex.theme", "com.fede.launcher.THEME_ICONPACK")

    private var loaded: String? = null
    private var resources: Resources? = null
    private var map: Map<String, String> = emptyMap()

    fun installed(context: Context): List<Pack> {
        val pm = context.packageManager
        return ACTIONS.flatMap { action -> runCatching { pm.queryIntentActivities(Intent(action), 0) }.getOrDefault(emptyList()) }
            .map { it.activityInfo.packageName }.distinct()
            .map { pkg -> Pack(pkg, runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)) }
            .sortedBy { it.label.lowercase() }
    }

    /** The pack's drawable for [component], or null when the pack doesn't cover it. Call off the main thread. */
    @Synchronized
    fun drawable(context: Context, pack: String, component: ComponentName): Drawable? {
        if (loaded != pack) load(context, pack)
        val res = resources ?: return null
        val name = map[component.packageName + "/" + component.className] ?: map[component.packageName] ?: return null
        val id = res.getIdentifier(name, "drawable", pack).takeIf { it != 0 } ?: res.getIdentifier(name, "mipmap", pack).takeIf { it != 0 } ?: return null
        return runCatching { res.getDrawable(id, null) }.getOrNull()
    }

    private fun load(context: Context, pack: String) {
        loaded = pack
        map = emptyMap()
        resources = runCatching { context.packageManager.getResourcesForApplication(pack) }.getOrNull() ?: return
        val res = resources!!
        val parser: XmlPullParser = runCatching {
            val id = res.getIdentifier("appfilter", "xml", pack)
            if (id != 0) res.getXml(id) else XmlPullParserFactory.newInstance().newPullParser().apply { setInput(res.assets.open("appfilter.xml"), "UTF-8") }
        }.getOrNull() ?: return
        map = runCatching { parseAppFilter(parser) }.getOrDefault(emptyMap())
    }

    /** "ComponentInfo{com.whatsapp/com.whatsapp.Main}" → "com.whatsapp/com.whatsapp.Main", plus the package alone as a fallback. */
    private fun parseAppFilter(parser: XmlPullParser): Map<String, String> {
        val out = HashMap<String, String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "item") {
                val component = parser.getAttributeValue(null, "component").orEmpty()
                val drawable = parser.getAttributeValue(null, "drawable").orEmpty()
                val inner = component.substringAfter("{", "").substringBefore("}", "")
                if (inner.contains('/') && drawable.isNotBlank()) {
                    val pkg = inner.substringBefore('/')
                    val cls = inner.substringAfter('/').let { if (it.startsWith(".")) pkg + it else it }
                    out["$pkg/$cls"] = drawable
                    out.putIfAbsent(pkg, drawable)
                }
            }
            event = parser.next()
        }
        return out
    }
}
