package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModelStore
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val owner = ViewModelStore()
    private class Repository : BackupRepository {
        override val maintenance = MutableStateFlow(BackupMaintenance.READY)
        var exports = 0; var restores = 0; var discarded: String? = null
        var options: BackupRestoreOptions? = null
        var fail = false
        override suspend fun recover() = Unit
        override suspend fun export(password: CharArray): ByteArray {
            try { exports++; delay(20); return byteArrayOf(1, 2, 3) } finally { password.fill('\u0000') }
        }
        override suspend fun inspect(bytes: ByteArray, password: CharArray): BackupPreview {
            try {
                delay(20)
                if (fail) throw BackupException(BackupProblem.AUTHENTICATION)
                return BackupPreview("token",1000,listOf("A"),1,0,1,2,3,4)
            } finally { password.fill('\u0000') }
        }
        override suspend fun restore(token: String, options: BackupRestoreOptions) { delay(20); restores++; this.options = options }
        override fun discard(token: String) { discarded = token }
    }
    private class Documents : BackupDocuments {
        val writes = mutableListOf<Pair<String,List<Byte>>>()
        override suspend fun read(uri: String) = BackupDocument("Synthetic.mnxb", byteArrayOf(4,5,6))
        override suspend fun writeNew(uri: String, bytes: ByteArray) { writes.add(uri to bytes.toList()) }
    }
    private val repository = Repository()
    private val documents = Documents()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { owner.clear(); Dispatchers.resetMain() }
    private fun model() = BackupViewModel(repository, documents).also { owner.put("backup",it) }
    private fun password() = "Synthetic-password-42".toCharArray()

    @Test fun exportRequiresMatchingPasswordsAndIgnoresDoubleClicksWhileWorking() = runTest(dispatcher) {
        val model = model()
        val first = password(); val second = "different".toCharArray()
        model.create(first, second)
        assertEquals(BackupProblem.PASSWORD_MISMATCH, model.state.value.problem)
        assertTrue(first.all { it == '\u0000' }); assertTrue(second.all { it == '\u0000' }); assertEquals(0,repository.exports)
        model.create(password(),password()); model.create(password(),password()); advanceUntilIdle()
        assertEquals(1,repository.exports); assertTrue(model.state.value.awaitingPicker)
    }
    @Test fun cancelingTheExportPickerWritesNothingAndAChosenUriGetsTheEncryptedBytesOnce() = runTest(dispatcher) {
        val model = model()
        model.create(password(),password()); advanceUntilIdle()
        model.consumeExportRequest(); assertNull(model.state.value.exportRequest)
        model.write(null); assertTrue(documents.writes.isEmpty()); assertFalse(model.state.value.busy)
        model.create(password(),password()); advanceUntilIdle()
        model.write("content://synthetic/new"); model.write("content://synthetic/duplicate"); advanceUntilIdle()
        assertEquals(listOf("content://synthetic/new"),documents.writes.map { it.first }); assertTrue(model.state.value.exported)
    }
    @Test fun unlockingOnlyShowsAPreviewAndRestoresAfterAnExplicitActionWithTheChosenOptions() = runTest(dispatcher) {
        val model = model(); model.read("content://synthetic/source"); advanceUntilIdle()
        val secret = password(); model.inspect(secret); advanceUntilIdle()
        assertTrue(secret.all { it == '\u0000' }); assertNotNull(model.state.value.preview); assertEquals(0,repository.restores)
        val options = BackupRestoreOptions(false,false,false)
        model.restore(options); model.restore(options); advanceUntilIdle()
        assertEquals(1,repository.restores); assertEquals(options,repository.options)
        assertTrue(model.state.value.restored); assertNull(model.state.value.preview)
    }
    @Test fun incorrectPasswordsAllowAnotherAttemptWithTheSameSelectedFile() = runTest(dispatcher) {
        val model = model(); model.read("content://synthetic/source"); advanceUntilIdle()
        repository.fail = true; model.inspect(password()); advanceUntilIdle()
        assertEquals(BackupProblem.AUTHENTICATION,model.state.value.problem); assertNotNull(model.state.value.fileName)
        repository.fail = false; model.inspect(password()); advanceUntilIdle(); assertNotNull(model.state.value.preview)
    }
    @Test fun cancelingOrClearingTheScreenDiscardsTheDecryptedPreview() = runTest(dispatcher) {
        val model = model(); model.read("content://synthetic/source"); advanceUntilIdle()
        model.inspect(password()); advanceUntilIdle(); model.clearPreview()
        assertEquals("token",repository.discarded); assertNull(model.state.value.preview)
        repository.discarded = null; model.inspect(password()); advanceUntilIdle(); owner.clear()
        assertEquals("token",repository.discarded)
    }
    @Test fun aMissingSystemPickerDoesNotLeaveTheFormPermanentlyBusy() = runTest(dispatcher) {
        val model = model(); model.create(password(),password()); advanceUntilIdle()
        model.pickerUnavailable()
        assertEquals(BackupProblem.PICKER,model.state.value.problem); assertFalse(model.state.value.busy)
        assertNull(model.state.value.exportRequest); assertTrue(documents.writes.isEmpty())
    }
}