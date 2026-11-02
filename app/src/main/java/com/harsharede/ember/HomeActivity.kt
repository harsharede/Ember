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
    private val layers = HomeLayers()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { EmberTheme { HomeScreen(layers) } }
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // The Home button while already home: back to the plain Home screen.
        if (intent.action == Intent.ACTION_MAIN) layers.closeAll()
    }

    override fun onResume() { super.onResume(); resumed = true }
    override fun onPause() { super.onPause(); resumed = false }

    companion object {
        /** True while Home is in front, so the pill stays out (the rail already shows new notifications). */
        @Volatile var resumed = false
            private set
    }
}
