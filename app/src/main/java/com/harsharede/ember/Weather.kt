package com.harsharede.ember

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * The weather card, from Open-Meteo (free, no account, no key). This is the only thing Ember uses the internet for, and
 * only once the card is in the stack: it sends a rounded latitude and longitude and gets the forecast back.
 */
object WeatherStore {
    data class Now(val place: String, val temp: Int, val high: Int, val low: Int, val code: Int, val isDay: Boolean,
                   val hours: List<Triple<String, Int, Int>>, val fahrenheit: Boolean, val at: Long,
                   /** The next five hours, hour by hour: weather code and whether it's daytime. */
                   val next: List<Pair<Int, Boolean>> = emptyList())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _now = MutableStateFlow<Now?>(null)
    val now: StateFlow<Now?> = _now.asStateFlow()
    private val _problem = MutableStateFlow<String?>(null)
    /** Why there's no forecast (no location yet, offline…), shown on the card. */
    val problem: StateFlow<String?> = _problem.asStateFlow()

    fun wanted(): Boolean {
        val prefs = EmberApp.instance.prefs
        return "card:weather" in prefs.stackItems.value || prefs.weatherEffects.value || prefs.headerWeather.value
    }

    /** Fahrenheit where people use it; Celsius everywhere else. */
    val fahrenheit: Boolean get() = Locale.getDefault().country in setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")

    fun refresh(context: Context, force: Boolean = false) {
        val prefs = EmberApp.instance.prefs
        // Needed by the Weather card, the weather drawn over Home, or the icons beside the day.
        if (!wanted()) return
        val last = _now.value
        if (!force && last != null && System.currentTimeMillis() - last.at < 30 * 60_000L) return
        val app = context.applicationContext
        scope.launch {
            runCatching {
                val place = EmberApp.instance.prefs.weatherPlace.value ?: locate(app)
                if (place == null) { _problem.value = "Choose a weather place: hold Home › Customize look › Weather place"; return@launch }
                _now.value = fetch(place.first, place.second, place.third)
                _problem.value = null
            }.onFailure { if (_now.value == null) _problem.value = "Couldn't get the weather. Check your connection." }
        }
    }

    /** The phone's approximate location, if allowed, with a place name from Android's geocoder. */
    private suspend fun locate(context: Context): Triple<String, Double, Double>? {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val lm = context.getSystemService(LocationManager::class.java)
        @Suppress("MissingPermission")
        val known = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }.maxByOrNull { it.time }
        @Suppress("MissingPermission")
        val location = known ?: withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine { cont ->
                runCatching {
                    lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, context.mainExecutor) { cont.resume(it) }
                }.onFailure { cont.resume(null) }
            }
        } ?: return null
        val name = withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("DEPRECATION")
                Geocoder(context).getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
            }.getOrNull()
        } ?: "Your location"
        return Triple(name, location.latitude, location.longitude)
    }

    private fun fetch(place: String, lat: Double, lon: Double): Now {
        val f = fahrenheit
        // Rounded to about a kilometre: plenty for a forecast, and less precise than the phone knows.
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${"%.2f".format(Locale.US, lat)}&longitude=${"%.2f".format(Locale.US, lon)}" +
            "&current=temperature_2m,weather_code,is_day&hourly=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min" +
            "&forecast_days=2&timezone=auto" + (if (f) "&temperature_unit=fahrenheit" else "")
        val json = JSONObject(get(url))
        val current = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")
        val hourly = json.getJSONObject("hourly")
        val times = hourly.getJSONArray("time"); val temps = hourly.getJSONArray("temperature_2m"); val codes = hourly.getJSONArray("weather_code")
        val days = hourly.optJSONArray("is_day")
        val nowIso = current.getString("time").take(13) // "2026-11-02T20"
        var start = 0
        for (i in 0 until times.length()) if (times.getString(i).take(13) >= nowIso) { start = i; break }
        val hours = (1..5).mapNotNull { k ->
            val i = start + k * 2
            if (i >= times.length()) null else Triple(times.getString(i).substring(11, 16), temps.getDouble(i).roundToInt(), codes.getInt(i))
        }
        val next = (1..5).mapNotNull { k ->
            val i = start + k
            if (i >= codes.length()) null else codes.getInt(i) to (days?.optInt(i, 1) ?: 1).let { it == 1 }
        }
        return Now(place, current.getDouble("temperature_2m").roundToInt(),
            daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(), daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
            current.getInt("weather_code"), current.getInt("is_day") == 1, hours, f, System.currentTimeMillis(), next)
    }

    /** City search for "choose a city" (Open-Meteo's geocoding). */
    suspend fun searchCity(query: String): List<Triple<String, Double, Double>> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            val url = "https://geocoding-api.open-meteo.com/v1/search?count=8&language=${Locale.getDefault().language}&name=" + URLEncoder.encode(query.trim(), "UTF-8")
            val results = JSONObject(get(url)).optJSONArray("results") ?: return@runCatching emptyList()
            (0 until results.length()).map { i ->
                val r = results.getJSONObject(i)
                val label = listOfNotNull(r.optString("name").takeIf { it.isNotBlank() }, r.optString("admin1").takeIf { it.isNotBlank() },
                    r.optString("country").takeIf { it.isNotBlank() }).joinToString(", ")
                Triple(label, r.getDouble("latitude"), r.getDouble("longitude"))
            }
        }.getOrDefault(emptyList())
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8_000; c.readTimeout = 8_000
        c.setRequestProperty("User-Agent", "Ember launcher")
        return try { c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
    }
}

/** WMO weather codes, as Open-Meteo reports them, in words. */
fun weatherLabel(code: Int): String = when (code) {
    0 -> "Clear"
    1 -> "Mostly clear"
    2 -> "Partly cloudy"
    3 -> "Cloudy"
    45, 48 -> "Fog"
    51, 53, 55, 56, 57 -> "Drizzle"
    61, 63, 65, 66, 67 -> "Rain"
    71, 73, 75, 77 -> "Snow"
    80, 81, 82 -> "Showers"
    85, 86 -> "Snow showers"
    95, 96, 99 -> "Thunderstorm"
    else -> "—"
}
