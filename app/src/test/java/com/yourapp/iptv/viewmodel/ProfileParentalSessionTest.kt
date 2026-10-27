package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModelStore
import com.yourapp.iptv.data.parental.Access
import com.yourapp.iptv.data.profile.PROFILE_A
import com.yourapp.iptv.data.profile.ProfileSettings
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.parental.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileParentalSessionTest {
    private val dispatcher = StandardTestDispatcher()
    private val owners = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { owners.clear();Dispatchers.resetMain() }
    @Test fun profileOnlySwitchCancelsOldDialogsAndExternalLeaseWithoutRevokingTheTransferredGrant() = runTest(dispatcher) {
        val settings = ProfileSettings();val pin = Access(ParentalState(ready = true,configured = true,unlocked = true))
        val model = ParentalViewModel(pin,settings,object : ParentalCatalog { override suspend fun groups(source: AppSettings) = emptyList<String>() })
        owners.put("session",model);runCurrent();model.beginExternal();model.requestPin("settings")
        settings.values.value = settings.values.value.copy(profileId = PROFILE_A);runCurrent()
        assertTrue(pin.state.value.unlocked);assertNull(model.externalFlow.value);assertNull(model.pinRequest.value)
        assertTrue(model.renderReset.value > 0)
        settings.values.value = settings.values.value.copy(m3uUrl = "https://synthetic.invalid/other");runCurrent()
        assertFalse(pin.state.value.unlocked)
    }
    @Test fun addingAChildChangesTheAdultPrivacyBoundaryEvenWhenNoPinEpochChanged() {
        val render = ParentalRenderSession();val open = ParentalState(ready = true,configured = true)
        val initial = render.frame(open,false,0)
        val after = render.frame(open.copy(profileLocked = true),false,0)
        assertNotEquals(initial.owner,after.owner);assertFalse(after.state.allows(ParentalSection.LIVE))
        assertFalse(after.hidden)
    }
}