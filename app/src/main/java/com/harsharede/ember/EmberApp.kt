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
    }

    companion object {
        lateinit var instance: EmberApp
            private set
    }
}
