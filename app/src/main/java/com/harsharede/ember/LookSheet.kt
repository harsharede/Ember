package com.harsharede.ember

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

/**
 * Customize look: the rail's width, how tall the dock may grow, and how see-through the rail and the cards are. Home
 * stays visible behind with no dimming, so each change shows as the slider moves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LookSheet(onClose: () -> Unit) {
    val prefs = EmberApp.instance.prefs
    val railWidth by prefs.railWidth.collectAsStateWithLifecycle()
    val dockHeight by prefs.dockHeight.collectAsStateWithLifecycle()
    val railAlpha by prefs.railAlpha.collectAsStateWithLifecycle()
    val cardAlpha by prefs.cardAlpha.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        scrimColor = Color.Transparent, containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .96f)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 12.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Customize look", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton({
                    prefs.setRailWidth(92); prefs.setDockHeight(1f); prefs.setRailAlpha(.8f); prefs.setCardAlpha(.8f)
                }) { Text("Reset") }
            }
            Setting("Rail width", "${railWidth} dp") {
                Slider(railWidth.toFloat(), { prefs.setRailWidth(it.roundToInt()) }, valueRange = 64f..128f, steps = 15)
            }
            Setting("Dock height", if (dockHeight >= .99f) "All the room left" else "Up to ${(dockHeight * 100).roundToInt()}% of the rail") {
                Slider(dockHeight, { prefs.setDockHeight(it) }, valueRange = .3f..1f)
            }
            Setting("Rail transparency", "${((1 - railAlpha) * 100).roundToInt()}%") {
                Slider(1 - railAlpha, { prefs.setRailAlpha(1 - it) }, valueRange = 0f..1f)
            }
            Setting("Card transparency", "${((1 - cardAlpha) * 100).roundToInt()}%") {
                Slider(1 - cardAlpha, { prefs.setCardAlpha(1 - it) }, valueRange = 0f..1f)
            }
        }
    }
}

@Composable
private fun Setting(title: String, value: String, slider: @Composable () -> Unit) {
    Column(Modifier.padding(top = 12.dp)) {
        Row {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = EmberColors.Secondary)
        }
        slider()
    }
}
