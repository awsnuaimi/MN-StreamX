package com.yourapp.iptv.data.source.epg

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import com.yourapp.iptv.domain.model.Program
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal class XtreamEpgCache(private val directory: File) {
    data class Entry(val fetchedAt: Long, val programs: List<Program>) {
        fun isFresh(now: Long): Boolean {
            val ttl = if (programs.isEmpty()) 120_000L else 300_000L
            val nextBoundary = programs.asSequence().map { it.endTime }.filter { it > fetchedAt }.minOrNull()
            val expires = minOf(fetchedAt + ttl, nextBoundary ?: Long.MAX_VALUE)
            return now >= fetchedAt && now < expires
        }
    }

    fun read(key: String, now: Long): Entry? {
        val file = file(key)
        return runCatching {
            if (!file.isFile || file.length() > 256 * 1024) return null
            JsonReader(file.reader(Charsets.UTF_8)).use { json ->
                var fetchedAt = 0L
                var version = 0
                var foundPrograms = false
                val programs = ArrayList<Program>()
                json.beginObject()
                while (json.hasNext()) {
                    when (json.nextName()) {
                        "version" -> version = json.nextInt()
                        "fetchedAt" -> fetchedAt = json.nextLong()
                        "programs" -> { readPrograms(json, programs); foundPrograms = true }
                        else -> json.skipValue()
                    }
                }
                json.endObject()
                if (version != 1 || !foundPrograms || json.peek() != JsonToken.END_DOCUMENT || fetchedAt > now ||
                    now - fetchedAt > 6 * 60 * 60 * 1000L) return null
                file.setLastModified(now)
                Entry(fetchedAt, programs.sortedBy { it.startTime })
            }
        }.getOrNull()
    }

    fun write(key: String, entry: Entry) {
        require(entry.programs.size <= 32)
        val target = file(key)
        directory.mkdirs()
        val temporary = File.createTempFile("epg-", ".tmp", directory)
        try {
            JsonWriter(temporary.writer(Charsets.UTF_8)).use { json ->
                json.beginObject().name("version").value(1).name("fetchedAt").value(entry.fetchedAt)
                json.name("programs").beginArray()
                entry.programs.forEach { program ->
                    json.beginObject().name("title").value(program.title.take(512))
                        .name("start").value(program.startTime).name("end").value(program.endTime)
                        .name("description").value(program.description?.take(2048)).endObject()
                }
                json.endArray().endObject()
            }
            require(temporary.length() <= 256 * 1024)
            try {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            target.setLastModified(entry.fetchedAt)
            directory.listFiles { candidate -> candidate.name.matches(Regex("[0-9a-f]{64}\\.json")) }
                ?.sortedByDescending { it.lastModified() }?.drop(64)?.forEach { it.delete() }
        } finally {
            temporary.delete()
        }
    }

    private fun readPrograms(json: JsonReader, programs: MutableList<Program>) {
        json.beginArray()
        var count = 0
        while (json.hasNext()) {
            require(++count <= 32)
            var title = ""
            var start = 0L
            var end = 0L
            var description: String? = null
            json.beginObject()
            while (json.hasNext()) {
                when (json.nextName()) {
                    "title" -> title = json.nextString().take(512)
                    "start" -> start = json.nextLong()
                    "end" -> end = json.nextLong()
                    "description" -> if (json.peek() == JsonToken.NULL) json.nextNull()
                        else description = json.nextString().take(2048)
                    else -> json.skipValue()
                }
            }
            json.endObject()
            require(title.isNotBlank() && start in 946684800000L..4102444800000L &&
                end in (start + 1)..4102444800000L && end - start <= 7 * 24 * 60 * 60 * 1000L)
            programs += Program(title, start, end, description)
        }
        json.endArray()
    }

    private fun file(key: String): File {
        require(key.matches(Regex("[0-9a-f]{64}")))
        return File(directory, "$key.json")
    }
}