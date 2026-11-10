package mnstudio.apps.mnstreamx.ui.presentation

import mnstudio.apps.mnstreamx.domain.model.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerDeckFocusVisibilityTest {
    private val channels = (0 until 180).map { index ->
        Channel(id = "channel-$index", name = "Synthetic $index", streamUrl = "synthetic:$index")
    }

    @Test fun currentChannelHasExactlyOneVisibleCardThroughoutTheQueue() {
        for (position in channels.indices) {
            val cards = PlayerDeckWindow.channels(channels, position)
            assertEquals(1, cards.count { it.channel.id == channels[position].id })
            assertEquals(cards.size, cards.map { it.channel.id }.distinct().size)
            assertTrue(cards.all { it.number in 1..channels.size })
        }
    }

    @Test fun visibleCardNumberTracksQueueAfterFiltering() {
        val filtered = channels.filterIndexed { index, _ -> index % 3 != 0 }
        for (position in filtered.indices step 7) {
            val cards = PlayerDeckWindow.channels(filtered, position)
            val active = cards.single { it.channel.id == filtered[position].id }
            assertEquals(position + 1, active.number)
            assertEquals(filtered[active.number - 1].id, active.channel.id)
        }
    }
}