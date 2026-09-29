package com.sianalimalik.wearablesync

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// A remote push can wake this service outside of any activity, so it keeps its own
// coroutine scope instead of relying on a lifecycleScope that doesn't exist here.
class SyncFirebaseMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        scope.launch {
            val settings = SyncSettings(applicationContext)
            val baseUrl = settings.baseUrlFlow.first()
            val apiKey = settings.apiKeyFlow.first()
            // Best-effort: there's no UI to surface a failure to here, and a stale
            // token on the backend just means missed pushes until the next refresh.
            SianOsApiClient().registerDeviceToken(baseUrl, apiKey, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        if (message.data["action"] == "sync_now") {
            SyncScheduler.syncOnce(applicationContext)
        }
    }
}
