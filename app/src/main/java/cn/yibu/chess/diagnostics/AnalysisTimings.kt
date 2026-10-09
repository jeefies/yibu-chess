package cn.yibu.chess.diagnostics

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import cn.yibu.chess.BuildConfig
import kotlinx.serialization.json.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** In-memory, bounded timing only. No tokens, request/response bodies or chess moves. */
internal object AnalysisTimings {
    const val LIMIT = 120
    private val records = ArrayDeque<AnalysisTiming>()
    private val reloads = ArrayDeque<JSONObject>()
    private val lock = Any()

    fun begin(context: Context, gameId: Long, ply: Int, deep: Boolean): AnalysisTiming =
        AnalysisTiming(gameId, ply, if (deep) "deep" else "fast").also { trace ->
            val transports = runCatching {
                val manager = context.getSystemService(ConnectivityManager::class.java)
                val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
                listOf(NetworkCapabilities.TRANSPORT_WIFI to "wifi", NetworkCapabilities.TRANSPORT_CELLULAR to "cellular",
                    NetworkCapabilities.TRANSPORT_ETHERNET to "ethernet", NetworkCapabilities.TRANSPORT_VPN to "vpn")
                    .filter { capabilities?.hasTransport(it.first) == true }.map { it.second }
            }.getOrDefault(emptyList())
            trace.put("transports", JSONArray(transports))
        }

    fun completed(trace: AnalysisTiming) = synchronized(lock) {
        records.addLast(trace)
        while (records.size > LIMIT) records.removeFirst()
    }
    fun frame(requestId: String) = synchronized(lock) { records.find { it.id == requestId }?.frame() }
    fun reload(startNanos: Long, rows: Int, characters: Long) = synchronized(lock) {
        reloads.addLast(JSONObject().put("started_elapsed_ms", startNanos / 1_000_000.0)
            .put("duration_ms", (SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000.0)
            .put("record_count", rows).put("payload_chars", characters))
        while (reloads.size > LIMIT) reloads.removeFirst()
    }
    fun snapshot(): JSONObject = synchronized(lock) {
        JSONObject().put("schema_version", 1).put("scope", "current_process_last_120")
            .put("analysis", JSONArray(records.map { it.snapshot() }))
            .put("record_reloads", JSONArray(reloads.map { JSONObject(it.toString()) }))
    }
    fun export(context: Context): String = JSONObject()
        .put("version", BuildConfig.VERSION_NAME).put("version_code", BuildConfig.VERSION_CODE)
        .put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL).put("android_sdk", Build.VERSION.SDK_INT)
        .put("exported_at_ms", System.currentTimeMillis()).put("timings", snapshot()).toString(2)

    internal fun clear() = synchronized(lock) { records.clear(); reloads.clear() }
}

/** Propagated across Default/IO coroutines; HTTP requests use the same random ID. */
internal class AnalysisTiming(gameId: Long, ply: Int, profile: String,
    private val clock: () -> Long = SystemClock::elapsedRealtimeNanos) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<AnalysisTiming>
    val id: String = UUID.randomUUID().toString()
    val started = clock()
    private var finished: Long? = null
    private val data = JSONObject().put("request_id", id).put("game_id", gameId).put("ply", ply)
        .put("profile", profile).put("multi_pv", 2).put("started_at_ms", System.currentTimeMillis())
        .put("started_elapsed_ms", started / 1_000_000.0).apply {
            listOf("http_ms", "http_call_ms", "response_processing_ms", "analysis_ms", "save_ms", "database_ms",
                "total_to_frame_ms", "after_completion_to_frame_ms", "server_search_ms", "server_queue_ms",
                "server_response_ms", "server_cached", "dns_ms", "connect_ms", "tls_ms").forEach { put(it, JSONObject.NULL) }
        }
    fun now(): Long = clock()
    @Synchronized fun put(name: String, value: Any?) { data.put(name, value ?: JSONObject.NULL) }
    fun duration(name: String, from: Long) = put(name, (clock() - from).coerceAtLeast(0) / 1_000_000.0)
    fun finish(status: String, failureType: String? = null) {
        val first = synchronized(this) {
            if (finished != null) false else {
                finished = clock()
                data.put("status", status).put("total_ms", (finished!! - started).coerceAtLeast(0) / 1_000_000.0)
                if (failureType != null) data.put("failure_type", failureType)
                true
            }
        }
        if (first) AnalysisTimings.completed(this)
    }
    @Synchronized fun frame() {
        if (!data.isNull("total_to_frame_ms")) return
        val now = clock()
        data.put("total_to_frame_ms", (now - started).coerceAtLeast(0) / 1_000_000.0)
        finished?.let { data.put("after_completion_to_frame_ms", (now - it).coerceAtLeast(0) / 1_000_000.0) }
    }
    @Synchronized fun snapshot() = JSONObject(data.toString())

    fun serverStats(stats: JsonElement?) {
        val obj = stats as? JsonObject ?: return
        for ((source, destination) in listOf("searchTimeMs" to "server_search_ms", "queueWaitMs" to "server_queue_ms",
            "responseTimeMs" to "server_response_ms")) {
            (obj[source] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() && it in 0.0..600_000.0 }
                ?.let { put(destination, it) }
        }
        (obj["cached"] as? JsonPrimitive)?.booleanOrNull?.let { put("server_cached", it) }
    }

    fun serverTiming(header: String?) {
        header?.take(2048)?.split(',')?.forEach { entry ->
            val parts = entry.trim().split(';')
            val target = when (parts.first()) {
                "search" -> "server_search_ms"; "queue" -> "server_queue_ms"; "total" -> "server_response_ms"; else -> null
            } ?: return@forEach
            val value = parts.drop(1).firstOrNull { it.trim().startsWith("dur=") }?.trim()?.removePrefix("dur=")
                ?.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..600_000.0 } ?: return@forEach
            put(target, value)
        }
    }
}
