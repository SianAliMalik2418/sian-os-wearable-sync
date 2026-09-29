package com.sianalimalik.wearablesync

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

class HealthConnectRepository(private val context: Context) {

    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
    )

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private fun client() = HealthConnectClient.getOrCreate(context)

    suspend fun hasAllPermissions(): Boolean =
        client().permissionController.getGrantedPermissions().containsAll(permissions)

    /** Today's running step/active-calorie totals, plus the most recently completed sleep session. */
    suspend fun readTodayMetrics(): WearableMetrics {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val startOfDay = today.atStartOfDay(zone).toInstant()
        val now = Instant.now()

        return WearableMetrics(
            date = today.toString(),
            steps = readStepsTotal(startOfDay, now),
            activeCalories = readActiveCaloriesTotal(startOfDay, now),
            sleepHours = readLastNightSleepHours(zone),
        )
    }

    private suspend fun readStepsTotal(start: Instant, end: Instant): Long? {
        val response = client().aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end),
            ),
        )
        return response[StepsRecord.COUNT_TOTAL]
    }

    private suspend fun readActiveCaloriesTotal(start: Instant, end: Instant): Double? {
        val response = client().aggregate(
            AggregateRequest(
                metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end),
            ),
        )
        return response[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
    }

    // Sleep sessions span midnight, so "last night's sleep" is whichever session most
    // recently ended, searched from yesterday noon onward rather than from today's midnight.
    private suspend fun readLastNightSleepHours(zone: ZoneId): Double? {
        val lookbackStart = LocalDate.now(zone).minusDays(1).atTime(12, 0).atZone(zone).toInstant()
        val now = Instant.now()
        val sessions = client().readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(lookbackStart, now),
            ),
        ).records
        val latest = sessions.maxByOrNull { it.endTime } ?: return null
        val minutes = Duration.between(latest.startTime, latest.endTime).toMinutes()
        return (minutes / 60.0 * 100).roundToInt() / 100.0
    }
}
