package com.yourapp.iptv.ui.screen.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.ui.state.LiveTvPreferencesState

@Composable
internal fun LiveTvPreferenceStatus(state: LiveTvPreferencesState, labels: HomeLabels, retry: () -> Unit, retryModifier: Modifier = Modifier) {
    if (state.saving || state.error) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.saving) {
            CircularProgressIndicator(Modifier.size(20.dp))
            Text(labels.text("جارٍ حفظ الاختيار…", "Saving preference…", "Auswahl wird gespeichert…"),
                modifier = Modifier.testTag("live-preferences-saving"))
        } else {
            Text(labels.text("تعذّر حفظ الاختيار. لم يُطبّق التغيير.", "Preference could not be saved. Change was not applied.",
                "Auswahl konnte nicht gespeichert werden. Änderung wurde nicht übernommen."), Modifier.weight(1f))
            TextButton(onClick = retry, modifier = retryModifier.testTag("live-preferences-retry")) {
                Text(labels.text("إعادة تحميل", "Reload", "Neu laden"))
            }
        }
    }
}