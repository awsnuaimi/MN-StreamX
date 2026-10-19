package com.yourapp.iptv.data.source.epg

import com.yourapp.iptv.domain.model.Program
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class XtreamEpgCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    private val now = 1700000000000L
    private val key = "a".repeat(64)
    private val program = Program("News", now - 1000, now + 60_000, "Description")

    @Test fun aNewCacheInstanceCanReadTheSavedScheduleAndExpiresAtTheProgramBoundary() {
        val folder = temporary.newFolder()
        XtreamEpgCache(folder).write(key, XtreamEpgCache.Entry(now, listOf(program)))
        val entry = XtreamEpgCache(folder).read(key, now + 30_000)!!
        assertEquals(listOf(program), entry.programs)
        assertTrue(entry.isFresh(now + 30_000))
        assertFalse(entry.isFresh(program.endTime))
        assertTrue(folder.listFiles()!!.none { it.extension == "tmp" })
    }

    @Test fun emptyResponsesHaveATtlAndOldOrFutureDataIsNotReused() {
        val cache = XtreamEpgCache(temporary.newFolder())
        cache.write(key, XtreamEpgCache.Entry(now, emptyList()))
        assertTrue(cache.read(key, now)!!.isFresh(now + 119_999))
        assertFalse(cache.read(key, now)!!.isFresh(now + 120_000))
        assertNull(cache.read(key, now - 1))
        assertNull(cache.read(key, now + 6 * 60 * 60 * 1000L + 1))
    }

    @Test fun corruptAndOversizedFilesAreIgnored() {
        val folder = temporary.newFolder()
        val cache = XtreamEpgCache(folder)
        val file = File(folder, "$key.json")
        file.writeText("{broken")
        assertNull(cache.read(key, now))
        file.writeText("x".repeat(256 * 1024 + 1))
        assertNull(cache.read(key, now))
    }

    @Test fun diskGrowthIsBoundedAndCacheKeysCannotEscapeTheDirectory() {
        val folder = temporary.newFolder()
        val cache = XtreamEpgCache(folder)
        repeat(70) { index ->
            val name = index.toString(16).padStart(64, '0')
            cache.write(name, XtreamEpgCache.Entry(now + index, listOf(program)))
        }
        assertEquals(64, folder.listFiles()!!.size)
        assertTrue(runCatching { cache.read("../outside", now) }.isFailure)
    }
}