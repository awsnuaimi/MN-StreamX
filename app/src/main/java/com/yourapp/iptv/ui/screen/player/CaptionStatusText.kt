package com.yourapp.iptv.ui.screen.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yourapp.iptv.R
import com.yourapp.iptv.domain.model.*

@Composable
internal fun captionStatusText(value: CaptionProgress): String {
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
        CaptionStatus.UNAVAILABLE -> R.string.caption_unavailable
        CaptionStatus.UNSUPPORTED_LANGUAGE -> R.string.caption_unsupported
        CaptionStatus.FAILED -> R.string.caption_failed
    })
    return if (value.percent != null) "$text ${value.percent}%" else text
}