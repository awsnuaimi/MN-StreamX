package com.yourapp.iptv.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.yourapp.iptv.domain.organization.OrganizationConfig
import com.yourapp.iptv.domain.organization.OrganizationNames
import com.yourapp.iptv.domain.repository.OrganizationRepository
import com.yourapp.iptv.domain.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.io.IOException

internal val Context.organizationStore by preferencesDataStore(name = "channel_organization")

@OptIn(ExperimentalCoroutinesApi::class)
class OrganizationDataStore(private val store: DataStore<Preferences>, private val settings: SettingsRepository) : OrganizationRepository {
    override fun observe(): Flow<OrganizationConfig> = settings.observeSettings().map(OrganizationNames::sourceKey)
        .distinctUntilChanged().flatMapLatest { source -> store.data.map { OrganizationCodec.decode(it[key(source)], source) }.distinctUntilChanged() }
    override suspend fun save(value: OrganizationConfig) {
        if (OrganizationNames.sourceKey(settings.observeSettings().first()) != value.sourceKey) throw IOException("Organization source changed")
        store.edit { values ->
            val current = OrganizationCodec.decode(values[key(value.sourceKey)], value.sourceKey)
            if (current.revision != value.revision) throw IOException("Organization was changed by another editor")
            values[key(value.sourceKey)] = OrganizationCodec.encode(value.copy(revision = current.revision + 1))
        }
    }
    private fun key(source: String) = stringPreferencesKey("source_$source")
}