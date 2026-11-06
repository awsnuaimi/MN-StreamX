package mnstudio.apps.mnstreamx.ui.screen.vod

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import mnstudio.apps.mnstreamx.domain.model.HomeLanguage
import mnstudio.apps.mnstreamx.domain.model.VodKind
import mnstudio.apps.mnstreamx.ui.component.common.rememberContextFocus
import mnstudio.apps.mnstreamx.ui.theme.IPTVAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w600dp-h540dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalComposeUiApi::class)
class VodToolbarResponsiveUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var filterClicks = 0
    private var sortClicks = 0

    @Composable
    private fun Content() {
        IPTVAppTheme {
            val mode = LocalInputModeManager.current
            SideEffect { mode.requestInputMode(InputMode.Keyboard) }
            val focus = rememberContextFocus("toolbar-responsive")
            var sorted by remember { mutableStateOf(false) }
            Surface(Modifier.fillMaxSize()) {
                VodCatalogToolbar(VodKind.MOVIE, 48, "", VodLabels(HomeLanguage.GERMAN),
                    focus, false, true, {}, {}, {}, { filterClicks++ }, sorted,
                    { sorted = !sorted; sortClicks++ })
            }
        }
    }

    @Test fun compactToolbarKeepsSearchAndCategoryControlsVisible() {
        compose.setContent { Content() }
        listOf("vod-back", "vod-query", "vod-filter", "vod-sort", "vod-refresh").forEach {
            compose.onNodeWithTag(it).assertIsDisplayed()
        }
        val top = compose.onNodeWithTag("vod-back").fetchSemanticsNode().boundsInRoot
        val search = compose.onNodeWithTag("vod-query").fetchSemanticsNode().boundsInRoot
        val filter = compose.onNodeWithTag("vod-filter").fetchSemanticsNode().boundsInRoot
        assertTrue(top.bottom <= search.top + 1f)
        assertTrue(search.right <= filter.left + 1f)
        compose.onNodeWithTag("vod-filter").performClick()
        compose.onNodeWithTag("vod-sort").performClick()
        compose.runOnIdle { assertEquals(1, filterClicks); assertEquals(1, sortClicks) }
    }

    @Test
    @Config(qualifiers = "w360dp-h540dp-mdpi")
    fun narrowToolbarPlacesSearchAboveFilterAndSort() {
        compose.setContent { Content() }
        listOf("vod-back", "vod-query", "vod-filter", "vod-sort", "vod-refresh").forEach {
            compose.onNodeWithTag(it).assertIsDisplayed()
        }
        val search = compose.onNodeWithTag("vod-query").fetchSemanticsNode().boundsInRoot
        val filter = compose.onNodeWithTag("vod-filter").fetchSemanticsNode().boundsInRoot
        assertTrue(search.bottom <= filter.top + 1f)
    }
}