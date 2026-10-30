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
import com.yourapp.iptv.ui.component.common.rememberedFocusGroup
import androidx.compose.runtime.remember

@Composable
internal fun HomeDashboardScene(labels: HomeLabels, appearance: HomeAppearance, profileName: String,
    selection: HomeDashboardSelection, channels: List<Channel>, sourceKey: String, focus: FocusRequester,
    onProfiles: () -> Unit, onSearch: () -> Unit, onPlay: (Channel) -> Unit,
    onLive: () -> Unit, onMovies: () -> Unit, onSeries: () -> Unit, onGuide: () -> Unit, onHistory: () -> Unit,
    sidebarFocus: FocusRequester? = null) {
    val parental = LocalParentalSession.current
    val headerFocus = remember { FocusRequester() }
    val bodyFocus = remember { FocusRequester() }
    val heroFocus = remember { FocusRequester() }
    HomeCaptionHost { captions, openCaptions ->
        HomeGuideHost { guides, bindGuides ->
            HomeProgramHost { program, selectProgram ->
                Column(Modifier.fillMaxSize().rememberedFocusGroup(focus, heroFocus), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    HomeDashboardTopBar(labels, profileName, onProfiles, onSearch, openCaptions,
                        translationLabel = homeTranslationLabel(captions, labels),
                        translationSelected = HomeChannelPresentation.kind(captions) == HomeTranslationKind.GENERATED,
                        protectionLabel = if (!parental.state.configured) null else if (parental.state.unlocked)
                            labels.text("قفل الحماية", "Lock", "Sperren") else labels.text("فتح الحماية", "Unlock", "Entsperren"),
                        onProtection = { if (parental.state.unlocked) parental.model?.lock() else parental.model?.requestPin() },
                        modifier = Modifier.rememberedFocusGroup(headerFocus) { direction ->
                            homeRegionExit(direction, below = bodyFocus, sidebar = sidebarFocus,
                                sidebarOnRight = appearance.sidebarOnRight, rememberBelow = true, fallbackBelow = heroFocus)
                        })
                    Box(Modifier.weight(1f)) {
                        HomeDashboard(labels, selection, channels.size, bodyFocus, channels.take(5), appearance.isDarkTheme,
                            appearance.isNightMode, onPlay, onLive, onMovies, onSeries, onGuide, onHistory,
                            programState = program.takeIf { it.sourceKey == sourceKey } ?: HomeProgramUiState(),
                            onPreviewChannel = selectProgram, theme = appearance.theme,
                            showChannelLogos = appearance.showChannelLogos, catalogChannels = channels,
                            captionState = captions, interfaceLanguage = appearance.language, guideState = guides,
                            guideOwner = sourceKey, accessEpoch = parental.state.epoch, onGuideChannels = bindGuides,
                            headerFocus = headerFocus, sidebarFocus = sidebarFocus, sidebarOnRight = appearance.sidebarOnRight,
                            heroEntry = heroFocus)
                    }
                }
            }
        }
    }
}