package com.yourapp.iptv.ui.screen.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.ui.presentation.ChannelProgramInfo
import com.yourapp.iptv.ui.presentation.LiveChannelRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun liveTvTime(time: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun LiveTvDetail(row: LiveChannelRow, channel: Channel, program: ChannelProgramInfo,
    nowMillis: Long, colors: HomeTvPalette, labels: HomeLabels, translated: Boolean, target: String,
    watch: FocusRequester, backToList: FocusRequester, onWatch: () -> Unit, onTranslation: () -> Unit,
    modifier: Modifier = Modifier, sidebarOnRight: Boolean? = null, above: FocusRequester? = null) {
    val direction = androidx.compose.ui.platform.LocalLayoutDirection.current
    val sidebarRight = sidebarOnRight ?: (direction == androidx.compose.ui.unit.LayoutDirection.Rtl)
    val translation = remember { FocusRequester() }
    var watchTop by remember { mutableFloatStateOf(Float.NaN) }
    var translationTop by remember { mutableFloatStateOf(Float.NaN) }
    val stacked = watchTop.isFinite() && translationTop.isFinite() && translationTop > watchTop + 1f
    fun routes(isTranslation: Boolean): Modifier {
        fun target(arrow: HomeDpad): FocusRequester? = when (HomeDpadPolicy.detail(arrow, isTranslation, stacked,
            direction == androidx.compose.ui.unit.LayoutDirection.Rtl, sidebarRight)) {
            HomeDpadPolicy.DetailTarget.OTHER -> if (isTranslation) watch else translation
            HomeDpadPolicy.DetailTarget.LIST -> backToList
            HomeDpadPolicy.DetailTarget.HEADER -> above
            HomeDpadPolicy.DetailTarget.STAY -> null
        }
        return Modifier.homeFocusLinks(target(HomeDpad.UP), target(HomeDpad.DOWN),
            target(HomeDpad.LEFT), target(HomeDpad.RIGHT))
    }
    val frame = rememberChannelFrame(channel)
    var loaded by remember(channel.id, channel.streamUrl) { mutableStateOf(false) }
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(colors.selected)
        .verticalScroll(rememberScrollState()).testTag("live-detail")) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.67f)) {
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(colors.cardFallbackGradient)),
                contentAlignment = Alignment.Center) {
                Text(row.title, color = colors.muted, fontSize = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(20.dp))
            }
            SavedChannelFrame(frame, Modifier.matchParentSize(), contentScale = ContentScale.Crop, onLoaded = { loaded = it })
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent,
                Color.Transparent, colors.selected))))
            Surface(Modifier.align(Alignment.TopStart).padding(14.dp, 10.dp), color = colors.liveBadge,
                shape = RoundedCornerShape(4.dp)) {
                Text("LIVE", Modifier.padding(7.dp, 3.dp), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            if (!loaded) Text(labels.text("معاينة محفوظة غير متاحة", "No saved preview", "Keine gespeicherte Vorschau"),
                color = colors.muted, fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomStart).padding(16.dp))
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(row.title, color = colors.text, fontSize = 21.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("live-detail-title"))
            program.current?.let { current ->
                Text(current.title, color = colors.text, fontSize = 14.sp, lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val minutes = ((current.endTime.toDouble() - nowMillis.toDouble()) / 60_000).coerceIn(0.0, 9999.0)
                val left = kotlin.math.ceil(minutes).toInt()
                Text("${liveTvTime(current.startTime)} – ${liveTvTime(current.endTime)} · " +
                    labels.text("باقٍ $left د", "$left min left", "noch $left Min."), color = colors.muted,
                    fontSize = 11.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                program.progress?.let { LiveTvProgress(it, colors, Modifier.fillMaxWidth()) }
                current.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = colors.muted, fontSize = 11.sp, lineHeight = 15.sp,
                        maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                }
            } ?: Text(labels.text("بث مباشر · دليل البرامج غير متاح", "Live TV · Programme guide unavailable",
                "Live-TV · Keine Programmdaten verfügbar"), color = colors.muted, fontSize = 12.sp, lineHeight = 16.sp)
            program.next?.let {
                Text(labels.text("التالي", "Next", "Danach") + " · ${liveTvTime(it.startTime)} · ${it.title}",
                    color = colors.muted, fontSize = 10.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            FlowRow(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    var focused by remember { mutableStateOf(false) }
                    Surface(onClick = onWatch, modifier = Modifier.focusRequester(watch)
                        .then(routes(false)).focusProperties { canFocus = true }
                        .onGloballyPositioned { watchTop = it.positionInParent().y }.onFocusChanged { focused = it.isFocused }.testTag("live-watch"),
                        shape = HomeHeroShape, color = colors.accent,
                        border = if (focused) BorderStroke(1.dp, colors.text) else null) {
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text(labels.text("مشاهدة", "Watch", "Ansehen"), color = liveTvOnAccent(colors),
                                fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                LiveTvPill(if (translated) labels.text("مع الترجمة · $target", "With translation · $target", "Mit Übersetzung · $target")
                    else labels.text("إعداد الترجمة", "Set up translation", "Übersetzung einrichten"),
                    colors, onTranslation, Modifier.focusRequester(translation).then(routes(true))
                        .onGloballyPositioned { translationTop = it.positionInParent().y }.testTag("live-translation"), compact = true)
            }
        }
    }
}