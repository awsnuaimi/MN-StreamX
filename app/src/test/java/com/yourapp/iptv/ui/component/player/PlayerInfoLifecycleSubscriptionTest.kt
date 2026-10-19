package com.yourapp.iptv.ui.component.player

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerInfoLifecycleSubscriptionTest {
    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test fun stoppingTheScreenDetachesAndNeverReattachesAReleasedPlayer() {
        val owner = Owner()
        owner.registry.currentState = Lifecycle.State.STARTED
        var attached = 0
        var detached = 0
        val subscription = PlayerInfoLifecycleSubscription(owner.lifecycle, { attached++ }, { detached++ })
        assertEquals(1, attached)
        owner.registry.currentState = Lifecycle.State.CREATED
        assertEquals(1, detached)
        owner.registry.currentState = Lifecycle.State.STARTED
        assertEquals(1, attached)
        subscription.close()
        assertEquals(1, detached)
    }

    @Test fun disposalBeforeStopDetachesExactlyOnceAndRemovesTheObserver() {
        val owner = Owner()
        owner.registry.currentState = Lifecycle.State.STARTED
        var detached = 0
        val subscription = PlayerInfoLifecycleSubscription(owner.lifecycle, {}, { detached++ })
        subscription.close(); subscription.close()
        owner.registry.currentState = Lifecycle.State.DESTROYED
        assertEquals(1, detached)
        assertEquals(0, owner.registry.observerCount)
    }

    @Test fun stoppedScreensDoNotSubscribeToAnOldPlayer() {
        val owner = Owner()
        owner.registry.currentState = Lifecycle.State.CREATED
        var attached = 0
        var detached = 0
        val subscription = PlayerInfoLifecycleSubscription(owner.lifecycle, { attached++ }, { detached++ })
        owner.registry.currentState = Lifecycle.State.STARTED
        subscription.close()
        assertEquals(0, attached)
        assertEquals(0, detached)
    }
}