package mnstudio.apps.mnstreamx.ui.screen.vod

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
import mnstudio.apps.mnstreamx.domain.model.VodItem
import mnstudio.apps.mnstreamx.domain.model.VodKind

/** A bounded poster request; never starts a second video decoder. */
@Composable
internal fun VodDetailsArtwork(item: VodItem, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val valid = item.poster?.let { it.startsWith("https://") || it.startsWith("http://") } == true
    val request = remember(context, item.poster) {
        if (valid) ImageRequest.Builder(context).data(item.poster).size(360, 520).crossfade(false).build()
        else null
    }
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(colors.surfaceContainerLow)
        .testTag("vod-details-artwork"), contentAlignment = Alignment.Center) {
        if (request != null) AsyncImage(request, contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text("▶", style = MaterialTheme.typography.displayMedium, color = colors.primary)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Color.Transparent, Color.Transparent, colors.surfaceContainerLowest.copy(alpha = .55f)))))
    }
}

@Composable
internal fun VodDetailsMetadata(item: VodItem, kind: VodKind, labels: VodLabels,
    modifier: Modifier = Modifier, compact: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)) {
        Text(if (kind == VodKind.SERIES) labels.title(VodKind.SERIES) else labels.title(VodKind.MOVIE),
            color = colors.primary, style = MaterialTheme.typography.labelMedium)
        Text(item.name, style = if (compact) MaterialTheme.typography.titleLarge
            else MaterialTheme.typography.headlineMedium, color = colors.onSurface,
            maxLines = if (compact) 2 else 3, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("vod-details-title"))
        val metadata = listOfNotNull(item.year?.takeIf { it.isNotBlank() },
            item.rating?.takeIf { it.isNotBlank() }?.let { "★ $it" })
        if (metadata.isNotEmpty()) Text(metadata.joinToString("  ·  "),
            style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        item.plot?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                maxLines = if (compact) 2 else 4, overflow = TextOverflow.Ellipsis)
        }
    }
}