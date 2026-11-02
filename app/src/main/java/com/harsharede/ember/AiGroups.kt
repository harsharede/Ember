package com.harsharede.ember

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Sorting the drawer with an AI the user already uses (Claude, ChatGPT, Gemini…): Ember writes the app list with
 * instructions, the user sends it to the AI, and brings the answer back by sharing it to Ember or pasting it. Nothing
 * is sent anywhere by Ember itself, and only app names and package names are in the list.
 */
object AiGroups {
    /** The list and instructions for the AI. Package names keep the answer unambiguous. */
    fun prompt(apps: List<AppEntry>): String {
        val list = apps.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }.joinToString("\n") { "${it.label} — ${it.packageName}" }
        return """
            |Group these Android apps for my phone's app drawer into 8 to 14 clear groups, for example Social, Messaging,
            |Work, Money, Shopping, Travel, Photos & Video, Music & Audio, Entertainment, News & Reading, Health & Fitness,
            |Food, Utilities, System, Games. Use short group names (one or two words). Put every app in exactly one group.
            |
            |Reply ONLY with one line per group, in this exact format, using the package names exactly as written below:
            |Group name: package.one, package.two, package.three
            |
            |Apps (name — package):
            |$list
        """.trimMargin()
    }

    /** Put the prompt on the clipboard and start listening for the answer (share it back to Ember). */
    fun copy(context: Context, apps: List<AppEntry>) {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("App list for AI", prompt(apps)))
        listenForAnswer(context, true)
        Toast.makeText(context, "Copied. Paste it into your AI app.", Toast.LENGTH_SHORT).show()
    }

    /** Send the prompt straight to an AI app through the share sheet. */
    fun share(context: Context, apps: List<AppEntry>) {
        listenForAnswer(context, true)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, prompt(apps)),
            "Send the app list to an AI").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun clipboardText(context: Context): String? =
        context.getSystemService(ClipboardManager::class.java).primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()

    /**
     * "Ember: apply app groups" appears in the share sheet only while an answer is expected, so it doesn't sit in
     * every share menu the rest of the time.
     */
    fun listenForAnswer(context: Context, on: Boolean) {
        runCatching {
            context.packageManager.setComponentEnabledSetting(ComponentName(context, AiGroupsActivity::class.java),
                if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP)
        }
    }

    fun apply(context: Context, result: Parsed) {
        EmberApp.instance.prefs.replaceAppGroups(result.groups, result.order)
        listenForAnswer(context, false)
        Toast.makeText(context, "${result.groups.size} apps in ${result.order.size} groups", Toast.LENGTH_SHORT).show()
    }

    data class Parsed(val groups: Map<String, String>, val order: List<String>, val missing: List<String>)
}

private val PACKAGE_PATTERN = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+")

/**
 * Reads an AI's answer. It copes with what AIs actually send: "Group: a, b" lines, markdown bold or headings, a group
 * on its own line with the apps as bullets under it, app names instead of package names, and chatter before or after.
 * [labels] maps package to app name; anything that matches neither is ignored.
 */
fun parseAiGroups(text: String, labels: Map<String, String>): AiGroups.Parsed {
    // Ember's own list pasted back by mistake is not an answer.
    if ("Reply ONLY with one line per group" in text) return AiGroups.Parsed(emptyMap(), emptyList(), labels.keys.toList())
    val byLabel = labels.entries.associate { it.value.lowercase().trim() to it.key }
    val groups = LinkedHashMap<String, String>()
    val order = ArrayList<String>()
    var current: String? = null
    fun clean(s: String) = s.replace(Regex("[*_`#>]"), "").replace(Regex("\\(\\d+\\)"), "").trim().trimStart('-', '•', '·', '+').trim().trimEnd(':').trim()
    fun appsIn(s: String): List<String> {
        val byPackage = PACKAGE_PATTERN.findAll(s).map { it.value }.filter { it in labels }.toList()
        if (byPackage.isNotEmpty()) return byPackage
        return s.split(',', ';', '|').map { clean(it).lowercase() }.mapNotNull { byLabel[it] }
    }
    fun add(group: String, apps: List<String>) {
        if (apps.isEmpty()) return
        if (group !in order) order += group
        apps.forEach { if (it !in groups) groups[it] = group }
    }
    for (raw in text.lines()) {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("```")) continue
        val colon = line.indexOf(':')
        val head = if (colon > 0) clean(line.substring(0, colon)) else ""
        val rest = if (colon > 0) line.substring(colon + 1) else ""
        val restApps = if (colon > 0) appsIn(rest) else emptyList()
        when {
            // "Social: com.instagram.android, com.reddit.frontpage"
            colon > 0 && head.isNotEmpty() && head.length <= 40 && restApps.isNotEmpty() && PACKAGE_PATTERN.matchEntire(head) == null -> {
                add(head, restApps); current = head
            }
            // A heading on its own ("## Social", "**Social:**"), with bullets to follow.
            appsIn(line).isEmpty() && clean(line).length in 1..40 && (colon == line.length - 1 || line.startsWith("#") || line.startsWith("**")) -> current = clean(line)
            // "- com.instagram.android" or "- Instagram" under the current heading.
            current != null -> add(current!!, appsIn(line))
        }
    }
    val missing = labels.keys.filter { it !in groups }
    return AiGroups.Parsed(groups, order, missing)
}

/** The steps, as a sheet over the drawer: send the list, then bring the answer back. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiGroupsSheet(apps: List<AppEntry>, onClose: () -> Unit) {
    val context = LocalContext.current
    var preview by remember { mutableStateOf<AiGroups.Parsed?>(null) }
    val labels = remember(apps) { apps.associate { it.packageName to it.label } }
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val p = preview
            if (p == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                    Text("  Sort apps with AI", style = MaterialTheme.typography.titleLarge)
                }
                Text("1. Send your app list (names and package names only) to Claude, ChatGPT or Gemini.", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ AiGroups.share(context, apps) }) { Icon(Icons.AutoMirrored.Rounded.Send, null, Modifier.size(18.dp)); Text("  Share") }
                    OutlinedButton({ AiGroups.copy(context, apps) }) { Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp)); Text("  Copy") }
                }
                Text("2. Bring the answer back: in the AI app, share its reply to \"Ember: apply app groups\", or copy the reply and paste it here.",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                FilledTonalButton({
                    val text = AiGroups.clipboardText(context)
                    val parsed = text?.let { parseAiGroups(it, labels) }
                    if (parsed == null || parsed.groups.isEmpty()) Toast.makeText(context, "Copy the AI's reply first (not the list Ember made)", Toast.LENGTH_SHORT).show()
                    else preview = parsed
                }) { Icon(Icons.Rounded.ContentPaste, null, Modifier.size(18.dp)); Text("  Paste answer") }
                TextButton({ EmberApp.instance.prefs.resetAppGroups(); onClose() }) { Text("Go back to automatic groups") }
            } else GroupsPreview(p, labels, onApply = { AiGroups.apply(context, p); onClose() }, onCancel = { preview = null })
        }
    }
}

/** What the answer would do, before it's applied. */
@Composable
fun GroupsPreview(parsed: AiGroups.Parsed, labels: Map<String, String>, onApply: () -> Unit, onCancel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${parsed.order.size} groups, ${parsed.groups.size} apps", style = MaterialTheme.typography.titleLarge)
        LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(parsed.order) { group ->
                val names = parsed.groups.filterValues { it == group }.keys.map { labels[it] ?: it }.sorted()
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp)).padding(12.dp)) {
                    Text("$group · ${names.size}", fontWeight = FontWeight.SemiBold)
                    Text(names.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = EmberColors.Secondary, maxLines = 3)
                }
            }
        }
        if (parsed.missing.isNotEmpty()) Text("${parsed.missing.size} apps weren't in the answer and keep their automatic group.",
            style = MaterialTheme.typography.bodySmall, color = EmberColors.Secondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onApply) { Text("Apply") }
            TextButton(onCancel) { Text("Cancel") }
        }
    }
}

/** The share target: an AI's reply shared to Ember lands here, shows what it would do, and applies on confirm. */
class AiGroupsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        setContent {
            EmberTheme {
                val apps by EmberApp.instance.apps.apps.collectAsStateWithLifecycle()
                val labels = remember(apps) { apps.associate { it.packageName to it.label } }
                val parsed = remember(labels, text) { if (labels.isEmpty()) null else parseAiGroups(text, labels) }
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp)) {
                    Text("Apply app groups", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 12.dp))
                    when {
                        parsed == null -> Text("Reading your apps…")
                        parsed.groups.isEmpty() -> {
                            Text("That doesn't look like a grouping of your apps. Share the AI's reply to the list Ember gave it.")
                            TextButton({ finish() }) { Text("Close") }
                        }
                        else -> GroupsPreview(parsed, labels, onApply = { AiGroups.apply(this@AiGroupsActivity, parsed); finish() }, onCancel = { finish() })
                    }
                }
            }
        }
    }
}
