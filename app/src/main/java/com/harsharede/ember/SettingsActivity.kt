package com.harsharede.ember

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Setup and preferences: what Ember needs from Android, the status icons, and the pill. */
class SettingsActivity : ComponentActivity() {
    // Bumped on every return to this screen, so the permission rows re-check what the user just changed.
    private var refresh by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { EmberTheme { SettingsScreen(refresh) } }
    }

    override fun onResume() { super.onResume(); refresh++ }

    @Composable
    private fun SettingsScreen(@Suppress("UNUSED_PARAMETER") tick: Int) {
        val prefs = EmberApp.instance.prefs
        val pill by prefs.pillEnabled.collectAsStateWithLifecycle()
        val hideBar by prefs.hideStatusBar.collectAsStateWithLifecycle()
        val hidden by prefs.hiddenStatus.collectAsStateWithLifecycle()
        val roles = getSystemService(RoleManager::class.java)
        val isHome = roles.isRoleHeld(RoleManager.ROLE_HOME)
        val listener = ComponentName(this, EmberNotificationListener::class.java).flattenToString()
        val hasAccess = Settings.Secure.getString(contentResolver, "enabled_notification_listeners").orEmpty().split(':').contains(listener)
        val canOverlay = Settings.canDrawOverlays(this)

        Column(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Ember", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)

            Section("Setup")
            Step("Use Ember as your Home app", isHome) {
                startActivity(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
            }
            Step("Notification access", hasAccess,
                "For the notification count, the notification page, reply, reminders and the pill.") {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listener))
            }
            Step("Display over other apps", canOverlay, "Lets the glowing pill appear while you use other apps.") {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }

            Section("Notification pill")
            Toggle("Glow a pill at the top left for new notifications", pill) { prefs.setPill(it) }
            Note("To stop Android's own banners covering the screen too, open Settings › Notifications › " +
                "Notification pop-up style and turn pop-ups off (on Samsung phones), or turn off \"Pop on screen\" per app.")
            Row(Modifier.fillMaxWidth().clip().clickable {
                runCatching { startActivity(Intent("android.settings.NOTIFICATION_SETTINGS")) }
                    .onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            }.padding(14.dp)) { Text("Open Android notification settings", color = EmberColors.EmberSoft) }

            Section("Home")
            Toggle("Hide Android's status bar on Home (the rail shows it instead)", hideBar) { prefs.setHideStatusBar(it) }

            Section("Status icons")
            Note("Swipe an icon sideways in the rail to hide it. Turn it back on here.")
            StatusIcon.entries.forEach { icon ->
                Toggle(icon.label, icon.id !in hidden) { on -> if (on) prefs.showStatus(icon.id) else prefs.hideStatus(icon.id) }
            }

            Section("About")
            Note("Ember ${runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()}. " +
                "No ads, no tracking, no internet permission: everything stays on your phone.")
        }
    }

    @Composable private fun Section(title: String) =
        Text(title, color = EmberColors.Ember, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp))

    @Composable private fun Note(text: String) = Text(text, color = EmberColors.Secondary, fontSize = 13.sp)

    @Composable
    private fun Step(title: String, done: Boolean, detail: String? = null, onClick: () -> Unit) {
        Row(Modifier.fillMaxWidth().clip().clickable(enabled = !done, onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 16.sp)
                if (detail != null) Text(detail, color = EmberColors.Secondary, fontSize = 13.sp)
            }
            Text(if (done) "On" else "Turn on", color = if (done) Color(0xFF34C759) else EmberColors.EmberSoft, fontWeight = FontWeight.SemiBold)
        }
    }

    @Composable
    private fun Toggle(title: String, on: Boolean, onChange: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth().clip().clickable { onChange(!on) }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(end = 12.dp))
            Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = EmberColors.Ember, checkedThumbColor = Color.Black))
        }
    }

    private fun Modifier.clip() = this.glass(RoundedCornerShape(18.dp))
}
