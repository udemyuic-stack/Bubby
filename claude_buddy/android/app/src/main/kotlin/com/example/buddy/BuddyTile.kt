package com.example.buddy

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.graphics.drawable.Icon
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile that summons the mascot and starts voice listening. */
class BuddyTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        try {
            if (Settings.canDrawOverlays(this)) {
                startForegroundService(Intent(this, BuddyService::class.java).setAction("com.example.buddy.LISTEN"))
            } else {
                val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (Build.VERSION.SDK_INT >= 34) {
                    try {
                        val pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                        startActivityAndCollapse(pi)
                    } catch (_: Exception) {
                        try { @Suppress("DEPRECATION") startActivityAndCollapse(i) } catch (_: Exception) {
                            try { startActivity(i) } catch (_: Exception) {}
                        }
                    }
                } else {
                    @Suppress("DEPRECATION") startActivityAndCollapse(i)
                }
            }
        } catch (_: Exception) {
        }
        refresh()
    }

    private fun refresh() {
        val t = qsTile ?: return
        t.label = "Bubby"
        t.icon = Icon.createWithResource(this, R.drawable.ic_tile)
        t.state = if (BuddyService.running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.updateTile()
    }
}
