package com.example.buddy

import android.content.Context
import android.content.pm.PackageManager

/** Music apps Bubby knows how to hand a search to. Only apps listed in the manifest's <queries> are visible on Android 11+. */
object MusicApps {
    class App(val id: String, val label: String, val kind: String)

    val ALL = listOf(
        App("com.google.android.apps.youtube.music", "YouTube Music", "streaming"),
        App("com.spotify.music", "Spotify", "streaming"),
        App("com.google.android.youtube", "YouTube", "video"),
        App("ru.yandex.music", "Яндекс Музыка", "streaming"),
        App("com.apple.android.music", "Apple Music", "streaming"),
        App("deezer.android.app", "Deezer", "streaming"),
        App("com.soundcloud.android", "SoundCloud", "streaming"),
        App("com.amazon.mp3", "Amazon Music", "streaming"),
        App("com.zvooq.openplay", "Звук", "streaming"),
        App("com.vkontakte.android", "VK (VK Музыка)", "streaming"),
        App("com.sec.android.app.music", "Samsung Music", "local"),
        App("com.miui.player", "Mi Music", "local"),
        App("org.videolan.vlc", "VLC", "local"),
    )

    fun installed(ctx: Context, id: String): Boolean = try {
        ctx.packageManager.getPackageInfo(id, 0); true
    } catch (_: PackageManager.NameNotFoundException) { false } catch (_: Exception) { false }

    fun status(ctx: Context): List<Map<String, Any>> =
        ALL.map { mapOf("id" to it.id, "label" to it.label, "kind" to it.kind, "installed" to installed(ctx, it.id)) }

    fun installedLabels(ctx: Context): String =
        ALL.filter { installed(ctx, it.id) }.joinToString(", ") { it.label }
}
