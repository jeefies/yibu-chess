package cn.yibu.chess.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import cn.yibu.chess.core.SoundBeat
import cn.yibu.chess.core.SoundCue
import kotlinx.coroutines.*

internal interface SoundOutput {
    fun load(cue: SoundCue, ready: () -> Unit)
    fun play(cue: SoundCue)
    fun stop()
    fun release()
}

/** Samples load once. Missed/unloaded cues are dropped instead of replaying late. */
internal class ChessSounds(context: Context, private val output: SoundOutput = PoolOutput(context),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)) {
    private val loaded = mutableSetOf<SoundCue>()
    private var foreground = true
    private var closed = false
    init { SoundCue.entries.forEach { cue -> output.load(cue) { if (!closed) loaded += cue } } }
    fun play(beats: List<SoundBeat>) {
        if (!foreground || closed || beats.isEmpty()) return
        scope.launch {
            var elapsed = 0L
            for (beat in beats.sortedBy { it.delayMs }) {
                delay((beat.delayMs - elapsed).coerceAtLeast(0))
                elapsed = beat.delayMs
                if (!foreground || closed) return@launch
                if (beat.cue in loaded) output.play(beat.cue)
            }
        }
    }
    fun stop() { scope.coroutineContext.cancelChildren(); output.stop() }
    fun pause() { foreground = false; stop() }
    fun resume() { if (!closed) foreground = true }
    fun release() { if (!closed) { closed = true; scope.cancel(); output.release(); loaded.clear() } }
}

private class PoolOutput(private val context: Context) : SoundOutput {
    private val pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val ids = mutableMapOf<SoundCue, Int>()
    private val pending = mutableMapOf<Int, () -> Unit>()
    private val streams = ArrayDeque<Int>()
    init { pool.setOnLoadCompleteListener { _, id, status -> pending.remove(id)?.let { if (status == 0) it() } } }
    override fun load(cue: SoundCue, ready: () -> Unit) {
        val resource = context.resources.getIdentifier("sfx_${cue.name.lowercase()}", "raw", context.packageName)
        if (resource == 0) return
        val id = pool.load(context, resource, 1)
        if (id != 0) { ids[cue] = id; pending[id] = ready }
    }
    override fun play(cue: SoundCue) {
        val id = ids[cue] ?: return
        val stream = pool.play(id, .65f, .65f, 1, 0, 1f)
        if (stream != 0) { streams.addLast(stream); if (streams.size > 4) streams.removeFirst() }
    }
    override fun stop() { streams.forEach(pool::stop); streams.clear() }
    override fun release() { stop(); pending.clear(); pool.release() }
}
