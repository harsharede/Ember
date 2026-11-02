package com.harsharede.ember

import android.app.Application

class EmberApp : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var apps: AppsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        apps = AppsRepository(this)
        Widgets.init(this)
        Wallpapers.init(this)
        ScreenTime.schedule(this)
        // A timed wallpaper change waits for the screen to go off, so it never lands in the middle of another app.
        registerReceiver(object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) = Wallpapers.onScreenOff(context)
        }, android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_OFF), android.content.Context.RECEIVER_EXPORTED)
    }

    companion object {
        lateinit var instance: EmberApp
            private set
    }
}
