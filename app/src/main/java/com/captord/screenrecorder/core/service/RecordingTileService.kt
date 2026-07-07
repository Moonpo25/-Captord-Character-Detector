package com.captord.screenrecorder.core.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.captord.screenrecorder.TransparentPermissionActivity

class RecordingTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (ScreenRecorderService.isSessionActive()) {
            ScreenRecorderService.sendAction(this, ScreenRecorderService.ACTION_STOP)
            updateTile()
            return
        }
        val intent = Intent(this, TransparentPermissionActivity::class.java).apply {
            action = TransparentPermissionActivity.ACTION_START
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this,
                2001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val active = ScreenRecorderService.isSessionActive()
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (active) "Stop Captord" else "Record screen"
        tile.contentDescription = tile.label
        tile.updateTile()
    }
}
