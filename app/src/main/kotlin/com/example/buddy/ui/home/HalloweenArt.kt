package com.example.buddy.ui.home

import android.graphics.Path
import android.graphics.RectF

enum class PetalIcon { HAND, PLAY, CHECK, GRID, CALENDAR, SETTINGS }

object HalloweenArt {
    const val ICON_SIZE = 24f
    const val WEB_SIZE = 40f

    val web: Path = PathData.parse(
        "M0 0 L40 0 M0 0 L36 16 M0 0 L26 30 M0 0 L12 38 M0 0 L0 40 " +
            "M12 0 Q11 4 11 5 Q8 8 7 9 Q4 10 3 11 Q1 11 0 12 " +
            "M24 0 Q22 7 22 10 Q17 15 15 18 Q9 20 7 23 Q2 23 0 24"
    )

    val ghostBody: Path = PathData.parse(
        "M30 118 C30 58 50 16 85 16 C120 16 140 58 140 118 C134 110 128 110 122 120 C116 110 108 110 102 120 " +
            "C96 110 88 110 82 120 C76 110 68 110 62 120 C56 110 48 110 42 120 C38 112 34 112 30 118Z"
    )
    val ghostArmLeft: Path = PathData.parse("M36 66 C22 74 14 92 16 108 C24 104 30 94 40 84Z")
    val ghostArmRight: Path = PathData.parse("M134 66 C148 74 156 92 154 108 C146 104 140 94 130 84Z")
    val ghostFangs: Path = PathData.parse("M76 84 L80 96 L84 84Z M86 84 L90 96 L94 84Z")
    val ghostLowerFangs: Path = PathData.parse("M78 106 L82 98 L86 106Z M84 106 L88 98 L92 106Z")

    private val icons = HashMap<PetalIcon, Path>()

    fun icon(icon: PetalIcon): Path = icons.getOrPut(icon) {
        when (icon) {
            PetalIcon.HAND -> hand()
            PetalIcon.PLAY -> PathData.parse("M8 5 L19 12 L8 19Z")
            PetalIcon.CHECK -> check()
            PetalIcon.GRID -> grid()
            PetalIcon.CALENDAR -> calendar()
            PetalIcon.SETTINGS -> settings()
        }
    }

    private fun hand() = Path().apply {
        moveTo(8f, 13f); lineTo(8f, 6f); arcTo(RectF(8f, 4.5f, 11f, 7.5f), 180f, 180f); lineTo(11f, 11f)
        moveTo(11f, 11f); lineTo(11f, 4.5f); arcTo(RectF(11f, 3f, 14f, 6f), 180f, 180f); lineTo(14f, 11f)
        moveTo(14f, 11f); lineTo(14f, 6f); arcTo(RectF(14f, 4.5f, 17f, 7.5f), 180f, 180f); lineTo(17f, 13f)
        cubicTo(17f, 17f, 14.5f, 20f, 11f, 20f)
        cubicTo(8.5f, 20f, 7f, 18.5f, 5.5f, 16f)
        lineTo(4f, 13f)
        quadTo(4.2f, 11.6f, 6.5f, 11.5f)
        lineTo(8f, 13f)
    }

    private fun check() = Path().apply {
        addCircle(12f, 12f, 8f, Path.Direction.CW)
        moveTo(8.5f, 12.5f); lineTo(11f, 15f); lineTo(15.5f, 10f)
    }

    private fun grid() = Path().apply {
        for (row in 0 until 3) for (col in 0 until 3) addCircle(7f + col * 5f, 7f + row * 5f, 1.2f, Path.Direction.CW)
    }

    private fun calendar() = Path().apply {
        addRoundRect(RectF(4f, 5f, 20f, 20f), 3f, 3f, Path.Direction.CW)
        moveTo(4f, 10f); lineTo(20f, 10f)
        moveTo(8f, 3f); lineTo(8f, 7f)
        moveTo(16f, 3f); lineTo(16f, 7f)
    }

    private fun settings() = Path().apply {
        addCircle(12f, 12f, 3f, Path.Direction.CW)
        moveTo(12f, 3f); lineTo(12f, 6f)
        moveTo(12f, 18f); lineTo(12f, 21f)
        moveTo(3f, 12f); lineTo(6f, 12f)
        moveTo(18f, 12f); lineTo(21f, 12f)
        moveTo(5.6f, 5.6f); lineTo(7.7f, 7.7f)
        moveTo(16.3f, 16.3f); lineTo(18.4f, 18.4f)
        moveTo(5.6f, 18.4f); lineTo(7.7f, 16.3f)
        moveTo(16.3f, 7.7f); lineTo(18.4f, 5.6f)
    }
}
