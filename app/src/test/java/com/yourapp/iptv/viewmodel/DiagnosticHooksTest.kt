package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModelStore
import com.yourapp.iptv.data.network.HttpResponseFailure
import com.yourapp.iptv.domain.diagnostics.*
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.VodRepository
import com.yourapp.iptv.domain.update.*
import com.yourapp.iptv.testing.SearchAccounts
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DiagnosticHooksTest {
    private val dispatcher = StandardTestDispatcher()
    private val owner = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher);DiagnosticJournal.clear() }
    @After fun cleanup() { owner.clear();dispatcher.scheduler.runCurrent();Dispatchers.resetMain();DiagnosticJournal.clear() }
    @Test fun movieCatalogErrorsKeepTheirSourceAndHttpStatusWithoutLoggingProviderText() = runTest(dispatcher) {
        val settings = SearchAccounts()
        val repository = object : VodRepository {
            override fun supports(settings: AppSettings) = true
            override suspend fun catalog(settings: AppSettings,kind: VodKind): VodCatalog =
                throw PlaylistLoadException("https://private.invalid/secret",HttpResponseFailure(503))
            override suspend fun category(settings: AppSettings,kind: VodKind,categoryId: String) = error("Unexpected category")
            override suspend fun details(settings: AppSettings,kind: VodKind,item: VodItem) = error("Unexpected details")
            override fun playbackUrl(settings: AppSettings,kind: VodKind,id: String,extension: String) = error("Unexpected playback")
        }
        val model = VodViewModel(settings,repository).also { owner.put("vod",it) }
        model.load(VodKind.MOVIE);runCurrent()
        val event = DiagnosticJournal.events.value.single()
        assertEquals(DiagnosticArea.MOVIE_CATALOG,event.area);assertEquals(settings.key,event.sourceKey);assertEquals(503,event.httpStatus)
        assertTrue(model.uiState.value.error);assertFalse(event.toString().contains("private.invalid"))
    }
    @Test fun updaterFailuresPreserveTheirExistingTypedIssueForTheReport() = runTest(dispatcher) {
        val repository = object : AppUpdateRepository {
            override suspend fun check(): AppUpdate? = throw UpdateFailure(UpdateIssue.NO_RELEASE)
            override suspend fun download(update: AppUpdate,progress: (Int) -> Unit): File = error("Unexpected download")
            override suspend fun verify(update: AppUpdate,file: File) = error("Unexpected verification")
        }
        val model = AppUpdateViewModel(repository).also { owner.put("update",it) }
        model.updateApp();runCurrent()
        assertEquals(UpdateIssue.NO_RELEASE,DiagnosticJournal.events.value.single().update)
        assertEquals(UpdateIssue.NO_RELEASE,model.state.value.issue)
    }
}