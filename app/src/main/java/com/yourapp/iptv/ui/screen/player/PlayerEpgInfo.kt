package com.yourapp.iptv.ui.screen.player

import com.yourapp.iptv.ui.state.PlayerEpgUiState

internal fun playerEpgStatus(state: PlayerEpgUiState): String = when {
    state.isLoading -> "جارٍ تحميل معلومات البرامج…"
    state.errorMessage != null -> state.errorMessage
    !state.isSupported -> "لا تتوفر معلومات برامج لهذه القناة."
    state.schedule.isStale -> "لا توجد معلومات برامج محدثة حاليًا."
    else -> "لا تتوفر معلومات عن البرنامج الحالي."
}