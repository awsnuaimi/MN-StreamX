package com.yourapp.iptv.ui.screen.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.HomeAppearance

@Composable
internal fun HomeSidebar(appearance: HomeAppearance, labels: HomeLabels, selected: HomeSection,
    loading: Boolean, focusRequesters: Map<HomeSection, FocusRequester>,
    onFocused: (HomeSection) -> Unit, onSelect: (HomeSection) -> Unit,
    dashboardFocus: FocusRequester? = null, modifier: Modifier = Modifier, organizationReady: Boolean = true) {
    val colors = HomeTvPalettes.forTheme(appearance.theme, appearance.isNightMode)
    var more by remember { mutableStateOf(false) }
    val expanded = appearance.expandedSidebar
    Column(modifier.width(if (expanded) 180.dp else 66.dp).fillMaxHeight().background(colors.sidebar)
        .focusGroup().padding(horizontal = 12.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (expanded) "MN StreamX" else "X", color = colors.accent, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (section in HomeFocusPolicy.rail.filter { it != HomeSection.SETTINGS }) {
                Box {
                    RailButton(section, expanded, selected, labels, colors, focusRequesters.getValue(section),
                        dashboardFocus, appearance.sidebarOnRight, onFocused) {
                        if (section == HomeSection.MORE) more = true else onSelect(section)
                    }
                    if (section == HomeSection.MORE) DropdownMenu(expanded = more, onDismissRequest = {
                        more = false; focusRequesters.getValue(HomeSection.MORE).requestFocus()
                    }) {
                        HomeSection.entries.filter { it !in HomeFocusPolicy.rail }.forEach { action ->
                            DropdownMenuItem(text = { Text(labels.section(action)) },
                                enabled = !(loading && action == HomeSection.REFRESH) &&
                                    (action != HomeSection.ORGANIZE || organizationReady),
                                onClick = { more = false; onSelect(action) },
                                modifier = Modifier.testTag("rail-menu-${action.name}"))
                        }
                    }
                }
            }
        }
        RailButton(HomeSection.SETTINGS, expanded, selected, labels, colors,
            focusRequesters.getValue(HomeSection.SETTINGS), dashboardFocus, appearance.sidebarOnRight, onFocused) {
            onSelect(HomeSection.SETTINGS)
        }
    }
}

@Composable
private fun RailButton(section: HomeSection, expanded: Boolean, selected: HomeSection, labels: HomeLabels,
    colors: HomeTvPalette, requester: FocusRequester, dashboard: FocusRequester?, sidebarOnRight: Boolean,
    onFocused: (HomeSection) -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val active = selected == section
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp)
        .focusRequester(requester).then(if (dashboard != null && selected == HomeSection.DASHBOARD)
            Modifier.focusProperties { if (sidebarOnRight) left = dashboard else right = dashboard } else Modifier)
        .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused(section) }
        .testTag("rail-${section.name}").semantics { contentDescription = labels.section(section); this.selected = active },
        color = when { active -> colors.accent; focused -> colors.focused; else -> colors.sidebar },
        shape = HomeHeroShape,
        border = if (focused) BorderStroke(2.dp, colors.text) else null) {
        Row(Modifier.padding(vertical = 8.dp, horizontal = if (expanded) 10.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeSidebarIcon(section, if (active) colors.sidebar else if (focused) colors.accent else colors.muted)
            if (expanded) Text(labels.section(section), color = if (active) colors.sidebar else colors.text,
                style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}