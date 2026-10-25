package com.yourapp.iptv.data.source.xtream

import com.yourapp.iptv.domain.model.VodItem
import com.yourapp.iptv.domain.model.VodKind
import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader

class XtreamVodParserTest {
    @Test fun parsesMovieCategoriesAndValidStreams() {
        val categories = XtreamVodParser.categories(StringReader("""[{"category_id":5,"category_name":"Drama"}]""")) { true }
        val streams = XtreamVodParser.items(StringReader("""[
            {"stream_id":12,"name":"Test Movie","category_id":5,"container_extension":"mkv","stream_icon":"https://unit.test/x.jpg"},
            {"stream_id":"../3","name":"Invalid"},
            {"stream_id":12,"name":"Duplicate"}
        ]"""), VodKind.MOVIE) { true }
        assertEquals("Drama", categories.single().name)
        assertEquals(1, streams.size)
        assertEquals("mkv", streams.single().extension)
        assertEquals("5", streams.single().categoryId)
    }

    @Test fun errorsAndNonArrayResponsesCannotSilentlyBecomeEmptyCatalogs() {
        for (body in listOf("{}", "null", "<html>Error</html>", "{\"message\":\"Denied\"}")) {
            assertNotNull(runCatching { XtreamVodParser.items(StringReader(body), VodKind.MOVIE) { true } }.exceptionOrNull())
        }
        assertTrue(XtreamVodParser.items(StringReader("[]"), VodKind.MOVIE) { true }.isEmpty())
    }

    @Test fun titlesBelongToEveryDeclaredCategoryNotOnlyTheFirst() {
        val item = XtreamVodParser.items(StringReader("""[{"stream_id":8,"category_id":3,"category_ids":[3,"4",null]}]"""),
            VodKind.MOVIE) { true }.single()
        assertTrue(item.belongsTo("3")); assertTrue(item.belongsTo("4"))
        assertFalse(item.belongsTo("5"))
        assertEquals(listOf("3", "4"), item.categoryIds)
    }

    @Test fun parsesSeriesSeasonsAndEpisodesInNumericalOrder() {
        val item = VodItem("20", "Example", "1")
        val response = """{"info":{"plot":"Story"},"episodes":{"2":[
            {"id":"202","episode_num":3,"title":"Three","container_extension":"mkv"},
            {"id":"201","episode_num":1,"title":"One","container_extension":"mp4"}
        ],"1":[{"id":"100","episode_num":1,"title":"Pilot","container_extension":"ts"}]}}"""
        val result = XtreamVodParser.details(StringReader(response), VodKind.SERIES, item)
        assertEquals("Story", result.item.plot)
        assertEquals(listOf(1, 2), result.seasons.map { it.number })
        assertEquals(listOf("201", "202"), result.seasons[1].episodes.map { it.id })
    }

    @Test fun detailsReadsMovieContainerExtensionAndMetadata() {
        val movie = VodItem("44", "Film", null)
        val details = XtreamVodParser.details(StringReader("""{"info":{"plot":"Synopsis","rating":"7.9"},
            "movie_data":{"container_extension":"avi"}}"""), VodKind.MOVIE, movie)
        assertEquals("avi", details.item.extension)
        assertEquals("7.9", details.item.rating)
        assertEquals("Synopsis", details.item.plot)
    }

    @Test fun malformedExtensionsCannotInjectPaths() {
        assertEquals("mp4", XtreamVodFormat.safeExtension("../x"))
        assertEquals("mp4", XtreamVodFormat.safeExtension("exe"))
        assertEquals("m3u8", XtreamVodFormat.safeExtension(".M3U8"))
    }

    @Test fun cancellationStopsReadingCatalog() {
        val failed = runCatching {
            XtreamVodParser.items(StringReader("""[{"stream_id":1,"name":"One"}]"""), VodKind.MOVIE) { false }
        }.exceptionOrNull()
        assertTrue(failed is java.util.concurrent.CancellationException)
    }
}