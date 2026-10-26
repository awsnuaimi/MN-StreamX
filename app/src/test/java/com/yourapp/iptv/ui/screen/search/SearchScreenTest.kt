package com.yourapp.iptv.ui.screen.search

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.component.common.LocalizedResources
import com.yourapp.iptv.ui.screen.vod.VodDetailsDialog
import com.yourapp.iptv.ui.screen.vod.VodLabels
import com.yourapp.iptv.ui.state.*
import com.yourapp.iptv.ui.theme.IPTVAppTheme
import com.yourapp.iptv.viewmodel.VodUiState
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w1280dp-h720dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalComposeUiApi::class, ExperimentalTestApi::class)
class SearchScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val page = mutableStateOf(SearchUiState(query = "shared", sourceLoading = false, sourceKey = "a".repeat(64),
        channels = listOf(Channel("42", "Shared channel", streamUrl = "https://synthetic.invalid/42", group = "Live")),
        movies = SearchVodState(loaded = true, results = listOf(VodItem("42", "Shared film", "1"))),
        series = SearchVodState(loaded = true, results = listOf(VodItem("42", "Shared series", "1")))))
    private var submitted = 0
    private var chosen: String? = null
    private var scanned: VodKind? = null
    private var stopped: VodKind? = null
    private val actions = SearchActions({}, {}, {},
        onQuery = { page.value = page.value.copy(query = it) }, onSubmit = { submitted++ },
        onHistory = { page.value = page.value.copy(query = it) },
        onClearHistory = { page.value = page.value.copy(history = emptyList()) },
        onRetrySource = {}, onReloadChannels = {}, onRetry = {},
        onScan = { scanned = it }, onStop = { stopped = it }, onChannel = { chosen = "channel-${it.id}" },
        onVod = { kind, item -> chosen = "${kind.name}-${item.id}" })

    @Composable private fun Content(language: HomeLanguage) {
        IPTVAppTheme(theme = HomeTheme.RED) {
            LocalizedResources(language) {
                val mode = LocalInputModeManager.current
                SideEffect { mode.requestInputMode(InputMode.Keyboard) }
                Surface(Modifier.fillMaxSize()) { SearchContent(page.value, SearchLabels(language), actions) }
            }
        }
    }

    @Test fun typingSubmittingAndUsingRecentQueriesWorkInTheGermanInterface() {
        page.value = page.value.copy(query = "", history = listOf("Saved title"))
        compose.setContent { Content(HomeLanguage.GERMAN) }
        compose.onNodeWithTag("search-query").performTextReplacement("Shared title")
        compose.onNodeWithTag("search-query").performImeAction()
        compose.runOnIdle { assertEquals("Shared title", page.value.query); assertEquals(1, submitted) }
        compose.onNodeWithText("Suchtext löschen").performClick()
        compose.onNodeWithText("Saved title").performScrollTo().performClick()
        compose.onNodeWithTag("search-query").assertTextContains("Saved title")
        compose.onNodeWithText("Suchtext löschen").performClick()
        compose.onNodeWithTag("search-clear-history").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(page.value.history.isEmpty()) }
    }
    @Test fun remoteDownAndEnterCanOpenAChannelWithoutTouchInput() {
        compose.setContent { Content(HomeLanguage.GERMAN) }
        compose.onNodeWithTag("search-query").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("search-query").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("channel-42").assertIsFocused()
        export("de-red")
        compose.onNodeWithTag("channel-42").performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals("channel-42", chosen) }
    }
    @Test fun partialCatalogsShowTheScopeAndExposeScanAndStopControls() {
        page.value = page.value.copy(channels = emptyList(), movies = SearchVodState(loaded = true,
            categoryScoped = true, categoriesTotal = 12))
        compose.setContent { Content(HomeLanguage.GERMAN) }
        compose.onNodeWithTag("movie-scan").performScrollTo()
        compose.onNodeWithText("0 von 12 Kategorien durchsucht.").assertExists()
        compose.onNodeWithText("Kategorien noch nicht durchsucht. Mit der Schaltfläche unten beginnen.").assertExists()
        compose.onNodeWithTag("movie-scan").performClick()
        compose.runOnIdle { assertEquals(VodKind.MOVIE, scanned); page.value = page.value.copy(movies = page.value.movies.copy(scanning = true)) }
        compose.onNodeWithTag("movie-stop").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(VodKind.MOVIE, stopped) }
    }
    @Test
    @Config(qualifiers = "w960dp-h540dp-mdpi")
    fun returningRestoresTheSelectedResultInTheSmallArabicLayout() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Content(HomeLanguage.ARABIC) }
        compose.onNodeWithTag("channel-42").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("channel-42").assertIsFocused()
        export("ar-red-small")
        compose.onNodeWithTag("movie-42").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("MOVIE-42", chosen) }
    }
    @Test fun aSeriesResultOpensAnEpisodeIdRatherThanTryingToPlayTheSeriesId() {
        var played: Triple<VodKind, String, String>? = null
        val item = VodItem("42", "Shared series", "1")
        val details = VodDetails(item, listOf(VodSeason(1, "One", listOf(VodEpisode("99", "Episode", 1, 1, "mkv")))))
        compose.setContent {
            IPTVAppTheme {
                VodDetailsDialog(VodUiState(kind = VodKind.SERIES, loading = false, selected = item, details = details),
                    item, VodLabels(HomeLanguage.GERMAN), emptyList(), {}, {}) { kind, id, ext, _ -> played = Triple(kind, id, ext) }
            }
        }
        compose.onNodeWithText("1. Episode").performClick()
        compose.runOnIdle { assertEquals(Triple(VodKind.SERIES, "99", "mkv"), played) }
    }

    private fun export(name: String) {
        val directory = System.getProperty("mnstreamx.uiPreviewDir")?.let(::File) ?: return
        val bounds = compose.onNodeWithTag("search-page", true).fetchSemanticsNode().boundsInRoot
        fun owner(view: View): View? {
            if (view.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") return view
            if (view is ViewGroup) for (i in 0 until view.childCount) owner(view.getChildAt(i))?.let { return it }
            return null
        }
        compose.runOnIdle {
            directory.mkdirs()
            val bitmap = Bitmap.createBitmap(bounds.width.toInt(), bounds.height.toInt(), Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap); canvas.translate(-bounds.left, -bounds.top)
                requireNotNull(owner(compose.activity.window.decorView)).draw(canvas)
                directory.resolve("search-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally { bitmap.recycle() }
        }
    }
}