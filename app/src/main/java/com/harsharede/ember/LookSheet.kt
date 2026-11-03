package com.harsharede.ember

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    val wallpaperPage by prefs.wallpaperPage.collectAsStateWithLifecycle()
    val dockRound by prefs.dockRound.collectAsStateWithLifecycle()
    val dockCorner by prefs.dockCorner.collectAsStateWithLifecycle()
    val animIcons by prefs.animIcons.collectAsStateWithLifecycle()
    val animCards by prefs.animCards.collectAsStateWithLifecycle()
    val parallax by prefs.parallax.collectAsStateWithLifecycle()
    val weatherFx by prefs.weatherEffects.collectAsStateWithLifecycle()
    val headerWeather by prefs.headerWeather.collectAsStateWithLifecycle()
    val place by prefs.weatherPlace.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var askPlace by remember { mutableStateOf(false) }
    val location = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { prefs.setWeatherPlace(null); WeatherStore.refresh(context, force = true) }
    }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        scrimColor = Color.Transparent, containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .96f),
        contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(horizontal = 24.dp).padding(bottom = 12.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Customize look", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton({
                    prefs.setRailWidth(92); prefs.setDockHeight(1f); prefs.setRailAlpha(.8f); prefs.setCardAlpha(.8f)
                    prefs.setDockRound(true); prefs.setDockCorner(18); prefs.setDateStyle(0); prefs.setClockStyle(0)
                }) { Text("Reset") }
            }
            // Date and clock styles, each with a live preview.
            val dateStyle by prefs.dateStyle.collectAsStateWithLifecycle()
            val clockStyle by prefs.clockStyle.collectAsStateWithLifecycle()
            val now = remember { System.currentTimeMillis() }
            Text("Date style", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Classic", "Big day", "One line", "Compact").forEachIndexed { i, name ->
                    StylePreview(name, dateStyle == i, { prefs.setDateStyle(i) }) {
                        Box(Modifier.graphicsLayer { scaleX = .55f; scaleY = .55f; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, .5f) }
                            .wrapContentWidth(Alignment.Start, unbounded = true)) { DateStyled(now, i) }
                    }
                }
            }
            Text("Clock style", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Digital", "Stacked", "12-hour").forEachIndexed { i, name ->
                    StylePreview(name, clockStyle == i, { prefs.setClockStyle(i) }, width = 96.dp) { RailClock(now, i, 18.sp) }
                }
            }
            Text("Dock shape", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(dockRound, { prefs.setDockRound(true) }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Round") }
                SegmentedButton(!dockRound, { prefs.setDockRound(false) }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Rounded rectangle") }
            }
            if (!dockRound) Setting("Corner size", "${dockCorner} dp") {
                Slider(dockCorner.toFloat(), { prefs.setDockCorner(it.roundToInt()) }, valueRange = 4f..36f)
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
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Wallpaper-only page", style = MaterialTheme.typography.bodyLarge)
                    Text("The last page, with nothing but the wallpaper", style = MaterialTheme.typography.bodyMedium, color = EmberColors.Secondary)
                }
                Switch(wallpaperPage, { prefs.setWallpaperPage(it) })
            }
            Text("Wallpaper", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 18.dp))
            SwitchRow("Parallax", "The wallpaper pans a little as you swipe between pages", parallax) { prefs.setParallax(it) }
            SwitchRow("Weather on the wallpaper", "Rain, snow, clouds, fog, sun or stars to match the forecast", weatherFx) { prefs.setWeatherEffects(it) }
            SwitchRow("Weather beside the day", "Icons for the next five hours next to the day's name", headerWeather) { prefs.setHeaderWeather(it) }
            val hasLocation = CardData.granted(context, android.Manifest.permission.ACCESS_COARSE_LOCATION)
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Weather place", style = MaterialTheme.typography.bodyLarge)
                    Text(place?.first ?: if (hasLocation) "My location" else "Not set yet: the weather needs a place",
                        style = MaterialTheme.typography.bodyMedium, color = if (place == null && !hasLocation) MaterialTheme.colorScheme.error else EmberColors.Secondary)
                }
                TextButton({ askPlace = true }) { Text("Change") }
            }
            Text("Coming back to Home", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 18.dp))
            SwitchRow("Dock icons come back into place", "One after another; pick the style in Settings › Animations", animIcons) { prefs.setAnimIcons(it) }
            if (askPlace) WeatherPlaceDialog(
                onLocation = {
                    askPlace = false
                    if (hasLocation) { prefs.setWeatherPlace(null); WeatherStore.refresh(context, force = true) }
                    else location.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                },
                onCity = { p -> askPlace = false; prefs.setWeatherPlace(p); WeatherStore.refresh(context, force = true) },
                onDismiss = { askPlace = false },
            )
            SwitchRow("Home cards come back into place", "The date, Smart Stack and widgets", animCards) { prefs.setAnimCards(it) }
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

@Composable
private fun SwitchRow(title: String, detail: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = EmberColors.Secondary)
        }
        Switch(on, onChange)
    }
}

/** A small tappable preview of one style, outlined in the accent when it's the chosen one. */
@Composable
private fun StylePreview(name: String, selected: Boolean, onPick: () -> Unit, width: androidx.compose.ui.unit.Dp = 200.dp,
                         content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(width).height(96.dp).clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = .45f))
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = .12f), RoundedCornerShape(18.dp))
            .clickable(onClick = onPick).padding(10.dp), contentAlignment = Alignment.CenterStart) { content() }
        Text(name, style = MaterialTheme.typography.labelMedium, color = if (selected) MaterialTheme.colorScheme.primary else EmberColors.Secondary,
            modifier = Modifier.padding(top = 4.dp))
    }
}
