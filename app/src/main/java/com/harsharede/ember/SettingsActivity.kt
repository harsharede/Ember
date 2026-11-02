package com.harsharede.ember

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Setup and preferences: what Ember needs from Android, the look, the drawer, the status icons and the pill. */
class SettingsActivity : ComponentActivity() {
    // Bumped on every return to this screen, so the setup rows re-check what was just changed in Android's settings.
    private var refresh by mutableIntStateOf(0)

    private val homeRole = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { EmberTheme { SettingsScreen(refresh) } }
    }

    override fun onResume() { super.onResume(); refresh++ }

    /** Opens a settings screen, trying each in turn, so a phone without one page still gets somewhere useful. */
    private fun open(vararg intents: Intent) {
        for (intent in intents) {
            if (runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
        }
        Toast.makeText(this, "Couldn't open that settings page", Toast.LENGTH_SHORT).show()
    }

    private fun appInfo() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))

    private fun requestHome() {
        val roles = getSystemService(RoleManager::class.java)
        if (roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
            runCatching { homeRole.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME)) }
                .onFailure { open(Intent(Settings.ACTION_HOME_SETTINGS)) }
        } else open(Intent(Settings.ACTION_HOME_SETTINGS))
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SettingsScreen(@Suppress("UNUSED_PARAMETER") tick: Int) {
        val prefs = EmberApp.instance.prefs
        val pill by prefs.pillEnabled.collectAsStateWithLifecycle()
        val hideBar by prefs.hideStatusBar.collectAsStateWithLifecycle()
        val hidden by prefs.hiddenStatus.collectAsStateWithLifecycle()
        val wallpaper by prefs.wallpaperColors.collectAsStateWithLifecycle()
        val keyboard by prefs.drawerKeyboard.collectAsStateWithLifecycle()
        val persistent by prefs.showPersistent.collectAsStateWithLifecycle()
        val isHome = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)
        val hasAccess = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        val canOverlay = Settings.canDrawOverlays(this)
        val listener = ComponentName(this, EmberNotificationListener::class.java).flattenToString()
        val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

        Scaffold(Modifier.nestedScroll(scroll.nestedScrollConnection), containerColor = MaterialTheme.colorScheme.surface,
            topBar = { LargeTopAppBar({ Text("Ember") }, scrollBehavior = scroll) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {

                Group("Setup") {
                    Step("Use Ember as your Home app", isHome, "Choose Ember on the next screen.") { requestHome() }
                    Step("Notification access", hasAccess, "For the count, the notification page, reply, reminders and the pill.") {
                        open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listener),
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                    Step("Display over other apps", canOverlay, "So the pill can glow while you use other apps.") {
                        open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                    }
                }
                // Android 13+ blocks notification access for apps installed from a file until this is allowed.
                if (!hasAccess && Build.VERSION.SDK_INT >= 33) Group("Notification access greyed out?") {
                    Text("Android blocks it for apps installed from a file. Open App info, tap ⋮ (top right), choose " +
                        "\"Allow restricted settings\", confirm, then come back and turn on Notification access.",
                        color = EmberColors.Secondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                    Row_("Open App info") { open(appInfo()) }
                }

                Group("Look") {
                    Toggle("Colours from wallpaper", "Material You: the accent and surfaces follow your wallpaper", wallpaper) { prefs.setWallpaperColors(it) }
                    Toggle("Hide Android's status bar on Home", "The rail shows the same things", hideBar) { prefs.setHideStatusBar(it) }
                }

                Group("App drawer") {
                    Toggle("Open the keyboard", "Start typing as soon as the drawer opens", keyboard) { prefs.setDrawerKeyboard(it) }
                }

                Group("Notifications") {
                    Toggle("Notification pill", "A light glows at the top left instead of a banner", pill) { prefs.setPill(it) }
                    Toggle("Show ones that can't be dismissed", "System and \"always on\" notices, in their own folded section", persistent) { prefs.setShowPersistent(it) }
                    Text("So Android's own banners don't cover the screen as well: Notifications › Notification pop-up style, and turn pop-ups off.",
                        color = EmberColors.Secondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                    Row_("Open Android notification settings") {
                        open(Intent("android.settings.NOTIFICATION_SETTINGS"), Intent(Settings.ACTION_SETTINGS))
                    }
                }

                Group("Status icons") {
                    Text("Swipe an icon sideways on the rail to hide it, or switch it here.", color = EmberColors.Secondary,
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                    StatusIcon.entries.forEach { icon ->
                        Toggle(icon.label, null, icon.id !in hidden) { on -> if (on) prefs.showStatus(icon.id) else prefs.hideStatus(icon.id) }
                    }
                }

                Group("About") {
                    Text("Ember ${runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()}. " +
                        "No ads, no tracking, no internet permission: everything stays on your phone.",
                        color = EmberColors.Secondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                }
                Text("", Modifier.padding(PaddingValues(bottom = 24.dp)))
            }
        }
    }

    @Composable
    private fun Group(title: String, content: @Composable () -> Unit) {
        Column {
            Text(title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp, top = 8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) { Column { content() } }
        }
    }

    @Composable
    private fun Step(title: String, done: Boolean, detail: String, onClick: () -> Unit) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(if (done) "On" else detail) },
            trailingContent = {
                if (done) Icon(Icons.Rounded.CheckCircle, "On", tint = Color(0xFF34C759))
                else Icon(Icons.Rounded.ChevronRight, null)
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            // Tappable even when done, so it can be checked or changed.
            modifier = Modifier.fillMaxWidth().clickableRow(onClick),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))
    }

    @Composable
    private fun Toggle(title: String, detail: String?, on: Boolean, onChange: (Boolean) -> Unit) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = if (detail != null) ({ Text(detail) }) else null,
            trailingContent = { Switch(on, onChange) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth().clickableRow { onChange(!on) },
        )
    }

    @Composable
    private fun Row_(title: String, onClick: () -> Unit) {
        ListItem(headlineContent = { Text(title, color = MaterialTheme.colorScheme.primary) },
            trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth().clickableRow(onClick))
    }

    private fun Modifier.clickableRow(onClick: () -> Unit) = this.clickable(onClick = onClick)
}
