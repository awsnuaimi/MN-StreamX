package com.yourapp.iptv.ui.screen.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yourapp.iptv.R
import com.yourapp.iptv.domain.model.*

@Composable
internal fun captionStatusText(value: CaptionProgress): String {
    if (value.status == CaptionStatus.SLOW_DEVICE && value.stage != null &&
        value.elapsedMillis != null && value.budgetMillis != null) {
        val stage = stringResource(when (value.stage) {
            CaptionStage.SPEECH -> R.string.caption_stage_speech
            CaptionStage.TRANSLATION -> R.string.caption_stage_translation
            CaptionStage.THROUGHPUT -> R.string.caption_stage_throughput
            CaptionStage.LAG -> R.string.caption_stage_lag
        })
        return stringResource(R.string.caption_slow_details, stage, value.elapsedMillis, value.budgetMillis)
    }
    val available = value.availableMb
    val required = value.requiredMb
    if (available != null && required != null) {
        val detail = when (value.status) {
            CaptionStatus.LOW_MEMORY -> R.string.caption_low_ram_details
            CaptionStatus.LOW_STORAGE -> R.string.caption_low_storage_details
            CaptionStatus.MEMORY_PRESSURE -> R.string.caption_memory_pressure_details
            else -> null
        }
        if (detail != null) return stringResource(detail, available, required)
    }
    val text = stringResource(when (value.status) {
        CaptionStatus.OFF -> R.string.caption_off
        CaptionStatus.EMBEDDED -> R.string.caption_embedded
        CaptionStatus.PREPARING -> R.string.caption_checking
        CaptionStatus.DOWNLOADING -> R.string.caption_downloading
        CaptionStatus.LOADING -> R.string.caption_loading
        CaptionStatus.LISTENING -> R.string.caption_listening
        CaptionStatus.TRANSLATING -> R.string.caption_translation_model
        CaptionStatus.SLOW_DEVICE -> R.string.caption_slow
        CaptionStatus.LOW_MEMORY -> R.string.caption_low_memory
        CaptionStatus.LOW_STORAGE -> R.string.caption_low_storage
        CaptionStatus.MEMORY_PRESSURE -> R.string.caption_memory_pressure
        CaptionStatus.UNAVAILABLE -> R.string.caption_unavailable
        CaptionStatus.UNSUPPORTED_LANGUAGE -> R.string.caption_unsupported
        CaptionStatus.FAILED -> R.string.caption_failed
    })
    return if (value.percent != null) "$text ${value.percent}%" else text
}