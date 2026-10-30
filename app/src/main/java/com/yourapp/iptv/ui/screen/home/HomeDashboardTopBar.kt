package com.yourapp.iptv.ui.screen.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourapp.iptv.ui.component.common.tvFocusIndicator

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeDashboardTopBar(labels: HomeLabels, profileName: String,
    onProfiles: () -> Unit, onSearch: () -> Unit, onTranslation: () -> Unit,
    modifier: Modifier = Modifier, translationLabel: String? = null, translationSelected: Boolean = false,
    protectionLabel: String? = null, onProtection: () -> Unit = {}) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HeaderPill(labels.text("الملف: ", "Profile: ", "Profil: ") + profileName.ifBlank {
            labels.text("الملف الأساسي", "Original profile", "Bisheriges Profil")
        }, onProfiles, Modifier.widthIn(max = 300.dp).testTag("home-profile"))
        protectionLabel?.let { HeaderPill(it, onProtection, Modifier.testTag("home-protection")) }
        Spacer(Modifier.weight(1f))
        HeaderPill(labels.text("البحث", "Search", "Suchen"), onSearch, Modifier.testTag("home-search"))
        HeaderPill(translationLabel ?: labels.text("الترجمة", "Translation", "Übersetzung"), onTranslation,
            Modifier.testTag("home-translation"), selected = translationSelected)
    }
}

@Composable
private fun HeaderPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, modifier = modifier.tvFocusIndicator(RoundedCornerShape(50)).height((32f * scale).dp).onFocusChanged { focused = it.isFocused }
        .semantics { contentDescription = text }, shape = RoundedCornerShape(50),
        color = if (focused) colors.primaryContainer else colors.surface.copy(alpha = 0.3f),
        border = BorderStroke(if (focused || selected) 1.5.dp else 1.dp,
            if (focused || selected) colors.primary else colors.outline.copy(alpha = 0.45f))) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (selected) Text("●", color = colors.primary, fontSize = 9.sp)
            Text(text, color = if (focused || selected) colors.onSurface else colors.onSurfaceVariant,
                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}