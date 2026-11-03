package com.harsharede.ember

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Review & clean: the photos you 👎'd as wallpaper, in one grid. Untick any you want to keep, then send the rest to
 * Android's own trash in one go. Android asks first, and trashed photos can be restored from the gallery for 30 days.
 */
class ReviewCleanActivity : ComponentActivity() {
    private var reload by mutableIntStateOf(0)
    private var pending: List<Long> = emptyList()

    private val trash = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            EmberApp.instance.prefs.forgetDisliked(pending)
            Toast.makeText(this, "${pending.size} photos moved to the trash (restorable for 30 days)", Toast.LENGTH_LONG).show()
        }
        pending = emptyList()
        reload++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { EmberTheme { Screen(reload) } }
    }

    private fun moveToTrash(items: List<Pair<Long, Uri>>) {
        if (items.isEmpty()) return
        pending = items.map { it.first }
        runCatching {
            val request = MediaStore.createTrashRequest(contentResolver, items.map { it.second }, true)
            trash.launch(IntentSenderRequest.Builder(request.intentSender).build())
        }.onFailure { Toast.makeText(this, "Couldn't ask Android to trash them", Toast.LENGTH_SHORT).show() }
    }

    @Composable
    private fun Screen(@Suppress("UNUSED_PARAMETER") tick: Int) {
        val photos by produceState(emptyList<Pair<Long, Uri>>(), tick) {
            value = withContext(Dispatchers.IO) { Wallpapers.dislikedUris(this@ReviewCleanActivity) }
        }
        var keep by remember(photos) { mutableStateOf(emptySet<Long>()) }
        val toTrash = photos.filter { it.first !in keep }
        val photoSelector = remember { packageManager.getLaunchIntentForPackage(PHOTO_SELECTOR) }
        Column(Modifier.fillMaxSize().layerBackdrop().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)) {
            Text("Review & clean", style = MaterialTheme.typography.headlineMedium)
            Text(if (photos.isEmpty()) "Nothing to clean. Photos you 👎 as wallpaper collect here."
                else "${photos.size} photos you 👎'd. Tap one to keep it; the rest go to the trash (restorable for 30 days).",
                color = EmberColors.Secondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            LazyVerticalGrid(GridCells.Fixed(3), Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(photos, key = { it.first }) { (id, uri) ->
                    val kept = id in keep
                    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                        .border(if (kept) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                        .clickable { keep = if (kept) keep - id else keep + id }) {
                        Thumb(uri)
                        if (kept) Icon(Icons.Rounded.CheckCircle, "Kept", tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(CircleShape).background(Color.White))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ moveToTrash(toTrash) }, enabled = toTrash.isNotEmpty()) { Text("Move ${toTrash.size} to trash") }
                if (photoSelector != null) OutlinedButton({ startActivity(photoSelector) }) { Text("Open Photo Selector") }
            }
        }
    }

    @Composable
    private fun Thumb(uri: Uri) {
        val bitmap by produceState<ImageBitmap?>(null, uri) {
            value = withContext(Dispatchers.IO) { runCatching { contentResolver.loadThumbnail(uri, Size(320, 320), null).asImageBitmap() }.getOrNull() }
        }
        bitmap?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            ?: Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh))
    }

    companion object {
        /** The companion photo-cleaning app, for burst and duplicate clean-ups. */
        const val PHOTO_SELECTOR = "com.harsharede.photoselector"
        fun open(context: android.content.Context) = context.startActivity(Intent(context, ReviewCleanActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
