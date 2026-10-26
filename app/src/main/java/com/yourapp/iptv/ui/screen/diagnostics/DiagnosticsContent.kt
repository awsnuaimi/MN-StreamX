package com.yourapp.iptv.ui.screen.diagnostics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.diagnostics.*
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.viewmodel.DiagnosticsState
import java.time.Instant

@Composable
internal fun DiagnosticsContent(state: DiagnosticsState, events: List<DiagnosticEvent>, text: DiagnosticText,
    onBack: () -> Unit, onRun: () -> Unit, onCancel: () -> Unit, onShare: () -> Unit, onClear: () -> Unit) {
    val initial = remember { FocusRequester() }
    val action = remember { FocusRequester() }
    var wasBusy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(18.dp).testTag("diagnostics-page"),verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AppButton(text.back(),onBack,Modifier.focusRequester(initial).testTag("diagnostics-back"))
            Text(text.title(),style = MaterialTheme.typography.headlineSmall,color = MaterialTheme.colorScheme.primary)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.busy) AppButton(text.cancel(),onCancel,Modifier.weight(1f).focusRequester(action).testTag("diagnostics-cancel"))
            else AppButton(text.start(),onRun,Modifier.weight(1f).focusRequester(action).testTag("diagnostics-start"),state.sourceReady && !state.exporting)
            AppButton(text.share(),onShare,Modifier.weight(1f).testTag("diagnostics-share"),state.sourceReady && state.snapshot != null && !state.busy && !state.exporting)
            AppButton(text.clear(),onClear,Modifier.weight(1.3f).testTag("diagnostics-clear"),!state.busy && !state.exporting)
        }
        if (state.busy || state.exporting) {
            Text(if (state.busy) text.busy() else text.exporting());LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (state.failure) Text(text.failure(),color = MaterialTheme.colorScheme.error)
        if (state.shareFailure) Text(text.shareFailed(),color = MaterialTheme.colorScheme.error)
        LazyColumn(Modifier.weight(1f).testTag("diagnostics-list"),verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(text.privacy(),style = MaterialTheme.typography.bodySmall) }
            item { Text(text.requestNote(),style = MaterialTheme.typography.bodySmall) }
            item { Text(text.device(),style = MaterialTheme.typography.titleMedium) }
            state.snapshot?.let { snapshot ->
                if (snapshot.connection == DiagnosticConnection.NONE) item { Text(text.issue(DiagnosticIssue.OFFLINE),color = MaterialTheme.colorScheme.error) }
                else if (snapshot.validated == false) item { Text(text.issue(DiagnosticIssue.UNVALIDATED)) }
                items(DiagnosticSummary.rows(snapshot,text)) { (name,value) ->
                    Surface(shape = MaterialTheme.shapes.small,color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) { Text(name,style = MaterialTheme.typography.labelLarge);Text(value) }
                    }
                }
            }
            item {
                if (state.sourceReady) Text("${text.current()}: ${state.source.name}")
                Text(text.checks(),style = MaterialTheme.typography.titleMedium);Text(text.categoryNote(),style = MaterialTheme.typography.bodySmall)
            }
            if (state.checks.isEmpty()) item { Text(text.idle()) }
            itemsIndexed(state.checks) { index,check ->
                Surface(shape = MaterialTheme.shapes.medium,color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(12.dp).testTag("diagnostics-check-$index"),verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${text.area(check.area)} — ${text.status(check.status)}",style = MaterialTheme.typography.titleSmall)
                        Text(text.issue(check.issue),color = if (check.status == DiagnosticStatus.FAIL) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        Text(listOfNotNull(check.httpStatus?.let { "HTTP $it" },check.count?.let { "count=$it" },"${check.elapsedMs} ms").joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { Text(text.events(),style = MaterialTheme.typography.titleMedium);Text(text.sessionNote(),style = MaterialTheme.typography.bodySmall) }
            if (events.isEmpty()) item { Text(text.noEvents()) }
            items(events,key = { it.id }) { event ->
                Surface(shape = MaterialTheme.shapes.medium,color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(12.dp).testTag("diagnostics-event-${event.id}"),verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        val scope = when { event.sourceKey == null -> text.app();event.sourceKey == state.sourceKey -> text.current();else -> text.other() }
                        Text("${text.area(event.area)} · $scope",style = MaterialTheme.typography.titleSmall)
                        Text(text.issue(event.issue))
                        Text(DiagnosticSummary.technical(event),style = MaterialTheme.typography.bodySmall)
                        Text(Instant.ofEpochMilli(event.timeMs.coerceIn(0,4_102_444_800_000L)).toString(),style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { withFrameNanos { };initial.requestFocus() }
    LaunchedEffect(state.busy) {
        if ((wasBusy || state.busy) && state.sourceReady) { withFrameNanos { };action.requestFocus() }
        wasBusy = state.busy
    }
}