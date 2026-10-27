package com.yourapp.iptv.domain.parental

import org.junit.Assert.*
import org.junit.Test

class ParentalRenderSessionTest {
    private val granted = ParentalState(ready = true,configured = true,unlocked = true,epoch = 1)
    @Test fun ordinaryRelockReplacesTheNavigationOwner() {
        val session = ParentalRenderSession();val first = session.frame(granted,false,0)
        val locked = session.frame(granted.copy(unlocked = false,epoch = 2),false,0)
        assertNotEquals(first.owner,locked.owner);assertFalse(locked.state.unlocked);assertFalse(locked.hidden)
    }
    @Test fun aDocumentReturnIsRetainedBehindALockUntilReauthentication() {
        val session = ParentalRenderSession();val first = session.frame(granted,false,0)
        session.frame(granted,true,0)
        val locked = session.frame(granted.copy(unlocked = false,epoch = 2),true,0)
        assertEquals(first.owner,locked.owner);assertTrue(locked.hidden)
        val resumed = session.frame(granted.copy(epoch = 3),true,0)
        assertEquals(first.owner,resumed.owner);assertFalse(resumed.hidden)
        assertEquals(first.owner,session.frame(granted.copy(epoch = 3),false,0).owner)
    }
    @Test fun coalescedPauseAndResultEventsStillHideTheOldScreen() {
        val session = ParentalRenderSession();val first = session.frame(granted,false,0)
        val locked = session.frame(granted.copy(unlocked = false,epoch = 2),true,0)
        assertTrue(locked.hidden);assertEquals(first.owner,locked.owner)
    }
    @Test fun cancellationExpiryAndSourceChangesDisposeTheRetainedOwner() {
        val session = ParentalRenderSession();val first = session.frame(granted,false,0)
        session.frame(granted.copy(unlocked = false,epoch = 2),true,0)
        val cancelled = session.frame(granted.copy(unlocked = false,epoch = 3),false,1)
        assertNotEquals(first.owner,cancelled.owner);assertFalse(cancelled.hidden);assertFalse(cancelled.state.unlocked)
    }
    @Test fun changedRulesCannotReuseAnOldAuthorizedProjection() {
        val session = ParentalRenderSession();val first = session.frame(granted,true,0)
        val changed = session.frame(granted.copy(unlocked = false,rules = ParentalRules(setOf(ParentalSection.LIVE)),epoch = 2),true,0)
        assertFalse(changed.hidden);assertNotEquals(first.owner,changed.owner);assertFalse(changed.state.unlocked)
    }
    @Test fun withoutAPinOrdinaryBackgroundingPreservesNavigation() {
        val session = ParentalRenderSession();val first = session.frame(ParentalState(ready = true),false,0)
        assertEquals(first.owner,session.frame(ParentalState(ready = true,epoch = 4),false,0).owner)
    }
}