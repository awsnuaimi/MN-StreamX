package mnstudio.apps.mnstreamx.ui.component.common

import org.junit.Assert.*
import org.junit.Test

class TvTextInputSessionTest {
    @Test fun initiallyHiddenImeDoesNotEndTheExplicitEditRequest() {
        val session = TvTextInputSession()
        repeat(10) { session.imeVisibilityChanged(false) }
        assertTrue(session.editing)
    }
    @Test fun shownThenHiddenImeEndsEditing() {
        val session = TvTextInputSession()
        session.imeVisibilityChanged(true)
        session.imeVisibilityChanged(false)
        assertFalse(session.editing)
    }
    @Test fun laterWindowAndImeEventsCannotRearmEditing() {
        val session = TvTextInputSession()
        session.imeVisibilityChanged(true)
        session.imeVisibilityChanged(false)
        repeat(100) { session.imeVisibilityChanged(true); session.imeVisibilityChanged(false) }
        assertFalse(session.editing)
    }
    @Test fun onlyAnotherExplicitEditActionRearmsTyping() {
        val session = TvTextInputSession()
        session.stopEditing()
        session.beginEditing()
        assertTrue(session.editing)
        session.imeVisibilityChanged(false)
        assertTrue(session.editing)
        session.imeVisibilityChanged(true)
        session.imeVisibilityChanged(false)
        assertFalse(session.editing)
    }
    @Test fun stoppingForLifecycleKeepsTypingStopped() {
        val session = TvTextInputSession()
        session.stopEditing()
        repeat(10) { session.imeVisibilityChanged(false) }
        assertFalse(session.editing)
    }
}