package com.captord.screenrecorder.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import com.captord.screenrecorder.MainActivity
import com.captord.screenrecorder.R

class RecordingNotificationController(private val service: Service) {

    fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = CHANNEL_DESC
            setShowBadge(false)
        }
        val manager = service.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun buildRecordingNotification(
        statusText: String = "Recording in progress",
        isPaused: Boolean = false,
    ): Notification {
        val pauseIntent = Intent(service, ScreenRecorderService::class.java).apply {
            action = ScreenRecorderService.ACTION_PAUSE
        }
        val resumeIntent = Intent(service, ScreenRecorderService::class.java).apply {
            action = ScreenRecorderService.ACTION_RESUME
        }
        val stopIntent = Intent(service, ScreenRecorderService::class.java).apply {
            action = ScreenRecorderService.ACTION_STOP
        }
        val contentIntent = Intent(service, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pausePendingIntent = PendingIntent.getService(service, 101, pauseIntent, flags)
        val resumePendingIntent = PendingIntent.getService(service, 102, resumeIntent, flags)
        val stopPendingIntent = PendingIntent.getService(service, 103, stopIntent, flags)
        val openPendingIntent = PendingIntent.getActivity(service, 100, contentIntent, flags)

        val builder = NotificationCompat.Builder(service, CHANNEL_ID)
            .setContentTitle(service.getString(R.string.app_name))
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (isPaused) {
            builder.addAction(android.R.drawable.ic_media_play, "Resume", resumePendingIntent)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)
        }
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
        return builder.build()
    }

    fun showRecordingNotification() {
        createNotificationChannel()
        val notification = buildRecordingNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            service.startForeground(NOTIFICATION_ID, notification, type)
        } else {
            service.startForeground(NOTIFICATION_ID, notification)
        }
    }

    fun updateNotificationText(text: String, isPaused: Boolean = false) {
        val notification = buildRecordingNotification(text, isPaused)
        val manager = service.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun stopNotification() {
        service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
        val manager = service.getSystemService(NotificationManager::class.java)
        manager.cancel(NOTIFICATION_ID)
    }

    fun showCompletionNotification() {
        val completionChannelId = "captord_completion"
        val manager = service.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            completionChannelId,
            "Recording Complete",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Shown after recording finishes"
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)

        val openIntent = Intent(service, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(service, 0, openIntent, flags)

        val notification = NotificationCompat.Builder(service, completionChannelId)
            .setContentTitle("Recording saved")
            .setContentText("Tap to open Captord")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        manager.notify(COMPLETION_NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "captord_recording"
        const val NOTIFICATION_ID = 1
        const val COMPLETION_NOTIFICATION_ID = 2
        private const val CHANNEL_NAME = "Screen Recording"
        private const val CHANNEL_DESC = "Shows while screen recording is active"
    }
}
