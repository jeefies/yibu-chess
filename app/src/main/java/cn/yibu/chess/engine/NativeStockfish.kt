package cn.yibu.chess.engine

import android.content.Context
import cn.yibu.chess.core.ChessEngine
import cn.yibu.chess.core.ChessRules
import cn.yibu.chess.core.SearchRequest
import cn.yibu.chess.core.SearchResult
import cn.yibu.chess.core.UciParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

object NativeBridge {
    init { System.loadLibrary("yibu_stockfish") }
    @JvmStatic external fun initialize(directory: String)
    @JvmStatic external fun search(history: String, timeMs: Int, depth: Int, multiPv: Int, skill: Int, threads: Int, hash: Int, restricted: String): String
    @JvmStatic external fun stop()
}

class NativeStockfish(private val context: Context) : ChessEngine {
    companion object {
        private val gate = Mutex()
        @Volatile private var initialized = false
        val networks = listOf("nn-1c0000000000.nnue", "nn-37f18f62d772.nnue")
    }
    suspend fun initialize() = gate.withLock {
        if (initialized) return@withLock
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "stockfish-17.1").apply { mkdirs() }
            networks.forEach { name ->
                val target = File(dir, name)
                if (!target.exists() || !verified(target, name)) {
                    val temp = File(dir, "$name.tmp")
                    context.assets.open("networks/$name").use { input -> temp.outputStream().use { input.copyTo(it) } }
                    check(verified(temp, name)) { "引擎权重校验失败：$name" }
                    check(temp.renameTo(target)) { "无法保存引擎权重" }
                }
            }
            try {
                NativeBridge.initialize(dir.absolutePath)
            } catch (e: LinkageError) {
                throw IllegalStateException("无法加载离线引擎：${e.message}", e)
            }
            initialized = true
        }
    }
    private fun verified(file: File, name: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        return sha.startsWith(name.removePrefix("nn-").removeSuffix(".nnue"))
    }
    override suspend fun search(history: List<String>, request: SearchRequest): SearchResult = gate.withLock {
        check(initialized) { "引擎尚未就绪" }
        val legal = withContext(Dispatchers.Default) { ChessRules.legal(history) }
        check(legal.isNotEmpty()) { "当前局面没有合法走法" }
        check(request.restricted.all { it in legal }) { "非法的指定走法" }
        val multiPv = minOf(request.multiPv, if (request.restricted.isEmpty()) legal.size else request.restricted.size)
        val output = withContext(Dispatchers.IO) {
            NativeBridge.search(history.joinToString(" "), request.timeMs.coerceIn(50, 10000), request.depth,
                multiPv, request.skill.coerceIn(0, 20), request.threads.coerceIn(1, 2), request.hashMb.coerceIn(16, 128), request.restricted.joinToString(" "))
        }
        currentCoroutineContext().ensureActive()
        UciParser.parse(output, multiPv)
    }
    override fun stop() { if (initialized) NativeBridge.stop() }
}
