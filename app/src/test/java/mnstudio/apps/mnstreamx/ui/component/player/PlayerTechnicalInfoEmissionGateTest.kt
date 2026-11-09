package mnstudio.apps.mnstreamx.ui.component.player

import mnstudio.apps.mnstreamx.domain.model.PlaybackTechnicalInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerTechnicalInfoEmissionGateTest {
    @Test fun initialEmptyInfoEmitsOnce() {
        val gate = TechnicalInfoEmissionGate()
        assertTrue(gate.accept(PlaybackTechnicalInfo()))
        assertFalse(gate.accept(PlaybackTechnicalInfo()))
    }

    @Test fun channelMetadataChangesAreNotLost() {
        val gate = TechnicalInfoEmissionGate()
        val sd = PlaybackTechnicalInfo(720, 576, 25f, 2)
        val hd = sd.copy(width = 1920, height = 1080)
        assertTrue(gate.accept(sd))
        assertFalse(gate.accept(sd.copy()))
        assertTrue(gate.accept(hd))
        assertFalse(gate.accept(hd))
        assertTrue(gate.accept(PlaybackTechnicalInfo()))
    }

    @Test fun aNewPlayerObserverOwnsAnIndependentGate() {
        val hd = PlaybackTechnicalInfo(1920, 1080)
        val first = TechnicalInfoEmissionGate()
        val second = TechnicalInfoEmissionGate()
        assertTrue(first.accept(hd))
        assertFalse(first.accept(hd))
        assertTrue(second.accept(hd))
    }
}