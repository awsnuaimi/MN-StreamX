package mnstudio.apps.mnstreamx.ui.screen.player

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CaptionTvEntryPolicyTest {
    @Test fun okOnIdleFieldEntersEditMode() {
        assertEquals(CaptionTvEntryAction.EDIT,
            captionTvEntryAction(false, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_DOWN))
    }
    @Test fun enterAlsoOpensEditorOnSomeRemotes() {
        assertEquals(CaptionTvEntryAction.EDIT,
            captionTvEntryAction(false, KeyEvent.KEYCODE_ENTER, KeyEvent.ACTION_DOWN))
        assertEquals(CaptionTvEntryAction.EDIT,
            captionTvEntryAction(false, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.ACTION_DOWN))
    }
    @Test fun navigatingToAFieldDoesNotOpenKeyboard() {
        for (key in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)) {
            assertEquals(CaptionTvEntryAction.NONE, captionTvEntryAction(false, key, KeyEvent.ACTION_DOWN))
        }
    }
    @Test fun keyReleaseDoesNotTriggerSecondEditorOpen() {
        assertEquals(CaptionTvEntryAction.NONE,
            captionTvEntryAction(false, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_UP))
    }
    @Test fun backClosesOnlyAnActiveEditor() {
        assertEquals(CaptionTvEntryAction.FINISH,
            captionTvEntryAction(true, KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_DOWN))
        assertEquals(CaptionTvEntryAction.NONE,
            captionTvEntryAction(false, KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_DOWN))
    }
    @Test fun escapeClosesEditorWhenSupported() {
        assertEquals(CaptionTvEntryAction.FINISH,
            captionTvEntryAction(true, KeyEvent.KEYCODE_ESCAPE, KeyEvent.ACTION_DOWN))
    }
    @Test fun okInsideEditingDoesNotDiscardInput() {
        assertEquals(CaptionTvEntryAction.NONE,
            captionTvEntryAction(true, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_DOWN))
    }
}