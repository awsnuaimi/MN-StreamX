package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.profile.ProfileScope

import com.yourapp.iptv.domain.organization.*
import com.yourapp.iptv.domain.repository.OrganizationRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.domain.model.PlaylistLoadException
import com.yourapp.iptv.domain.model.PlaylistSource
import com.yourapp.iptv.domain.model.AppSettings
import com.yourapp.iptv.domain.model.PlaylistCacheKey
import com.yourapp.iptv.domain.model.HomeStartupPolicy
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
    private val playlists: PlaylistRepository,
    @DefaultDispatcher private val background: CoroutineDispatcher,
    favorites: FavoriteRepository,
    organization: OrganizationRepository = OrganizationRepository.Disabled,
    private val startup: HomeStartupRepository = HomeStartupRepository.Disabled,
    browseSessions: HomeSessionRepository = HomeSessionRepository.Disabled,
    private val parental: ParentalControl = ParentalControl.Disabled
) : ViewModel() {
    private val state = MutableStateFlow(HomeUiState())
    val uiState = state.asStateFlow()
    private var loadJob: Job? = null
    private var settingsJob: Job? = null
    private var filterJob: Job? = null
    private var persistJob: Job? = null
    private var pendingRefresh: AppSettings? = null
    private var refreshedAtMillis = 0L
    private var restoring = false
    private var catalogTree = OrganizationTree()
    private val openedAtNanos = System.nanoTime()
    private var reportedReady = false
    private var activeSource: AppSettings? = null
    private var generation = 0L
    private var refreshing = false
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

    private fun onCatalogChanged(value: OrganizedChannels) {
        val result = ParentalProjection.prune(value,parental.state.value)
        catalogTree = result.tree
        state.update { it.copy(channels = result.channels,
            groups = if (result.tree.enabled) result.tree.folders.map { f -> FolderFilter.of(f.id) }
                else result.channels.mapNotNull { c -> c.group }.distinct().sorted(),
            selectedGroup = result.resolveFilter(it.selectedGroup), organization = result.tree,
            needsSource = false, isRefreshing = refreshing,
            isLoading = if (result.channels.isEmpty()) false else it.isLoading) }
        filterChannels()
        val source = activeSource ?: return
        val config = organizer.currentConfig() ?: return
        if (restoring) return
        val verifiedAt = refreshedAtMillis
        persistJob?.cancel()
        persistJob = viewModelScope.launch(background) {
            try { startup.save(source, config, result, verifiedAt) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* Room remains authoritative; a cache write cannot break playback. */ }
        }
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
        if (loadJob?.isActive == true) return
        pendingRefresh = null
        val source = activeSource
        if (source == null) observeSource()
        else if (source.hasSelectedSource()) load(source, useStartup = false)
    }

    /** Called after the catalog has actually been drawn, never after an arbitrary timeout. */
    fun onHomeReady() {
        if (!reportedReady) {
            reportedReady = true
            LoadTimings.recordSince(LoadTimings.Stage.HOME_READY, openedAtNanos)
        }
        val source = pendingRefresh ?: return
        val token = generation
        val opening = loadJob
        viewModelScope.launch {
            opening?.join()
            if (generation == token && pendingRefresh == source) {
                pendingRefresh = null
                load(source, useStartup = false)
            }
        }
    }

    private fun observeSource() {
        settingsJob?.cancel()
        settingsJob = viewModelScope.launch {
            try {
                settings.observeSettings().distinctUntilChangedBy(ProfileScope::of).collect { source ->
                    generation++
                    loadJob?.cancel()
                    filterJob?.cancel()
                    persistJob?.cancel()
                    pendingRefresh = null
                    refreshedAtMillis = 0L
                    catalogTree = OrganizationTree()
                    pendingBrowse = null
                    browseDraft = null
                    organizer.clearChannels()
                    activeSource = source
                    refreshing = false
                    state.value = HomeUiState(isLoading = source.hasSelectedSource(),
                        needsSource = !source.hasSelectedSource(), browseScope = ProfileScope.of(source))
                    favoriteController.bind(state.value.browseScope.takeIf { source.hasSelectedSource() })
                    session.open(state.value.browseScope.takeIf { source.hasSelectedSource() })
                    if (source.hasSelectedSource()) load(source)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                generation++
                loadJob?.cancel()
                organizer.clearChannels()
                activeSource = null
                favoriteController.bind(null)
                pendingBrowse = null
                session.open(null)
                refreshing = false
                state.update { it.copy(isLoading = false, isRefreshing = false,
                    errorMessage = "تعذر قراءة إعدادات مصدر القنوات. أعد المحاولة.") }
            }
        }
    }

    private fun load(source: AppSettings, useStartup: Boolean = true) {
        val token = ++generation
        refreshing = true
        state.update { it.copy(isLoading = it.channels.isEmpty(), isRefreshing = true, errorMessage = null) }
        loadJob = viewModelScope.launch {
            try {
                if (state.value.channels.isEmpty()) {
                    if (useStartup) {
                        val saved = try { startup.restore(source) }
                        catch (error: CancellationException) { throw error }
                        catch (_: Exception) { null }
                        currentCoroutineContext().ensureActive()
                        if (generation != token) return@launch
                        if (saved != null) {
                            refreshedAtMillis = saved.refreshedAtMillis
                            refreshing = false
                            restoring = true
                            val adopted = try { organizer.restore(saved.config, saved.catalog) }
                                finally { restoring = false }
                            if (adopted) {
                                pendingRefresh = source.takeIf {
                                    HomeStartupPolicy.needsRefresh(refreshedAtMillis, System.currentTimeMillis())
                                }
                                return@launch
                            }
                            refreshedAtMillis = 0L
                            refreshing = true
                        }
                    }
                    // Cache failure is a miss; it must not prevent a network retry.
                    val cached = try { playlists.cachedPlaylist(source) }
                    catch (error: CancellationException) { throw error }
                    catch (_: Exception) { null }
                    currentCoroutineContext().ensureActive()
                    if (generation != token) return@launch
                    cached?.channels?.takeIf { it.isNotEmpty() }?.let(organizer::setChannels)
                }
                val channels = LoadTimings.measure(LoadTimings.Stage.REFRESH) {
                    withContext(background) {
                        when (source.source) {
                            PlaylistSource.M3U -> playlists.loadPlaylist(source.m3uUrl)
                            PlaylistSource.XTREAM -> playlists.loadXtream(
                                source.xtreamServer, source.xtreamUsername, source.xtreamPassword)
                        }.channels
                    }
                }
                currentCoroutineContext().ensureActive()
                if (generation != token) return@launch
                refreshedAtMillis = System.currentTimeMillis()
                refreshing = false
                state.update { it.copy(isRefreshing = false) }
                organizer.setChannels(channels)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generation != token) return@launch
                refreshing = false
                state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = (error as? PlaylistLoadException)?.message
                            ?: "تعذر تحميل القنوات. تحقق من الشبكة والإعدادات ثم أعد المحاولة."
                    )
                }
            }
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