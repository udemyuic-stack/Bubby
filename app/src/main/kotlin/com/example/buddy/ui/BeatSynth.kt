package com.example.buddy.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

class BeatSynth {
    @Volatile private var track: AudioTrack? = null
    @Volatile private var released = false
    private var playing = false
    private var lastHead = 0
    private var lastNano = 0L
    private var frames = 0

    fun prepare(chart: BeatChart): Boolean {
        val pcm = try { render(chart) } catch (e: Throwable) {
            android.util.Log.w("Buddy", "beat render failed", e)
            return false
        }
        var made: AudioTrack? = null
        return try {
            val t = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
            made = t
            if (t.state == AudioTrack.STATE_UNINITIALIZED) {
                android.util.Log.w("Buddy", "beat track uninitialized frames=${pcm.size}")
                t.release()
                return false
            }
            val wrote = t.write(pcm, 0, pcm.size)
            if (wrote != pcm.size || t.state != AudioTrack.STATE_INITIALIZED) {
                android.util.Log.w("Buddy", "beat write=$wrote of ${pcm.size} state=${t.state}")
                t.release()
                return false
            }
            frames = pcm.size
            if (released) {
                android.util.Log.w("Buddy", "beat released during prepare")
                t.release()
                return false
            }
            track = t
            lastHead = 0
            true
        } catch (e: Exception) {
            android.util.Log.w("Buddy", "beat track failed", e)
            made?.release()
            false
        }
    }

    fun play() {
        val t = track ?: return
        try { t.play() } catch (e: Exception) { return }
        playing = true
        lastNano = System.nanoTime()
        lastHead = t.playbackHeadPosition
    }

    fun pause() {
        val t = track ?: return
        if (playing) { try { t.pause() } catch (e: Exception) {} }
        playing = false
    }

    fun position(): Float {
        val t = track ?: return 0f
        val head = t.playbackHeadPosition
        val now = System.nanoTime()
        if (head != lastHead) { lastHead = head; lastNano = now }
        val extra = if (playing) min((now - lastNano) / 1e9f, 0.05f) else 0f
        return head.toFloat() / RATE + extra
    }

    fun finished() = track != null && lastHead >= frames - 1

    fun release() {
        released = true
        val t = track ?: return
        try { t.stop() } catch (e: Exception) {}
        t.release()
        track = null
        playing = false
    }

    private fun render(chart: BeatChart): ShortArray {
        val beat = chart.beatSec
        val n = ((chart.duration + 1.5f) * RATE).toInt()
        val mix = FloatArray(n)
        val rnd = Random(5)

        fun at(sec: Float) = (sec * RATE).toInt()

        fun kick(start: Int, vol: Float) {
            var ph = 0.0
            val len = at(0.2f)
            for (i in 0 until len) {
                val s = start + i
                if (s >= n) return
                val tt = i.toFloat() / RATE
                ph += 2 * PI * (45 + 90 * exp(-tt * 25f)) / RATE
                mix[s] += sin(ph).toFloat() * exp(-tt * 14f) * vol
            }
        }

        fun snare(start: Int, vol: Float) {
            val len = at(0.15f)
            for (i in 0 until len) {
                val s = start + i
                if (s >= n) return
                val tt = i.toFloat() / RATE
                val noise = rnd.nextFloat() * 2f - 1f
                mix[s] += (noise * 0.5f * exp(-tt * 22f) + sin(2 * PI * 190 * tt).toFloat() * 0.3f * exp(-tt * 30f)) * vol
            }
        }

        fun hat(start: Int, vol: Float) {
            val len = at(0.05f)
            var prev = 0f
            for (i in 0 until len) {
                val s = start + i
                if (s >= n) return
                val tt = i.toFloat() / RATE
                val noise = rnd.nextFloat() * 2f - 1f
                mix[s] += (noise - prev) * 0.5f * exp(-tt * 90f) * vol
                prev = noise
            }
        }

        fun tone(start: Int, freq: Float, lenSec: Float, vol: Float, decay: Float, square: Float) {
            val len = at(lenSec)
            var ph = 0.0
            val step = freq / RATE
            for (i in 0 until len) {
                val s = start + i
                if (s >= n) return
                val tt = i.toFloat() / RATE
                ph += step
                val f = (ph - Math.floor(ph)).toFloat()
                val tri = 2f * abs(2f * f - 1f) - 1f
                val sq = if (f < 0.5f) 1f else -1f
                val env = min(1f, tt * 200f) * exp(-tt * decay)
                mix[s] += (tri * (1f - square) + sq * square) * env * vol
            }
        }

        fun hz(semi: Int, base: Float) = base * 2f.pow(semi / 12f)

        for (b in 0 until chart.totalBeats) {
            val s = at(b * beat)
            val bar = (b - chart.leadBeats).coerceAtLeast(0) / 4
            val prog = BeatTracks.progression[bar % BeatTracks.progression.size]
            if (b < chart.leadBeats) {
                hat(s, 0.6f)
                if (b == chart.leadBeats - 1) kick(s, 0.5f)
                continue
            }
            if (b % 2 == 0) kick(s, 0.7f) else snare(s, 0.6f)
            hat(s, 0.35f)
            hat(s + at(beat * 0.5f), 0.22f)
            val bassSemi = prog + if (b % 4 == 2) 7 else 0
            tone(s, hz(bassSemi, 55f), beat * 0.9f, 0.32f, 4f, 0.5f)
        }

        for (note in chart.notes) {
            tone(at(note.time), hz(note.pitch + 12, 220f), 0.24f + note.len, 0.28f, 7f, 0.25f)
        }

        val out = ShortArray(n)
        for (i in 0 until n) {
            val v = mix[i] * 0.8f
            out[i] = (v.coerceIn(-1f, 1f) * 32000f).toInt().toShort()
        }
        return out
    }

    companion object {
        const val RATE = 22050
    }
}
