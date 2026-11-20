package mnstudio.apps.mnstreamx.ui.screen.player

import android.view.KeyEvent

import mnstudio.apps.mnstreamx.ui.component.player.*
import org.junit.Assert.*
import org.junit.Test

class PlayerPanelStateTest {
    @Test fun initialPlaybackAndSelectionDoNotOpenCards() {
        assertEquals(PlayerPanel.WATCHING, PlayerPanelState().panel)
        assertEquals(PlayerPanel.WATCHING, PlayerPanelState().cards().afterSelection().panel)
        assertEquals(PlayerPanel.WATCHING, PlayerPanelState().afterZapping().panel)
    }
    @Test fun explicitlyOpenedCardsStayOpenDuringRepeatedZapping() {
        var state = PlayerPanelState().cards()
        repeat(20) { state = state.afterZapping(); assertEquals(PlayerPanel.CARDS, state.panel) }
        assertEquals(PlayerPanel.WATCHING, state.afterSelection().panel)
    }
    @Test fun nestedMoreDetailsAndCardsHavePredictableBackAndFocusRestoration() {
        var state = PlayerPanelState().cards().more().details()
        assertTrue(state.isModal)
        state = state.closeTop(); assertEquals(PlayerPanel.MORE, state.panel)
        state = state.closeTop(); assertEquals(PlayerPanel.CARDS, state.panel); assertTrue(state.focusMore)
        state = state.closeTop(); assertEquals(PlayerPanel.WATCHING, state.panel); assertFalse(state.isModal)
    }
    @Test fun channelListReturnsToItsActualCaller() {
        assertEquals(PlayerPanel.WATCHING, PlayerPanelState().channels().closeTop().panel)
        assertEquals(PlayerPanel.MORE, PlayerPanelState().cards().more().channels().closeTop().panel)
        assertEquals(PlayerPanel.CARDS, PlayerPanelState().cards().channels().closeTop().panel)
    }
    @Test fun historyIsModalAndBackReturnsToMoreThenCards() {
        val history = PlayerPanelState().cards().more().history()
        assertTrue(history.isModal)
        assertEquals(PlayerPanel.MORE, history.closeTop().panel)
        assertEquals(PlayerPanel.CARDS, history.closeTop().closeTop().panel)
        assertEquals(PlayerPanel.WATCHING, history.afterSelection().panel)
    }
    @Test fun recentRailIsModalAndBackOrSelectionClosesToWatching() {
        val state = PlayerPanelState().recents()
        assertEquals(PlayerPanel.RECENTS, state.panel)
        assertTrue(state.isModal)
        assertEquals(PlayerBackAction.CLOSE_RECENTS, playerBackAction(state.panel))
        assertEquals(PlayerPanel.WATCHING, state.closeTop().panel)
        assertEquals(PlayerPanel.WATCHING, state.afterSelection().panel)
        assertEquals(PlayerPanel.WATCHING, state.afterZapping().panel)
    }
    @Test fun captionsReturnToMoreAndNeverChangeTheWatchingState() {
        val state = PlayerPanelState().cards().more().captions()
        assertTrue(state.isModal)
        assertEquals(PlayerPanel.MORE, state.closeTop().panel)
        assertEquals(PlayerPanel.CARDS, state.closeTop().closeTop().panel)
        assertEquals(PlayerPanel.WATCHING, state.afterSelection().panel)
    }
    @Test fun arrowsOpenAndCloseCardsAndOnlyChannelKeysZapDuringPlayback() {
        var state = PlayerPanelState(); var next = 0; var previous = 0
        val keys = PlayerRemoteKeyGate()
        fun press(code: Int, handled: Boolean = true) {
            fun send(action: Int) = keys.handle(code, action, 0, false, state.isModal, state.panel == PlayerPanel.CARDS) {
                when (it) {
                    PlayerRemoteCommand.OPEN_CARDS -> state = state.cards()
                    PlayerRemoteCommand.HIDE_CARDS -> state = state.watching()
                    PlayerRemoteCommand.NEXT -> { next++; state = state.afterZapping() }
                    PlayerRemoteCommand.PREVIOUS -> { previous++; state = state.afterZapping() }
                    else -> Unit
                }
            }
            assertEquals(handled, send(0)); assertEquals(handled, send(1))
        }
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals(PlayerPanel.CARDS, state.panel)
        press(KeyEvent.KEYCODE_DPAD_DOWN, handled = false)
        press(KeyEvent.KEYCODE_CHANNEL_UP, handled = false)
        assertEquals(0, next)
        press(KeyEvent.KEYCODE_DPAD_UP)
        assertEquals(PlayerPanel.WATCHING, state.panel)
        press(KeyEvent.KEYCODE_DPAD_UP, handled = false)
        press(KeyEvent.KEYCODE_CHANNEL_UP)
        press(KeyEvent.KEYCODE_CHANNEL_DOWN)
        assertEquals(1, next)
        assertEquals(1, previous)
    }
}