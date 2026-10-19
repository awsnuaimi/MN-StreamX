package com.yourapp.iptv.ui.screen.appearance

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.viewmodel.PlayerAppearanceSettingsViewModel

@Composable
fun PlayerAppearanceScreen(onBack: () -> Unit, viewModel: PlayerAppearanceSettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val enabled = !state.isLoading && !state.isSaving && !state.loadFailed
    BackHandler(enabled = state.isSaving) { }
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) { viewModel.resetSaveStatus(); onBack() }
    }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("مظهر المشغّل", style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary)
            AppButton("رجوع", onBack, enabled = !state.isSaving)
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("اختر ما يناسبك. زر تفاصيل البرنامج يبقى متاحًا في النمطين.")
            if (state.isLoading) CircularProgressIndicator()
            else if (state.loadFailed) AppButton("إعادة قراءة المظهر", viewModel::load)
            else {
                PlayerAppearancePreview(state.appearance)
                PlayerAppearanceForm(state.appearance, enabled, viewModel::update)
            }
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppButton(if (state.isSaving) "جارٍ الحفظ…" else "حفظ المظهر", viewModel::save, enabled = enabled)
            AppButton("استعادة الافتراضي", viewModel::reset, enabled = enabled)
        }
    }
}