package com.example.buddy

import android.content.ComponentName
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.view.KeyEvent

object MediaControl {

    private fun sessions(ctx: Context): List<MediaController>? {
        return try {
            val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            msm.getActiveSessions(ComponentName(ctx, BuddyNotifications::class.java))
        } catch (e: SecurityException) { null } catch (e: Exception) { null }
    }

    private fun playing(ctx: Context): MediaController? =
        sessions(ctx)?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }

    private fun any(ctx: Context): MediaController? {
        val l = sessions(ctx) ?: return null
        return l.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: l.firstOrNull()
    }

    private fun key(ctx: Context, code: Int) {
        try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        } catch (e: Exception) { }
    }

    fun nowPlaying(ctx: Context): String? {
        val c = playing(ctx) ?: return null
        val md = c.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim().orEmpty()
        val artist = (md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST))?.trim().orEmpty()
        val app = try {
            ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(c.packageName, 0)).toString()
        } catch (e: Exception) { c.packageName }
        if (title.isEmpty() && artist.isEmpty()) return app
        val main = if (artist.isEmpty()) title else if (title.isEmpty()) artist else "$title — $artist"
        return "$main ($app)"
    }

    fun isPlaying(ctx: Context): Boolean {
        val l = sessions(ctx)
        if (l != null) return l.any { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        return try {
            (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager).isMusicActive
        } catch (e: Exception) { false }
    }

    fun pause(ctx: Context) {
        val c = playing(ctx)
        if (c != null) c.transportControls.pause()
        else if (sessions(ctx) == null && isPlaying(ctx)) key(ctx, KeyEvent.KEYCODE_MEDIA_PAUSE)
    }

    fun play(ctx: Context) {
        val c = any(ctx)
        if (c != null) c.transportControls.play() else key(ctx, KeyEvent.KEYCODE_MEDIA_PLAY)
    }

    fun toggle(ctx: Context) {
        if (isPlaying(ctx)) pause(ctx) else play(ctx)
    }

    fun next(ctx: Context) {
        val c = any(ctx)
        if (c != null) c.transportControls.skipToNext() else key(ctx, KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun previous(ctx: Context) {
        val c = any(ctx)
        if (c != null) c.transportControls.skipToPrevious() else key(ctx, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    fun seekBy(ctx: Context, deltaSec: Int): Boolean {
        val c = any(ctx) ?: return false
        val st = c.playbackState ?: return false
        if (st.actions and PlaybackState.ACTION_SEEK_TO == 0L) return false
        val dur = c.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        var base = st.position
        if (st.state == PlaybackState.STATE_PLAYING && st.lastPositionUpdateTime > 0) {
            base += ((android.os.SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
        }
        var pos = base + deltaSec * 1000L
        if (dur > 0) pos = minOf(pos, dur)
        pos = maxOf(pos, 0L)
        return try { c.transportControls.seekTo(pos); true } catch (e: Exception) { false }
    }

    fun stop(ctx: Context) {
        val c = any(ctx)
        if (c != null) c.transportControls.stop() else key(ctx, KeyEvent.KEYCODE_MEDIA_STOP)
    }

    fun setVolumePercent(ctx: Context, percent: Int) {
        try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val v = Math.round(max * percent.coerceIn(0, 100) / 100f)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
        } catch (e: Exception) { }
    }

    /** Ducks other audio while the assistant speaks; call the returned lambda to release. */
    fun requestFocusForSpeech(ctx: Context): () -> Unit {
        return try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .build()
            am.requestAudioFocus(req)
            val done = { try { am.abandonAudioFocusRequest(req) } catch (e: Exception) { }; Unit }
            done
        } catch (e: Exception) { {} }
    }
}
