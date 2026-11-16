package mnstudio.apps.mnstreamx

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import mnstudio.apps.mnstreamx.data.diagnostics.DeviceLivePlaybackHistory
import mnstudio.apps.mnstreamx.data.image.AppImageLoader
import mnstudio.apps.mnstreamx.domain.performance.LivePlaybackHealthJournal
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class IptvApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        LivePlaybackHealthJournal.attachHistory(DeviceLivePlaybackHistory.forApp(this))
    }

    override fun newImageLoader(): ImageLoader = AppImageLoader.create(this)
}