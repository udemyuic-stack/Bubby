package com.example.buddy.ui.home

import android.content.Context

class DesertHome private constructor(ctx: Context, host: Host, mascot: DesertMascot) :
    IceHome(ctx, host, mascot, DesertShow(mascot), decor = false) {
    constructor(ctx: Context, host: Host) : this(ctx, host, DesertMascot())
}
