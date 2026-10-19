package com.yourapp.iptv.ui.screen.appearance

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
            Text("مظهر الرئيسية", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            AppButton("رجوع", onBack, enabled = !state.saving)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("تخصيص الرئيسية والشريط الجانبي. لغة الشاشات الأخرى تبقى العربية حاليًا.")
            if (state.loading) CircularProgressIndicator()
            else if (state.failed) AppButton("إعادة القراءة", viewModel::load)
            else {
                AppearanceChoice("لغة الرئيسية والشريط", value.language,
                    listOf(HomeLanguage.ARABIC to "العربية", HomeLanguage.ENGLISH to "English", HomeLanguage.GERMAN to "Deutsch"), enabled) {
                    viewModel.update(value.copy(language = it))
                }
                AppearanceChoice("موضع الشريط", value.sidebarSide,
                    listOf(SidebarSide.AUTO to "حسب اللغة", SidebarSide.RIGHT to "يمين", SidebarSide.LEFT to "يسار"), enabled) {
                    viewModel.update(value.copy(sidebarSide = it))
                }
                AppearanceChoice("بطاقات القنوات في الصف", value.columns,
                    listOf(2 to "كبيرة · 2", 3 to "متوسطة · 3", 4 to "صغيرة · 4"), enabled) {
                    viewModel.update(value.copy(columns = it))
                }
                AppearanceToggle("إبقاء أسماء أقسام الشريط ظاهرة", value.expandedSidebar, enabled) {
                    viewModel.update(value.copy(expandedSidebar = it))
                }
                Text(if (value.sidebarOnRight) "المعاينة: الشريط يمين الشاشة" else "المعاينة: الشريط يسار الشاشة")
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppButton(if (state.saving) "جارٍ الحفظ…" else "حفظ المظهر", viewModel::save, enabled = enabled)
            AppButton("استعادة الافتراضي", viewModel::reset, enabled = enabled)
        }
    }
}