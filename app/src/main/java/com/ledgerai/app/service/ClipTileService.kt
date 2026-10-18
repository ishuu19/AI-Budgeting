package com.ledgerai.app.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService

/** Shade tile: copy, pull down, tap. The activity reads the clipboard once it has focus. */
class ClipTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val intent = NotebookPanelActivity.intent(this, listen = false, clipboard = true, gesture = false)
        if (Build.VERSION.SDK_INT >= 34) {
            val pending = PendingIntent.getActivity(
                this, 8, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
