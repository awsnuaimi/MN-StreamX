package com.yourapp.iptv.player.vod

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.TrackNameProvider

internal data class VodTrackChoice(val group: TrackGroup, val index: Int, val name: String, val chosen: Boolean)
internal data class VodTrackList(val choices: List<VodTrackChoice> = emptyList(), val automatic: Boolean = true, val disabled: Boolean = false)

/** Session preferences survive an episode transition, but never contain source URLs or credentials. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class VodTrackPreferences {
    private var audioLanguage: String? = null
    private var textLanguage: String? = null
    private var textDisabled = false

    fun apply(player: Player) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguage(audioLanguage).setPreferredTextLanguage(textLanguage)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT,textDisabled).build()
    }
    fun select(player: Player, type: Int, choice: VodTrackChoice?) {
        if (!player.isCommandAvailable(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS)) return
        // The dialog may have been open while the provider replaced the track groups.
        if (choice != null && player.currentTracks.groups.none { group ->
                group.type == type && group.mediaTrackGroup == choice.group &&
                    choice.index in 0 until group.length && group.isTrackSupported(choice.index)
            }) return
        val language = choice?.group?.getFormat(choice.index)?.language
        val builder = player.trackSelectionParameters.buildUpon().clearOverridesOfType(type).setTrackTypeDisabled(type,false)
        if (choice != null) builder.setOverrideForType(TrackSelectionOverride(choice.group,choice.index))
        when (type) {
            C.TRACK_TYPE_AUDIO -> { audioLanguage = language; builder.setPreferredAudioLanguage(language) }
            C.TRACK_TYPE_TEXT -> { textLanguage = language; textDisabled = false; builder.setPreferredTextLanguage(language) }
            else -> return
        }
        player.trackSelectionParameters = builder.build()
    }
    fun disableText(player: Player) {
        if (!player.isCommandAvailable(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS)) return
        textDisabled = true
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true).build()
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun vodTracks(player: Player, type: Int, names: TrackNameProvider): VodTrackList {
    val parameters = player.trackSelectionParameters
    val disabled = type in parameters.disabledTrackTypes
    val choices = player.currentTracks.groups.filter { it.type == type }.flatMap { group ->
        (0 until group.length).filter { group.isTrackSupported(it) }.map { index ->
            VodTrackChoice(group.mediaTrackGroup,index,names.getTrackName(group.getTrackFormat(index)),
                !disabled && parameters.overrides[group.mediaTrackGroup]?.trackIndices?.contains(index) == true)
        }
    }
    return VodTrackList(choices, !disabled && choices.none { it.chosen },disabled)
}