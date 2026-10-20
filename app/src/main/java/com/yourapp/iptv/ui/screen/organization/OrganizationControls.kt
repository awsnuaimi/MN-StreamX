package com.yourapp.iptv.ui.screen.organization

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yourapp.iptv.R
import com.yourapp.iptv.domain.organization.FolderSort
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.component.organization.OrganizerRow

@Composable
internal fun folderSortName(sort: FolderSort): String = stringResource(when (sort) {
    FolderSort.SAVED -> R.string.org_sort_saved
    FolderSort.NAME -> R.string.org_sort_name
    FolderSort.COUNT -> R.string.org_sort_count
})

@Composable
internal fun OrganizationSortDialog(current: FolderSort, onChoose: (FolderSort) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.org_sort_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.org_sort_note), style = MaterialTheme.typography.bodySmall)
                FolderSort.entries.forEach { sort -> OrganizerRow(folderSortName(sort), selected = sort == current) { onChoose(sort) } }
                AppButton(stringResource(R.string.player_close), onDismiss)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GroupSelectionBar(count: Int, enabled: Boolean, resultsReady: Boolean,
    onAll: () -> Unit, onClear: () -> Unit, onMove: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AppButton(stringResource(R.string.org_select_results), onAll, enabled = enabled && resultsReady)
            AppButton(stringResource(R.string.org_clear_selection), onClear, enabled = enabled)
            AppButton(stringResource(R.string.org_move_selected, count), onMove, enabled = enabled && resultsReady && count > 0)
    }
}