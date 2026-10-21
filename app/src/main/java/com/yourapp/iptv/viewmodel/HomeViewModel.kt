package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.organization.*
import com.yourapp.iptv.domain.repository.OrganizationRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.domain.model.PlaylistLoadException
import com.yourapp.iptv.domain.model.PlaylistSource
import com.yourapp.iptv.domain.model.AppSettings
import com.yourapp.iptv.domain.model.PlaylistCacheKey
import com.yourapp.iptv.domain.model.HomeStartupPolicy
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

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val playlists: PlaylistRepository,
    @DefaultDispatcher private val background: CoroutineDispatcher,
    favorites: FavoriteRepository,
    organization: OrganizationRepository = OrganizationRepository.Disabled,
    private val startup: HomeStartupRepository = HomeStartupRepository.Disabled
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
    private val favoriteController = FavoriteController(viewModelScope, favorites) {
        favoriteIds = it
        filterChannels()
    }
    val favoriteState = favoriteController.uiState
    private val organizer: OrganizationController = OrganizationController(viewModelScope, organization, background, ::onCatalogChanged)

    private fun onCatalogChanged(result: OrganizedChannels) {
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

    init { observeSource() }

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
                settings.observeSettings().distinctUntilChangedBy(PlaylistCacheKey::of).collect { source ->
                    generation++
                    loadJob?.cancel()
                    filterJob?.cancel()
                    persistJob?.cancel()
                    pendingRefresh = null
                    refreshedAtMillis = 0L
                    catalogTree = OrganizationTree()
                    organizer.clearChannels()
                    activeSource = source
                    refreshing = false
                    state.value = HomeUiState(isLoading = source.hasSelectedSource(),
                        needsSource = !source.hasSelectedSource())
                    if (source.hasSelectedSource()) load(source)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                generation++
                loadJob?.cancel()
                organizer.clearChannels()
                activeSource = null
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
        state.update { it.copy(query = query.take(200), selectedChannelId = null) }
        filterChannels()
    }

    fun selectGroup(group: String?) {
        state.update { it.copy(selectedGroup = group, selectedChannelId = null,
            favoritesOnly = if (group == null) false else it.favoritesOnly) }
        filterChannels()
    }

    fun toggleFavoritesFilter() {
        state.update { it.copy(favoritesOnly = !it.favoritesOnly, selectedChannelId = null) }
        filterChannels()
    }

    fun selectAllGroups() {
        state.update { it.copy(selectedGroup = null, selectedChannelId = null) }
        filterChannels()
    }

    fun selectSection(favoritesOnly: Boolean) {
        state.update { it.copy(favoritesOnly = favoritesOnly, selectedGroup = null, query = "", selectedChannelId = null) }
        filterChannels()
    }

    fun clearFilters() {
        state.update { it.copy(query = "", selectedGroup = null, favoritesOnly = false, selectedChannelId = null) }
        filterChannels()
    }

    fun selectChannel(id: String) {
        state.update { current ->
            val selected = current.visibleChannels.firstOrNull { it.id == id }?.id
                ?: current.visibleChannels.firstOrNull()?.id
            current.copy(selectedChannelId = selected)
        }
    }

    private fun filterChannels() {
        filterJob?.cancel()
        val snapshot = state.value
        val ids = favoriteIds
        val topology = catalogTree
        filterJob = viewModelScope.launch {
            val result = withContext(background) {
                val marked = ChannelFavorites.apply(snapshot.channels, ids)
                val all = snapshot.query.isBlank() && !snapshot.favoritesOnly
                val matching = if (all) marked else PlaybackQueue.filterChannels(marked,
                    query = snapshot.query, favoritesOnly = snapshot.favoritesOnly)
                Triple(marked, if (snapshot.selectedGroup == null) matching else
                    PlaybackQueue.filterChannels(matching, snapshot.selectedGroup),
                    if (all) topology else topology.withCounts(matching))
            }
            val (marked, visible, tree) = result
            state.update { current ->
                current.copy(channels = marked, visibleChannels = visible, organization = tree,
                    isLoading = if (marked.isNotEmpty()) false else current.isLoading,
                    selectedChannelId = current.selectedChannelId?.let { id ->
                        visible.firstOrNull { it.id == id }?.id ?: visible.firstOrNull()?.id
                    })
            }
        }
    }
}