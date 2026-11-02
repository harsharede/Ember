package com.harsharede.ember

import android.Manifest
import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

/**
 * Quick maths for the drawer's search: + − × ÷ (x and * too), ^, brackets, and percentages ("15% of 240", "240 + 10%").
 * Returns null for anything that isn't a sum, so ordinary app names never turn into results.
 */
fun evaluateExpression(input: String): Double? {
    var text = input.trim().lowercase(Locale.ROOT).replace('×', '*').replace('÷', '/').replace(',', '')
    if (text.isEmpty() || text.none { it.isDigit() }) return null
    Regex("^([\\d.]+)\\s*%\\s*of\\s*([\\d.]+)$").matchEntire(text)?.let { m ->
        return m.groupValues[1].toDoubleOrNull()?.let { a -> m.groupValues[2].toDoubleOrNull()?.let { b -> a / 100 * b } }
    }
    text = text.replace(Regex("(?<=\\d)\\s*x\\s*(?=[\\d(])"), "*")
    if (!text.all { it.isDigit() || it in "+-*/^().% " }) return null
    if (text.none { it in "+-*/^%" }) return null
    val parser = object {
        var pos = 0
        fun peek() = text.getOrNull(pos)?.also { if (it == ' ') { pos++; return peek() } }
        fun eat(c: Char): Boolean { if (peek() == c) { pos++; return true }; return false }
        fun expr(): Double {
            var v = term()
            while (true) {
                if (eat('+')) { val r = term(); v = if (lastPercent) v + v * r / 100 else v + r }
                else if (eat('-')) { val r = term(); v = if (lastPercent) v - v * r / 100 else v - r }
                else return v
            }
        }
        var lastPercent = false
        fun term(): Double {
            var v = power()
            while (true) {
                if (eat('*')) v *= power().let { if (lastPercent) it / 100 else it }.also { lastPercent = false }
                else if (eat('/')) v /= power().let { if (lastPercent) it / 100 else it }.also { lastPercent = false }
                else return v
            }
        }
        fun power(): Double { val b = unary(); return if (eat('^')) b.pow(power()) else b }
        fun unary(): Double = if (eat('-')) -unary() else if (eat('+')) unary() else number()
        fun number(): Double {
            lastPercent = false
            if (eat('(')) { val v = expr(); if (!eat(')')) throw IllegalArgumentException(); return v }
            val start = pos
            while (peek()?.let { it.isDigit() || it == '.' } == true) pos++
            val v = text.substring(start, pos).trim().toDouble()
            if (eat('%')) lastPercent = true
            return v
        }
    }
    return runCatching {
        val v = parser.expr()
        if (parser.peek() != null) null else v.takeIf { it.isFinite() }
    }.getOrNull()
}

/** "216", "0.3333", "1,234.5": short and readable. */
fun formatNumber(v: Double): String {
    if (abs(v - Math.round(v)) < 1e-9 && abs(v) < 1e15) return String.format(Locale.getDefault(), "%,d", Math.round(v))
    return String.format(Locale.getDefault(), "%,.4f", v).trimEnd('0').trimEnd('.', ',')
}

private data class Unit_(val names: List<String>, val kind: String, val factor: Double)

private val UNITS = listOf(
    Unit_(listOf("mm", "millimeter", "millimeters", "millimetre"), "length", .001), Unit_(listOf("cm", "centimeter", "centimeters", "centimetre"), "length", .01),
    Unit_(listOf("m", "meter", "meters", "metre", "metres"), "length", 1.0), Unit_(listOf("km", "kilometer", "kilometers", "kilometre", "kms"), "length", 1000.0),
    Unit_(listOf("in", "inch", "inches"), "length", .0254), Unit_(listOf("ft", "foot", "feet"), "length", .3048),
    Unit_(listOf("yd", "yard", "yards"), "length", .9144), Unit_(listOf("mi", "mile", "miles"), "length", 1609.344),
    Unit_(listOf("g", "gram", "grams"), "mass", .001), Unit_(listOf("kg", "kilo", "kilos", "kilogram", "kilograms"), "mass", 1.0),
    Unit_(listOf("lb", "lbs", "pound", "pounds"), "mass", .45359237), Unit_(listOf("oz", "ounce", "ounces"), "mass", .028349523),
    Unit_(listOf("ml", "milliliter", "milliliters"), "volume", .001), Unit_(listOf("l", "liter", "liters", "litre", "litres"), "volume", 1.0),
    Unit_(listOf("gal", "gallon", "gallons"), "volume", 3.785411784), Unit_(listOf("cup", "cups"), "volume", .2365882365),
    Unit_(listOf("kmh", "kph", "km/h"), "speed", 1 / 3.6), Unit_(listOf("mph"), "speed", .44704), Unit_(listOf("m/s", "mps"), "speed", 1.0),
    Unit_(listOf("c", "°c", "celsius"), "temp", 0.0), Unit_(listOf("f", "°f", "fahrenheit"), "temp", 0.0), Unit_(listOf("k", "kelvin"), "temp", 0.0),
)

/** "10 km in mi" → "10 km = 6.21 mi". Length, mass, volume, speed and temperature; null for anything else. */
fun convertUnits(input: String): String? {
    val m = Regex("^\\s*(-?[\\d.,]+)\\s*([a-z°/]+)\\s+(?:to|in|=|into)\\s+([a-z°/]+)\\s*$").matchEntire(input.lowercase(Locale.ROOT)) ?: return null
    val value = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
    val from = UNITS.firstOrNull { m.groupValues[2] in it.names } ?: return null
    val to = UNITS.firstOrNull { m.groupValues[3] in it.names } ?: return null
    if (from.kind != to.kind) return null
    val result = if (from.kind == "temp") {
        val c = when (from.names[0]) { "f" -> (value - 32) * 5 / 9; "k" -> value - 273.15; else -> value }
        when (to.names[0]) { "f" -> c * 9 / 5 + 32; "k" -> c + 273.15; else -> c }
    } else value * from.factor / to.factor
    val shown = String.format(Locale.getDefault(), "%,.2f", result).trimEnd('0').trimEnd('.', ',')
    return "${formatNumber(value)} ${m.groupValues[2]} = $shown ${m.groupValues[3]}"
}

private data class Contact(val name: String, val number: String, val photo: ImageBitmap?, val uri: Uri?)

/** The extra rows above app results: a sum, a conversion, matching contacts and a web search. */
@Composable
fun SmartResults(query: String) {
    val context = LocalContext.current
    val q = query.trim()
    if (q.isEmpty()) return
    val math = remember(q) { evaluateExpression(q) }
    val conversion = remember(q) { convertUnits(q) }
    var contactsAllowed by remember { mutableStateOf(CardData.granted(context, Manifest.permission.READ_CONTACTS)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { contactsAllowed = it }
    val contacts by produceState(emptyList<Contact>(), q, contactsAllowed) {
        value = if (!contactsAllowed || q.length < 2 || math != null) emptyList() else withContext(Dispatchers.IO) { findContacts(context, q) }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        math?.let { v -> SmartRow(Icons.Rounded.Calculate, "= ${formatNumber(v)}", "Tap to copy") { copy(context, formatNumber(v)) } }
        conversion?.let { c -> SmartRow(Icons.Rounded.Straighten, c, "Tap to copy") { copy(context, c.substringAfter("= ")) } }
        contacts.forEach { c ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
                c.uri?.let { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            }.padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (c.photo != null) Image(c.photo, null, contentScale = ContentScale.Crop, modifier = Modifier.size(40.dp).clip(CircleShape))
                else Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Text(c.name.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(c.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(c.number, style = MaterialTheme.typography.bodySmall, color = EmberColors.Secondary)
                }
                IconButton({ runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${c.number}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }) {
                    Icon(Icons.Rounded.Call, "Call ${c.name}", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton({ runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${c.number}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }) {
                    Icon(Icons.AutoMirrored.Rounded.Message, "Message ${c.name}", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (!contactsAllowed && math == null && conversion == null && q.length >= 2)
            SmartRow(Icons.Rounded.Call, "Search people too", "Allow contacts") { ask.launch(Manifest.permission.READ_CONTACTS) }
        SmartRow(Icons.Rounded.Public, "Search the web for \"$q\"", null) { webSearch(context, q) }
    }
}

@Composable
private fun SmartRow(icon: ImageVector, title: String, detail: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = EmberColors.Secondary)
        }
    }
}

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Result", text))
    Toast.makeText(context, "Copied $text", Toast.LENGTH_SHORT).show()
}

/** The phone's web search (the default browser or search app), or a Google search page if there's none. */
fun webSearch(context: Context, query: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
}

private fun findContacts(context: Context, query: String): List<Contact> = runCatching {
    val out = ArrayList<Contact>()
    val uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(query))
    context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER,
        ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI, ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
        ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY), null, null, null)?.use { c ->
        val seen = HashSet<String>()
        while (c.moveToNext() && out.size < 4) {
            val name = c.getString(0).orEmpty()
            if (!seen.add(name)) continue
            val photo = c.getString(2)?.let { p ->
                runCatching { context.contentResolver.openInputStream(Uri.parse(p))?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
            }
            out += Contact(name, c.getString(1).orEmpty(), photo, ContactsContract.Contacts.getLookupUri(c.getLong(3), c.getString(4)))
        }
    }
    out
}.getOrDefault(emptyList())
