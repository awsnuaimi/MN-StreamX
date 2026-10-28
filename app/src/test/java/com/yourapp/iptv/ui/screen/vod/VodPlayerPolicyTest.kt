package com.yourapp.iptv.ui.screen.vod

import com.yourapp.iptv.player.vod.VodPlayerSnapshot
import org.junit.Assert.*
import org.junit.Test

class VodPlayerPolicyTest {
    private val playing = VodPlayerSnapshot(playing = true,buffering = false)
    @Test fun controlsOnlyHideDuringActiveUninterruptedPlayback() {
        assertTrue(vodMayHideControls(playing,true,false,false))
        assertFalse(vodMayHideControls(playing,false,false,false))
        assertFalse(vodMayHideControls(playing,true,true,false))
        assertFalse(vodMayHideControls(playing,true,false,true))
    }
    @Test fun pauseBufferingFailureAndCompletionKeepControlsAvailable() {
        listOf(playing.copy(playing = false),playing.copy(buffering = true),playing.copy(failed = true),
            playing.copy(ended = true)).forEach { assertFalse(vodMayHideControls(it,true,false,false)) }
    }
    @Test fun dismissingEachModalRestoresItsOwnOpeningControl() {
        val state = VodControlsState()
        listOf(VodPlayerPanel.AUDIO to VodControlTarget.AUDIO,VodPlayerPanel.SUBTITLES to VodControlTarget.SUBTITLES,
            VodPlayerPanel.SPEED to VodControlTarget.SPEED,VodPlayerPanel.DISPLAY to VodControlTarget.DISPLAY,
            VodPlayerPanel.RESTART to VodControlTarget.RESTART).forEach { (panel,target) ->
            val before = state.focusEpoch
            state.open(panel,target);state.hide();assertTrue(state.shown)
            state.closePanel();assertNull(state.panel);assertEquals(target,state.restore)
            assertTrue(state.focusEpoch > before)
        }
    }
    @Test fun sliderDragCannotBeHiddenAndShowingAgainStartsAtThePrimaryAction() {
        val state = VodControlsState();state.scrubbing = true;state.hide();assertTrue(state.shown)
        state.scrubbing = false;state.hide();assertFalse(state.shown)
        state.touch();assertTrue(state.shown);assertEquals(VodControlTarget.PLAY,state.restore)
    }
}