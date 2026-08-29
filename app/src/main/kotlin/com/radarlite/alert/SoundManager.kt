package com.radarlite.alert

import android.content.Context
import android.media.*
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.*
import java.util.Locale

class SoundManager(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: Speech? = null
    private var activeAlertSpeechId: String? = null
    private var blockSpeedSpeech = false
    private val alertId = AtomicInteger()
    private val utteranceId = AtomicInteger()

    private data class Speech(val text: String, val alert: Int? = null)

    fun play(
        stage: AlertStage,
        speedLimit: Int? = null,
        cameraType: String? = null,
        overspeed: Boolean = false
    ) {
        if (audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) return
        val id = alertId.incrementAndGet()
        reserveSpeechForAlert(id)
        scope.launch {
            when (stage) {
                AlertStage.WARNING -> {
                    // A single short tone is noticeable without competing with navigation instructions.
                    beep(freqHz = 880f, durationMs = 150)
                    warningPhrase(speedLimit, cameraType, overspeed)?.let { speak(it, id) }
                }
                AlertStage.URGENT  -> {
                    beep(freqHz = 1_200f, durationMs = 500)
                    finishAlert(id)
                }
            }
        }
    }

    // Speed announcements intentionally have no tone: only the selected number is spoken.
    fun speakSpeed(speedKmh: Int) = speak(speedKmh.toString())

    private fun warningPhrase(speedLimit: Int?, cameraType: String?, overspeed: Boolean): String? {
        if (overspeed && speedLimit != null) return "Over speed limit $speedLimit"
        return when (cameraType) {
            "red_light" -> "Red light"
            "average_speed" -> speedLimit?.let { "Average speed zone $it" } ?: "Average speed zone"
            "sharp_curve" -> "Sharp curve ahead"
            "dangerous_junction" -> "Dangerous junction ahead"
            "level_crossing" -> "Level crossing ahead"
            "traffic_calming" -> "Traffic calming ahead"
            else -> speedLimit?.let { "Speed limit $it" } ?: "Speed limit"
        }
    }

    private fun reserveSpeechForAlert(id: Int) {
        mainHandler.post {
            if (id != alertId.get()) return@post
            blockSpeedSpeech = true
            pendingSpeech = null
            activeAlertSpeechId = null
            tts?.stop()
        }
    }

    private fun finishAlert(id: Int) {
        mainHandler.post {
            if (id == alertId.get()) blockSpeedSpeech = false
        }
    }

    private fun speak(text: String, alert: Int? = null) {
        // Alerts reserve speech before their tone, so a speed update cannot interrupt them.
        mainHandler.post {
            if (alert != null && alert != alertId.get()) return@post
            if (alert == null && blockSpeedSpeech) return@post
            val speech = Speech(text, alert)
            if (ttsReady) sayNow(speech) else {
                pendingSpeech = speech
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
                    blockSpeedSpeech = false
                    pendingSpeech = null
                    return@post
                }
                tts?.language = Locale.getDefault()
                tts?.setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String) = Unit

                    override fun onDone(utteranceId: String) {
                        mainHandler.post {
                            if (utteranceId == activeAlertSpeechId) {
                                activeAlertSpeechId = null
                                blockSpeedSpeech = false
                            }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String) = onDone(utteranceId)
                })
                pendingSpeech?.let(::sayNow)
                pendingSpeech = null
            }
        }
    }

    private fun sayNow(speech: Speech) {
        val id = "radarlite_${utteranceId.incrementAndGet()}"
        if (speech.alert != null) activeAlertSpeechId = id
        if (tts?.speak(speech.text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.ERROR) {
            if (id == activeAlertSpeechId) {
                activeAlertSpeechId = null
                blockSpeedSpeech = false
            }
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
            activeAlertSpeechId = null
            blockSpeedSpeech = false
            ttsReady = false
            tts?.shutdown()
            tts = null
        }
        scope.cancel()
    }
}
