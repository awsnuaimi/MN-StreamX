package mnstudio.apps.mnstreamx.domain.playback

import mnstudio.apps.mnstreamx.domain.model.Channel
import org.junit.Assert.*
import org.junit.Test

class ChannelPlaybackHandoffTest {
    private val row = Channel("1","Synthetic",streamUrl = "synthetic:one")
    @Test fun aClickHasExactlyOneConsumer() {
        val cache = ChannelPlaybackHandoff { 0 }
        cache.offer("profile/account",row,1)
        assertSame(row,cache.take("profile/account","1",1))
        assertNull(cache.take("profile/account","1",1))
    }
    @Test fun anotherProfileSourceIdOrAccessEpochCannotUseThePreviousSelection() {
        val cache = ChannelPlaybackHandoff { 0 }
        for ((scope,id,epoch) in listOf(Triple("other/account","1",1L),
            Triple("profile/other","1",1L),Triple("profile/account","2",1L),Triple("profile/account","1",2L))) {
            cache.offer("profile/account",row,1)
            assertNull(cache.take(scope,id,epoch))
            assertNull(cache.take("profile/account","1",1))
        }
    }
    @Test fun staleSelectionsExpireAndASecondClickReplacesTheFirst() {
        var now = 0L;val cache = ChannelPlaybackHandoff { now }
        cache.offer("owner",row,1);now = 5_001
        assertNull(cache.take("owner","1",1))
        cache.offer("owner",row,1);cache.offer("owner",row.copy(id = "2"),1)
        assertEquals("2",cache.take("owner","2",1)?.id)
    }
}