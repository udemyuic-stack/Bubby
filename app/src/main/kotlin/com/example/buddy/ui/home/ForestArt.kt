package com.example.buddy.ui.home

import android.graphics.Path

object ForestArt {
    const val BOLT_SIZE = 170f

    val flameOuter: Path = PathData.parse("M0 0 C-12 -6 -14 -24 0 -46 C5 -30 16 -22 12 -8 C10 -2 5 0 0 0Z")
    val flameInner: Path = PathData.parse("M0 0 C-7 -4 -8 -15 0 -28 C3 -18 9 -13 7 -5 C6 -1 3 0 0 0Z")
    val stemShade: Path = PathData.parse("M60 70 C100 125 160 118 193 96")
    val cloudBolt: Path = PathData.parse("M97 98 L82 124 L95 124 L78 154 L111 116 L97 116 L107 98Z")
    val cloudBody: Path = PathData.parse("M30 104 C12 104 5 86 17 74 C10 56 29 41 46 49 C50 29 72 19 90 27 C104 13 133 19 137 41 C155 39 168 56 159 72 C171 83 164 104 144 104Z")
}
