package com.yourapp.iptv.ui.screen.vod

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.yourapp.iptv.domain.model.VodItem
import com.yourapp.iptv.domain.model.VodKind
import com.yourapp.iptv.domain.model.VodProgress
import com.yourapp.iptv.domain.model.VodProgressEntry
import com.yourapp.iptv.ui.component.common.tvFocusIndicator

private fun validPoster(url: String?): Boolean = url?.startsWith("https://") == true ||
    url?.startsWith("http://") == true

@Composable
private fun Cover(item: VodItem, modifier: Modifier, size: Int) {
    val context = LocalContext.current
    val request = remember(context, item.poster, size) {
        ImageRequest.Builder(context).data(item.poster).size(size, size).crossfade(false).build()
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        if (validPoster(item.poster)) {
            AsyncImage(request, item.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

/** A still image only: no video decoder, connection or additional repository call. */
@Composable
internal fun VodMovieHero(item: VodItem, labels: VodLabels, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth().height(148.dp).clip(RoundedCornerShape(17.dp))
        .background(colors.surfaceContainerLow).testTag("vod-movie-hero")) {
        Cover(item, Modifier.fillMaxHeight().fillMaxWidth(.58f).align(Alignment.CenterEnd), 640)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(
            colors.surfaceContainerLow, colors.surfaceContainerLow.copy(alpha = .96f),
            colors.surfaceContainerLow.copy(alpha = .28f), Color.Transparent))))
        Column(Modifier.fillMaxHeight().fillMaxWidth(.66f).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(labels.featured(), color = colors.primary, style = MaterialTheme.typography.labelSmall)
            Text(item.name, style = MaterialTheme.typography.headlineSmall, color = colors.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(item.year, item.rating?.let { "★ $it" }).joinToString("  ·  "),
                color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            item.plot?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onOpen, modifier = Modifier.height(40.dp).tvFocusIndicator(VodCutShape)
                .testTag("vod-featured-open"), contentPadding = PaddingValues(horizontal = 18.dp),
                shape = VodCutShape) {
                Text(labels.details())
            }
        }
    }
}

/** Right-side series preview tracks only the focused card, not a network-fetched detail state. */
@Composable
internal fun VodSeriesPreview(item: VodItem?, progress: List<VodProgressEntry>, labels: VodLabels,
    onOpen: (VodItem) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(colors.surfaceContainerLow)
        .testTag("vod-series-preview")) {
        if (item == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(labels.empty(), color = colors.onSurfaceVariant)
            }
        } else {
            Box(Modifier.fillMaxWidth().height(130.dp)) {
                Cover(item, Modifier.fillMaxSize(), 512)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                    Color.Transparent, colors.surfaceContainerLow.copy(alpha = .25f), colors.surfaceContainerLow))))
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(item.name, style = MaterialTheme.typography.headlineSmall, color = colors.onSurface,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(item.year, item.rating?.let { "★ $it" }).joinToString("  ·  "),
                    color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                item.plot?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                // A saved episode can belong to this series; never show progress for another series.
                val last = progress.firstOrNull { it.kind == VodKind.SERIES &&
                    it.seriesId == item.id && VodProgress.resumable(it) }
                if (last != null) {
                    Text("${labels.resume()}: ${last.title}", color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    LinearProgressIndicator(progress = {
                        (last.positionMs.toFloat() / last.durationMs.toFloat()).coerceIn(0f, 1f)
                    }, modifier = Modifier.fillMaxWidth(), color = colors.primary)
                }
                OutlinedButton(onClick = { onOpen(item) }, modifier = Modifier.fillMaxWidth()
                    .tvFocusIndicator().testTag("vod-series-details"), border = BorderStroke(1.dp, colors.outlineVariant)) {
                    Text(labels.episodes())
                }
            }
        }
    }
}