package com.example.buddy

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import com.example.buddy.ui.Backend
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class SttClient(private val ctx: Context, private val main: Handler) {
    @Volatile private var job: Thread? = null
    @Volatile private var cancelled = false

    val active get() = job != null

    val configured get() = url().isNotBlank() && token().isNotBlank()

    private fun url() = Backend.str(ctx, KEY_URL).trim().trimEnd('/').ifEmpty { BuildConfig.STT_URL }

    private fun token() = Backend.str(ctx, KEY_TOKEN).trim().ifEmpty { BuildConfig.STT_TOKEN }

    fun cancel() {
        cancelled = true
        job?.interrupt()
        job = null
    }

    fun listen(done: (String?, String?) -> Unit, fallback: () -> Unit) {
        cancel()
        cancelled = false
        val t = Thread {
            val outcome = try { transcribe(record()) } catch (_: Exception) { Outcome.Failed }
            main.post {
                if (job !== Thread.currentThread() && job != null) return@post
                job = null
                if (cancelled) return@post
                when (outcome) {
                    is Outcome.Text -> done(outcome.value, null)
                    Outcome.Silence -> done(null, null)
                    Outcome.Failed -> fallback()
                }
            }
        }
        job = t
        t.start()
    }

    private sealed interface Outcome {
        data class Text(val value: String) : Outcome
        data object Silence : Outcome
        data object Failed : Outcome
    }

    @SuppressLint("MissingPermission")
    private fun record(): ByteArray? {
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) throw IllegalStateException("no audio input")
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2)
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); throw IllegalStateException("audio input busy") }
        val pcm = ByteArrayOutputStream()
        val chunk = ShortArray(CHUNK_SAMPLES)
        var spoke = false
        var quietMs = 0
        var elapsedMs = 0
        var chunks = 0
        var floor = Double.MAX_VALUE
        var threshold = SPEECH_RMS_MAX
        val preRoll = ArrayDeque<ByteArray>()
        fun pack(n: Int): ByteArray {
            val bytes = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until n) bytes.putShort(chunk[i])
            return bytes.array()
        }
        try {
            rec.startRecording()
            while (!cancelled && elapsedMs < MAX_MS) {
                val n = rec.read(chunk, 0, chunk.size)
                if (n <= 0) throw IllegalStateException("audio read failed")
                val ms = n * 1000 / RATE
                elapsedMs += ms
                val level = rms(chunk, n)
                if (chunks < CALIBRATION_CHUNKS) {
                    floor = minOf(floor, level)
                    threshold = (floor * NOISE_FACTOR).coerceIn(SPEECH_RMS_MIN, SPEECH_RMS_MAX)
                }
                chunks++
                val loud = level > threshold
                if (loud) { quietMs = 0 } else quietMs += ms
                if (loud && !spoke) {
                    spoke = true
                    preRoll.forEach { pcm.write(it) }
                    preRoll.clear()
                }
                if (spoke) {
                    pcm.write(pack(n))
                    if (quietMs >= END_QUIET_MS) break
                } else {
                    preRoll.addLast(pack(n))
                    if (preRoll.size > PRE_ROLL_CHUNKS) preRoll.removeFirst()
                    if (elapsedMs >= NO_SPEECH_MS) break
                }
            }
        } finally {
            try { rec.stop() } catch (_: Exception) {}
            rec.release()
        }
        return if (spoke && !cancelled) wav(pcm.toByteArray()) else null
    }

    private fun rms(buf: ShortArray, n: Int): Double {
        var sum = 0.0
        for (i in 0 until n) sum += buf[i].toDouble() * buf[i]
        return sqrt(sum / n)
    }

    private fun wav(pcm: ByteArray): ByteArray {
        val head = ByteBuffer.allocate(WAV_HEADER).order(ByteOrder.LITTLE_ENDIAN)
        head.put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVEfmt ".toByteArray())
        head.putInt(16).putShort(1).putShort(1).putInt(RATE).putInt(RATE * 2).putShort(2).putShort(16)
        head.put("data".toByteArray()).putInt(pcm.size)
        return head.array() + pcm
    }

    private fun transcribe(audio: ByteArray?): Outcome {
        if (audio == null) return Outcome.Silence
        val c = URL(url() + "/transcribe").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = CONNECT_MS
            c.readTimeout = READ_MS
            c.doOutput = true
            c.setRequestProperty("Authorization", "Bearer " + token())
            c.setRequestProperty("Content-Type", "audio/wav")
            c.setFixedLengthStreamingMode(audio.size)
            c.outputStream.use { it.write(audio) }
            if (c.responseCode != 200) return Outcome.Failed
            val text = org.json.JSONObject(c.inputStream.bufferedReader().use { it.readText() }).optString("text").trim()
            return if (text.isEmpty()) Outcome.Silence else Outcome.Text(text)
        } finally {
            c.disconnect()
        }
    }

    companion object {
        const val KEY_URL = "stt_url"
        const val KEY_TOKEN = "stt_token"
        private const val RATE = 16000
        private const val CHUNK_SAMPLES = 1600
        private const val SPEECH_RMS_MAX = 900.0
        private const val SPEECH_RMS_MIN = 250.0
        private const val NOISE_FACTOR = 2.5
        private const val CALIBRATION_CHUNKS = 3
        private const val PRE_ROLL_CHUNKS = 2
        private const val END_QUIET_MS = 1300
        private const val NO_SPEECH_MS = 6000
        private const val MAX_MS = 20000
        private const val WAV_HEADER = 44
        private const val CONNECT_MS = 4000
        private const val READ_MS = 30000
    }
}
