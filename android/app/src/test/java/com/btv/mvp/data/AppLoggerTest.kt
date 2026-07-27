package com.btv.mvp.data

import org.junit.Assert.*
import org.junit.Test

class AppLoggerTest {

    @Test
    fun `log adds entry and can be retrieved`() {
        AppLogger.clear()
        AppLogger.i("Test", "hello")
        assertEquals(1, AppLogger.logs.size)
        assertEquals("hello", AppLogger.logs[0].message)
        assertEquals(AppLogger.Level.INFO, AppLogger.logs[0].level)
        assertEquals("Test", AppLogger.logs[0].tag)
    }

    @Test
    fun `log respects max entries limit of 500`() {
        AppLogger.clear()
        for (i in 1..600) {
            AppLogger.d("Tag", "msg $i")
        }
        assertEquals(500, AppLogger.logs.size)
        assertEquals("msg 101", AppLogger.logs[0].message)
        assertEquals("msg 600", AppLogger.logs[499].message)
    }

    @Test
    fun `clear removes all entries`() {
        AppLogger.i("A", "1")
        AppLogger.i("B", "2")
        AppLogger.clear()
        assertEquals(0, AppLogger.logs.size)
    }

    @Test
    fun `formatted produces correct timestamp format`() {
        AppLogger.clear()
        AppLogger.i("TAG", "msg")
        val formatted = AppLogger.logs[0].formatted()
        assertTrue(formatted.contains("[INFO ]"))
        assertTrue(formatted.contains("[TAG]"))
        assertTrue(formatted.contains("msg"))
    }

    @Test
    fun `all log levels work`() {
        AppLogger.clear()
        AppLogger.i("T", "info")  // Keep "T" so it sorts naturally
        AppLogger.w("U", "warn")
        AppLogger.e("V", "error")
        AppLogger.d("W", "debug")
        assertEquals(4, AppLogger.logs.size)
        assertEquals(AppLogger.Level.INFO, AppLogger.logs[0].level)
        assertEquals(AppLogger.Level.WARN, AppLogger.logs[1].level)
        assertEquals(AppLogger.Level.ERROR, AppLogger.logs[2].level)
        assertEquals(AppLogger.Level.DEBUG, AppLogger.logs[3].level)
    }
}
