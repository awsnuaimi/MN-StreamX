package com.yourapp.iptv.domain.organization

enum class FolderLabel {
    ARABIC, BEIN, GERMAN, POLISH, UNCLASSIFIED,
    SYRIA, EGYPT, LEBANON, IRAQ, JORDAN, PALESTINE, SAUDI, UAE, KUWAIT, QATAR, BAHRAIN, OMAN,
    MOROCCO, ALGERIA, TUNISIA, LIBYA, SUDAN, YEMEN, MAURITANIA, SOMALIA, DJIBOUTI, COMOROS,
    ARAB_MISC, GENERAL, SPORTS, NEWS, MOVIES, KIDS, DOCUMENTARY, MUSIC, OTHER
}

data class ChannelFolder(val id: String, val parentId: String? = null, val label: FolderLabel? = null,
    val name: String = "", val order: Int = 0)

data class OrganizationConfig(val sourceKey: String = "", val enabled: Boolean = false,
    val folders: List<ChannelFolder> = OrganizationDefaults.folders(),
    val groupOverrides: Map<String, String> = emptyMap(), val channelOverrides: Map<String, String> = emptyMap(),
    val revision: Long = 0)

enum class AssignmentReason { CHANNEL_RULE, GROUP_RULE, NETWORK, COUNTRY, UNCLASSIFIED }
data class FolderAssignment(val folderId: String, val reason: AssignmentReason)
data class SourceGroup(val key: String, val name: String?, val count: Int, val folderIds: Set<String>)

object FolderFilter {
    private const val prefix = "mnx-folder:"
    fun of(id: String) = prefix + id
    fun id(value: String?): String? = value?.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)
}

object OrganizationDefaults {
    val countries = listOf(FolderLabel.SYRIA, FolderLabel.EGYPT, FolderLabel.LEBANON, FolderLabel.IRAQ,
        FolderLabel.JORDAN, FolderLabel.PALESTINE, FolderLabel.SAUDI, FolderLabel.UAE, FolderLabel.KUWAIT,
        FolderLabel.QATAR, FolderLabel.BAHRAIN, FolderLabel.OMAN, FolderLabel.MOROCCO, FolderLabel.ALGERIA,
        FolderLabel.TUNISIA, FolderLabel.LIBYA, FolderLabel.SUDAN, FolderLabel.YEMEN, FolderLabel.MAURITANIA,
        FolderLabel.SOMALIA, FolderLabel.DJIBOUTI, FolderLabel.COMOROS)
    val genres = listOf(FolderLabel.GENERAL, FolderLabel.SPORTS, FolderLabel.NEWS, FolderLabel.MOVIES,
        FolderLabel.KIDS, FolderLabel.DOCUMENTARY, FolderLabel.MUSIC, FolderLabel.OTHER)
    fun child(root: String, label: FolderLabel) = "$root-${label.name.lowercase(java.util.Locale.ROOT)}"
    fun folders(): List<ChannelFolder> = buildList {
        listOf(FolderLabel.ARABIC, FolderLabel.BEIN, FolderLabel.GERMAN, FolderLabel.POLISH,
            FolderLabel.UNCLASSIFIED).forEachIndexed { i, label ->
            add(ChannelFolder(label.name.lowercase(java.util.Locale.ROOT), label = label, order = i))
        }
        (countries + FolderLabel.ARAB_MISC).forEachIndexed { i, label ->
            add(ChannelFolder(child("arabic", label), "arabic", label, order = i))
        }
        for (root in listOf("german", "polish")) genres.forEachIndexed { i, label ->
            add(ChannelFolder(child(root, label), root, label, order = i))
        }
    }
}