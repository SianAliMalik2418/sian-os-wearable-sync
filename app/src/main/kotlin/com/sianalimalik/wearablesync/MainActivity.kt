package com.sianalimalik.wearablesync

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import com.sianalimalik.wearablesync.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: SyncSettings
    private lateinit var healthConnect: HealthConnectRepository

    private val requestPermissions = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        updatePermissionStatus(granted.containsAll(healthConnect.permissions))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = SyncSettings(this)
        healthConnect = HealthConnectRepository(this)

        lifecycleScope.launch {
            binding.baseUrlInput.setText(settings.baseUrlFlow.first())
            binding.apiKeyInput.setText(settings.apiKeyFlow.first())
            binding.autoSyncSwitch.isChecked = settings.autoSyncFlow.first()
            updatePermissionStatus(healthConnect.isAvailable() && healthConnect.hasAllPermissions())
        }

        binding.saveSettingsButton.setOnClickListener {
            lifecycleScope.launch {
                settings.save(
                    binding.baseUrlInput.text.toString().trim(),
                    binding.apiKeyInput.text.toString().trim(),
                )
                Toast.makeText(this@MainActivity, "Settings saved", Toast.LENGTH_SHORT).show()
            }
        }

        binding.grantAccessButton.setOnClickListener {
            if (!healthConnect.isAvailable()) {
                Toast.makeText(
                    this,
                    "Health Connect isn't available on this device. Install it from the Play Store first.",
                    Toast.LENGTH_LONG,
                ).show()
                return@setOnClickListener
            }
            requestPermissions.launch(healthConnect.permissions)
        }

        binding.autoSyncSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch { settings.setAutoSync(isChecked) }
            if (isChecked) SyncScheduler.enable(this) else SyncScheduler.disable(this)
        }

        binding.syncNowButton.setOnClickListener {
            binding.statusText.text = "Syncing…"
            lifecycleScope.launch { runManualSync() }
        }
    }

    private fun updatePermissionStatus(granted: Boolean) {
        binding.permissionStatusText.text =
            if (granted) "Health Connect access granted" else "Health Connect access not granted yet"
    }

    private suspend fun runManualSync() {
        if (!healthConnect.isAvailable()) {
            binding.statusText.text = "Health Connect isn't installed on this device"
            return
        }
        if (!healthConnect.hasAllPermissions()) {
            binding.statusText.text = "Grant Health Connect access first"
            return
        }

        val metrics = healthConnect.readTodayMetrics()
        if (!metrics.hasAnyMetric) {
            binding.statusText.text = "No step, calorie, or sleep data available yet in Health Connect"
            return
        }

        val baseUrl = binding.baseUrlInput.text.toString().trim().ifBlank { SyncSettings.DEFAULT_BASE_URL }
        val apiKey = binding.apiKeyInput.text.toString().trim()
        val result = SianOsApiClient().postWearableMetrics(baseUrl, apiKey, metrics)
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))

        binding.statusText.text = result.fold(
            onSuccess = {
                "Synced at $timestamp — steps: ${metrics.steps ?: "—"}, " +
                    "active cal: ${metrics.activeCalories?.let { "%.0f".format(it) } ?: "—"}, " +
                    "sleep: ${metrics.sleepHours?.let { "%.2f h".format(it) } ?: "—"}"
            },
            onFailure = { "Sync failed at $timestamp: ${it.message}" },
        )
    }
}
