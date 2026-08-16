package com.syncdroid.app.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.syncdroid.app.BuildConfig
import com.syncdroid.shared.update.LastUpdateCheckStore
import com.syncdroid.shared.update.ReleaseUpdateService
import com.syncdroid.shared.update.UpdatePlatform
import java.util.concurrent.TimeUnit

object AndroidUpdateProvider {
    @Volatile private var service: ReleaseUpdateService? = null

    fun get(context: Context): ReleaseUpdateService = service ?: synchronized(this) {
        service ?: create(context.applicationContext).also { service = it }
    }

    fun schedule(context: Context, checkNow: Boolean) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val periodic = PeriodicWorkRequestBuilder<UpdateCheckWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic,
        )
        if (checkNow) {
            val immediate = OneTimeWorkRequestBuilder<UpdateCheckWorker>().setConstraints(constraints).build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                immediate,
            )
        }
    }

    private fun create(context: Context): ReleaseUpdateService {
        val preferences = context.getSharedPreferences("software_updates", Context.MODE_PRIVATE)
        return ReleaseUpdateService(
            currentVersion = BuildConfig.VERSION_NAME,
            platform = UpdatePlatform.Android,
            cacheDirectory = context.filesDir.toPath().resolve("updates"),
            lastCheck = { preferences.getLong(KEY_LAST_CHECK, 0L) },
            lastCheckStore = LastUpdateCheckStore { preferences.edit().putLong(KEY_LAST_CHECK, it).apply() },
        )
    }

    private const val KEY_LAST_CHECK = "last_check_millis"
    private const val PERIODIC_WORK_NAME = "syncdroid-daily-update-check"
    private const val IMMEDIATE_WORK_NAME = "syncdroid-boot-update-check"
}

class UpdateCheckWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        AndroidUpdateProvider.get(applicationContext).checkForUpdate(force = false)
        return Result.success()
    }
}
