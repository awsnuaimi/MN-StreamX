package mnstudio.apps.mnstreamx.domain.catalog

import mnstudio.apps.mnstreamx.domain.model.Channel
import java.util.RandomAccess

/** Copy-on-write pages. Published lists never observe a later favorite edit. */
internal class ChannelSnapshots private constructor(
    private val pages: Array<Array<Channel>>, override val size: Int
) : AbstractList<Channel>(), RandomAccess {
    override fun get(index: Int): Channel {
        if (index !in 0 until size) throw IndexOutOfBoundsException("index=$index size=$size")
        return pages[index / PAGE_SIZE][index % PAGE_SIZE]
    }

    fun patch(values: Map<Int, Channel>, checkpoint: () -> Unit): ChannelSnapshots {
        if (values.isEmpty()) return this
        val next = pages.copyOf()
        val copied = HashSet<Int>()
        values.forEach { (position, channel) ->
            checkpoint()
            val page = position / PAGE_SIZE
            if (copied.add(page)) next[page] = next[page].copyOf()
            next[page][position % PAGE_SIZE] = channel
        }
        checkpoint()
        return ChannelSnapshots(next, size)
    }

    companion object {
        private const val PAGE_SIZE = 128
        fun of(source: List<Channel>, checkpoint: () -> Unit): ChannelSnapshots {
            val pages = Array((source.size + PAGE_SIZE - 1) / PAGE_SIZE) { page ->
                checkpoint()
                Array(minOf(PAGE_SIZE, source.size - page * PAGE_SIZE)) { offset -> source[page * PAGE_SIZE + offset] }
            }
            return ChannelSnapshots(pages, source.size)
        }
    }
}

/** A read-only view over one immutable catalog version, always in provider/catalog order. */
internal class ChannelSelection(private val catalog: List<Channel>, private val positions: IntArray) :
    AbstractList<Channel>(), RandomAccess {
    override val size get() = positions.size
    override fun get(index: Int): Channel = catalog[positions[index]]
}