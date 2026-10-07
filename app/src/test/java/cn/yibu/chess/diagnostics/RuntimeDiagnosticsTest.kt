package cn.yibu.chess.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivityManager.ApplicationExitInfoBuilder
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RuntimeDiagnosticsTest {
    @Test fun reportsNativeCrashReasonAndSignalWithoutLoadingModel() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val exit = ApplicationExitInfoBuilder.newBuilder()
            .setProcessName(context.packageName)
            .setReason(ApplicationExitInfo.REASON_CRASH_NATIVE)
            .setStatus(4) // SIGILL, the affected ARM kernel failure.
            .setTimestamp(123456789L)
            .setDescription("Illegal instruction")
            .setTraceInputStream(ByteArrayInputStream(byteArrayOf(0, -1, 127, -128)))
            .build()
        shadowOf(context.getSystemService(ActivityManager::class.java)).addApplicationExitInfo(exit)

        val report = JSONObject(RuntimeDiagnostics.collect(context))
        assertEquals(context.packageName, report.getString("application_id"))
        assertTrue(report.getBoolean("exit_history_supported"))
        val crash = report.getJSONArray("recent_process_exits").getJSONObject(0)
        assertEquals(ApplicationExitInfo.REASON_CRASH_NATIVE, crash.getInt("reason"))
        assertEquals(4, crash.getInt("status"))
        assertEquals("Illegal instruction", crash.getString("description"))
        assertEquals("base64", crash.getString("trace_encoding"))
        assertArrayEquals(byteArrayOf(0, -1, 127, -128), Base64.decode(crash.getString("trace"), Base64.DEFAULT))
        assertFalse(crash.getBoolean("trace_truncated"))
    }

    @Test @Config(sdk = [26]) fun olderAndroidStillExportsDeviceInformation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val report = JSONObject(RuntimeDiagnostics.collect(context))
        assertEquals(26, report.getInt("android_sdk"))
        assertFalse(report.getBoolean("exit_history_supported"))
        assertEquals(0, report.getJSONArray("recent_process_exits").length())
        assertFalse(report.has("history_error"))
    }

    @Test fun boundsLargeTracesAndClosesStreams() {
        var closed = false
        val input = object : ByteArrayInputStream("x".repeat(RuntimeDiagnostics.TRACE_LIMIT + 1000).toByteArray()) {
            override fun close() { closed = true; super.close() }
        }
        val (trace, truncated) = RuntimeDiagnostics.readTrace(input)
        assertEquals(RuntimeDiagnostics.TRACE_LIMIT, trace.size)
        assertTrue(truncated)
        assertTrue(closed)
        val short = RuntimeDiagnostics.readTrace(ByteArrayInputStream("SIGILL".toByteArray()))
        assertArrayEquals("SIGILL".toByteArray(), short.first)
        assertFalse(short.second)
    }
}
