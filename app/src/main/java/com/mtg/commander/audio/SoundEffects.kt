package com.mtg.commander.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedurally synthesized sound effects (same additive/filter approach as the
 * Web Audio prototype used to pick these two sounds) — no bundled audio assets needed.
 */
object SoundEffects {

    private const val SAMPLE_RATE = 44100

    suspend fun playElimination() = playSamples(glassBreakSamples())
    suspend fun playVictory() = playSamples(epicHornSamples())

    // ─── Glas-Zerbrechen (Kill-Sound) ──────────────────────────────────────────

    private fun glassBreakSamples(): FloatArray {
        val total = FloatArray((SAMPLE_RATE * 0.65f).toInt())

        val noise = onePoleHighpass(whiteNoise(0.26f), 3200f)
        val noiseEnv = envelopeExp(noise.size, attack = 0.001f, hold = 0.01f, decay = 0.22f, peak = 0.8f)
        add(total, applyEnvelope(noise, noiseEnv), 0f)

        val partials = floatArrayOf(2600f, 3900f, 5200f, 6800f)
        partials.forEachIndexed { i, freq ->
            val tone = sineWave({ freq }, 0.4f)
            val env = envelopeExp(
                tone.size, attack = 0.002f, hold = 0.01f,
                decay = (0.35f - i * 0.04f).coerceAtLeast(0.05f), peak = 0.2f
            )
            add(total, applyEnvelope(tone, env), i * 0.02f)
        }
        return scaleAndNormalize(total, masterGain = 0.55f)
    }

    // ─── Episches Horn (Sieg-Sound) ────────────────────────────────────────────

    private fun epicHornSamples(): FloatArray {
        val duration = 1.9f
        val total = FloatArray((SAMPLE_RATE * duration).toInt())

        val freqs = floatArrayOf(196f, 246.94f, 293.66f)
        freqs.forEachIndexed { i, freq ->
            val osc = sawWave({ t -> freq + sin(2.0 * PI * 5.5 * t).toFloat() * 3f }, duration)
            val filtered = onePoleLowpass(osc, 1400f)
            val peak = if (i == 0) 0.55f else 0.3f
            val env = envelopeExp(filtered.size, attack = 0.25f, hold = 0.9f, decay = 0.65f, peak = peak)
            add(total, applyEnvelope(filtered, env), 0f)
        }
        return scaleAndNormalize(total, masterGain = 0.4f)
    }

    // ─── Synthese-Bausteine ────────────────────────────────────────────────────

    private fun sineWave(freqAt: (Float) -> Float, duration: Float): FloatArray {
        val n = (SAMPLE_RATE * duration).toInt()
        val out = FloatArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i / SAMPLE_RATE.toFloat()
            phase += 2.0 * PI * freqAt(t) / SAMPLE_RATE
            out[i] = sin(phase).toFloat()
        }
        return out
    }

    private fun sawWave(freqAt: (Float) -> Float, duration: Float): FloatArray {
        val n = (SAMPLE_RATE * duration).toInt()
        val out = FloatArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i / SAMPLE_RATE.toFloat()
            phase += freqAt(t) / SAMPLE_RATE
            phase -= floor(phase)
            out[i] = (2.0 * phase - 1.0).toFloat()
        }
        return out
    }

    private fun whiteNoise(duration: Float): FloatArray {
        val n = (SAMPLE_RATE * duration).toInt()
        return FloatArray(n) { Random.nextFloat() * 2f - 1f }
    }

    /** Linear attack, flat hold, exponential-ish decay — same shape as the JS prototype's envGain(). */
    private fun envelopeExp(n: Int, attack: Float, hold: Float, decay: Float, peak: Float): FloatArray {
        val out = FloatArray(n)
        val attackN = (attack * SAMPLE_RATE).toInt().coerceAtLeast(1)
        val holdN = (hold * SAMPLE_RATE).toInt()
        val decayN = (decay * SAMPLE_RATE).toInt().coerceAtLeast(1)
        for (i in 0 until n) {
            out[i] = when {
                i < attackN -> peak * (i.toFloat() / attackN)
                i < attackN + holdN -> peak
                i < attackN + holdN + decayN -> peak * exp(-5f * (i - attackN - holdN).toFloat() / decayN)
                else -> 0f
            }
        }
        return out
    }

    private fun applyEnvelope(signal: FloatArray, env: FloatArray): FloatArray {
        val n = minOf(signal.size, env.size)
        return FloatArray(signal.size) { if (it < n) signal[it] * env[it] else 0f }
    }

    /** Adds `source` into `into` starting at `offsetSeconds`, clipped to `into`'s bounds. */
    private fun add(into: FloatArray, source: FloatArray, offsetSeconds: Float) {
        val offset = (offsetSeconds * SAMPLE_RATE).toInt()
        for (i in source.indices) {
            val idx = i + offset
            if (idx in into.indices) into[idx] += source[i]
        }
    }

    private fun onePoleLowpass(signal: FloatArray, cutoffHz: Float): FloatArray {
        val out = FloatArray(signal.size)
        val rc = 1f / (2f * PI.toFloat() * cutoffHz)
        val dt = 1f / SAMPLE_RATE
        val alpha = dt / (rc + dt)
        var prev = 0f
        for (i in signal.indices) {
            prev += alpha * (signal[i] - prev)
            out[i] = prev
        }
        return out
    }

    private fun onePoleHighpass(signal: FloatArray, cutoffHz: Float): FloatArray {
        val lowpassed = onePoleLowpass(signal, cutoffHz)
        return FloatArray(signal.size) { signal[it] - lowpassed[it] }
    }

    private fun scaleAndNormalize(signal: FloatArray, masterGain: Float, targetPeak: Float = 0.85f): FloatArray {
        val scaled = FloatArray(signal.size) { signal[it] * masterGain }
        val maxAbs = scaled.maxOfOrNull { abs(it) }?.takeIf { it > 0.0001f } ?: return scaled
        val scale = (targetPeak / maxAbs).coerceAtMost(1f / masterGain) // never amplify above the designed gain
        return FloatArray(scaled.size) { (scaled[it] * scale).coerceIn(-1f, 1f) }
    }

    // ─── Wiedergabe ─────────────────────────────────────────────────────────────

    private suspend fun playSamples(samples: FloatArray) = withContext(Dispatchers.Default) {
        val pcm = ShortArray(samples.size) { (samples[it].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort() }

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        val track = AudioTrack(
            attributes, format, pcm.size * 2,
            AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        try {
            track.write(pcm, 0, pcm.size)
            track.play()
            delay(samples.size * 1000L / SAMPLE_RATE + 150L) // + kleine Sicherheitsmarge
        } finally {
            track.stop()
            track.release()
        }
    }
}
