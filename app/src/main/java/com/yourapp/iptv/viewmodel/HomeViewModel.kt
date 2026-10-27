package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.profile.ProfileScope

import com.yourapp.iptv.domain.organization.*
import com.yourapp.iptv.domain.repository.OrganizationRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.domain.model.PlaylistLoadException
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.HomeStartupSnapshot
import com.yourapp.iptv.domain.model.AppSettings
import com.yourapp.iptv.domain.model.HomeBrowseSession
import com.yourapp.iptv.domain.model.HomeGridPosition
import com.yourapp.iptv.domain.model.HomeGridRestore
import com.yourapp.iptv.domain.model.HomeSessionResolver
import com.yourapp.iptv.domain.repository.HomeSessionRepository
import com.yourapp.iptv.domain.repository.HomeStartupRepository
import com.yourapp.iptv.domain.model.ChannelFavorites
import com.yourapp.iptv.domain.model.PlaybackQueue
import com.yourapp.iptv.domain.repository.FavoriteRepository
import com.yourapp.iptv.domain.repository.PlaylistRepository
import com.yourapp.iptv.domain.repository.SettingsRepository
import com.yourapp.iptv.ui.state.HomeUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import com.yourapp.iptv.di.DefaultDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.yourapp.iptv.util.LoadTimings
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.yourapp.iptv.domain.parental.ParentalControl
import com.yourapp.iptv.domain.parental.ParentalProjection

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settings: SettingsRepository,
    playlists: PlaylistRepository,
    @DefaultDispatcher private val background: CoroutineDispatcher,
    favorites: FavoriteRepository,
    organization: OrganizationRepository = OrganizationRepository.Disabled,
    startup: HomeStartupRepository = HomeStartupRepository.Disabled,
    browseSessions: HomeSessionRepository = HomeSessionRepository.Disabled,
    private val parental: ParentalControl = ParentalControl.Disabled
) : ViewModel() {
    private val state = MutableStateFlow(HomeUiState())
    val uiState = state.asStateFlow()
    private var settingsJob: Job? = null
    private var filterJob: Job? = null
    private var catalogTree = OrganizationTree()
    private val openedAtNanos = System.nanoTime()
    private var reportedReady = false
    private var favoriteIds: Set<String>? = null
    private var pendingBrowse: HomeBrowseSession? = null
    private var browseDraft: HomeBrowseSession? = null
    private var restoreSequence = 0L
    private val session = HomeSessionController(viewModelScope, browseSessions, ::onSessionLoaded)
    private val favoriteController = FavoriteController(viewModelScope, favorites, onReset = { favoriteIds = null }) {
        favoriteIds = it
        filterChannels()
    }
    val favoriteState = favoriteController.uiState
    private val organizer: OrganizationController = OrganizationController(viewModelScope, organization, background, ::onCatalogChanged)

    private val catalogLoader: HomeCatalogController = HomeCatalogController(viewModelScope,playlists,startup,background,
        object : HomeCatalogController.Target {
            override val hasChannels get() = state.value.channels.isNotEmpty()
            override fun loading() { state.update { it.copy(isLoading = it.channels.isEmpty(),
                isRefreshing = true,errorMessage = null) } }
            override fun restore(snapshot: HomeStartupSnapshot) = organizer.restore(snapshot.config,snapshot.catalog)
            override fun channels(value: List<Channel>, refreshing: Boolean) {
                state.update { it.copy(isRefreshing = refreshing) }
                organizer.setChannels(value)
            }
            override fun failed(error: Exception) { state.update { it.copy(isLoading = false,isRefreshing = false,
                errorMessage = (error as? PlaylistLoadException)?.message
                    ?: "تعذر تحميل القنوات. تحقق من الشبكة والإعدادات ثم أعد المحاولة.") } }
        })

    private fun onCatalogChanged(value: OrganizedChannels) {
        val result = ParentalProjection.prune(value,parental.state.value)
        catalogTree = result.tree
        state.update { it.copy(channels = result.channels,
            groups = if (result.tree.enabled) result.tree.folders.map { f -> FolderFilter.of(f.id) }
                else result.channels.mapNotNull { c -> c.group }.distinct().sorted(),
            selectedGroup = result.resolveFilter(it.selectedGroup), organization = result.tree,
            needsSource = false, isRefreshing = catalogLoader.isRefreshing,
            isLoading = if (result.channels.isEmpty()) false else it.isLoading) }
        filterChannels()
        val config = organizer.currentConfig() ?: return
        catalogLoader.save(config,result)
    }

    fun retryFavorites() = favoriteController.retry()

    fun configureHomeSession(enabled: Boolean) = session.configure(enabled)
    fun flushBrowseSession() = session.flush()
    fun onBrowseInteraction() = touchSession()

    fun onGridRestoreHandled(token: Long) {
        state.update { if (it.gridRestore?.token == token) it.copy(gridRestore = null) else it }
    }

    fun onBrowsePosition(scope: String, group: String?, favoritesOnly: Boolean,
        focusedId: String, position: HomeGridPosition) {
        val current = state.value
        if (scope != current.browseScope || group != current.selectedGroup ||
            favoritesOnly != current.favoritesOnly || current.query.isNotBlank() || current.channels.isEmpty()) return
        touchSession()
        browseDraft = browseContext().copy(focusedChannelId = focusedId, position = position)
        session.remember(browseDraft!!)
    }

    private fun onSessionLoaded(value: HomeBrowseSession?) {
        val discarded = pendingBrowse != null && value == null
        pendingBrowse = value
        state.update { it.copy(sessionReady = value == null) }
        if ((value != null || discarded) && state.value.channels.isNotEmpty()) filterChannels()
    }

    private fun touchSession() {
        session.interact()
        val wasPending = pendingBrowse != null
        pendingBrowse = null
        if (!state.value.sessionReady || state.value.gridRestore != null)
            state.update { it.copy(sessionReady = true, gridRestore = null,
                selectedChannelId = if (it.gridRestore != null) null else it.selectedChannelId) }
        if (wasPending) { filterJob?.cancel(); filterChannels() }
    }

    private fun browseContext(): HomeBrowseSession {
        val current = state.value
        val root = FolderFilter.id(current.selectedGroup)?.let { catalogTree.path(it).firstOrNull()?.id }
        return HomeBrowseSession(current.selectedGroup, root, current.favoritesOnly)
    }

    private fun rememberContext() {
        browseDraft = browseContext()
        if (state.value.query.isBlank()) session.remember(browseDraft!!)
    }

    init {
        observeSource()
        viewModelScope.launch {
            favoriteState.collect {
                if (it.errorMessage != null && favoriteIds == null && pendingBrowse?.favoritesOnly == true)
                    filterChannels()
            }
        }
    }

    fun refresh() {
        if (!catalogLoader.refresh()) observeSource()
    }

    /** Called after the catalog has actually been drawn, never after an arbitrary timeout. */
    fun onHomeReady() {
        if (!reportedReady) {
            reportedReady = true
            LoadTimings.recordSince(LoadTimings.Stage.HOME_READY, openedAtNanos)
        }
        catalogLoader.onHomeReady()
    }

    private fun resetSource(source: AppSettings?, error: String? = null) {
        filterJob?.cancel();filterJob = null
        organizer.clearChannels()
        catalogTree = OrganizationTree();pendingBrowse = null;browseDraft = null
        val selected = source?.hasSelectedSource() == true
        state.value = HomeUiState(isLoading = selected,needsSource = source != null && !selected,
            browseScope = source?.let(ProfileScope::of).orEmpty(),errorMessage = error)
        val key = state.value.browseScope.takeIf { selected }
        favoriteController.bind(key);session.open(key)
        catalogLoader.bind(source)
    }

    private fun observeSource() {
        settingsJob?.cancel()
        settingsJob = viewModelScope.launch {
            try {
                settings.observeSettings().distinctUntilChangedBy(ProfileScope::of).collect { resetSource(it) }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { resetSource(null,"تعذر قراءة إعدادات مصدر القنوات. أعد المحاولة.") }
        }
    }

    fun updateQuery(query: String) {
        touchSession()
        state.update { it.copy(query = query.take(200), selectedChannelId = null) }
        filterChannels()
    }

    fun selectGroup(group: String?) {
        touchSession()
        state.update { it.copy(selectedGroup = group, selectedChannelId = null,
            favoritesOnly = if (group == null) false else it.favoritesOnly) }
        rememberContext()
        filterChannels()
    }

    fun toggleFavoritesFilter() {
        touchSession()
        state.update { it.copy(favoritesOnly = !it.favoritesOnly, selectedChannelId = null) }
        rememberContext()
        filterChannels()
    }

    fun selectAllGroups() {
        touchSession()
        state.update { it.copy(selectedGroup = null, selectedChannelId = null) }
        rememberContext()
        filterChannels()
    }

    fun selectSection(favoritesOnly: Boolean) {
        touchSession()
        state.update { it.copy(favoritesOnly = favoritesOnly, selectedGroup = null, query = "", selectedChannelId = null) }
        rememberContext()
        filterChannels()
    }

    fun clearFilters() {
        touchSession()
        state.update { it.copy(query = "", selectedGroup = null, favoritesOnly = false, selectedChannelId = null) }
        rememberContext()
        filterChannels()
    }

    fun selectChannel(id: String) {
        touchSession()
        state.update { current ->
            val selected = current.visibleChannels.firstOrNull { it.id == id }?.id
                ?: current.visibleChannels.firstOrNull()?.id
            current.copy(selectedChannelId = selected)
        }
        if (state.value.query.isBlank()) {
            val current = state.value
            browseDraft = (browseDraft?.takeIf { it.group == current.selectedGroup &&
                it.favoritesOnly == current.favoritesOnly } ?: browseContext()).copy(focusedChannelId = current.selectedChannelId)
            session.remember(browseDraft!!)
        }
        session.flush()
    }

    private fun filterChannels() {
        filterJob?.cancel()
        val snapshot = state.value
        val ids = favoriteIds
        val topology = catalogTree
        val saved = pendingBrowse?.takeIf { snapshot.channels.isNotEmpty() &&
            (!it.favoritesOnly || ids != null || favoriteState.value.errorMessage != null) }
        filterJob = viewModelScope.launch {
            val result = withContext(background) {
                val marked = ChannelFavorites.apply(snapshot.channels, ids)
                val restored = saved?.let { HomeSessionResolver.resolve(it, marked, topology) }
                val favorites = restored?.session?.favoritesOnly ?: snapshot.favoritesOnly
                val group = if (restored != null) restored.session.group else snapshot.selectedGroup
                val all = snapshot.query.isBlank() && !favorites
                val matching = if (all) marked else PlaybackQueue.filterChannels(marked,
                    query = snapshot.query, favoritesOnly = favorites)
                FilteredHome(marked, restored?.visible ?: if (group == null) matching else
                    PlaybackQueue.filterChannels(matching, group),
                    if (all) topology else topology.withCounts(matching), restored?.session)
            }
            currentCoroutineContext().ensureActive()
            if (state.value.browseScope != snapshot.browseScope) return@launch
            val (marked, visible, tree, restored) = result
            if (restored != null) { pendingBrowse = null; browseDraft = restored }
            state.update { current ->
                current.copy(channels = marked, visibleChannels = visible, organization = tree,
                    isLoading = if (marked.isNotEmpty()) false else current.isLoading,
                    selectedGroup = if (restored != null) restored.group else current.selectedGroup,
                    favoritesOnly = restored?.favoritesOnly ?: current.favoritesOnly,
                    sessionReady = current.sessionReady || restored != null,
                    gridRestore = if (restored != null) HomeGridRestore(++restoreSequence, restored.position) else current.gridRestore,
                    selectedChannelId = restored?.focusedChannelId ?: current.selectedChannelId?.let { id ->
                        visible.firstOrNull { it.id == id }?.id ?: visible.firstOrNull()?.id
                    })
            }
        }
    }
}

private data class FilteredHome(val channels: List<com.yourapp.iptv.domain.model.Channel>,
    val visible: List<com.yourapp.iptv.domain.model.Channel>, val tree: OrganizationTree,
    val restored: HomeBrowseSession?)