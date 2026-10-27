package com.yourapp.iptv.ui.screen.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yourapp.iptv.domain.profile.*
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.screen.parental.LocalParentalSession
import com.yourapp.iptv.viewmodel.ProfilesViewModel

@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun ProfileScreen(onBack: () -> Unit, onManage: () -> Unit, onUsed: () -> Unit,
    selected: String? = null, onSelectionHandled: () -> Unit = {}, model: ProfilesViewModel = hiltViewModel()) {
    val people by model.people.collectAsState()
    val operation by model.operation.collectAsState()
    val parental = LocalParentalSession.current
    val labels = ProfileLabels(parental.language)
    fun use(id: String) { model.select(id) { result ->
        when (result) {
            ProfileResult.PIN_REQUIRED -> parental.model?.requestPin("profiles?select=$id")
            ProfileResult.OK -> onUsed()
            else -> Unit
        }
    } }
    LaunchedEffect(selected) { if (selected != null && UserProfiles.validId(selected)) { onSelectionHandled();use(selected) } }
    val initialFocus = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current;val window = LocalWindowInfo.current
    LaunchedEffect(window.isWindowFocused) { if (window.isWindowFocused) {
        inputMode.requestInputMode(InputMode.Keyboard);withFrameNanos { };initialFocus.requestFocus()
    } }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().padding(18.dp).testTag("profile-picker"),verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppButton(labels.back(),onBack,Modifier.focusRequester(initialFocus));Text(labels.title(),style = MaterialTheme.typography.headlineSmall)
        }
        Text(labels.text("لكل ملف مفضلة وسجل ومظهر مستقل. الاشتراكات والقنوات مشتركة.",
            "Each profile has its own favorites, history and appearance. Subscriptions and channels are shared.",
            "Jedes Profil hat eigene Favoriten, Verlauf und Darstellung. Abos und Sender werden gemeinsam genutzt."))
        LazyColumn(Modifier.weight(1f),verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(people.catalog.entries,key = { it.id }) { row ->
                AppButton((if (row.id == people.catalog.activeId) "✓ " else "") + labels.name(row) + " • " + labels.kind(row.kind),
                    { use(row.id) },enabled = !operation.busy && !people.switching)
            }
        }
        if (operation.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (labels.message(operation.result).isNotEmpty()) Text(labels.message(operation.result),color = MaterialTheme.colorScheme.error)
        AppButton(labels.manage(),onManage,enabled = !operation.busy)
    }
}