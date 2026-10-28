package com.yourapp.iptv.ui.screen.vod

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yourapp.iptv.player.vod.VOD_SPEEDS
import com.yourapp.iptv.ui.component.common.AppButton

@Composable
internal fun VodPlayerOptionDialog(panel: VodPlayerPanel, labels: VodPlaybackLabels, speed: Float,
    display: VodDisplayMode, onSpeed: (Float) -> Unit, onDisplay: (VodDisplayMode) -> Unit,
    onRestart: () -> Unit, onClose: () -> Unit) {
    val initial = remember(panel) { FocusRequester() }
    // New dialog windows may start in touch mode; TV choices still need an initial focus.
    Dialog(onDismissRequest = onClose) {
        Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = 440.dp),shape = MaterialTheme.shapes.large) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp).testTag("vod-option-dialog"),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(when (panel) { VodPlayerPanel.SPEED -> labels.speed();VodPlayerPanel.DISPLAY -> labels.picture()
                    else -> labels.restartTitle() },style = MaterialTheme.typography.titleLarge)
                if (panel == VodPlayerPanel.RESTART) {
                    Text(labels.restartHint())
                    AppButton(labels.stay(),onClose,Modifier.fillMaxWidth().focusProperties { canFocus = true }.focusRequester(initial).testTag("vod-restart-stay"))
                    AppButton(labels.restart(),onRestart,Modifier.fillMaxWidth().focusProperties { canFocus = true }.testTag("vod-restart-confirm"))
                } else {
                    Text(if (panel == VodPlayerPanel.DISPLAY) labels.pictureHint() else labels.sessionOnly(),style = MaterialTheme.typography.bodyMedium)
                    if (panel == VodPlayerPanel.SPEED) VOD_SPEEDS.forEachIndexed { index,value ->
                        AppButton((if (speed == value) "✓ " else "")+"${value}×",{ onSpeed(value) },
                            Modifier.fillMaxWidth().focusProperties { canFocus = true }.then(if (value == speed || index == 0 && speed !in VOD_SPEEDS) Modifier.focusRequester(initial) else Modifier).testTag("vod-speed-$index"))
                    }
                    if (panel == VodPlayerPanel.DISPLAY) VodDisplayMode.entries.forEachIndexed { index,value ->
                        AppButton((if (display == value) "✓ " else "")+labels.display(value),{ onDisplay(value) },
                            Modifier.fillMaxWidth().focusProperties { canFocus = true }.then(if (value == display) Modifier.focusRequester(initial) else Modifier).testTag("vod-display-$index"))
                    }
                    AppButton(labels.close(),onClose,Modifier.fillMaxWidth().focusProperties { canFocus = true }.testTag("vod-option-close"))
                }
            }
        }
        LaunchedEffect(panel) { withFrameNanos { };initial.requestFocus() }
    }
}