package com.harsharede.ember

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.widget.Toast

/**
 * What a status icon does when tapped. Android doesn't let apps switch Wi-Fi or Bluetooth themselves any more, so those
 * open Android's own quick panel or page; Do Not Disturb and the ringer Ember can change directly (Do Not Disturb once
 * the user allows it). A long press always opens the full settings page.
 */
object StatusActions {
    fun tap(context: Context, icon: StatusIcon, status: DeviceStatus) {
        when (icon) {
            StatusIcon.WIFI -> open(context, Intent(Settings.Panel.ACTION_WIFI), Intent(Settings.ACTION_WIFI_SETTINGS))
            StatusIcon.CELLULAR -> open(context, Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY), Intent(Settings.ACTION_WIRELESS_SETTINGS))
            StatusIcon.VPN -> open(context, Intent(Settings.ACTION_VPN_SETTINGS))
            StatusIcon.BLUETOOTH -> open(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            StatusIcon.AIRPLANE -> open(context, Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
            StatusIcon.LOCATION -> open(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            StatusIcon.POWER_SAVE -> open(context, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
            StatusIcon.BATTERY -> open(context, Intent(Intent.ACTION_POWER_USAGE_SUMMARY), Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
            StatusIcon.DND -> toggleDnd(context, status.dnd)
            StatusIcon.RINGER -> cycleRinger(context)
            StatusIcon.NOTIFICATIONS -> Unit
        }
    }

    fun longPress(context: Context, icon: StatusIcon) {
        when (icon) {
            StatusIcon.WIFI -> open(context, Intent(Settings.ACTION_WIFI_SETTINGS))
            StatusIcon.CELLULAR -> open(context, Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS))
            StatusIcon.DND -> open(context, Intent("android.settings.ZEN_MODE_SETTINGS"), Intent(Settings.ACTION_SOUND_SETTINGS))
            StatusIcon.RINGER -> open(context, Intent(Settings.ACTION_SOUND_SETTINGS))
            StatusIcon.BATTERY, StatusIcon.POWER_SAVE -> open(context, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
            else -> tap(context, icon, DeviceStatus())
        }
    }

    /** Do Not Disturb off (or on, priority only). Needs Do Not Disturb access, asked for the first time. */
    private fun toggleDnd(context: Context, on: Boolean) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            Toast.makeText(context, "Allow Ember to turn Do Not Disturb on and off", Toast.LENGTH_LONG).show()
            open(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return
        }
        runCatching { nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY) }
    }

    /** Ring → vibrate → silent → ring. Silent can need Do Not Disturb access; without it, Android's volume panel opens. */
    private fun cycleRinger(context: Context) {
        val audio = context.getSystemService(AudioManager::class.java)
        val next = when (audio.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        runCatching { audio.ringerMode = next }.onFailure { open(context, Intent(Settings.Panel.ACTION_VOLUME), Intent(Settings.ACTION_SOUND_SETTINGS)) }
        val label = when (audio.ringerMode) { AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"; AudioManager.RINGER_MODE_SILENT -> "Silent"; else -> "Ring" }
        Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
    }

    private fun open(context: Context, vararg intents: Intent) {
        for (intent in intents) if (runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }
}
