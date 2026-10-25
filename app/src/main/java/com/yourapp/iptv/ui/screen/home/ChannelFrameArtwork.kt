package com.yourapp.iptv.ui.screen.home

import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.ui.component.player.ChannelFrameStore
import java.io.File

/** Hero and channel cards observe the same cache, including writes completed after navigation. */
@Composable
internal fun rememberChannelFrame(channel: Channel?): File? {
    val context = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    var resumed by remember { mutableIntStateOf(0) }
    val revision by ChannelFrameStore.revision.collectAsStateWithLifecycle()
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumed++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return remember(context, channel?.id, channel?.streamUrl, revision, resumed) {
        channel?.let { ChannelFrameStore.fileFor(context, it) }
    }
}

/** Keeps the logo visible underneath while a real cached JPEG loads, or if decoding fails. */
@Composable
internal fun SavedChannelFrame(frame: File?, modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop, alignment: Alignment = Alignment.Center,
    onLoaded: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val stamp = frame?.let { "${it.absolutePath}:${it.lastModified()}:${it.length()}" }
    var failed by remember(stamp) { mutableStateOf(false) }
    var loaded by remember(stamp) { mutableStateOf(false) }
    val report by rememberUpdatedState(onLoaded)
    LaunchedEffect(stamp, failed, loaded) { report(loaded && !failed) }
    if (frame == null || !frame.isFile || failed) return
    val request = remember(context, stamp) {
        ImageRequest.Builder(context).data(frame).size(640, 360)
            .memoryCacheKey("channel-frame:$stamp").crossfade(false).build()
    }
    AsyncImage(model = request, contentDescription = null,
        contentScale = contentScale, alignment = alignment,
        modifier = modifier.testTag(if (loaded) "saved-channel-frame" else "loading-channel-frame"),
        onSuccess = { loaded = true }, onError = {
            failed = true
            Log.w("MNX.FrameArtwork", "Cached video frame could not be decoded", it.result.throwable)
        })
}