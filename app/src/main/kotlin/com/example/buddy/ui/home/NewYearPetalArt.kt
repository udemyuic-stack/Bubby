package com.example.buddy.ui.home

import android.graphics.Path
import android.graphics.RectF

object NewYearPetalArt {
    const val CAP_VIEW_WIDTH = 100f
    const val CAP_VIEW_HEIGHT = 26f

    val capSnow: Path = PathData.parse(
        "M2 16 C2 8 10 4 18 6 C24 0 36 0 42 5 C50 0 62 1 66 6 C74 2 86 3 90 9 C97 9 99 14 98 18 " +
            "C97 21 92 21 90 20 C89 25 85 26 84 21 C78 23 70 22 66 21 C64 26 58 26 58 21 C48 23 36 22 30 21 " +
            "C28 25 23 25 22 21 C14 22 6 22 2 16Z"
    )
    val capShade: Path = PathData.parse(
        "M6 18 C20 21 40 20 58 20 C72 20 86 20 96 17 C97 20 92 21 90 20 C89 25 85 26 84 21 C78 23 70 22 66 21 " +
            "C64 26 58 26 58 21 C48 23 36 22 30 21 C28 25 23 25 22 21 C14 22 8 21 6 18Z"
    )
    val capGlint = RectF(24f, 5f, 44f, 9f)
}
