package com.harsharede.ember

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class PhotoScoreTest {
    private val size = 128

    /** A test picture: fine checkerboard (in focus) where [sharp] says so, flat grey (blurred) everywhere else. */
    private fun picture(sharp: (Int, Int) -> Boolean) = IntArray(size * size) { i ->
        val x = i % size; val y = i / size
        if (sharp(x, y)) (if ((x + y) % 2 == 0) 40 else 210) else 120
    }

    @Test fun `a flat picture is not sharp and has no bokeh`() {
        val s = analyzePhoto(picture { _, _ -> false }, size, size)
        assertTrue(s.sharpness < .05f); assertEquals(0f, s.bokeh, 0f); assertTrue(s.exposure > .9f)
    }

    @Test fun `sharp everywhere is sharp but not portrait mode`() {
        val s = analyzePhoto(picture { _, _ -> true }, size, size)
        assertTrue(s.sharpness > .9f); assertTrue(s.bokeh < .1f)
    }

    @Test fun `a sharp subject on a soft background scores as portrait mode, and the crop finds it`() {
        val s = analyzePhoto(picture { x, y -> x in 64 until 112 && y in 16 until 80 }, size, size)
        assertTrue(s.sharpness > .9f)
        assertTrue("bokeh ${s.bokeh}", s.bokeh > .7f)
        assertEquals(.69f, s.focusX, .08f); assertEquals(.38f, s.focusY, .08f)
        // Portrait mode beats sharp-everywhere overall.
        assertTrue(s.total > analyzePhoto(picture { _, _ -> true }, size, size).total)
    }

    @Test fun `crop keeps the screen's shape and the subject, inside the picture`() {
        // 4000 x 3000 landscape onto a 1080 x 2340 phone: full height, a 1384-wide strip.
        assertArrayEquals(intArrayOf(1308, 0, 1384, 3000), wallpaperCrop(4000, 3000, 1080, 2340, .5f, .5f))
        assertArrayEquals(intArrayOf(0, 0, 1384, 3000), wallpaperCrop(4000, 3000, 1080, 2340, 0f, .5f))
        assertArrayEquals(intArrayOf(2616, 0, 1384, 3000), wallpaperCrop(4000, 3000, 1080, 2340, 1f, .5f))
    }

    @Test fun `weighted pick favours heavier items and handles edge cases`() {
        val items = listOf("a", "b")
        assertEquals("a", pickWeighted(items, { if (it == "a") 9.0 else 1.0 }, .5))
        assertEquals("b", pickWeighted(items, { if (it == "a") 9.0 else 1.0 }, .95))
        assertEquals(null, pickWeighted(emptyList<String>(), { 1.0 }, .5))
        assertEquals("b", pickWeighted(items, { 0.0 }, .9))
    }

    @Test fun `three a day lands on 8, 14 and 20 o'clock`() {
        fun at(h: Int, m: Int) = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 2, h, m, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val hour = 60 * 60_000L
        assertEquals(2 * hour, Wallpapers.delayToNextSlot(at(6, 0)))
        assertEquals(30 * 60_000L, Wallpapers.delayToNextSlot(at(13, 30)))
        assertEquals(10 * hour, Wallpapers.delayToNextSlot(at(22, 0)))
    }
}
