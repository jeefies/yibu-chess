package cn.yibu.chess.engine

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import cn.yibu.chess.core.HumanPolicy
import cn.yibu.chess.core.MaiaEncoding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.security.MessageDigest

class MaiaModel(private val context: Context) : HumanPolicy {
    companion object {
        private val gate = Mutex()
        private var session: OrtSession? = null
        private var environment: OrtEnvironment? = null
        private const val NAME = "maia3-5m-int8.onnx"
    }
    suspend fun initialize() = gate.withLock {
        if (session != null) return@withLock
        withContext(Dispatchers.IO) {
            val metadata = context.assets.open("models/maia3-metadata.json").bufferedReader().use { JSONObject(it.readText()) }
            val expected = metadata.getString("onnx_sha256")
            val directory = File(context.filesDir, "maia3-5m").apply { mkdirs() }
            val target = File(directory, NAME)
            if (!target.exists() || checksum(target) != expected) {
                val temporary = File(directory, "$NAME.tmp")
                context.assets.open("models/$NAME").use { input -> temporary.outputStream().use { input.copyTo(it) } }
                check(checksum(temporary) == expected) { "拟人模型校验失败" }
                check(temporary.renameTo(target)) { "无法保存拟人模型" }
            }
            try {
                val env = OrtEnvironment.getEnvironment()
                OrtSession.SessionOptions().use { options ->
                    options.setIntraOpNumThreads(2)
                    options.setInterOpNumThreads(1)
                    options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    val loaded = env.createSession(target.absolutePath, options)
                    check(loaded.inputNames == setOf("tokens", "self_elo", "opponent_elo")) { "拟人模型输入不匹配" }
                    environment = env
                    session = loaded
                }
            } catch (e: LinkageError) { throw IllegalStateException("无法加载拟人模型：${e.message}", e) }
        }
    }
    override suspend fun logits(history: List<String>, selfElo: Int, opponentElo: Int): FloatArray = gate.withLock {
        val loaded = checkNotNull(session) { "拟人模型尚未就绪" }
        val env = checkNotNull(environment)
        val result = withContext(Dispatchers.Default) {
            val values = MaiaEncoding.tokens(history)
            OnnxTensor.createTensor(env, FloatBuffer.wrap(values), longArrayOf(1, 64, MaiaEncoding.FEATURES.toLong())).use { tokens ->
                OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(selfElo.toLong())), longArrayOf(1)).use { self ->
                    OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(opponentElo.toLong())), longArrayOf(1)).use { other ->
                        loaded.run(mapOf("tokens" to tokens, "self_elo" to self, "opponent_elo" to other)).use { output ->
                            val raw = output[0].value as Array<*>
                            (raw[0] as FloatArray).copyOf().also { check(it.size == MaiaEncoding.VOCABULARY) }
                        }
                    }
                }
            }
        }
        currentCoroutineContext().ensureActive()
        result
    }
    private fun checksum(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
