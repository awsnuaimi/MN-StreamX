package mnstudio.apps.mnstreamx.ui.component.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CaptionPresentationTest {
    @Test fun sameSettingsProduceSameDisplayKey() {
        assertEquals(CaptionPresentation(false, 28, false), CaptionPresentation(false, 28, false))
    }

    @Test fun styleChangesUpdateTheViewKey() {
        val base = CaptionPresentation(true, 28, false)
        assertNotEquals(base, base.copy(shaded = true))
        assertNotEquals(base, base.copy(textSp = 34))
        assertNotEquals(base, base.copy(visible = false))
    }
}