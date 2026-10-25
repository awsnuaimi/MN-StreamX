package com.yourapp.iptv.ui.screen.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourapp.iptv.BuildConfig
import com.yourapp.iptv.R
import com.yourapp.iptv.domain.update.UpdateIssue
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.state.AppUpdatePhase
import com.yourapp.iptv.ui.state.AppUpdateUiState
import com.yourapp.iptv.update.UpdateInstaller
import com.yourapp.iptv.viewmodel.AppUpdateViewModel

@Composable
internal fun AppUpdateSection(viewModel: AppUpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val installer = remember(context) { UpdateInstaller(context.applicationContext) }
    val install = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (runCatching { installer.allowed() }.getOrDefault(false)) viewModel.installAgain()
        else viewModel.installBlocked(UpdateIssue.INSTALL_PERMISSION)
    }
    DisposableEffect(owner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && viewModel.state.value.busy) viewModel.cancel()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); viewModel.cancel() }
    }
    LaunchedEffect(state.installRequestId, state.requestInstall, state.file) {
        if (!state.requestInstall) return@LaunchedEffect
        viewModel.consumeInstallRequest()
        val file = state.file ?: return@LaunchedEffect
        try {
            if (installer.allowed()) install.launch(installer.installIntent(file))
            else permission.launch(installer.permissionIntent())
        } catch (_: RuntimeException) { viewModel.installBlocked(UpdateIssue.INSTALL_UNAVAILABLE) }
    }
    AppUpdateCard(state, viewModel::updateApp, viewModel::cancel, viewModel::installAgain)
}

@Composable
internal fun AppUpdateCard(state: AppUpdateUiState, onUpdate: () -> Unit,
    onCancel: () -> Unit, onInstall: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("app-update-section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.update_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.update_installed, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyMedium)
        when (state.phase) {
            AppUpdatePhase.CHECKING -> Text(stringResource(R.string.update_checking))
            AppUpdatePhase.DOWNLOADING -> {
                Text(stringResource(R.string.update_downloading, state.update?.versionName.orEmpty(), state.percent))
                LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth())
            }
            AppUpdatePhase.VERIFYING -> Text(stringResource(R.string.update_verifying))
            AppUpdatePhase.CURRENT -> Text(stringResource(R.string.update_current))
            AppUpdatePhase.READY -> Text(stringResource(R.string.update_ready, state.update?.versionName.orEmpty()))
            AppUpdatePhase.IDLE -> Unit
        }
        state.issue?.let { Text(stringResource(updateIssueText(it)), color = MaterialTheme.colorScheme.error) }
        if (state.busy) {
            if (state.phase != AppUpdatePhase.DOWNLOADING) CircularProgressIndicator()
            AppButton(stringResource(R.string.update_cancel), onCancel, Modifier.testTag("update-cancel"))
        } else if (state.phase == AppUpdatePhase.READY) {
            AppButton(stringResource(R.string.update_install), onInstall, Modifier.testTag("update-install"))
        } else {
            AppButton(stringResource(R.string.update_action), onUpdate, Modifier.testTag("update-action"))
        }
        Text(stringResource(R.string.update_confirmation_note), style = MaterialTheme.typography.bodySmall)
    }
}

private fun updateIssueText(issue: UpdateIssue): Int = when (issue) {
    UpdateIssue.NOT_CONFIGURED -> R.string.update_not_configured
    UpdateIssue.NO_RELEASE -> R.string.update_no_release
    UpdateIssue.ACCESS -> R.string.update_access
    UpdateIssue.NETWORK -> R.string.update_network
    UpdateIssue.RATE_LIMIT -> R.string.update_rate_limit
    UpdateIssue.INVALID_RELEASE -> R.string.update_invalid_release
    UpdateIssue.NO_COMPATIBLE_APK -> R.string.update_no_compatible
    UpdateIssue.STORAGE -> R.string.update_storage
    UpdateIssue.INTEGRITY -> R.string.update_integrity
    UpdateIssue.PACKAGE_MISMATCH -> R.string.update_package_mismatch
    UpdateIssue.SIGNATURE_MISMATCH -> R.string.update_signature_mismatch
    UpdateIssue.VERSION_MISMATCH -> R.string.update_version_mismatch
    UpdateIssue.ANDROID_UNSUPPORTED -> R.string.update_android_unsupported
    UpdateIssue.INSTALL_PERMISSION -> R.string.update_permission
    UpdateIssue.INSTALL_UNAVAILABLE -> R.string.update_install_unavailable
}