package com.harsharede.ember

import android.Manifest
import android.app.WallpaperManager
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.location.Geocoder
import androidx.exifinterface.media.ExifInterface
import java.util.Locale
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.MediaStore
import android.view.Display
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Wallpapers from the user's own photos. Ember scores camera photos (sharp ones first, portrait-mode shots with a
 * blurred background next), keeps what it learns in a small file, and picks a good one at random, more often the ones
 * the user liked and never the ones they disliked. Each photo is cropped to the screen around its sharp subject.
 *
 * Automatic changes wait until the screen is off or Home is in front: a new wallpaper changes the Material You colours,
 * and Android restarts open apps' screens for that, which nobody wants in the middle of something.
 */
object Wallpapers {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()

    data class Current(val id: Long, val takenAt: Long, val liked: Boolean, val place: String? = null, val yearsAgo: Int = 0) {
        val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
    }

    private val _current = MutableStateFlow<Current?>(null)
    /** The photo on the wallpaper now, if Ember put it there. */
    val current: StateFlow<Current?> = _current.asStateFlow()
    private val _changing = MutableStateFlow(false)
    val changing: StateFlow<Boolean> = _changing.asStateFlow()

    private const val WORK = "ember-wallpaper"
    private const val SCORES_FILE = "photo_scores.tsv"
    private const val ANALYSIS_SIZE = 256
    private const val NEW_PER_RUN = 24

    fun init(context: Context) {
        val prefs = EmberApp.instance.prefs
        val id = prefs.wallpaperPhoto.value
        if (id > 0) _current.value = Current(id, prefs.wallpaperTakenAt.value, id in prefs.likedPhotos.value, prefs.wallpaperPlace.value,
            prefs.wallpaperYearsAgo.value)
        schedule(context, restart = false)
        // A wallpaper from before places were shown: look its place up once.
        if (id > 0 && prefs.wallpaperPlace.value == null) lookUpPlace(context)
    }

    fun hasPhotoAccess(context: Context): Boolean {
        fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        return if (Build.VERSION.SDK_INT >= 33) granted(Manifest.permission.READ_MEDIA_IMAGES) ||
            (Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))
        else granted(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun photoPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, Manifest.permission.ACCESS_MEDIA_LOCATION)
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.ACCESS_MEDIA_LOCATION)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_MEDIA_LOCATION)
    }

    /** Android hides where a photo was taken unless this is allowed. */
    fun hasPlaceAccess(context: Context) = context.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** After the place permission is granted: name the current photo's place. */
    fun lookUpPlace(context: Context) {
        val app = context.applicationContext
        scope.launch(Dispatchers.IO) {
            val now = _current.value ?: return@launch
            val place = runCatching { placeOf(app, now.uri) }.getOrNull() ?: return@launch
            EmberApp.instance.prefs.setWallpaperPlace(place)
            if (_current.value?.id == now.id) _current.value = _current.value?.copy(place = place)
        }
    }

    /**
     * Where a photo was taken, as a place name ("Page, Arizona, United States"): the GPS position in the photo, named by
     * Android's geocoder. Null without the permission, without a position in the photo, or without a name for it.
     */
    private fun placeOf(context: Context, uri: Uri): String? {
        if (!hasPlaceAccess(context) || !Geocoder.isPresent()) return null
        val original = MediaStore.setRequireOriginal(uri)
        val latLong = context.contentResolver.openInputStream(original)?.use { ExifInterface(it).latLong } ?: return null
        if (latLong[0] == 0.0 && latLong[1] == 0.0) return null
        @Suppress("DEPRECATION")
        val a = Geocoder(context, Locale.getDefault()).getFromLocation(latLong[0], latLong[1], 1)?.firstOrNull() ?: return null
        return placeName(a.subLocality, a.locality ?: a.subAdminArea, a.adminArea, a.countryName)
    }

    /** (Re)start the timer after a settings change: every 30 minutes, or morning, afternoon and evening. */
    fun schedule(context: Context, restart: Boolean = true) {
        val prefs = EmberApp.instance.prefs
        val work = WorkManager.getInstance(context)
        if (!prefs.wallpaperRotate.value) { work.cancelUniqueWork(WORK); return }
        val request = if (prefs.wallpaperEvery.value == EVERY_30_MIN)
            PeriodicWorkRequestBuilder<WallpaperWorker>(30, TimeUnit.MINUTES).setInitialDelay(30, TimeUnit.MINUTES)
        else PeriodicWorkRequestBuilder<WallpaperWorker>(8, TimeUnit.HOURS)
            .setInitialDelay(delayToNextSlot(System.currentTimeMillis()), TimeUnit.MILLISECONDS)
        // On app start keep the running timer (UPDATE); after a settings change start it afresh.
        work.enqueueUniquePeriodicWork(WORK, if (restart) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.UPDATE,
            request.setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build()).build())
    }

    /** From the timer: change now if nobody's looking at another app, otherwise as soon as they aren't. */
    fun timerFired(context: Context) {
        val power = context.getSystemService(PowerManager::class.java)
        if (!power.isInteractive || HomeActivity.resumed) changeNow(context)
        else EmberApp.instance.prefs.setWallpaperPending(true)
    }

    fun onScreenOff(context: Context) { if (EmberApp.instance.prefs.wallpaperPending.value) changeNow(context) }
    fun onHomeVisible(context: Context) { if (EmberApp.instance.prefs.wallpaperPending.value) changeNow(context) }

    fun like() {
        val now = _current.value ?: return
        val prefs = EmberApp.instance.prefs
        if (now.liked) prefs.unlikePhoto(now.id) else {
            prefs.likePhoto(now.id)
            // A like keeps the photo: a copy goes into Pictures/Ember Memories (the original stays where it is).
            if (prefs.memoriesCopy.value && !prefs.memoryCopied(now.id)) scope.launch(Dispatchers.IO) {
                if (runCatching { copyToMemories(EmberApp.instance, now.uri) }.getOrDefault(false)) prefs.markMemoryCopied(now.id)
            }
        }
        _current.value = now.copy(liked = !now.liked)
    }

    const val MEMORIES_FOLDER = "Ember Memories"

    /** Copy a photo into Pictures/Ember Memories through MediaStore (no extra permission: Ember owns the copy). */
    fun copyToMemories(context: Context, source: Uri): Boolean {
        val resolver = context.contentResolver
        val name = resolver.query(source, arrayOf(MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_TAKEN), null, null, null)?.use { c ->
            if (c.moveToFirst()) Triple(c.getString(0) ?: "memory.jpg", c.getString(1) ?: "image/jpeg", c.getLong(2)) else null
        } ?: return false
        val values = android.content.ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name.first)
            put(MediaStore.Images.Media.MIME_TYPE, name.second)
            if (name.third > 0) put(MediaStore.Images.Media.DATE_TAKEN, name.third)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$MEMORIES_FOLDER")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val target = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return runCatching {
            resolver.openInputStream(source)!!.use { input -> resolver.openOutputStream(target)!!.use { input.copyTo(it) } }
            resolver.update(target, android.content.ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            true
        }.getOrElse { resolver.delete(target, null, null); false }
    }

    /** Photo albums (folders) with how many photos each holds, most first, for Settings. */
    fun albums(context: Context): List<Pair<String, Int>> {
        val counts = HashMap<String, Int>()
        runCatching {
            context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media.BUCKET_DISPLAY_NAME),
                null, null, null)?.use { c -> while (c.moveToNext()) c.getString(0)?.let { counts[it] = (counts[it] ?: 0) + 1 } }
        }
        return counts.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    /** The disliked photos that still exist, for Review & clean. */
    fun dislikedUris(context: Context): List<Pair<Long, Uri>> {
        val ids = EmberApp.instance.prefs.dislikedPhotos.value
        if (ids.isEmpty()) return emptyList()
        val found = ArrayList<Pair<Long, Uri>>()
        context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media._ID} IN (${ids.joinToString(",")})", null, null)?.use { c ->
            while (c.moveToNext()) { val id = c.getLong(0); found += id to ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id) }
        }
        // Gone already (deleted elsewhere): stop remembering them.
        EmberApp.instance.prefs.forgetDisliked(ids - found.map { it.first }.toSet())
        return found
    }

    /** Never this one again, and a new one right away. */
    fun dislike(context: Context) {
        _current.value?.let { EmberApp.instance.prefs.dislikePhoto(it.id) }
        changeNow(context)
    }

    fun changeNow(context: Context) {
        val app = context.applicationContext
        scope.launch {
            if (!lock.tryLock()) return@launch
            _changing.value = true
            try { runCatching { change(app) } } finally { _changing.value = false; lock.unlock() }
        }
    }

    private suspend fun change(context: Context) = withContext(Dispatchers.IO) {
        val prefs = EmberApp.instance.prefs
        prefs.setWallpaperPending(false)
        if (!hasPhotoAccess(context)) return@withContext
        val photos = listPhotos(context, prefs.wallpaperCameraOnly.value, prefs.photoSource.value, prefs.photoAlbums.value, prefs.likedPhotos.value)
        if (photos.isEmpty()) return@withContext
        val scores = loadScores(context)
        val disliked = prefs.dislikedPhotos.value
        val liked = prefs.likedPhotos.value

        // Learn a few more photos each time; the file remembers them, so the choice gets better as it goes.
        val unknown = photos.filter { it.id !in scores && it.id !in disliked }.shuffled().take(NEW_PER_RUN)
        for (photo in unknown) analyze(context, photo)?.let { scores[photo.id] = it }
        saveScores(context, scores, photos.mapTo(HashSet()) { it.id })

        val recent = prefs.recentPhotos.value
        val known = photos.filter { it.id in scores && it.id !in disliked }
        // Leave out the last ones shown, as long as enough are left to choose from.
        val fresh = known.filter { it.id !in recent }.ifEmpty { known }
        // Only the better half competes; within it, the higher the score the likelier.
        val cutoff = fresh.map { scores.getValue(it.id).total }.sorted().let { it.getOrElse(it.size / 2) { 0f } }
        val pool = fresh.filter { scores.getValue(it.id).total >= cutoff || it.id in liked }
        val weight: (Photo) -> Double = { p ->
            val s = scores.getValue(p.id)
            val portrait = if (p.portrait) 1.25 else 1.0
            Math.pow(s.total.toDouble(), 3.0) * portrait * (if (p.id in liked) 3.0 else 1.0)
        }
        // On this day: the first change of the day picks a good photo taken on this date in an earlier year, if any.
        val now = System.currentTimeMillis()
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date(now))
        val memories = if (prefs.onThisDay.value && prefs.memoryDay.value != today)
            known.filter { yearsAgoToday(it.takenAt, now) > 0 && scores.getValue(it.id).total >= .35f } else emptyList()
        val memory = pickWeighted(memories, weight, Random.nextDouble())
        val pick = memory ?: pickWeighted(pool, weight, Random.nextDouble()) ?: return@withContext
        val yearsAgo = if (memory != null) yearsAgoToday(pick.takenAt, now) else 0
        if (memory != null) prefs.setMemoryDay(today)

        val bitmap = render(context, pick, scores.getValue(pick.id)) ?: return@withContext
        // Home's parallax uses a slightly wider copy; the system wallpaper is its middle.
        val which = if (prefs.wallpaperLock.value) WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK else WallpaperManager.FLAG_SYSTEM
        WallpaperManager.getInstance(context).setBitmap(bitmap, null, true, which)
        bitmap.recycle()
        val place = runCatching { placeOf(context, pick.uri) }.getOrNull()
        prefs.setWallpaperPhoto(pick.id, pick.takenAt)
        prefs.setWallpaperPlace(place)
        prefs.setWallpaperYearsAgo(yearsAgo)
        prefs.addRecentPhoto(pick.id, keep = (known.size / 3).coerceIn(1, 60))
        _current.value = Current(pick.id, pick.takenAt, pick.id in liked, place, yearsAgo)
    }

    private data class Photo(val id: Long, val width: Int, val height: Int, val takenAt: Long) {
        val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
        val portrait: Boolean get() = height > width
    }

    /**
     * Photos big enough for the screen, from where the user chose: all camera photos, chosen albums, or only favourites
     * (liked in Ember or starred in the gallery). Screenshots, GIFs and Ember's own Memories copies are always left out.
     */
    private fun listPhotos(context: Context, cameraOnly: Boolean, source: Int, albums: Set<String>, liked: Set<Long>): List<Photo> {
        val out = ArrayList<Photo>()
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.ORIENTATION, MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.MIME_TYPE, MediaStore.Images.Media.BUCKET_DISPLAY_NAME, MediaStore.MediaColumns.IS_FAVORITE)
        context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, null)?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val iW = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val iH = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val iO = c.getColumnIndexOrThrow(MediaStore.Images.Media.ORIENTATION)
            val iT = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val iP = c.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            val iM = c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val iB = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val iF = c.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_FAVORITE)
            while (c.moveToNext()) {
                val path = c.getString(iP).orEmpty().lowercase()
                val mime = c.getString(iM).orEmpty()
                if (mime == "image/gif" || "screenshot" in path) continue
                if (MEMORIES_FOLDER.lowercase() in path) continue
                when (source) {
                    Prefs.PHOTOS_ALBUMS -> if (c.getString(iB) !in albums) continue
                    Prefs.PHOTOS_FAVOURITES -> if (c.getLong(iId) !in liked && c.getInt(iF) != 1) continue
                    else -> if (cameraOnly && !(path.startsWith("dcim/") && !path.contains("screen"))) continue
                }
                var w = c.getInt(iW); var h = c.getInt(iH)
                if (c.getInt(iO) % 180 == 90) { val t = w; w = h; h = t }
                if (minOf(w, h) < 1000) continue
                out += Photo(c.getLong(iId), w, h, c.getLong(iT))
            }
        }
        return out
    }

    private fun analyze(context: Context, photo: Photo): PhotoScore? = runCatching {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, photo.uri)) { decoder, info, _ ->
            val scale = ANALYSIS_SIZE.toFloat() / maxOf(info.size.width, info.size.height)
            decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(3), (info.size.height * scale).toInt().coerceAtLeast(3))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val w = bitmap.width; val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        bitmap.recycle()
        val luma = IntArray(w * h) { i -> val p = pixels[i]; (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000 }
        analyzePhoto(luma, w, h)
    }.getOrNull()

    /**
     * Decoded just large enough, cropped around the subject to a little wider than the screen (saved for Home's parallax),
     * and the middle of that, the screen's shape, returned for the system wallpaper.
     */
    private fun render(context: Context, photo: Photo, score: PhotoScore): Bitmap? = runCatching {
        val (screenW, screenH) = screenSize(context)
        val wideW = (screenW * PARALLAX_WIDTH).toInt()
        val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, photo.uri)) { decoder, info, _ ->
            val w = info.size.width; val h = info.size.height
            val scale = (if (w.toDouble() / h > wideW.toDouble() / screenH) screenH.toDouble() / h else wideW.toDouble() / w).coerceAtMost(1.0)
            decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val (l, t, cw, ch) = wallpaperCrop(decoded.width, decoded.height, wideW, screenH, score.focusX, score.focusY).toList()
        val cropped = Bitmap.createBitmap(decoded, l, t, cw, ch)
        if (cropped !== decoded) decoded.recycle()
        val wide = if (cropped.width == wideW && cropped.height == screenH) cropped
            else Bitmap.createScaledBitmap(cropped, wideW, screenH, true).also { if (it !== cropped) cropped.recycle() }
        runCatching {
            File(context.filesDir, WIDE_FILE).outputStream().use { wide.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            _wideVersion.value++
        }
        val middle = Bitmap.createBitmap(wide, (wideW - screenW) / 2, 0, screenW, screenH)
        if (middle !== wide) wide.recycle()
        middle
    }.getOrNull()

    /** How much wider than the screen the parallax copy is. */
    const val PARALLAX_WIDTH = 1.3f
    private const val WIDE_FILE = "wallpaper_wide.jpg"
    private val _wideVersion = MutableStateFlow(0)
    /** Goes up each time a new wide copy is saved, so Home reloads it. */
    val wideVersion: StateFlow<Int> = _wideVersion.asStateFlow()
    fun wideFile(context: Context): File = File(context.filesDir, WIDE_FILE)

    private fun screenSize(context: Context): Pair<Int, Int> {
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val size = android.graphics.Point()
        @Suppress("DEPRECATION") display.getRealSize(size)
        return minOf(size.x, size.y) to maxOf(size.x, size.y)
    }

    private fun loadScores(context: Context): HashMap<Long, PhotoScore> {
        val map = HashMap<Long, PhotoScore>()
        val file = File(context.filesDir, SCORES_FILE)
        if (!file.exists()) return map
        file.forEachLine { line ->
            val p = line.split('\t')
            if (p.size == 6) runCatching {
                map[p[0].toLong()] = PhotoScore(p[1].toFloat(), p[2].toFloat(), p[3].toFloat(), p[4].toFloat(), p[5].toFloat())
            }
        }
        return map
    }

    /** Only photos that still exist are kept, so deleted ones don't pile up in the file. */
    private fun saveScores(context: Context, scores: Map<Long, PhotoScore>, existing: Set<Long>) {
        val text = scores.entries.filter { it.key in existing }.joinToString("\n") { (id, s) ->
            "$id\t${s.sharpness}\t${s.bokeh}\t${s.exposure}\t${s.focusX}\t${s.focusY}"
        }
        File(context.filesDir, SCORES_FILE).writeText(text)
    }

    const val EVERY_30_MIN = 0
    const val THREE_A_DAY = 1

    /** Morning, afternoon and evening: 08:00, 14:00 and 20:00. */
    fun delayToNextSlot(now: Long): Long {
        val slots = listOf(8, 14, 20)
        for (day in 0..1) for (hour in slots) {
            val t = Calendar.getInstance().apply {
                timeInMillis = now; add(Calendar.DAY_OF_YEAR, day)
                set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            if (t > now) return t - now
        }
        return 8 * 60 * 60_000L
    }
}

class WallpaperWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Wallpapers.timerFired(applicationContext)
        return Result.success()
    }
}

/** "Page, Arizona, United States": the most local name first, no repeats, at most three parts. */
fun placeName(vararg parts: String?): String? =
    parts.filterNotNull().map { it.trim() }.filter { it.isNotEmpty() && !it.first().isDigit() }
        .distinctBy { it.lowercase() }.take(3).joinToString(", ").ifEmpty { null }

/** How many years ago today [taken] was (same month and day, an earlier year), or 0. */
fun yearsAgoToday(taken: Long, now: Long): Int {
    if (taken <= 0) return 0
    val a = Calendar.getInstance().apply { timeInMillis = taken }
    val b = Calendar.getInstance().apply { timeInMillis = now }
    if (a.get(Calendar.MONTH) != b.get(Calendar.MONTH) || a.get(Calendar.DAY_OF_MONTH) != b.get(Calendar.DAY_OF_MONTH)) return 0
    return (b.get(Calendar.YEAR) - a.get(Calendar.YEAR)).coerceAtLeast(0)
}
