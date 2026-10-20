package com.yourapp.iptv.ui.component.organization

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yourapp.iptv.R
import com.yourapp.iptv.domain.organization.*

@Composable
fun folderName(folder: ChannelFolder): String {
    if (folder.name.isNotBlank()) return folder.name
    return stringResource(when (folder.label) {
        FolderLabel.ARABIC -> R.string.org_folder_arabic
        FolderLabel.BEIN -> R.string.org_folder_bein
        FolderLabel.GERMAN -> R.string.org_folder_german
        FolderLabel.POLISH -> R.string.org_folder_polish
        FolderLabel.UNCLASSIFIED -> R.string.org_folder_unclassified
        FolderLabel.SYRIA -> R.string.org_folder_syria
        FolderLabel.EGYPT -> R.string.org_folder_egypt
        FolderLabel.LEBANON -> R.string.org_folder_lebanon
        FolderLabel.IRAQ -> R.string.org_folder_iraq
        FolderLabel.JORDAN -> R.string.org_folder_jordan
        FolderLabel.PALESTINE -> R.string.org_folder_palestine
        FolderLabel.SAUDI -> R.string.org_folder_saudi
        FolderLabel.UAE -> R.string.org_folder_uae
        FolderLabel.KUWAIT -> R.string.org_folder_kuwait
        FolderLabel.QATAR -> R.string.org_folder_qatar
        FolderLabel.BAHRAIN -> R.string.org_folder_bahrain
        FolderLabel.OMAN -> R.string.org_folder_oman
        FolderLabel.MOROCCO -> R.string.org_folder_morocco
        FolderLabel.ALGERIA -> R.string.org_folder_algeria
        FolderLabel.TUNISIA -> R.string.org_folder_tunisia
        FolderLabel.LIBYA -> R.string.org_folder_libya
        FolderLabel.SUDAN -> R.string.org_folder_sudan
        FolderLabel.YEMEN -> R.string.org_folder_yemen
        FolderLabel.MAURITANIA -> R.string.org_folder_mauritania
        FolderLabel.SOMALIA -> R.string.org_folder_somalia
        FolderLabel.DJIBOUTI -> R.string.org_folder_djibouti
        FolderLabel.COMOROS -> R.string.org_folder_comoros
        FolderLabel.ARAB_MISC -> R.string.org_folder_arab_misc
        FolderLabel.GENERAL -> R.string.org_folder_general
        FolderLabel.SPORTS -> R.string.org_folder_sports
        FolderLabel.NEWS -> R.string.org_folder_news
        FolderLabel.MOVIES -> R.string.org_folder_movies
        FolderLabel.KIDS -> R.string.org_folder_kids
        FolderLabel.DOCUMENTARY -> R.string.org_folder_documentary
        FolderLabel.MUSIC -> R.string.org_folder_music
        FolderLabel.OTHER -> R.string.org_folder_other
        null -> R.string.org_folder_other
    })
}

@Composable
fun folderPath(tree: OrganizationTree, id: String): String {
    val names = mutableListOf<String>()
    for (folder in tree.path(id)) names.add(folderName(folder))
    return names.joinToString(" › ")
}

@Composable
fun organizationFilterName(filter: String?, tree: OrganizationTree): String? =
    FolderFilter.id(filter)?.let { id -> tree.folder(id)?.let { folderPath(tree, id) } } ?: filter

@Composable
fun assignmentName(reason: AssignmentReason): String = stringResource(when (reason) {
    AssignmentReason.CHANNEL_RULE -> R.string.org_channel_rule
    AssignmentReason.GROUP_RULE -> R.string.org_group_rule
    AssignmentReason.NETWORK -> R.string.org_network_rule
    AssignmentReason.COUNTRY -> R.string.org_country_rule
    AssignmentReason.UNCLASSIFIED -> R.string.org_unknown_rule
})