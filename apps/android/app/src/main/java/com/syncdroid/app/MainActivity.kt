package com.syncdroid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.syncdroid.app.ui.SyncDroidApp
import com.syncdroid.app.service.SyncServiceController
import com.syncdroid.app.update.AndroidUpdateProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AndroidUpdateProvider.schedule(this, checkNow = true)
        setContent { SyncDroidApp() }
    }

    override fun onStart() {
        super.onStart()
        SyncServiceController.setAppInForeground(true)
    }

    override fun onResume() {
        super.onResume()
        SyncServiceController.start(this)
    }

    override fun onStop() {
        SyncServiceController.setAppInForeground(false)
        super.onStop()
    }
}
