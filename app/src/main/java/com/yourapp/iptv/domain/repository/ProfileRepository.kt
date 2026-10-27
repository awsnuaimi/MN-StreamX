package com.yourapp.iptv.domain.repository

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.parental.ParentalRules
import com.yourapp.iptv.domain.profile.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface ProfileRepository {
    val state: StateFlow<ProfilesState>
    suspend fun initialize()
    suspend fun create(name: String, kind: ProfileKind): ProfileResult
    suspend fun rename(id: String, name: String): ProfileResult
    suspend fun remove(id: String): ProfileResult
    suspend fun select(id: String): ProfileResult
    suspend fun saveRules(id: String, value: ParentalRules): ProfileResult
    suspend fun saveHome(id: String, value: HomeAppearance)
    suspend fun savePlayer(id: String, value: PlayerAppearance)
    suspend fun saveStartup(id: String, value: Boolean)

    companion object {
        val Disabled = object : ProfileRepository {
            override val state = MutableStateFlow(ProfilesState(ready = true))
            override suspend fun initialize() = Unit
            override suspend fun create(name: String, kind: ProfileKind) = ProfileResult.DENIED
            override suspend fun rename(id: String, name: String) = ProfileResult.DENIED
            override suspend fun remove(id: String) = ProfileResult.DENIED
            override suspend fun select(id: String) = ProfileResult.DENIED
            override suspend fun saveRules(id: String, value: ParentalRules) = ProfileResult.DENIED
            override suspend fun saveHome(id: String, value: HomeAppearance) = Unit
            override suspend fun savePlayer(id: String, value: PlayerAppearance) = Unit
            override suspend fun saveStartup(id: String, value: Boolean) = Unit
        }
    }
}