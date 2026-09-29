package com.sianalimalik.wearablesync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class SianOsApiClient(private val client: OkHttpClient = OkHttpClient()) {

    suspend fun postWearableMetrics(baseUrl: String, apiKey: String?, metrics: WearableMetrics): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val json = buildJsonObject {
                    put("date", metrics.date)
                    metrics.steps?.let { put("steps", it) }
                    metrics.activeCalories?.let { put("active_calories", it) }
                    metrics.sleepHours?.let { put("sleep_hours", it) }
                }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val requestBuilder = Request.Builder()
                    .url(baseUrl.trimEnd('/') + "/api/wearable-metrics")
                    .post(body)
                if (!apiKey.isNullOrBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                }
                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("Sync failed: HTTP ${response.code} ${response.body?.string().orEmpty()}")
                    }
                }
            }
        }
}
