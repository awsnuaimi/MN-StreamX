package com.yourapp.iptv.data.parental

import com.yourapp.iptv.domain.parental.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StoredParentalControlTest {
    private val storage = PinMemory()
    private val clock = PinClock()
    private val control = StoredParentalControl(storage,clock,Dispatchers.Unconfined)
    private suspend fun start() { control.foreground(); control.initialize() }
    private suspend fun create() { start(); assertEquals(ParentalResult.OK,control.create("123456".toCharArray())) }
    private suspend fun wrong() = control.unlock("654321".toCharArray())

    @Test fun nothingIsAccessibleBeforeTheProtectionStateIsRead() = runBlocking {
        assertFalse(control.state.value.allows(ParentalSection.LIVE));start()
        assertTrue(control.state.value.allows(ParentalSection.LIVE));assertFalse(control.state.value.configured)
    }
    @Test fun creatingThePinStoresASaltedVerifierAndWipesTheInput() = runBlocking {
        start();val input = "123456".toCharArray();assertEquals(ParentalResult.OK,control.create(input))
        assertTrue(input.all { it == '\u0000' });assertTrue(control.state.value.unlocked)
        val saved = requireNotNull(storage.value);assertEquals(16,saved.salt.size);assertEquals(32,saved.hash.size)
        assertTrue(PinHash.matches("123456".toCharArray(),saved));assertFalse(PinHash.matches("654321".toCharArray(),saved))
    }
    @Test fun onlySixAsciiDigitsAreAccepted() = runBlocking {
        start()
        for (value in listOf("12345","1234567","１２３４５６","12345x")) {
            val input = value.toCharArray();assertEquals(ParentalResult.INVALID_PIN,control.create(input))
            assertTrue(input.all { it == '\u0000' })
        }
        assertNull(storage.value)
    }
    @Test fun fiveFailuresStartAPersistedCooldownAndWaitingDoesNotConsumeAnotherAttempt() = runBlocking {
        create();control.lock();repeat(4) { assertEquals(ParentalResult.WRONG_PIN,wrong()) }
        assertEquals(ParentalResult.WAIT,wrong());assertEquals(30L,control.remainingSeconds())
        val writes = storage.writes
        assertEquals(ParentalResult.WAIT,control.unlock("123456".toCharArray()))
        assertEquals(writes,storage.writes);assertEquals(5,storage.value?.failures)
        val restarted = StoredParentalControl(storage,clock,Dispatchers.Unconfined);restarted.foreground();restarted.initialize()
        assertEquals(30L,restarted.remainingSeconds());assertFalse(restarted.state.value.unlocked)
    }
    @Test fun cooldownIncreasesAfterExpiryAndCorrectPinResetsIt() = runBlocking {
        create();repeat(5) { wrong() };clock.now += 30_001
        assertEquals(ParentalResult.WAIT,wrong());assertEquals(60L,control.remainingSeconds())
        clock.now += 60_001;assertEquals(ParentalResult.OK,control.unlock("123456".toCharArray()))
        assertEquals(0,storage.value?.failures);assertEquals(0L,control.remainingSeconds())
    }
    @Test fun rebootDoesNotEraseAFailedAttemptCooldown() = runBlocking {
        create();repeat(5) { wrong() };clock.bootId++;clock.now = 0
        val restarted = StoredParentalControl(storage,clock,Dispatchers.Unconfined);restarted.foreground();restarted.initialize()
        assertEquals(30L,restarted.remainingSeconds());assertEquals(ParentalResult.WAIT,restarted.unlock("123456".toCharArray()))
    }
    @Test fun rulesAreSavedAtomicallyAndSavingRelocksTheSession() = runBlocking {
        create();val rules = ParentalRules(setOf(ParentalSection.MOVIES),mapOf("a".repeat(64) to setOf("Protected")))
        assertEquals(ParentalResult.OK,control.saveRules(rules))
        assertFalse(control.state.value.unlocked);assertEquals(rules,storage.value?.rules)
        assertFalse(control.state.value.allows(ParentalSection.MOVIES));assertTrue(control.state.value.allows(ParentalSection.LIVE))
    }
    @Test fun invalidRuleInputDoesNotTurnIntoAStorageFailure() = runBlocking {
        create();val writes = storage.writes
        assertEquals(ParentalResult.INVALID_RULES,control.saveRules(ParentalRules(groups = mapOf("invalid" to setOf("Group")))))
        assertFalse(control.state.value.storageError);assertTrue(control.state.value.unlocked);assertEquals(writes,storage.writes)
    }
    @Test fun changingPinRequiresThePreviousPinAndDisablingAlsoRequiresIt() = runBlocking {
        create();assertEquals(ParentalResult.WRONG_PIN,control.changePin("654321".toCharArray(),"222222".toCharArray()))
        assertEquals(ParentalResult.OK,control.changePin("123456".toCharArray(),"222222".toCharArray()))
        assertEquals(ParentalResult.WRONG_PIN,control.unlock("123456".toCharArray()))
        assertEquals(ParentalResult.OK,control.unlock("222222".toCharArray()))
        assertEquals(ParentalResult.WRONG_PIN,control.changePin("123456".toCharArray(),null));assertNotNull(storage.value)
        assertEquals(ParentalResult.OK,control.changePin("222222".toCharArray(),null));assertNull(storage.value)
        assertFalse(control.state.value.configured)
    }
    @Test fun backgroundDuringTheAtomicWriteCannotGrantAccessAfterward() = runBlocking {
        start();storage.beforeWrite = control::background
        assertEquals(ParentalResult.DENIED,control.create("123456".toCharArray()))
        assertTrue(control.state.value.configured);assertFalse(control.state.value.unlocked)
        storage.beforeWrite = {};control.foreground()
        assertEquals(ParentalResult.OK,control.unlock("123456".toCharArray()))
    }
    @Test fun backgroundBlocksPinOperationsAndEachLockInvalidatesTheOldSession() = runBlocking {
        create();val epoch = control.state.value.epoch;control.background();control.background()
        assertTrue(control.state.value.epoch >= epoch + 2);assertFalse(control.state.value.unlocked)
        assertEquals(ParentalResult.DENIED,control.unlock("123456".toCharArray()))
        control.foreground();assertEquals(ParentalResult.OK,control.unlock("123456".toCharArray()))
    }
    @Test fun unreadableStorageFailsClosedAndIsNeverSilentlyDeleted() = runBlocking {
        storage.failRead = true;start()
        assertTrue(control.state.value.storageError);assertFalse(control.state.value.allows(ParentalSection.LIVE));assertEquals(0,storage.writes)
        storage.failRead = false;control.initialize();assertFalse(control.state.value.storageError)
    }
    @Test fun inabilityToPersistTheAttemptCounterFailsClosed() = runBlocking {
        create();control.lock();storage.failWrite = true
        assertEquals(ParentalResult.STORAGE_ERROR,wrong());assertTrue(control.state.value.storageError)
        assertFalse(control.state.value.allows(ParentalSection.LIVE))
    }
}