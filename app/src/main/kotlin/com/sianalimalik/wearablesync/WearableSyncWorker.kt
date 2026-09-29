package com.sianalimalik.wearablesync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first

class WearableSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val settings = SyncSettings(applicationContext)
            val repo = HealthConnectRepository(applicationContext)

            if (!repo.isAvailable() || !repo.hasAllPermissions()) return Result.failure()

            val metrics = repo.readTodayMetrics()
            if (!metrics.hasAnyMetric) return Result.success()

            val baseUrl = settings.baseUrlFlow.first()
            val apiKey = settings.apiKeyFlow.first()
            val outcome = SianOsApiClient().postWearableMetrics(baseUrl, apiKey, metrics)
            if (outcome.isSuccess) Result.success() else Result.retry()
        } catch (throwable: Throwable) {
            Result.retry()
        }
    }
}
