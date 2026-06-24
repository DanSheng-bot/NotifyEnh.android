package com.dansheng.notifyenh.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dansheng.notifyenh.data.prefs.AppPreferences
import com.dansheng.notifyenh.service.NotifyEnhService
import com.dansheng.notifyenh.util.LogUtils
import com.dansheng.notifyenh.util.PermissionUtils
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class ServiceCheckWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val appPreferences = AppPreferences(applicationContext)
        val isManuallyStopped = appPreferences.isManuallyStoppedFlow.first()
        val isEnabled = PermissionUtils.isNotificationServiceEnabled(applicationContext)
        val isRunning = NotifyEnhService.isServiceRunning.value

        LogUtils.d("ServiceCheckWorker: isEnabled=$isEnabled, isRunning=$isRunning, isManuallyStopped=$isManuallyStopped")

        if (isEnabled && !isManuallyStopped && !isRunning) {
            LogUtils.d("ServiceCheckWorker: Service not running, attempting to reconnect...")
            NotifyEnhService.tryReconnectService(applicationContext)
        }

        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "ServiceCheckWorker"

        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<ServiceCheckWorker>(
                15, TimeUnit.MINUTES
            ).setConstraints(
                Constraints.Builder()
                    .build()
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            LogUtils.d("ServiceCheckWorker scheduled every 10 minutes")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            LogUtils.d("ServiceCheckWorker cancelled")
        }
    }
}
