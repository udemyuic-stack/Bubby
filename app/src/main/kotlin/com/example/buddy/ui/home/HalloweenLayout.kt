package com.example.buddy.ui.home

import com.example.buddy.ui.GlassKind

class PetalSpec(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val rotation: Float,
    val kind: GlassKind,
    val icon: PetalIcon,
    val label: String,
    val onTap: (HomeScene.Host) -> Unit
)

object HalloweenLayout {
    const val MASCOT_LEFT = 137f
    const val MASCOT_TOP = 134f
    const val MASCOT_WIDTH = 116f
    const val MASCOT_HEIGHT = 93f
    const val ART_WIDTH = 170f
    const val ART_HEIGHT = 136f
    const val CENTER_X = 195f
    const val FLOWER_X = 193f
    const val FLOWER_Y = 470f
    const val HIT_INSET = 0.1f

    val petals = listOf(
        PetalSpec(186f, 310f, 104f, 112f, -6f, GlassKind.Mid, PetalIcon.HAND, "Проверить:\nпривет") { it.ask("привет") },
        PetalSpec(237f, 379f, 116f, 106f, 12f, GlassKind.Active, PetalIcon.PLAY, "Запустить") { it.toggle() },
        PetalSpec(44f, 459f, 104f, 112f, -28f, GlassKind.Dim, PetalIcon.CHECK, "Задачи") { it.nav("tasks") },
        PetalSpec(246f, 459f, 104f, 112f, 28f, GlassKind.Dim, PetalIcon.GRID, "Ещё") { it.nav("more") },
        PetalSpec(100f, 524f, 104f, 112f, -8f, GlassKind.Dim, PetalIcon.CALENDAR, "Календарь") { it.nav("calendar") },
        PetalSpec(184f, 524f, 104f, 112f, 8f, GlassKind.Dim, PetalIcon.SETTINGS, "Настройки") { it.nav("settings") }
    )
}
