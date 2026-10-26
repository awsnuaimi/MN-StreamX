package com.yourapp.iptv.testing

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.SubscriptionRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import java.io.IOException

internal class FakeSubscriptions(initial: AppSettings = account("A")) : SubscriptionRepository {
    val catalog = MutableStateFlow(Subscriptions(listOf(Subscription(Subscriptions.DEFAULT_ID, "Original", initial)),
        Subscriptions.DEFAULT_ID, initial.takeIf { it.hasSelectedSource() }?.let(PlaylistCacheKey::of)))
    var failRead = false
    var failWrite = false
    var failReadAfterSave = false
    var saves = 0
    var latency = 20L
    override fun observeSubscriptions() = flow {
        if (failRead) throw IOException("Synthetic read failure")
        emitAll(catalog)
    }
    override fun observeSettings() = observeSubscriptions().map { it.active.settings }.distinctUntilChanged()
    override suspend fun saveSettings(settings: AppSettings) { saveSubscription(catalog.value.activeId, catalog.value.active.name, settings) }
    override suspend fun saveSubscription(id: String?, name: String, settings: AppSettings): Subscriptions {
        delay(latency)
        if (failWrite) throw IOException("Synthetic write failure")
        catalog.value = SubscriptionChanges.save(catalog.value, id, name, settings); saves++
        if (failReadAfterSave) failRead = true
        return catalog.value
    }
    override suspend fun activateSubscription(id: String): Subscriptions {
        delay(latency)
        if (failWrite) throw IOException("Synthetic write failure")
        catalog.value = SubscriptionChanges.activate(catalog.value, id)
        return catalog.value
    }
    override suspend fun removeSubscription(id: String): Subscriptions {
        delay(latency)
        if (failWrite) throw IOException("Synthetic write failure")
        catalog.value = SubscriptionChanges.remove(catalog.value, id)
        return catalog.value
    }
    companion object { fun account(name: String) = AppSettings(m3uUrl = "https://synthetic.invalid/$name.m3u") }
}