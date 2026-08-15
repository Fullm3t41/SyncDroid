package com.syncdroid.app.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.syncdroid.app.MainActivity
import com.syncdroid.app.R

class SyncNotificationCenter(context: Context) {
    private val appContext = context.applicationContext
    private val manager = NotificationManagerCompat.from(appContext)

    init { createChannels() }

    @SuppressLint("MissingPermission")
    fun showSyncStarted(peerName: String) {
        if (!canNotify()) return
        manager.notify(SYNC_NOTIFICATION_ID, NotificationCompat.Builder(appContext, CHANNEL_SYNC)
            .setSmallIcon(R.drawable.ic_syncdroid)
            .setContentTitle("Syncing with $peerName")
            .setContentText("Comparing indexes and transferring changed files")
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .build())
    }

    @SuppressLint("MissingPermission")
    fun showSyncComplete(peerName: String) {
        if (!canNotify()) return
        manager.notify(SYNC_NOTIFICATION_ID, NotificationCompat.Builder(appContext, CHANNEL_SYNC)
            .setSmallIcon(R.drawable.ic_syncdroid)
            .setContentTitle("Sync complete")
            .setContentText("Files are up to date with $peerName")
            .setAutoCancel(true)
            .setTimeoutAfter(8_000)
            .setContentIntent(openAppIntent())
            .build())
    }

    @SuppressLint("MissingPermission")
    fun showSyncFailed(peerName: String) {
        if (!canNotify()) return
        manager.notify(SYNC_NOTIFICATION_ID, NotificationCompat.Builder(appContext, CHANNEL_ACTIONS)
            .setSmallIcon(R.drawable.ic_syncdroid)
            .setContentTitle("Sync needs attention")
            .setContentText("Could not finish syncing with $peerName. Tap to review.")
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build())
    }

    @SuppressLint("MissingPermission")
    fun showChatMessages(count: Int, authorName: String, preview: String) {
        if (!canNotify()) return
        val title = if (count == 1) authorName else "$count new mesh messages"
        manager.notify(CHAT_NOTIFICATION_ID, NotificationCompat.Builder(appContext, CHANNEL_CHAT)
            .setSmallIcon(R.drawable.ic_syncdroid)
            .setContentTitle(title)
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setNumber(count)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build())
    }

    @SuppressLint("MissingPermission")
    fun updateActionItems(conflicts: Int, foldersToConfigure: Int) {
        if (conflicts == 0 && foldersToConfigure == 0) {
            manager.cancel(ACTION_NOTIFICATION_ID)
            return
        }
        if (!canNotify()) return
        val parts = buildList {
            if (conflicts > 0) add("$conflicts sync conflict${if (conflicts == 1) "" else "s"}")
            if (foldersToConfigure > 0) add("$foldersToConfigure folder${if (foldersToConfigure == 1) "" else "s"} to configure")
        }
        manager.notify(ACTION_NOTIFICATION_ID, NotificationCompat.Builder(appContext, CHANNEL_ACTIONS)
            .setSmallIcon(R.drawable.ic_syncdroid)
            .setContentTitle("SyncDroid needs your input")
            .setContentText(parts.joinToString(" · "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(parts.joinToString(" · ")))
            .setNumber(conflicts + foldersToConfigure)
            .setOngoing(true)
            .setContentIntent(openAppIntent())
            .build())
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        appContext,
        0,
        Intent(appContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val system = appContext.getSystemService(NotificationManager::class.java)
        system.createNotificationChannels(listOf(
            NotificationChannel(CHANNEL_SYNC, "Sync progress", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Current local mesh synchronization progress"
            },
            NotificationChannel(CHANNEL_ACTIONS, "Action required", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Conflicts, folder configuration and failed synchronization"
            },
            NotificationChannel(CHANNEL_CHAT, "Mesh chat", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Messages from trusted devices in your local mesh"
            },
        ))
    }

    private companion object {
        const val CHANNEL_SYNC = "sync_progress"
        const val CHANNEL_ACTIONS = "sync_actions"
        const val CHANNEL_CHAT = "mesh_chat"
        const val SYNC_NOTIFICATION_ID = 1001
        const val ACTION_NOTIFICATION_ID = 1002
        const val CHAT_NOTIFICATION_ID = 1003
    }
}
