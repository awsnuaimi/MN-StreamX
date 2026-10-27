package com.yourapp.iptv.data.profile

import com.yourapp.iptv.domain.parental.*
import com.yourapp.iptv.domain.profile.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class ProfileSnapshotTest {
    @Test fun aGrantReadBeforeAConcurrentSwitchCannotBeReturnedForTheNewProfile() {
        val people = ProfilePeople(UserProfiles(UserProfiles().entries + profileRow(kind = ProfileKind.CHILD)))
        val raw = MutableStateFlow(ParentalState(ready = true,configured = true,unlocked = true))
        var switch = true
        val access = object : ParentalControl by ParentalControl.Disabled {
            override val state = object : StateFlow<ParentalState> by raw {
                override val value: ParentalState get() {
                    val captured = raw.value
                    if (switch) {
                        switch = false
                        raw.value = captured.copy(unlocked = false,epoch = 1)
                        people.state.value = people.state.value.copy(catalog = people.state.value.catalog.copy(activeId = PROFILE_A),epoch = 1)
                    }
                    return captured
                }
            }
        }
        val effective = ProfileParentalControl(access,people)
        val during = effective.state.value
        assertEquals(PROFILE_A,during.profileId);assertFalse(during.unlocked)
        assertFalse(during.ready);assertFalse(during.allows(ParentalSection.LIVE))
        val stable = effective.state.value
        assertTrue(stable.ready);assertEquals(PROFILE_A,stable.profileId);assertFalse(stable.unlocked)
    }
}