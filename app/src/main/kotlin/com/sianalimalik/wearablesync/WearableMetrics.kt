package com.sianalimalik.wearablesync

/** One day's worth of wearable data ready to send to POST /api/wearable-metrics. */
data class WearableMetrics(
    val date: String,
    val steps: Long?,
    val activeCalories: Double?,
    val sleepHours: Double?,
) {
    val hasAnyMetric: Boolean
        get() = steps != null || activeCalories != null || sleepHours != null
}
