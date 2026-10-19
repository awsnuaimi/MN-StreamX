package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.model.PlayerAppearance
import com.yourapp.iptv.domain.repository.PlayerAppearanceRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.io.IOException

internal class FakePlayerAppearanceRepository(initial: PlayerAppearance = PlayerAppearance()) : PlayerAppearanceRepository {
    val values = MutableStateFlow(initial)
    var reads = 0
    var saves = 0
    var failRead = false
    var failSave = false
    var latency = 0L
    override fun observe() = flow {
        reads++; delay(latency)
        if (failRead) throw IOException("Synthetic read failure")
        emitAll(values)
    }
    override suspend fun save(appearance: PlayerAppearance) {
        delay(latency)
        if (failSave) throw IOException("Synthetic write failure")
        saves++; values.value = appearance
    }
}