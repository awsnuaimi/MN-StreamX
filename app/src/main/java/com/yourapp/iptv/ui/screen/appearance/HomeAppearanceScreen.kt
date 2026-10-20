package com.yourapp.iptv.ui.screen.appearance

import androidx.compose.ui.res.stringResource
import com.yourapp.iptv.R
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.viewmodel.HomeAppearanceSettingsViewModel

@Composable
fun HomeAppearanceScreen(onBack: () -> Unit, viewModel: HomeAppearanceSettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val value = state.value
    val enabled = !state.loading && !state.saving && !state.failed
    BackHandler(enabled = state.saving) { }
    LaunchedEffect(state.saved) { if (state.saved) onBack() }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.ui_home_title), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            AppButton(stringResource(R.string.ui_back), onBack, enabled = !state.saving)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.ui_language_hint))
            if (state.loading) CircularProgressIndicator()
            else if (state.failed) AppButton(stringResource(R.string.ui_reload), viewModel::load)
            else {
                AppearanceChoice(stringResource(R.string.ui_language), value.language,
                    listOf(HomeLanguage.ARABIC to "العربية", HomeLanguage.ENGLISH to "English", HomeLanguage.GERMAN to "Deutsch"), enabled) {
                    viewModel.update(value.copy(language = it))
                }
                AppearanceChoice(stringResource(R.string.ui_sidebar_side), value.sidebarSide,
                    listOf(SidebarSide.AUTO to stringResource(R.string.ui_side_auto), SidebarSide.RIGHT to stringResource(R.string.ui_right), SidebarSide.LEFT to stringResource(R.string.ui_left)), enabled) {
                    viewModel.update(value.copy(sidebarSide = it))
                }
                AppearanceChoice(stringResource(R.string.ui_columns), value.columns,
                    listOf(2 to stringResource(R.string.ui_columns_two), 3 to stringResource(R.string.ui_columns_three), 4 to stringResource(R.string.ui_columns_four)), enabled) {
                    viewModel.update(value.copy(columns = it))
                }
                Text(stringResource(R.string.ui_grid_adaptive_hint), style = MaterialTheme.typography.bodySmall)
                AppearanceToggle(stringResource(R.string.ui_sidebar_expanded), value.expandedSidebar, enabled) {
                    viewModel.update(value.copy(expandedSidebar = it))
                }
                Text(if (value.sidebarOnRight) stringResource(R.string.ui_preview_right) else stringResource(R.string.ui_preview_left))
            }
            if (state.error != null) Text(stringResource(if (state.failed) R.string.ui_home_read_error
                else R.string.ui_home_save_error), color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppButton(if (state.saving) stringResource(R.string.ui_saving) else stringResource(R.string.ui_save_appearance), viewModel::save, enabled = enabled)
            AppButton(stringResource(R.string.ui_reset), viewModel::reset, enabled = enabled)
        }
    }
}