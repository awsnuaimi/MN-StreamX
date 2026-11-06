package mnstudio.apps.mnstreamx.domain.catalog

import mnstudio.apps.mnstreamx.domain.model.Channel
import mnstudio.apps.mnstreamx.domain.organization.FolderFilter
import mnstudio.apps.mnstreamx.domain.organization.OrganizationTree
import java.util.BitSet
import java.util.TreeSet

data class IndexedBrowseResult(val visible: List<Channel>, val tree: OrganizationTree)

/** One Home owner, confined to its presentation worker. No global/account-shared index. */
class ChannelBrowseIndex {
    private var scope = ""
    private var source: List<Channel>? = null
    private var topology: OrganizationTree? = null
    private var catalog: Catalog? = null

    fun prepare(scope: String, source: List<Channel>, tree: OrganizationTree, favoriteIds: Set<String>?,
        checkpoint: () -> Unit = {}): Catalog {
        checkpoint()
        val previous = catalog
        val sameOwner = previous != null && this.scope == scope && topology === tree
        val current = if (sameOwner && (this.source === source || previous!!.refreshValues(this.source!!, source, checkpoint)))
            previous!! else Catalog(source, tree, checkpoint)
        current.applyFavorites(favoriteIds, checkpoint)
        checkpoint()
        this.scope = scope; this.source = source; topology = tree; catalog = current
        return current
    }

    class Catalog internal constructor(source: List<Channel>, private val tree: OrganizationTree,
        checkpoint: () -> Unit) {
        private var snapshot = ChannelSnapshots.of(source, checkpoint)
        val channels: List<Channel> get() = snapshot
        private val byId = HashMap<String, Int>()
        private val groups: Map<String, IntArray>
        private val all = IntArray(source.size) { it }
        private var favorites = TreeSet<Int>()
        private var favoriteIds: Set<String>? = null
        private data class Key(val group: String?, val query: String, val favorites: Boolean)
        private data class Entry(val positions: IntArray, val result: IndexedBrowseResult)
        private data class Search(val positions: IntArray, val membership: BitSet)
        private val selections = LinkedHashMap<Key, Entry>(8, .75f, true)
        private val searches = LinkedHashMap<String, Search>(4, .75f, true)
        private val counts = LinkedHashMap<Pair<String, Boolean>, OrganizationTree>(4, .75f, true)

        init {
            val postings = HashMap<String, MutableList<Int>>()
            source.forEachIndexed { index, channel ->
                checkpoint()
                // Organized input is unique. First occurrence matches PlaybackQueue's duplicate policy.
                if (byId.putIfAbsent(channel.id, index) == null) {
                    if (channel.isFavorite) favorites.add(index)
                    val keys = channel.folderIds.mapTo(HashSet(), FolderFilter::of)
                    channel.group?.let(keys::add)
                    keys.forEach { postings.getOrPut(it) { ArrayList() }.add(index) }
                }
            }
            groups = postings.mapValues { (_, values) -> checkpoint(); values.toIntArray() }
        }

        internal fun applyFavorites(ids: Set<String>?, checkpoint: () -> Unit) {
            if (ids == null || ids == favoriteIds) return
            val nextIds = ids.toSet()
            val next = TreeSet<Int>()
            nextIds.forEach { checkpoint(); byId[it]?.let(next::add) }
            val changed = HashSet<Int>()
            favorites.forEach { if (it !in next) changed.add(it) }
            next.forEach { if (it !in favorites) changed.add(it) }
            val updates = changed.associateWith { position ->
                checkpoint(); snapshot[position].copy(isFavorite = position in next)
            }
            val patched = snapshot.patch(updates, checkpoint)
            val refreshed = LinkedHashMap<Key, Entry>()
            selections.forEach { (key, value) ->
                checkpoint()
                if (!key.favorites) {
                    val affected = changed.any { value.positions.binarySearch(it) >= 0 }
                    refreshed[key] = if (!affected) value else value.copy(result = value.result.copy(
                        visible = view(patched, value.positions)))
                }
            }
            checkpoint()
            snapshot = patched; favorites = next; favoriteIds = nextIds
            selections.clear(); selections.putAll(refreshed)
            counts.keys.removeAll { it.second }
        }

        /** Same membership/order: patch stream metadata without rebuilding the category/search indexes. */
        internal fun refreshValues(old: List<Channel>, next: List<Channel>, checkpoint: () -> Unit): Boolean {
            if (old.size != next.size) return false
            val updates = HashMap<Int, Channel>()
            next.forEachIndexed { position, channel ->
                checkpoint()
                val before = old[position]
                if (before.id != channel.id || before.name != channel.name || before.group != channel.group ||
                    before.folderIds != channel.folderIds || favoriteIds == null && before.isFavorite != channel.isFavorite) return false
                val current = snapshot[position]
                if (current.logoUrl != channel.logoUrl || current.streamUrl != channel.streamUrl) {
                    updates[position] = channel.copy(isFavorite = current.isFavorite)
                }
            }
            val patched = snapshot.patch(updates, checkpoint)
            val replacements = selections.mapValues { (_, entry) ->
                checkpoint()
                if (updates.keys.none { entry.positions.binarySearch(it) >= 0 }) entry
                else entry.copy(result = entry.result.copy(visible = view(patched, entry.positions)))
            }
            checkpoint()
            snapshot = patched;selections.clear();selections.putAll(replacements)
            return true
        }

        fun select(group: String?, query: String, favoritesOnly: Boolean,
            checkpoint: () -> Unit = {}): IndexedBrowseResult {
            checkpoint()
            val search = query.trim()
            val key = Key(group, search, favoritesOnly)
            selections[key]?.let { return it.result }
            val searchResult = if (search.isEmpty()) null else search(search, checkpoint)
            val members = group?.let { groups[it] ?: IntArray(0) }
            val candidates = when {
                favoritesOnly && favorites.size < (members?.size ?: all.size) -> favorites.toIntArray()
                members != null -> members
                searchResult != null -> searchResult.positions
                else -> all
            }
            val positions = if (members == null && searchResult == null && !favoritesOnly) all else
                candidates.filter { position ->
                    checkpoint()
                    (members == null || members.binarySearch(position) >= 0) &&
                        (searchResult == null || searchResult.membership[position]) &&
                        (!favoritesOnly || snapshot[position].isFavorite)
                }.toIntArray()
            val counted = if (search.isEmpty() && !favoritesOnly) tree else
                counts[search to favoritesOnly] ?: run {
                    val values = HashMap<String, Int>()
                    val matching = if (favoritesOnly) favorites.toIntArray() else searchResult!!.positions
                    matching.forEach { position ->
                        checkpoint()
                        if (searchResult == null || searchResult.membership[position]) snapshot[position].folderIds.forEach {
                            values[it] = (values[it] ?: 0) + 1
                        }
                    }
                    OrganizationTree(tree.enabled, tree.folders, values, tree.readFailed, tree.folderSort).also {
                        counts[search to favoritesOnly] = it
                        while (counts.size > 4) counts.entries.iterator().apply { next(); remove() }
                    }
                }
            val result = IndexedBrowseResult(view(snapshot, positions), counted)
            checkpoint()
            selections[key] = Entry(positions, result)
            // At most eight views/two catalog-sized position arrays; never eight copied catalogs.
            while (selections.size > 8 || selections.size > 1 &&
                selections.values.sumOf { it.positions.size.toLong() } > all.size.toLong() * 2) {
                selections.entries.iterator().apply { next(); remove() }
            }
            return result
        }

        private fun search(query: String, checkpoint: () -> Unit): Search {
            searches[query]?.let { return it }
            val prefix = searches.entries.filter { query.contains(it.key, ignoreCase = true) }
                .maxByOrNull { it.key.length }?.value?.positions ?: all
            val bits = BitSet(snapshot.size)
            val positions = prefix.filter { position ->
                checkpoint()
                snapshot[position].name.contains(query, ignoreCase = true).also { if (it) bits.set(position) }
            }.toIntArray()
            checkpoint()
            return Search(positions, bits).also {
                searches[query] = it
                while (searches.size > 2) searches.entries.iterator().apply { next(); remove() }
            }
        }

        private fun view(catalog: ChannelSnapshots, positions: IntArray): List<Channel> =
            if (positions === all) catalog else ChannelSelection(catalog, positions)
    }
}