package mnstudio.apps.mnstreamx.player.controller

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import mnstudio.apps.mnstreamx.data.network.HttpClient
import mnstudio.apps.mnstreamx.domain.performance.LiveStartupTrace
import mnstudio.apps.mnstreamx.domain.performance.PerformanceStage
import mnstudio.apps.mnstreamx.player.recovery.LiveRecoveryErrors
import java.util.concurrent.TimeUnit
import okhttp3.Dispatcher

/** Live-only transport. Reuses the API connection pool without imposing its finite call timeout. */
@androidx.annotation.OptIn(UnstableApi::class)
internal object LiveMediaSourceFactory {
    internal val client by lazy {
        HttpClient.create().newBuilder()
            .dispatcher(Dispatcher())
            .connectTimeout(8,TimeUnit.SECONDS)
            .readTimeout(8,TimeUnit.SECONDS)
            .callTimeout(0,TimeUnit.MILLISECONDS)
            .followSslRedirects(false)
            .build()
    }

    fun dataSource(context: Context, trace: LiveStartupTrace): DataSource.Factory {
        val http = OkHttpDataSource.Factory(client)
            .setUserAgent(System.getProperty("http.agent") ?: Util.getUserAgent(context,"MN StreamX"))
        // The listener belongs to this media source, never to a mutable global current channel.
        return DefaultDataSource.Factory(context,http).setTransferListener(object : TransferListener {
            override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
            override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
                if (isNetwork) trace.reached(PerformanceStage.LIVE_HTTP_OPEN)
            }
            override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
                if (isNetwork && bytesTransferred > 0) trace.reached(PerformanceStage.LIVE_FIRST_BYTE)
            }
            override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
        })
    }

    fun create(context: Context, trace: LiveStartupTrace) =
        DefaultMediaSourceFactory(dataSource(context, trace)).setLoadErrorHandlingPolicy(LiveLoadErrorPolicy)
}

/** Let the live session own server cooldowns while Media3 still handles ordinary segment retries. */
@androidx.annotation.OptIn(UnstableApi::class)
internal object LiveLoadErrorPolicy : DefaultLoadErrorHandlingPolicy() {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long =
        if (requiresSessionRecovery(loadErrorInfo)) C.TIME_UNSET else super.getRetryDelayMsFor(loadErrorInfo)

    override fun getFallbackSelectionFor(
        fallbackOptions: LoadErrorHandlingPolicy.FallbackOptions,
        loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo
    ): LoadErrorHandlingPolicy.FallbackSelection? =
        if (requiresSessionRecovery(loadErrorInfo)) null else super.getFallbackSelectionFor(fallbackOptions, loadErrorInfo)

    private fun requiresSessionRecovery(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Boolean {
        val http = loadErrorInfo.exception as? HttpDataSource.InvalidResponseCodeException ?: return false
        return http.responseCode == 429 || (http.responseCode == 503 && LiveRecoveryErrors.retryAfterMs(http) > 0)
    }
}