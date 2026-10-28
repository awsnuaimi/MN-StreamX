package com.yourapp.iptv.ui.screen.home

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.presentation.HomeChannelPresentation
import com.yourapp.iptv.ui.presentation.HomeTranslationKind
import com.yourapp.iptv.ui.screen.parental.LocalParentalSession
import com.yourapp.iptv.ui.state.HomeProgramUiState

@Composable
internal fun HomeDashboardScene(labels: HomeLabels, appearance: HomeAppearance, profileName: String,
    selection: HomeDashboardSelection, channels: List<Channel>, sourceKey: String, focus: FocusRequester,
    onProfiles: () -> Unit, onSearch: () -> Unit, onPlay: (Channel) -> Unit,
    onLive: () -> Unit, onMovies: () -> Unit, onSeries: () -> Unit, onGuide: () -> Unit, onHistory: () -> Unit) {
    val parental = LocalParentalSession.current
    HomeCaptionHost { captions, openCaptions ->
        HomeGuideHost { guides, bindGuides ->
            HomeProgramHost { program, selectProgram ->
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    HomeDashboardTopBar(labels, profileName, onProfiles, onSearch, openCaptions,
                        translationLabel = homeTranslationLabel(captions, labels),
                        translationSelected = HomeChannelPresentation.kind(captions) == HomeTranslationKind.GENERATED,
                        protectionLabel = if (!parental.state.configured) null else if (parental.state.unlocked)
                            labels.text("قفل الحماية", "Lock", "Sperren") else labels.text("فتح الحماية", "Unlock", "Entsperren"),
                        onProtection = { if (parental.state.unlocked) parental.model?.lock() else parental.model?.requestPin() })
                    Box(Modifier.weight(1f)) {
                        HomeDashboard(labels, selection, channels.size, focus, channels.take(5), appearance.isDarkTheme,
                            appearance.isNightMode, onPlay, onLive, onMovies, onSeries, onGuide, onHistory,
                            programState = program.takeIf { it.sourceKey == sourceKey } ?: HomeProgramUiState(),
                            onPreviewChannel = selectProgram, theme = appearance.theme,
                            showChannelLogos = appearance.showChannelLogos, catalogChannels = channels,
                            captionState = captions, interfaceLanguage = appearance.language, guideState = guides,
                            guideOwner = sourceKey, accessEpoch = parental.state.epoch, onGuideChannels = bindGuides)
                    }
                }
            }
        }
    }
}