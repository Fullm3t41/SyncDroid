package com.syncdroid.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SyncBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            SyncServiceController.start(context)
        }
    }
}
