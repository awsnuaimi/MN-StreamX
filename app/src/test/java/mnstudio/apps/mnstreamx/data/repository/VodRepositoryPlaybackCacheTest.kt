package mnstudio.apps.mnstreamx.data.repository

import android.app.Application
import android.content.ContextWrapper
import mnstudio.apps.mnstreamx.domain.model.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34],application = Application::class)
class VodRepositoryPlaybackCacheTest {
    private val account = AppSettings(source = PlaylistSource.XTREAM,xtreamServer = "https://synthetic.invalid",
        xtreamUsername = "synthetic",xtreamPassword = "synthetic")
    private val item = VodItem("7","Synthetic film",null)
    private fun test(block: suspend (android.content.Context) -> Unit) {
        val directory = Files.createTempDirectory("synthetic-playback-cache").toFile()
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getCacheDir() = directory
        }
        try { runBlocking { block(context) } } finally { directory.deleteRecursively() }
    }
    @Test fun openingThenPlayingAMovieReusesVerifiedExtensionAndSignedTarget() = test { context ->
        var calls = 0
        val repo = VodRepositoryImpl(context,detailFetch = { _,_,current ->
            calls++;VodDetails(current.copy(extension = "mkv"),playback = VodPlaybackSource("https://cdn.invalid/signed:$calls","mkv"))
        },detailClock = { 0 })
        repo.details(account,VodKind.MOVIE,item)
        val playback = repo.resolvePlayback(account,VodKind.MOVIE,"7","mp4")
        assertEquals("https://cdn.invalid/signed:1",playback.url);assertEquals("mkv",playback.extension)
        assertEquals(1,calls)
    }
    @Test fun manualPlaybackRetryAndDetailsRetryFetchFreshMetadata() = test { context ->
        var calls = 0
        val repo = VodRepositoryImpl(context,detailFetch = { _,_,current ->
            calls++;VodDetails(current,playback = VodPlaybackSource("target:$calls","mp4"))
        },detailClock = { 0 })
        repo.resolvePlayback(account,VodKind.MOVIE,"7","mp4")
        assertEquals("target:2",repo.refreshPlayback(account,VodKind.MOVIE,"7","mp4").url)
        assertEquals("target:3",repo.refreshDetails(account,VodKind.MOVIE,item).playback?.url)
        assertEquals(3,calls)
    }
    @Test fun metadataKeysSeparateCredentialsProfilesKindsAndIds() = test { context ->
        var calls = 0
        val repo = VodRepositoryImpl(context,detailFetch = { _,_,current -> calls++;VodDetails(current) },detailClock = { 0 })
        repo.details(account,VodKind.MOVIE,item)
        repo.details(account.copy(xtreamPassword = "another"),VodKind.MOVIE,item)
        repo.details(account.copy(profileId = "11111111-1111-4111-8111-111111111111"),VodKind.MOVIE,item)
        repo.details(account,VodKind.SERIES,item)
        repo.details(account,VodKind.MOVIE,item.copy(id = "8"))
        repo.details(account,VodKind.MOVIE,item)
        assertEquals(5,calls)
    }
    @Test fun expiredMovieMetadataIsRefetchedInsteadOfReplayingAnOldSignedUrl() = test { context ->
        var now = 0L;var calls = 0
        val repo = VodRepositoryImpl(context,detailFetch = { _,_,current ->
            calls++;VodDetails(current,playback = VodPlaybackSource("target:$calls","mp4"))
        },detailClock = { now })
        repo.details(account,VodKind.MOVIE,item);now = 15_000
        assertEquals("target:2",repo.resolvePlayback(account,VodKind.MOVIE,"7","mp4").url)
        assertEquals(2,calls)
    }
    @Test fun failedFreshMetadataFallsBackWithoutReusingTheExpiredTarget() = test { context ->
        var fail = false
        val repo = VodRepositoryImpl(context,detailFetch = { _,_,current ->
            if (fail) throw java.io.IOException("Synthetic provider failure")
            VodDetails(current,playback = VodPlaybackSource("expired","mp4"))
        },detailClock = { 0 })
        repo.details(account,VodKind.MOVIE,item);fail = true
        val result = repo.refreshPlayback(account,VodKind.MOVIE,"7","mp4")
        assertEquals(repo.playbackUrl(account,VodKind.MOVIE,"7","mp4"),result.url)
    }
}