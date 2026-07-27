package com.btv.mvp.data

import org.junit.Assert.*
import org.junit.Test
import java.net.URL

class NetworkDiagnosticsTest {

    @Test
    fun `FullDiagReport has correct structure`() {
        val result = DiagResult("step", true, "detail", 100)
        assertTrue(result.success)
        assertEquals("step", result.step)
        assertEquals("detail", result.detail)
        assertEquals(100, result.durationMs)
    }

    @Test
    fun `DiagResult failure`() {
        val result = DiagResult("dns", false, "failed", 50)
        assertFalse(result.success)
        assertEquals("dns", result.step)
    }

    @Test
    fun `FullDiagReport health all pass`() {
        val results = listOf(
            DiagResult("a", true, "ok", 10),
            DiagResult("b", true, "ok", 20),
            DiagResult("c", true, "ok", 30)
        )
        val report = FullDiagReport("host", 8080, results, "全部通过")
        assertEquals("全部通过", report.overallHealth)
        assertEquals(3, report.results.size)
        assertEquals("host", report.host)
        assertEquals(8080, report.port)
    }

    @Test
    fun `FullDiagReport health with failures`() {
        val results = listOf(
            DiagResult("a", true, "ok", 10),
            DiagResult("b", false, "fail", 20)
        )
        val report = FullDiagReport("host", 8080, results, "部分异常")
        assertEquals("部分异常", report.overallHealth)
    }
}
