package com.radarlite.alert

import android.content.Context
import android.media.*
import kotlinx.coroutines.*
import kotlin.math.*

class SoundManager(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun play(stage: AlertStage) {
        if (audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) return
        scope.launch {
            when (stage) {
                AlertStage.WARNING -> burst(freqHz = 880f, count = 3, durationMs = 90, gapMs = 220)
                AlertStage.URGENT  -> burst(freqHz = 1_200f, count = 3, durationMs = 700, gapMs = 120)
            }
        }
    }

    // One alert burst is intentionally short so repeated bursts are easy to count.
    private suspend fun burst(freqHz: Float, count: Int, durationMs: Int, gapMs: Long) {
        repeat(count) { index ->
            beep(freqHz, durationMs)
            if (index < count - 1) delay(gapMs)
        }
    }

    private suspend fun beep(freqHz: Float, durationMs: Int) {
        val sampleRate = 44_100
        val numSamples = sampleRate * durationMs / 1000
        val samples = ShortArray(numSamples)
        val fadeLen = minOf(numSamples / 8, 800)

        for (i in samples.indices) {
            val raw = (sin(2 * Math.PI * i * freqHz / sampleRate) * Short.MAX_VALUE * 0.75).toInt().toShort()
            samples[i] = when {
                i < fadeLen -> (raw * i.toFloat() / fadeLen).toInt().toShort()
                i > numSamples - fadeLen -> (raw * (numSamples - i).toFloat() / fadeLen).toInt().toShort()
                else -> raw
            }
        }

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(samples, 0, samples.size)
        track.play()
        delay(durationMs.toLong() + 30)
        track.stop()
        track.release()
    }

    fun release() = scope.cancel()
}
