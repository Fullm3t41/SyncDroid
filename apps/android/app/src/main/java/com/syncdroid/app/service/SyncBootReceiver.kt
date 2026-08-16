package com.syncdroid.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.syncdroid.app.update.AndroidUpdateProvider

class SyncBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            AndroidUpdateProvider.schedule(context, checkNow = true)
            SyncServiceController.start(context)
        }
    }
}
