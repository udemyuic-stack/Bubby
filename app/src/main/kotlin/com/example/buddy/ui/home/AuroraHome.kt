package com.example.buddy.ui.home

import android.content.Context

class AuroraHome private constructor(ctx: Context, host: Host, mascot: AuroraMascot) :
    IceHome(ctx, host, mascot, AuroraShow(mascot), decor = false) {
    constructor(ctx: Context, host: Host) : this(ctx, host, AuroraMascot())
}
