package mnstudio.apps.mnstreamx.player.vod

import android.app.Application
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class,sdk = [30])
@androidx.annotation.OptIn(UnstableApi::class)
class VodMediaItemTest {
    @Test fun opaqueHlsUrlsGetAnExplicitMimeTypeWithoutChangingTheSignature() {
        val url = "https://cdn.test/play?sig=a%2Bb&expires=123"
        val item = vodMediaItem(url,"m3u8").localConfiguration!!
        assertEquals(MimeTypes.APPLICATION_M3U8,item.mimeType)
        assertEquals(url,item.uri.toString())
    }
    @Test fun progressiveContainersRemainAvailableToExtractorDetection() {
        for (extension in listOf("mp4","mkv","m4v","ts")) assertNull(vodMediaItem("https://cdn.test/7.$extension",extension).localConfiguration!!.mimeType)
    }
}