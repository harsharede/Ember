package com.harsharede.ember

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.SizeF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android widgets on Home. Ember hosts them the way every launcher does: it asks Android for a widget id, binds the
 * provider to it (Android asks the user the first time for each app), runs the widget's own setup screen if it has
 * one, then shows it in a stack on the Today column.
 */
object Widgets {
    private const val HOST_ID = 0x454D
    const val REQUEST_BIND = 4101
    const val REQUEST_CONFIGURE = 4102

    lateinit var host: AppWidgetHost
        private set
    lateinit var manager: AppWidgetManager
        private set
    private var pendingId = -1

    fun init(context: Context) {
        manager = AppWidgetManager.getInstance(context)
        host = AppWidgetHost(context, HOST_ID)
    }

    /** Start adding [info]: bind it, then configure it, then put it on Home. Results come back to [onResult]. */
    fun add(activity: Activity, info: AppWidgetProviderInfo) {
        val id = host.allocateAppWidgetId()
        pendingId = id
        if (manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)) configure(activity, id, info)
        else runCatching {
            @Suppress("DEPRECATION")
            activity.startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile), REQUEST_BIND)
        }.onFailure { cancel(id) }
    }

    fun onResult(activity: Activity, requestCode: Int, resultCode: Int) {
        val id = pendingId
        if (id < 0) return
        when (requestCode) {
            REQUEST_BIND -> {
                val info = manager.getAppWidgetInfo(id)
                if (resultCode == Activity.RESULT_OK && info != null) configure(activity, id, info) else cancel(id)
            }
            REQUEST_CONFIGURE -> if (resultCode == Activity.RESULT_OK) place(id) else cancel(id)
        }
    }

    private fun configure(activity: Activity, id: Int, info: AppWidgetProviderInfo) {
        val optional = info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
        if (info.configure == null || optional) { place(id); return }
        runCatching { host.startAppWidgetConfigureActivityForResult(activity, id, 0, REQUEST_CONFIGURE, null) }
            .onFailure { place(id) }
    }

    private fun place(id: Int) {
        pendingId = -1
        val info = manager.getAppWidgetInfo(id)
        // Start at the height the widget asks for, within reason; it can be made taller or shorter in edit mode.
        val dp = info?.minHeight?.let { px -> (px / EmberApp.instance.resources.displayMetrics.density).toInt() } ?: 160
        EmberApp.instance.prefs.addWidget(Prefs.HomeWidget(id, dp.coerceIn(90, 420)))
    }

    private fun cancel(id: Int) { pendingId = -1; runCatching { host.deleteAppWidgetId(id) } }

    fun remove(id: Int) {
        EmberApp.instance.prefs.removeWidget(id)
        runCatching { host.deleteAppWidgetId(id) }
    }
}

/** One widget on Home. In edit mode it shows controls to move, resize and remove it. */
@Composable
fun HomeWidgetView(widget: Prefs.HomeWidget, editing: Boolean, widthDp: Int, modifier: Modifier = Modifier) {
    val info = remember(widget.id) { Widgets.manager.getAppWidgetInfo(widget.id) }
    val prefs = EmberApp.instance.prefs
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(widget.heightDp.dp).clip(RoundedCornerShape(28.dp))) {
            if (info == null) Box(Modifier.fillMaxSize().glass(), contentAlignment = Alignment.Center) {
                Text("This widget's app was removed", color = EmberColors.Secondary)
            } else AndroidView(
                factory = { ctx -> Widgets.host.createView(ctx.applicationContext, widget.id, info).apply { setPadding(0, 0, 0, 0) } },
                update = { view -> resize(view, widthDp, widget.heightDp) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (editing) Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
            FilledTonalIconButton({ prefs.moveWidget(widget.id, -1) }) { Icon(Icons.Rounded.ArrowUpward, "Move up") }
            FilledTonalIconButton({ prefs.moveWidget(widget.id, 1) }) { Icon(Icons.Rounded.ArrowDownward, "Move down") }
            FilledTonalIconButton({ prefs.resizeWidget(widget.id, (widget.heightDp - 40).coerceAtLeast(70)) }) { Icon(Icons.Rounded.UnfoldLess, "Shorter") }
            FilledTonalIconButton({ prefs.resizeWidget(widget.id, (widget.heightDp + 40).coerceAtMost(560)) }) { Icon(Icons.Rounded.UnfoldMore, "Taller") }
            FilledTonalIconButton({ Widgets.remove(widget.id) }) { Icon(Icons.Rounded.Delete, "Remove") }
        }
    }
}

/** Tells the widget how much room it has, so it can choose its own layout (Android 12 responsive widgets). */
private fun resize(view: AppWidgetHostView, widthDp: Int, heightDp: Int) {
    val tag = widthDp to heightDp
    if (view.tag == tag) return
    view.tag = tag
    runCatching { view.updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp.toFloat(), heightDp.toFloat()))) }
}

/** Every widget on the phone, grouped by app, with its preview. Tap one to add it. */
@Composable
fun WidgetPicker(onPick: (AppWidgetProviderInfo) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val providers by produceState(emptyList<AppWidgetProviderInfo>()) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val launcher = context.getSystemService(android.content.pm.LauncherApps::class.java)
            launcher.profiles.flatMap { Widgets.manager.getInstalledProvidersForProfile(it) }
                .sortedWith(compareBy({ EmberApp.instance.apps.labelForPackage(it.provider.packageName).lowercase() }, { it.loadLabel(pm) }))
        }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = .97f)).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Add a widget", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TextButton(onClose) { Text("Done") }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            providers.groupBy { it.provider.packageName }.forEach { (pkg, list) ->
                item(key = "app-$pkg") {
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        PackageIcon(pkg, 28.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(EmberApp.instance.apps.labelForPackage(pkg), style = MaterialTheme.typography.titleMedium)
                    }
                }
                items(list, key = { it.provider.flattenToString() + it.profile.hashCode() }) { info -> WidgetChoice(info) { onPick(info) } }
            }
            if (providers.isEmpty()) item { Text("Loading widgets…", color = EmberColors.Secondary) }
        }
    }
}

@Composable
private fun WidgetChoice(info: AppWidgetProviderInfo, onClick: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val preview by produceState<ImageBitmap?>(null, info) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                (info.loadPreviewImage(context, (density * 160).toInt()) ?: info.loadIcon(context, (density * 160).toInt()))
                    ?.let { d -> d.toBitmap(d.intrinsicWidth.coerceIn(1, 900), d.intrinsicHeight.coerceIn(1, 900)).asImageBitmap() }
            }.getOrNull()
        }
    }
    val label = remember(info) { info.loadLabel(context.packageManager) }
    val cells = "${((info.minWidth / density + 30) / 70).toInt().coerceAtLeast(1)} × ${((info.minHeight / density + 30) / 70).toInt().coerceAtLeast(1)}"
    Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(96.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            preview?.let { Image(it, null, contentScale = ContentScale.Fit, modifier = Modifier.heightIn(max = 96.dp).padding(6.dp)) }
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(cells, color = EmberColors.Secondary, fontSize = 13.sp)
        }
    }
}
