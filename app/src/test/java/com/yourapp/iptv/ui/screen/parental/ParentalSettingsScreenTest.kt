package com.yourapp.iptv.ui.screen.parental

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import com.yourapp.iptv.data.parental.*
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.parental.*
import com.yourapp.iptv.testing.SearchAccounts
import com.yourapp.iptv.ui.component.common.LocalizedResources
import com.yourapp.iptv.ui.theme.IPTVAppTheme
import com.yourapp.iptv.viewmodel.ParentalViewModel
import java.io.File
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowDialog
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34],application = Application::class,qualifiers = "w960dp-h540dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ParentalSettingsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val owner = ViewModelStore()
    @After fun cleanup() { owner.clear() }
    private fun show(language: HomeLanguage, access: ParentalControl = Access(ParentalState(ready = true,configured = true,unlocked = true))) {
        lateinit var model: ParentalViewModel
        compose.runOnIdle {
            model = ParentalViewModel(access,SearchAccounts(),object : ParentalCatalog {
                override suspend fun groups(source: AppSettings) = (1..120).map { "Group %03d".format(it) }
            });owner.put("root",model)
        }
        compose.setContent { IPTVAppTheme(theme = HomeTheme.RED) { LocalizedResources(language) {
            Surface(Modifier.fillMaxSize()) { CompositionLocalProvider(LocalParentalSession provides ParentalUiSession(access.state.value,language,model)) {
                ParentalSettingsScreen({},model)
            } }
        } } }
    }
    @Test fun germanSettingsFitTheSmallTvAndTheLastGroupAndSaveActionAreReachable() {
        show(HomeLanguage.GERMAN)
        compose.onNodeWithText("Jugendschutz").assertIsDisplayed();compose.onNodeWithText("Zurück").assertIsDisplayed()
        export("de-small")
        compose.onNodeWithTag("parental-settings-list").performScrollToNode(hasText("Group 120",substring = true))
        compose.onNodeWithText("☐ Group 120").assertIsDisplayed().performClick()
        compose.onNodeWithTag("parental-settings-list").performScrollToNode(hasText("Speichern und jetzt sperren"))
        compose.onNodeWithText("Speichern und jetzt sperren").assertIsDisplayed()
    }
    @Test fun arabicSettingsHaveLocalizedActionsAndReadableRtlSections() {
        show(HomeLanguage.ARABIC)
        compose.onNodeWithText("الرقابة الأبوية").assertIsDisplayed();compose.onNodeWithText("رجوع").assertIsDisplayed()
        export("ar-small")
    }
    @Test fun setupRequiresMatchingConfirmationAndEachStepGivesFocusBackToThePad() {
        var writes = 0;var chosen: CharArray? = null
        val access = object : ParentalControl by ParentalControl.Disabled {
            override val state = MutableStateFlow(ParentalState(ready = true))
            override suspend fun create(pin: CharArray): ParentalResult {
                writes++;chosen = pin.copyOf();pin.fill('\u0000')
                state.value = state.value.copy(configured = true,unlocked = true,epoch = 1)
                return ParentalResult.OK
            }
        }
        show(HomeLanguage.GERMAN,access)
        compose.onNodeWithText("6-stellige PIN festlegen").performClick()
        fun focusWindow() {
            compose.runOnIdle { ShadowDialog.getLatestDialog().window!!.decorView.dispatchWindowFocusChanged(true) }
            compose.waitForIdle();compose.onNodeWithText("1").assertIsFocused()
        }
        fun enter(value: String) {
            focusWindow();value.forEach { compose.onNodeWithText(it.toString()).performClick() }
            compose.onNodeWithText("Bestätigen").performClick()
        }
        enter("123456");compose.onNodeWithText("PIN wiederholen").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0,writes) }
        enter("654321");compose.onNodeWithText("Die PINs stimmen nicht überein").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0,writes) }
        enter("123456");enter("123456")
        compose.runOnIdle { assertEquals(1,writes);assertArrayEquals("123456".toCharArray(),chosen) }
    }
    private fun export(name: String) {
        val directory = System.getProperty("mnstreamx.uiPreviewDir")?.let(::File) ?: return
        val bounds = compose.onNodeWithTag("parental-settings",true).fetchSemanticsNode().boundsInRoot
        fun owner(view: View): View? {
            if (view.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") return view
            if (view is ViewGroup) for (i in 0 until view.childCount) owner(view.getChildAt(i))?.let { return it }
            return null
        }
        compose.runOnIdle {
            directory.mkdirs()
            val bitmap = Bitmap.createBitmap(bounds.width.toInt(),bounds.height.toInt(),Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap);canvas.translate(-bounds.left,-bounds.top)
                requireNotNull(owner(compose.activity.window.decorView)).draw(canvas)
                directory.resolve("parental-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            } finally { bitmap.recycle() }
        }
    }
}