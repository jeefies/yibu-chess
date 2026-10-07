package cn.yibu.chess.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Base64
import cn.yibu.chess.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.ByteArrayOutputStream

/** Reads only this application's exit records; never loads the native model runtime. */
object RuntimeDiagnostics {
    internal const val TRACE_LIMIT = 64 * 1024

    fun collect(context: Context): String {
        val report = JSONObject()
            .put("application_id", context.packageName)
            .put("version", BuildConfig.VERSION_NAME)
            .put("version_code", BuildConfig.VERSION_CODE)
            .put("onnx_runtime", BuildConfig.ONNX_RUNTIME_VERSION)
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("android_release", Build.VERSION.RELEASE)
            .put("android_sdk", Build.VERSION.SDK_INT)
            .put("supported_abis", JSONArray(Build.SUPPORTED_ABIS.toList()))
            .put("exit_history_supported", Build.VERSION.SDK_INT >= 30)
        if (Build.VERSION.SDK_INT >= 31) report.put("soc_model", Build.SOC_MODEL)
        val exits = JSONArray()
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val manager = context.getSystemService(ActivityManager::class.java)
                manager.getHistoricalProcessExitReasons(context.packageName, 0, 5).forEach { exit ->
                    val item = JSONObject()
                        .put("timestamp", exit.timestamp)
                        .put("process", exit.processName)
                        .put("reason", exit.reason)
                        .put("status", exit.status)
                        .put("description", exit.description)
                    try {
                        exit.traceInputStream?.let { input ->
                            val (trace, truncated) = readTrace(input)
                            // Android 12+ exposes native tombstones as binary protobuf, not text.
                            val native = exit.reason == ApplicationExitInfo.REASON_CRASH_NATIVE
                            item.put("trace", if (native) Base64.encodeToString(trace, Base64.NO_WRAP) else trace.toString(Charsets.UTF_8))
                                .put("trace_encoding", if (native) "base64" else "utf-8")
                                .put("trace_truncated", truncated)
                        }
                    } catch (e: Exception) {
                        item.put("trace_error", "${e.javaClass.simpleName}: ${e.message}")
                    }
                    exits.put(item)
                }
            } catch (e: Exception) {
                report.put("history_error", "${e.javaClass.simpleName}: ${e.message}")
            }
        }
        return report.put("recent_process_exits", exits).toString(2)
    }

    internal fun readTrace(input: InputStream): Pair<ByteArray, Boolean> = input.use { stream ->
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (bytes.size() < TRACE_LIMIT) {
            val count = stream.read(buffer, 0, minOf(buffer.size, TRACE_LIMIT - bytes.size()))
            if (count == -1) break
            bytes.write(buffer, 0, count)
        }
        bytes.toByteArray() to (stream.read() != -1)
    }
}
