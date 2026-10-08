package cn.yibu.chess.audio

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import cn.yibu.chess.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChessSoundsTest {
    private class Output(val automatic: Boolean = true) : SoundOutput {
        val loaded = mutableMapOf<SoundCue, () -> Unit>()
        val played = mutableListOf<SoundCue>()
        var stopped = 0
        var released = false
        override fun load(cue: SoundCue, ready: () -> Unit) { loaded[cue] = ready; if (automatic) ready() }
        override fun play(cue: SoundCue) { played += cue }
        override fun stop() { stopped++ }
        override fun release() { released = true }
    }
    private fun context() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun backgroundCancelsPendingResultAndResumeNeverReplaysStaleAudio() {
        val output = Output()
        val sounds = ChessSounds(context(), output)
        sounds.play(listOf(SoundBeat(SoundCue.MOVE), SoundBeat(SoundCue.WIN, 1100)))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(SoundCue.MOVE), output.played)
        sounds.pause()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        sounds.play(listOf(SoundBeat(SoundCue.CHECK)))
        sounds.resume()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(SoundCue.MOVE), output.played)
        assertTrue(output.stopped > 0)
        sounds.play(listOf(SoundBeat(SoundCue.SELECT)))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(SoundCue.MOVE, SoundCue.SELECT), output.played)
        sounds.release()
        sounds.resume(); sounds.play(listOf(SoundBeat(SoundCue.START)))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(output.released)
        assertEquals(2, output.played.size)
    }

    @Test fun unloadingOrCancelingAnEventCannotMakeItAudibleLater() {
        val output = Output(automatic = false)
        val sounds = ChessSounds(context(), output)
        sounds.play(listOf(SoundBeat(SoundCue.CAPTURE)))
        shadowOf(Looper.getMainLooper()).idle()
        output.loaded.getValue(SoundCue.CAPTURE)()
        assertTrue(output.played.isEmpty())
        sounds.play(listOf(SoundBeat(SoundCue.CAPTURE), SoundBeat(SoundCue.LOSE, 850)))
        shadowOf(Looper.getMainLooper()).idle()
        sounds.stop()
        output.loaded.getValue(SoundCue.LOSE)()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(listOf(SoundCue.CAPTURE), output.played)
        sounds.release()
    }

    @Test fun everyCueHasAShortPlayableOfflinePcmResourceWithAudibleNonClippingSamples() {
        SoundCue.entries.forEach { cue ->
            val id = context().resources.getIdentifier("sfx_${cue.name.lowercase()}", "raw", context().packageName)
            assertTrue("Missing audio for $cue", id != 0)
            val bytes = context().resources.openRawResource(id).use { it.readBytes() }
            assertEquals("RIFF", String(bytes, 0, 4))
            assertEquals("WAVE", String(bytes, 8, 4))
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(1, buffer.getShort(20).toInt())
            assertEquals(1, buffer.getShort(22).toInt())
            assertEquals(44100, buffer.getInt(24))
            assertEquals(16, buffer.getShort(34).toInt())
            assertTrue(bytes.size in 2000..60000)
            val peak = (44 until bytes.size step 2).maxOf { kotlin.math.abs(buffer.getShort(it).toInt()) }
            assertTrue("Silent or clipped sample for $cue: $peak", peak in 100..26215)
        }
    }
}
