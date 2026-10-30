package com.yourapp.iptv.ui.presentation

/** One immutable projection. No global cache: an outgoing owner keeps no channel references alive. */
internal class LiveChannelIndex(val rows: List<LiveChannelRow>) {
    private val keys = HashMap<String, Int>(rows.size)
    private val channels = HashMap<String, Int>()
    val numberedRows: Map<Int, LiveChannelRow>

    init {
        val numbers = HashMap<Int, LiveChannelRow>()
        rows.forEachIndexed { position, row ->
            keys.putIfAbsent(row.key, position)
            row.variants.forEach { channels.putIfAbsent(it.id, position) }
            if (row.number > 0) numbers[row.number] = row
        }
        numberedRows = numbers
    }

    fun keyPosition(key: String?): Int = key?.let(keys::get) ?: -1
    fun channelPosition(id: String?): Int = id?.let(channels::get) ?: -1
    fun forKey(key: String?): LiveChannelRow? = rows.getOrNull(keyPosition(key))
    fun forChannel(id: String?): LiveChannelRow? = rows.getOrNull(channelPosition(id))
    override fun toString() = "LiveChannelIndex(rows=${rows.size})"

    companion object { val Empty = LiveChannelIndex(emptyList()) }
}