package com.radarlite.alert

import android.content.Context
import android.media.*
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.*
import kotlin.math.*
import java.util.Locale

class SoundManager(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false
    @Volatile private var pendingSpeech: String? = null

    fun play(stage: AlertStage, speedLimit: Int? = null, cameraType: String? = null) {
        if (audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) return
        scope.launch {
            when (stage) {
                AlertStage.WARNING -> {
                    burst(freqHz = 880f, count = 3, durationMs = 90, gapMs = 220)
                    warningPhrase(speedLimit, cameraType)?.let { speak(it) }
                }
                AlertStage.URGENT  -> {
                    clearSpeech()
                    beep(freqHz = 1_200f, durationMs = 1000)
                }
            }
        }
    }

    private fun warningPhrase(speedLimit: Int?, cameraType: String?): String? = when (cameraType) {
        "red_light"     -> "Red light"
        "average_speed" -> speedLimit?.let { "Average speed zone $it" } ?: "Average speed zone"
        else            -> speedLimit?.let { "Speed limit $it" } ?: "Speed limit"
    }

    // One alert burst is intentionally short so repeated bursts are easy to count.
    private suspend fun burst(freqHz: Float, count: Int, durationMs: Int, gapMs: Long) {
        repeat(count) { index ->
            beep(freqHz, durationMs)
            if (index < count - 1) delay(gapMs)
        }
    }

    private fun speak(text: String) {
        // TTS starts only when needed; alerts still beep if the engine is not ready yet.
        mainHandler.post {
            if (ttsReady) sayNow(text) else {
                pendingSpeech = text
                ensureTts()
            }
        }
    }

    private fun ensureTts() {
        if (tts != null) return
        tts = TextToSpeech(appContext) { status ->
            mainHandler.post {
                ttsReady = status == TextToSpeech.SUCCESS
                if (!ttsReady) {
                    tts = null
                    pendingSpeech = null
                    return@post
                }
                tts?.language = Locale.getDefault()
                tts?.setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                pendingSpeech?.let { sayNow(it) }
                pendingSpeech = null
            }
        }
    }

    private fun sayNow(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "radarlite_limit")
    }

    private fun clearSpeech() {
        mainHandler.post {
            pendingSpeech = null
            tts?.stop()
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

    fun release() {
        mainHandler.post {
            pendingSpeech = null
            ttsReady = false
            tts?.shutdown()
            tts = null
        }
        scope.cancel()
    }
}
