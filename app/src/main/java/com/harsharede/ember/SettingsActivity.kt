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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    private val photoAccess = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh++
        if (Wallpapers.hasPhotoAccess(this)) startRotating()
        else Toast.makeText(this, "Ember needs your photos to make wallpapers from them", Toast.LENGTH_LONG).show()
    }

    private fun startRotating() {
        val prefs = EmberApp.instance.prefs
        prefs.setWallpaperRotate(true)
        Wallpapers.schedule(this)
        Wallpapers.changeNow(this)
        Toast.makeText(this, "Picking a photo for your wallpaper…", Toast.LENGTH_SHORT).show()
    }

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
        val light by prefs.lightEnabled.collectAsStateWithLifecycle()
        val lightMinutes by prefs.lightMinutes.collectAsStateWithLifecycle()
        val lightIcon by prefs.lightIcon.collectAsStateWithLifecycle()
        val lightBrightness by prefs.lightBrightness.collectAsStateWithLifecycle()
        val rotate by prefs.wallpaperRotate.collectAsStateWithLifecycle()
        val every by prefs.wallpaperEvery.collectAsStateWithLifecycle()
        val wpLock by prefs.wallpaperLock.collectAsStateWithLifecycle()
        val cameraOnly by prefs.wallpaperCameraOnly.collectAsStateWithLifecycle()
        val wpCard by prefs.wallpaperCard.collectAsStateWithLifecycle()
        val stackSeconds by prefs.stackSeconds.collectAsStateWithLifecycle()
        val onThisDay by prefs.onThisDay.collectAsStateWithLifecycle()
        val lightCycle by prefs.lightCycle.collectAsStateWithLifecycle()
        val lightMusic by prefs.lightMusic.collectAsStateWithLifecycle()
        val chargingLight by prefs.chargingLight.collectAsStateWithLifecycle()
        val limits by prefs.limits.collectAsStateWithLifecycle()
        val usage by ScreenTime.today.collectAsStateWithLifecycle()
        val animIcons by prefs.animIcons.collectAsStateWithLifecycle()
        val animCards by prefs.animCards.collectAsStateWithLifecycle()
        val iconStyle by prefs.iconStyle.collectAsStateWithLifecycle()
        val cardStyle by prefs.cardStyle.collectAsStateWithLifecycle()
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
                Group("Animations") {
                    val styles = EntranceStyle.entries.map { it.ordinal to it.label }
                    Toggle("Dock icons come back into place", "When you return to Home, one after another. Off: they just stay put.", animIcons) { prefs.setAnimIcons(it) }
                    if (animIcons) Choice("Dock icons", styles, iconStyle) { prefs.setIconStyle(it) }
                    Toggle("Home cards come back into place", "The date, Smart Stack and widgets. Off: no animation.", animCards) { prefs.setAnimCards(it) }
                    if (animCards) Choice("Home cards", styles, cardStyle) { prefs.setCardStyle(it) }
                    Text("Drop: falls into place and lands with a spring. Scatter: starts scattered and tilted, then falls into line. " +
                        "Float: rises gently. Pop: grows with a springy pop. Go back to Home to see it.", color = EmberColors.Secondary,
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                }

                // Android 13+ blocks notification access for apps installed from a file until this is allowed.
                if (!hasAccess && Build.VERSION.SDK_INT >= 33) Group("Notification access greyed out?") {
                    Text("Android blocks it for apps installed from a file. Open App info, tap ⋮ (top right), choose " +
                        "\"Allow restricted settings\", confirm, then come back and turn on Notification access.",
                        color = EmberColors.Secondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                    Row_("Open App info") { open(appInfo()) }
                }

                Group("Lock screen light") {
                    Toggle("Notification light", "When something arrives with the screen off, the screen wakes black with only a " +
                        "glowing pill at the top left. Tap to see the lock screen, swipe up to unlock into your notifications.", light) { prefs.setLight(it) }
                    if (light) {
                        Choice("Keep glowing for", listOf(15 to "15 min", 30 to "30 min", 60 to "1 hour", 180 to "3 hours"), lightMinutes) { prefs.setLightMinutes(it) }
                        Choice("Brightness", listOf(0 to "Low", 1 to "Medium", 2 to "High"), lightBrightness) { prefs.setLightBrightness(it) }
                        Toggle("Show app icons in the light", "Off: just a coloured light, nothing to read from across the room", lightIcon) { prefs.setLightIcon(it) }
                        Toggle("Cycle app colours", "With several apps waiting, each blink takes the next app's colour", lightCycle) { prefs.setLightCycle(it) }
                        Toggle("Music bars", "Little bouncing bars in the light while something plays", lightMusic) { prefs.setLightMusic(it) }
                        Toggle("Charging light", "Plugging in with the screen off shows a green light with the battery level", chargingLight) { prefs.setChargingLight(it) }
                        if (!canOverlay) Text("Needs \"Display over other apps\" (above) to wake the screen.", color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                    }
                }

                Group("Smart Stack") {
                    Choice("Next widget every", listOf(5 to "5 s", 7 to "7 s", 10 to "10 s"), stackSeconds) { prefs.setStackSeconds(it) }
                    Text("It only moves while Home is on screen, and pauses for a few seconds after you touch it. " +
                        "Choose what's in it with the pencil under the stack on Home.", color = EmberColors.Secondary,
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                }

                Group("Wallpaper") {
                    Toggle("Wallpaper from your photos", "Ember picks sharp photos and favours portrait shots with a blurred " +
                        "background, cropped around the subject. Nothing leaves your phone.", rotate) { on ->
                        if (!on) { prefs.setWallpaperRotate(false); Wallpapers.schedule(this@SettingsActivity) }
                        else if (Wallpapers.hasPhotoAccess(this@SettingsActivity)) startRotating()
                        else photoAccess.launch(Wallpapers.photoPermissions())
                    }
                    if (rotate) {
                        Choice("Change it", listOf(Wallpapers.EVERY_30_MIN to "Every 30 min", Wallpapers.THREE_A_DAY to "3 times a day"), every) {
                            prefs.setWallpaperEvery(it); Wallpapers.schedule(this@SettingsActivity)
                        }
                        Toggle("Lock screen too", null, wpLock) { prefs.setWallpaperLock(it) }
                        Toggle("Camera photos only", "Leave out screenshots, downloads and chat images", cameraOnly) { prefs.setWallpaperCameraOnly(it) }
                        Toggle("On this day", "Once a day, a photo taken on this date in an earlier year (\"3 years ago today\")", onThisDay) { prefs.setOnThisDay(it) }
                        Toggle("Wallpaper button on Home", "A tiny button in the bottom-left corner: tap for like, new photo and dislike", wpCard) { prefs.setWallpaperCard(it) }
                        Row_("New photo now") { Wallpapers.changeNow(this@SettingsActivity) }
                        Row_("Forget likes and dislikes") {
                            prefs.forgetPhotoChoices()
                            Toast.makeText(this@SettingsActivity, "Done", Toast.LENGTH_SHORT).show()
                        }
                        Text("Timed changes wait until the screen is off or you're on Home: a new wallpaper changes the colours, " +
                            "and Android restarts open apps' screens for that.", color = EmberColors.Secondary,
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                    }
                }

                Group("Screen time") {
                    if (!ScreenTime.hasAccess(this@SettingsActivity)) {
                        Step("Usage access", false, "For the Screen time card and limits. Android shows how long each app was on screen; nothing leaves the phone.") {
                            open(ScreenTime.accessIntent())
                        }
                    } else {
                        androidx.compose.runtime.LaunchedEffect(Unit) { ScreenTime.refresh(this@SettingsActivity) }
                        Text("Set a daily limit and Ember sends one gentle reminder when you pass it.", color = EmberColors.Secondary,
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                        val shown = (usage.keys.take(10) + limits.keys).distinct()
                        shown.forEach { pkg -> LimitRow(pkg, usage[pkg] ?: 0L, limits[pkg]) }
                        if (shown.isEmpty()) Text("No app time recorded yet today.", color = EmberColors.Secondary, modifier = Modifier.padding(16.dp))
                    }
                }

                Group("Icons") {
                    val packs = remember { IconPacks.installed(this@SettingsActivity) }
                    val pack by prefs.iconPack.collectAsStateWithLifecycle()
                    Toggle("Apps' own icons", null, pack == null) { if (it) { prefs.setIconPack(null); EmberApp.instance.apps.reloadIcons() } }
                    packs.forEach { p ->
                        Toggle(p.label, "Icon pack", pack == p.packageName) { on ->
                            prefs.setIconPack(if (on) p.packageName else null); EmberApp.instance.apps.reloadIcons()
                        }
                    }
                    if (packs.isEmpty()) Text("No icon packs installed. Any pack made for Nova, ADW or similar launchers works.",
                        color = EmberColors.Secondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                    Row_("Find icon packs on the Play Store") {
                        open(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=icon%20pack&c=apps")),
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=icon%20pack&c=apps")))
                    }
                }

                Group("Look") {
                    Text("Rail width, dock height and transparency: hold an empty spot on Home › Customize look. " +
                        "More pages for widgets: hold Home › Add page.", color = EmberColors.Secondary,
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                    Toggle("Colours from wallpaper", "Material You: the accent and surfaces follow your wallpaper", wallpaper) { prefs.setWallpaperColors(it) }
                    Toggle("Hide Android's status bar on Home", "The rail shows the same things", hideBar) { prefs.setHideStatusBar(it) }
                }

                Group("App drawer") {
                    Toggle("Open the keyboard", "Start typing as soon as the drawer opens", keyboard) { prefs.setDrawerKeyboard(it) }
                }

                Group("Notifications") {
                    Toggle("Pill while using the phone", "A light glows at the top left when something arrives", pill) { prefs.setPill(it) }
                    Toggle("Show ones that can't be dismissed", "System and \"always on\" notices, in their own folded section", persistent) { prefs.setShowPersistent(it) }
                    Text("So Android's own banners don't cover the screen as well: Notifications › Notification pop-up style, and turn pop-ups off.",
                        color = EmberColors.Secondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                    Row_("Open Android notification settings") {
                        open(Intent("android.settings.NOTIFICATION_SETTINGS"), Intent(Settings.ACTION_SETTINGS))
                    }
                }

                Group("Status icons") {
                    val showOff by prefs.statusShowOff.collectAsStateWithLifecycle()
                    Toggle("Show switched-off icons, dimmed", "So Wi-Fi, Bluetooth, location and the like can be tapped back on", showOff) { prefs.setStatusShowOff(it) }
                    Text("Tap an icon for its quick control (Wi-Fi and mobile open Android's internet panel; Do Not Disturb and the ringer change straight away), " +
                        "hold it for its settings page. Swipe an icon sideways on the rail to hide it, or switch it here.", color = EmberColors.Secondary,
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                    StatusIcon.entries.forEach { icon ->
                        Toggle(icon.label, null, icon.id !in hidden) { on -> if (on) prefs.showStatus(icon.id) else prefs.hideStatus(icon.id) }
                    }
                }

                Group("About") {
                    Text("Ember ${runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()}. " +
                        "No ads, no tracking, no accounts. The internet is used only by the Weather card, if you add it.",
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

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Choice(title: String, options: List<Pair<Int, String>>, selected: Int, onPick: (Int) -> Unit) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { i, (value, label) ->
                    SegmentedButton(selected == value, { onPick(value) }, SegmentedButtonDefaults.itemShape(i, options.size)) { Text(label, maxLines = 1) }
                }
            }
        }
    }

    @Composable
    private fun LimitRow(pkg: String, usedMs: Long, limit: Int?) {
        var menu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        ListItem(
            headlineContent = { Text(EmberApp.instance.apps.labelForPackage(pkg)) },
            supportingContent = { Text(formatDuration(usedMs) + " today") },
            leadingContent = { PackageIcon(pkg, 32.dp) },
            trailingContent = {
                androidx.compose.foundation.layout.Box {
                    androidx.compose.material3.TextButton({ menu = true }) { Text(if (limit == null) "No limit" else "Limit ${formatDuration(limit * 60_000L)}") }
                    androidx.compose.material3.DropdownMenu(menu, { menu = false }) {
                        listOf(null, 15, 30, 60, 120, 180).forEach { m ->
                            androidx.compose.material3.DropdownMenuItem({ Text(if (m == null) "No limit" else formatDuration(m * 60_000L)) }, {
                                menu = false
                                EmberApp.instance.prefs.setLimit(pkg, m)
                                ScreenTime.schedule(this@SettingsActivity)
                                if (m != null && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                                    notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            })
                        }
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
