package com.yourapp.iptv.ui.screen.vod

import androidx.compose.runtime.*

/** One overlay owner. Modal dismissal restores the button that opened it. */
@Stable
internal class VodControlsState {
    var shown by mutableStateOf(true); private set
    var panel by mutableStateOf<VodPlayerPanel?>(null); private set
    var interaction by mutableLongStateOf(0); private set
    var scrubbing by mutableStateOf(false)
    var restore by mutableStateOf(VodControlTarget.PLAY); private set
    var focusEpoch by mutableIntStateOf(0); private set
    fun touch() {
        interaction++
        if (!shown) { restore = VodControlTarget.PLAY;focusEpoch++ }
        shown = true
    }
    fun hide() { if (panel == null && !scrubbing) shown = false }
    fun open(value: VodPlayerPanel, target: VodControlTarget) {
        touch();restore = target;panel = value
    }
    fun closePanel() { panel = null;touch();focusEpoch++ }
    fun resetFocus() { restore = VodControlTarget.PLAY;focusEpoch++ }
}