package mnstudio.apps.mnstreamx.domain.repository

import mnstudio.apps.mnstreamx.domain.model.AppSettings
import mnstudio.apps.mnstreamx.domain.model.Subscriptions
import kotlinx.coroutines.flow.Flow

interface SubscriptionRepository : SettingsRepository {
    fun observeSubscriptions(): Flow<Subscriptions>
    suspend fun saveSubscription(id: String?, name: String, settings: AppSettings): Subscriptions
    suspend fun activateSubscription(id: String): Subscriptions
    suspend fun removeSubscription(id: String): Subscriptions
}