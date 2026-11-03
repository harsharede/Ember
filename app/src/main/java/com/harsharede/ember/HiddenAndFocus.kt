package com.harsharede.ember

import android.app.Activity
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Unlock with fingerprint, face or the screen lock before showing hidden apps. No screen lock set: opens straight away. */
fun unlockHidden(activity: Activity, onUnlocked: () -> Unit) {
    if (!activity.getSystemService(KeyguardManager::class.java).isDeviceSecure) {
        Toast.makeText(activity, "Set a screen lock to keep hidden apps private", Toast.LENGTH_LONG).show()
        onUnlocked(); return
    }
    runCatching {
        BiometricPrompt.Builder(activity).setTitle("Hidden apps").setSubtitle("Unlock to see them")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
            .authenticate(CancellationSignal(), activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onUnlocked()
            })
    }.onFailure { Toast.makeText(activity, "Couldn't ask for your fingerprint", Toast.LENGTH_SHORT).show() }
}

/** The hidden apps, after unlocking: open one, or show it again. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenAppsSheet(onLaunch: (AppEntry) -> Unit, onClose: () -> Unit) {
    val prefs = EmberApp.instance.prefs
    val hidden by prefs.hiddenApps.collectAsStateWithLifecycle()
    val apps by EmberApp.instance.apps.apps.collectAsStateWithLifecycle()
    val list = remember(hidden, apps) { apps.filter { it.packageName in hidden } }
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("Hidden apps", style = MaterialTheme.typography.titleLarge)
            if (list.isEmpty()) Text("None. Hold an app in the drawer › Hide.", color = EmberColors.Secondary, modifier = Modifier.padding(vertical = 16.dp))
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(list, key = { it.key }) { app ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onLaunch(app); onClose() }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(app, 40.dp)
                        Text(app.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        TextButton({ prefs.unhideApp(app.packageName) }) { Text("Show again") }
                    }
                }
            }
        }
    }
}

/**
 * Focus: put chosen apps away for a while. They leave the drawer, dock and suggestions, and their notifications don't
 * light the pill or the lock-screen light, until the time is up or Focus is ended.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusSheet(onClose: () -> Unit) {
    val prefs = EmberApp.instance.prefs
    val chosen by prefs.focusApps.collectAsStateWithLifecycle()
    val until by prefs.focusUntil.collectAsStateWithLifecycle()
    val apps by EmberApp.instance.apps.apps.collectAsStateWithLifecycle()
    val active = until > System.currentTimeMillis()
    var minutes by remember { mutableIntStateOf(60) }
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Focus", style = MaterialTheme.typography.titleLarge)
            if (active) {
                Text(if (until == Long.MAX_VALUE) "On until you end it" else "On until ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(until))}",
                    style = MaterialTheme.typography.bodyLarge)
                Button({ prefs.setFocusUntil(0); onClose() }) { Text("End Focus") }
                return@Column
            }
            Text("Put these apps away for a while: they leave the drawer, dock and suggestions, and their notifications stay quiet.",
                color = EmberColors.Secondary, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(30 to "30 min", 60 to "1 h", 120 to "2 h", 0 to "Until I end it").forEach { (m, label) ->
                    FilterChip(minutes == m, { minutes = m }, label = { Text(label) })
                }
            }
            // Suggestions from screen time: the apps that took the most of today, not yet chosen.
            val context = androidx.compose.ui.platform.LocalContext.current
            val usage by ScreenTime.today.collectAsStateWithLifecycle()
            androidx.compose.runtime.LaunchedEffect(Unit) { ScreenTime.refresh(context) }
            val launchable = remember(apps) { apps.map { it.packageName }.toSet() }
            val suggested = remember(usage, chosen, launchable) {
                usage.entries.filter { it.key in launchable && it.key !in chosen && it.value >= 15 * 60_000L && !it.key.startsWith("com.android.") }
                    .take(4)
            }
            if (suggested.isNotEmpty()) {
                Text("Suggested from today's screen time", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggested.forEach { (pkg, ms) ->
                        androidx.compose.material3.AssistChip({ prefs.setFocusApp(pkg, true) },
                            label = { Text("${EmberApp.instance.apps.labelForPackage(pkg)} · ${formatDuration(ms)}") },
                            leadingIcon = { PackageIcon(pkg, 18.dp) })
                    }
                }
            } else if (!ScreenTime.hasAccess(context)) androidx.compose.material3.TextButton({ runCatching { context.startActivity(ScreenTime.accessIntent()) } }) {
                Text("Get suggestions from your screen time")
            }
            Text("${chosen.size} apps chosen", style = MaterialTheme.typography.titleSmall)
            LazyColumn(Modifier.heightIn(max = 300.dp)) {
                items(apps.distinctBy { it.packageName }, key = { it.key }) { app ->
                    val on = app.packageName in chosen
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { prefs.setFocusApp(app.packageName, !on) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(app, 32.dp)
                        Text(app.label, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        Checkbox(on, { prefs.setFocusApp(app.packageName, it) })
                    }
                }
            }
            Row {
                Button({
                    prefs.setFocusUntil(if (minutes == 0) Long.MAX_VALUE else System.currentTimeMillis() + minutes * 60_000L); onClose()
                }, enabled = chosen.isNotEmpty()) { Text("Start Focus") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClose) { Text("Cancel") }
            }
        }
    }
}

/** Is Focus keeping [packageName] quiet right now? */
fun focusSilences(packageName: String): Boolean {
    val prefs = EmberApp.instance.prefs
    return prefs.focusActive() && packageName in prefs.focusApps.value
}
