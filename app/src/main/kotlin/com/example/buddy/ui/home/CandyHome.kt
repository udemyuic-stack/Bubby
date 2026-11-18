package com.example.buddy.ui.home

import android.content.Context

class CandyHome private constructor(ctx: Context, host: Host, mascot: CandyMascot) :
    IceHome(ctx, host, mascot, CandyShow(mascot), decor = false) {
    constructor(ctx: Context, host: Host) : this(ctx, host, CandyMascot())
}
