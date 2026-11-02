package com.harsharede.ember

import kotlin.math.abs
import kotlin.math.log10

/**
 * How good a photo is as a wallpaper, from a small greyscale copy of it. Pure Kotlin, so it's tested without a phone.
 *
 * - [sharpness]: how crisp the sharpest part is (0 to 1). A wallpaper that's soft everywhere looks like a mistake.
 * - [bokeh]: a sharp subject against a softly blurred background, the look of portrait mode (0 to 1).
 * - [exposure]: neither murky nor blown out (0 to 1).
 * - [focusX], [focusY]: where the sharp part is (0 to 1 across and down), so the crop keeps it on screen.
 */
data class PhotoScore(val sharpness: Float, val bokeh: Float, val exposure: Float, val focusX: Float, val focusY: Float) {
    /** Sharp first, portrait-mode blur next, exposure as a tie-breaker. */
    val total: Float get() = .45f * sharpness + .35f * bokeh + .20f * exposure
}

/**
 * Splits the picture into a [grid] × [grid] of blocks and measures the detail in each with a Laplacian (how much each
 * pixel differs from its neighbours). In-focus blocks have a lot; blurred ones almost none.
 */
fun analyzePhoto(luma: IntArray, width: Int, height: Int, grid: Int = 8): PhotoScore {
    require(luma.size >= width * height && width >= 3 && height >= 3)
    val energy = DoubleArray(grid * grid)
    val counts = IntArray(grid * grid)
    var sum = 0L
    var clipped = 0
    for (y in 0 until height) for (x in 0 until width) {
        val v = luma[y * width + x]
        sum += v
        if (v < 8 || v > 247) clipped++
        if (x == 0 || y == 0 || x == width - 1 || y == height - 1) continue
        val lap = abs(4 * v - luma[y * width + x - 1] - luma[y * width + x + 1] - luma[(y - 1) * width + x] - luma[(y + 1) * width + x])
        val block = (y * grid / height) * grid + (x * grid / width)
        energy[block] += (lap * lap).toDouble()
        counts[block]++
    }
    val blocks = DoubleArray(grid * grid) { if (counts[it] == 0) 0.0 else energy[it] / counts[it] }
    val sorted = blocks.sorted()
    val p90 = sorted[(sorted.size * 9 / 10).coerceAtMost(sorted.lastIndex)]
    val p30 = sorted[sorted.size * 3 / 10]

    // A Laplacian energy around 200 is clearly in focus at this size; well past it is crisp.
    val sharpness = (p90 / (p90 + 200.0)).toFloat()

    // Portrait mode: the sharp part stands well clear of the rest, and a good share of the frame (not all of it) is soft.
    val blurredShare = blocks.count { it < .12 * p90 }.toFloat() / blocks.size
    val contrast = ((log10(p90 / (p30 + 1.0)) - .6) / 1.4).coerceIn(0.0, 1.0).toFloat()
    val shareFit = when {
        blurredShare < .2f -> blurredShare / .2f
        blurredShare <= .85f -> 1f
        else -> ((1f - blurredShare) / .15f).coerceAtLeast(0f)
    }
    val bokeh = if (sharpness < .3f) 0f else contrast * shareFit

    val mean = sum.toDouble() / (width * height)
    val clippedShare = clipped.toFloat() / (width * height)
    val exposure = ((1.0 - abs(mean - 120.0) / 120.0).toFloat() - clippedShare * 1.5f).coerceIn(0f, 1f)

    // The middle of the sharp blocks.
    var fx = 0.0; var fy = 0.0; var weight = 0.0
    for (i in blocks.indices) if (blocks[i] >= .5 * p90 && p90 > 0) {
        fx += (i % grid + .5) / grid * blocks[i]; fy += (i / grid + .5) / grid * blocks[i]; weight += blocks[i]
    }
    return PhotoScore(sharpness, bokeh, exposure,
        if (weight > 0) (fx / weight).toFloat() else .5f, if (weight > 0) (fy / weight).toFloat() else .5f)
}

/**
 * The part of a [srcW] × [srcH] picture to show on a [dstW] × [dstH] screen: the screen's shape, as large as fits,
 * centred on the subject at ([focusX], [focusY]) but never past the picture's edges. Returns left, top, width, height.
 */
fun wallpaperCrop(srcW: Int, srcH: Int, dstW: Int, dstH: Int, focusX: Float, focusY: Float): IntArray {
    val target = dstW.toDouble() / dstH
    val cropW: Int; val cropH: Int
    if (srcW.toDouble() / srcH > target) { cropH = srcH; cropW = (srcH * target).toInt().coerceIn(1, srcW) }
    else { cropW = srcW; cropH = (srcW / target).toInt().coerceIn(1, srcH) }
    // A face sits a little above the middle of a good portrait crop.
    val left = (focusX * srcW - cropW / 2.0).toInt().coerceIn(0, srcW - cropW)
    val top = (focusY * srcH - cropH * .42).toInt().coerceIn(0, srcH - cropH)
    return intArrayOf(left, top, cropW, cropH)
}

/** Weighted random pick: better scores and liked photos come up more, so the wallpaper varies but stays good. */
fun <T> pickWeighted(items: List<T>, weight: (T) -> Double, random: Double): T? {
    if (items.isEmpty()) return null
    val weights = items.map { weight(it).coerceAtLeast(0.0) }
    val total = weights.sum()
    if (total <= 0) return items[(random * items.size).toInt().coerceIn(0, items.lastIndex)]
    var r = random * total
    for (i in items.indices) { r -= weights[i]; if (r <= 0) return items[i] }
    return items.last()
}
