package mnstudio.apps.mnstreamx.data.datastore

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import mnstudio.apps.mnstreamx.domain.model.*

/** Accept v1 bookmarks forever. V2 adds bounded episode completion metadata. */
internal object VodProgressCodec {
    private const val MAX_JSON = 64 * 1024

    fun encode(entries: List<VodProgressEntry>): String = encodeState(VodProgressSnapshot(entries))

    fun encodeState(state: VodProgressSnapshot): String = JsonObject().apply {
        require(state.entries.size <= VodProgress.LIMIT && state.entries.all(VodProgress::resumable))
        require(state.entries.map { it.kind to it.id }.distinct().size == state.entries.size)
        require(state.completed.size <= VodEpisodeHistory.LIMIT && state.completed.all(VodEpisodeHistory::valid))
        require(state.completed.map { it.seriesId to it.id }.distinct().size == state.completed.size)
        require(state.entries.none { entry -> entry.kind == VodKind.SERIES && state.completed.any { marker ->
            marker.id == entry.id && marker.seriesId == entry.seriesId } })
        addProperty("version", if (state.completed.isEmpty()) 1 else 2)
        add("entries", JsonArray().apply { state.entries.forEach { entry ->
            add(JsonObject().apply {
                addProperty("kind", entry.kind.name)
                addProperty("id", entry.id)
                addProperty("title", entry.title)
                addProperty("ext", entry.extension)
                addProperty("pos", entry.positionMs)
                addProperty("dur", entry.durationMs)
                addProperty("at", entry.updatedAtMs)
                entry.seriesId?.let { addProperty("series", it) }
            })
        } })
        if (state.completed.isNotEmpty()) add("completed", JsonArray().apply {
            state.completed.forEach { entry -> add(JsonObject().apply {
                addProperty("id", entry.id)
                addProperty("series", entry.seriesId)
                addProperty("at", entry.updatedAtMs)
            }) }
        })
    }.toString().also { require(it.toByteArray(Charsets.UTF_8).size <= MAX_JSON) }

    fun decode(text: String?): List<VodProgressEntry> = decodeState(text).entries

    fun decodeState(text: String?): VodProgressSnapshot {
        if (text.isNullOrBlank() || text.toByteArray(Charsets.UTF_8).size > MAX_JSON) return VodProgressSnapshot()
        return try {
            val root = JsonParser.parseString(text).asJsonObject
            val version = root.get("version").asInt
            require(version == 1 || version == 2)
            val array = root.getAsJsonArray("entries")
            require(array.size() <= VodProgress.LIMIT)
            val rows = array.map { element ->
                val obj = element.asJsonObject
                val entry = VodProgressEntry(
                    VodKind.valueOf(obj.get("kind").asString), obj.get("id").asString,
                    obj.get("title").asString, obj.get("ext").asString,
                    obj.get("pos").asLong, obj.get("dur").asLong, obj.get("at").asLong,
                    obj.get("series")?.takeUnless { it.isJsonNull }?.let {
                        require(it.isJsonPrimitive && it.asJsonPrimitive.isString); it.asString
                    })
                require(VodProgress.resumable(entry));entry
            }
            require(rows.map { it.kind to it.id }.distinct().size == rows.size)
            val finished = if (version == 1) {
                require(!root.has("completed"));emptyList()
            } else {
                val values = root.getAsJsonArray("completed")
                require(values.size() <= VodEpisodeHistory.LIMIT)
                values.map { element -> element.asJsonObject.let { obj ->
                    VodCompletedEpisode(obj.get("id").asString,obj.get("series").asString,
                        obj.get("at").asLong).also { require(VodEpisodeHistory.valid(it)) }
                } }
            }
            require(finished.map { it.seriesId to it.id }.distinct().size == finished.size)
            require(rows.none { entry -> entry.kind == VodKind.SERIES && finished.any { marker ->
                marker.id == entry.id && marker.seriesId == entry.seriesId } })
            VodProgressSnapshot(rows.sortedByDescending { it.updatedAtMs },
                finished.sortedByDescending { it.updatedAtMs })
        } catch (_: Exception) { VodProgressSnapshot() }
    }
}