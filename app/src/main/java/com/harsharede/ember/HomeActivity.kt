package com.harsharede.ember

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

/** Ember's Home: the Today column, the status rail and dock; swipe up for apps, down for notifications. */
class HomeActivity : ComponentActivity() {
    private val requests = HomeRequests()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EmberTheme {
                HomeScreen(requests, onAddWidget = { info, target -> Widgets.add(this, info, target) },
                    onAutoPick = { SmartStack.autoPick(this, lifecycleScope) })
            }
        }
        if (intent?.action == ACTION_OPEN_NOTIFICATIONS) requests.openNotifications++
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                EmberApp.instance.prefs.hideStatusBar.collect { hide -> applyStatusBar(hide) }
            }
        }
    }

    /** The rail shows what the status bar would, so Home can hide Android's; a swipe from the top still brings it back. */
    private fun applyStatusBar(hide: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hide) controller.hide(WindowInsetsCompat.Type.statusBars()) else controller.show(WindowInsetsCompat.Type.statusBars())
    }

    override fun onStart() {
        super.onStart()
        // Widgets only update while Home is visible, like every launcher.
        runCatching { Widgets.host.startListening() }
    }

    override fun onStop() {
        super.onStop()
        runCatching { Widgets.host.stopListening() }
    }

    @Deprecated("Widget binding and setup screens still report back here")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        Widgets.onResult(this, requestCode, resultCode)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // The Home button while already home: back to the plain Home screen.
        when (intent.action) {
            ACTION_OPEN_NOTIFICATIONS -> requests.openNotifications++
            Intent.ACTION_MAIN -> requests.closeAll++
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        Wallpapers.onHomeVisible(this)
        Widgets.nudge(this)
        CardData.refresh(this)
    }
    override fun onPause() { super.onPause(); resumed = false }

    companion object {
        const val ACTION_OPEN_NOTIFICATIONS = "com.harsharede.ember.OPEN_NOTIFICATIONS"

        /** True while Home is in front, so the pill stays out (the rail already shows new notifications). */
        @Volatile var resumed = false
            private set
    }
}
