package com.yourapp.iptv.ui.screen.organization

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yourapp.iptv.R
import com.yourapp.iptv.domain.organization.*
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.component.organization.OrganizerRow
import com.yourapp.iptv.ui.component.organization.folderPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal fun OrganizationRecoveryDialog(
    config: OrganizationConfig,
    preview: OrganizedChannels,
    busy: Boolean,
    failed: Boolean,
    report: GroupRecoveryPreview?,
    onPreview: (String, String) -> Unit,
    onApply: () -> Unit,
    onCancelPreview: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedKey by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    val missing by produceState<List<MissingGroupRule>?>(null, config, preview) {
        value = null
        value = withContext(Dispatchers.Default) {
            OrganizationRecovery.missing(config, preview.groups)
        }
    }

    val selected = missing?.firstOrNull { it.key == selectedKey }

    val options by produceState<List<RecoveryCandidate>?>(
        null, selected, config, preview, query
    ) {
        value = null
        val rule = selected ?: return@produceState
        if (query.isNotBlank()) delay(150)
        value = withContext(Dispatchers.Default) {
            OrganizationRecovery.candidates(
                rule, config, preview.groups, query
            )
        }
    }

    val height = LocalConfiguration.current.screenHeightDp.dp * 0.9f

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            Modifier.fillMaxWidth(0.88f).height(height),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    stringResource(R.string.org_recovery_open),
                    style = MaterialTheme.typography.titleLarge
                )

                if (failed) {
                    Text(
                        stringResource(R.string.org_recovery_error),
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

                when {
                    report != null -> {
                        Column(
                            Modifier.weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                stringResource(
                                    R.string.org_source,
                                    selected?.name ?: stringResource(
                                        R.string.org_recovery_unknown,
                                        report.oldKey.take(8)
                                    )
                                )
                            )
                            Text(stringResource(R.string.org_recovery_confirm))
                            Text(
                                report.newName?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.org_no_group),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(folderPath(preview.tree, report.folderId))
                            Text(
                                stringResource(
                                    R.string.org_recovery_counts,
                                    report.total,
                                    report.changed,
                                    report.protectedChannels
                                )
                            )
                            Text(
                                stringResource(R.string.org_recovery_draft),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AppButton(
                                stringResource(R.string.org_recovery_apply),
                                onApply,
                                enabled = !busy
                            )
                            AppButton(
                                stringResource(R.string.ui_back),
                                onCancelPreview,
                                enabled = !busy
                            )
                        }
                    }

                    selected != null -> {
                        Text(
                            selected.name ?: stringResource(
                                R.string.org_recovery_unknown,
                                selected.key.take(8)
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            stringResource(R.string.org_recovery_choose),
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            query,
                            { query = it.take(200) },
                            enabled = !busy,
                            singleLine = true,
                            label = {
                                Text(stringResource(R.string.org_search))
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        LazyColumn(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (options == null) {
                                item { CircularProgressIndicator() }
                            }

                            items(
                                options.orEmpty(),
                                key = { it.group.key }
                            ) { candidate ->
                                val group = candidate.group
                                val score = if (candidate.score > 0) {
                                    stringResource(
                                        R.string.org_recovery_score,
                                        candidate.score
                                    )
                                } else ""

                                val note = if (!candidate.available) {
                                    stringResource(R.string.org_group_rule)
                                } else score

                                OrganizerRow(
                                    group.name?.takeIf { it.isNotBlank() }
                                        ?: stringResource(R.string.org_no_group),
                                    subtitle = stringResource(
                                        R.string.org_count,
                                        group.count
                                    ) + " · " + note,
                                    enabled = !busy && candidate.available
                                ) {
                                    onPreview(selected.key, group.key)
                                }
                            }

                            if (options?.isEmpty() == true) {
                                item {
                                    Text(stringResource(R.string.org_empty))
                                }
                            }
                        }

                        AppButton(
                            stringResource(R.string.ui_back),
                            {
                                onCancelPreview()
                                selectedKey = null
                                query = ""
                            },
                            enabled = !busy
                        )
                    }

                    else -> {
                        Text(
                            stringResource(R.string.org_recovery_help),
                            style = MaterialTheme.typography.bodySmall
                        )

                        LazyColumn(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (missing == null) {
                                item { CircularProgressIndicator() }
                            }

                            items(
                                missing.orEmpty(),
                                key = { it.key }
                            ) { rule ->
                                OrganizerRow(
                                    rule.name ?: stringResource(
                                        R.string.org_recovery_unknown,
                                        rule.key.take(8)
                                    ),
                                    subtitle = folderPath(
                                        preview.tree,
                                        rule.folderId
                                    ),
                                    enabled = !busy
                                ) {
                                    selectedKey = rule.key
                                    query = ""
                                }
                            }

                            if (missing?.isEmpty() == true) {
                                item {
                                    Text(
                                        stringResource(
                                            R.string.org_recovery_empty
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                AppButton(
                    stringResource(R.string.player_close),
                    onDismiss
                )
            }
        }
    }
}