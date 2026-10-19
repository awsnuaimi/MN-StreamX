package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.model.AppSettings
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.EpgSchedule
import com.yourapp.iptv.domain.repository.LiveEpgRepository
import com.yourapp.iptv.ui.state.PlayerEpgUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

internal fun observePlayerEpg(
    channel: Channel?,
    settings: AppSettings,
    repository: LiveEpgRepository,
    clock: () -> Long = System::currentTimeMillis
): Flow<PlayerEpgUiState> = flow {
    if (channel == null) { emit(PlayerEpgUiState()); return@flow }
    emit(PlayerEpgUiState(channelId = channel.id, nowMillis = clock(), isLoading = true))
    // Fast zapping should not send an EPG request for every intermediate channel.
    delay(350)
    var schedule = EpgSchedule(emptyList())
    var message: String? = null
    var lastAttempt: Long? = null
    while (currentCoroutineContext().isActive) {
        val now = clock()
        if (lastAttempt == null || now < lastAttempt || now - lastAttempt >= 60_000L) {
            try {
                val loaded = repository.load(channel, settings)
                if (loaded == null) {
                    emit(PlayerEpgUiState(channelId = channel.id, nowMillis = clock(), isSupported = false))
                    return@flow
                }
                schedule = loaded
                message = null
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                schedule = schedule.copy(isStale = true)
                message = "تعذر تحديث دليل البرامج. ستتم إعادة المحاولة تلقائيًا."
            }
            lastAttempt = clock()
        }
        emit(PlayerEpgUiState(channelId = channel.id, schedule = schedule,
            nowMillis = clock(), errorMessage = message))
        delay(15_000)
    }
}