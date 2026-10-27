package com.yourapp.iptv.ui.screen.profile

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import java.io.File
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import com.yourapp.iptv.data.profile.*
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.profile.*
import com.yourapp.iptv.domain.repository.ProfileRepository
import com.yourapp.iptv.ui.component.common.LocalizedResources
import com.yourapp.iptv.ui.screen.parental.*
import com.yourapp.iptv.ui.theme.IPTVAppTheme
import com.yourapp.iptv.viewmodel.ProfilesViewModel
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34],application = Application::class,qualifiers = "w960dp-h540dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val owners = ViewModelStore()
    @After fun cleanup() { owners.clear() }
    private fun show(language: HomeLanguage, manager: Boolean = false, used: () -> Unit = {}) {
        val people = ProfilePeople(UserProfiles(UserProfiles().entries + profileRow().copy(name = "Family") + profileRow(PROFILE_B,ProfileKind.CHILD).copy(name = "Kids")))
        val repository = object : ProfileRepository by people {
            override suspend fun select(id: String): ProfileResult = ProfileResult.OK
        }
        lateinit var model: ProfilesViewModel
        compose.runOnIdle { model = ProfilesViewModel(repository);owners.put("profiles",model) }
        compose.setContent { IPTVAppTheme { LocalizedResources(language) { Surface(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalParentalSession provides ParentalUiSession(language = language)) {
                if (manager) ProfileManagerScreen({},{},model) else ProfileScreen({},{},used,model = model)
            }
        } } } }
    }
    @Test fun germanPickerFitsSmallTvAndRemoteCanSelectAProfile() {
        var used = 0;show(HomeLanguage.GERMAN,used = { used++ })
        compose.runOnIdle { compose.activity.window.decorView.dispatchWindowFocusChanged(true) }
        compose.waitForIdle()
        compose.onNodeWithText("Profile").assertIsDisplayed();compose.onNodeWithText("Profile verwalten").assertIsDisplayed();export("profiles-de","profile-picker")
        compose.onNodeWithText("Zurück").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("✓ Bisheriges Profil • Erwachsen").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Family • Erwachsen").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1,used) }
    }
    @Test fun arabicManagerShowsLocalizedNameInputAndChildChoice() {
        show(HomeLanguage.ARABIC,manager = true)
        compose.onNodeWithText("إدارة الملفات").assertIsDisplayed()
        compose.onNodeWithText("اسم الملف").assertIsDisplayed();export("profiles-ar","profile-manager")
        compose.onNodeWithText("أطفال").assertIsDisplayed().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("أسرة")
        compose.onNodeWithText("إنشاء ملف").assertIsEnabled()
    }
    private fun export(name: String, tag: String) {
        val directory = System.getProperty("mnstreamx.uiPreviewDir")?.let(::File) ?: return
        val bounds = compose.onNodeWithTag(tag,true).fetchSemanticsNode().boundsInRoot
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