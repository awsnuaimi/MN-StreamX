package com.yourapp.iptv.domain.search

import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

object SearchText {
    const val QUERY_LIMIT = 100
    private val spaces = Regex("\\s+")
    fun query(value: String): String = value.filterNot { it.isISOControl() }
        .trim().replace(spaces, " ").take(QUERY_LIMIT)

    fun normalized(value: String): String = buildString {
        Normalizer.normalize(value.take(512).lowercase(Locale.ROOT), Normalizer.Form.NFKD).forEach { c ->
            if (Character.getType(c) == Character.NON_SPACING_MARK.toInt() || c == '\u0640') return@forEach
            append(when (c) {
                'ł' -> "l"
                'ß' -> "ss"
                'æ' -> "ae"
                'œ' -> "oe"
                'أ', 'إ', 'آ', 'ٱ' -> "ا"
                'ى' -> "ي"
                else -> if (c.isLetterOrDigit()) c.toString() else " "
            })
        }
    }.trim().replace(spaces, " ")
}

data class SearchMatches<T>(val items: List<T> = emptyList(), val more: Boolean = false, val limited: Boolean = false)

/** A compact, immutable title index. Both construction and matching run off the UI thread. */
class SearchIndex<T> private constructor(private val rows: List<Row<T>>, val limited: Boolean) {
    private data class Row<T>(val item: T, val title: String)
    val size: Int get() = rows.size

    suspend fun find(query: String, limit: Int = RESULT_LIMIT): SearchMatches<T> {
        require(limit in 1..RESULT_LIMIT)
        val text = SearchText.normalized(SearchText.query(query))
        if (text.isEmpty()) return SearchMatches()
        val tokens = text.split(' ')
        val buckets = List(3) { ArrayList<T>(limit) }
        var count = 0
        rows.forEachIndexed { i, row ->
            if (i % 128 == 0) currentCoroutineContext().ensureActive()
            if (tokens.all(row.title::contains)) {
                count++
                val rank = when { row.title == text -> 0; row.title.startsWith(text) -> 1; else -> 2 }
                if (buckets[rank].size < limit) buckets[rank].add(row.item)
            }
        }
        return SearchMatches(buckets.flatten().take(limit), count > limit)
    }

    companion object {
        const val RESULT_LIMIT = 100
        const val INDEX_LIMIT = 100_000
        suspend fun <T> create(items: List<T>, id: (T) -> String, title: (T) -> String): SearchIndex<T> {
            val ids = HashSet<String>()
            val rows = ArrayList<Row<T>>(minOf(items.size, INDEX_LIMIT))
            var limited = false
            for ((i, item) in items.withIndex()) {
                if (i % 128 == 0) currentCoroutineContext().ensureActive()
                if (rows.size == INDEX_LIMIT) { limited = true; break }
                val key = id(item)
                if (key.isNotBlank() && ids.add(key)) {
                    rows.add(Row(item, SearchText.normalized(title(item))))
                }
            }
            return SearchIndex(rows, limited)
        }
    }
}