package com.yourapp.iptv.domain.repository

import com.yourapp.iptv.domain.model.AppSettings
import com.yourapp.iptv.domain.model.Subscriptions
import kotlinx.coroutines.flow.Flow

interface SubscriptionRepository : SettingsRepository {
    fun observeSubscriptions(): Flow<Subscriptions>
    /** Save the whole account and activate it in one durable transaction. */
    suspend fun saveSubscription(id: String?, name: String, settings: AppSettings): Subscriptions
    suspend fun activateSubscription(id: String): Subscriptions
    /** An active account must be switched away from before removal. */
    suspend fun removeSubscription(id: String): Subscriptions
}