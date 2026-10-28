package com.yourapp.iptv.ui.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsActionRow(title: String, summary: String = "", value: String = "",
    category: SettingsCategory, modifier: Modifier = Modifier, enabled: Boolean = true,
    checked: Boolean? = null, glyph: String = "", onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Surface(onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 44.dp).onFocusChanged { focused = it.hasFocus }
            .semantics { if (checked != null) toggleableState = if (checked) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off },
        color = if (focused) colors.primaryContainer else colors.surfaceContainerLow,
        contentColor = colors.onSurface, shape = MaterialTheme.shapes.medium,
        border = BorderStroke(if (focused) 2.dp else 1.dp, if (focused) colors.primary else colors.outlineVariant)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (glyph.isBlank()) SettingsIcon(category, if (focused) colors.primary else colors.onSurfaceVariant)
            else SettingsOptionIcon(glyph, if (focused) colors.primary else colors.onSurfaceVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (summary.isNotBlank()) Text(summary, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            if (checked != null) Switch(checked, onCheckedChange = null, enabled = enabled)
            else {
                if (value.isNotBlank()) Text(value, Modifier.widthIn(max = 160.dp), maxLines = 2,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge, color = colors.primary)
                Text(if (rtl) "‹" else "›", color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun <T> SettingsChoiceRow(title: String, value: T, choices: List<Pair<T, String>>, category: SettingsCategory,
    tag: String, enabled: Boolean, modifier: Modifier = Modifier, onChange: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    Box {
        SettingsActionRow(title, value = choices.firstOrNull { it.first == value }?.second.orEmpty(), category = category,
            modifier = modifier.focusRequester(focus).testTag(tag), enabled = enabled, glyph = tag) { open = true }
        DropdownMenu(open && enabled, onDismissRequest = { open = false;focus.requestFocus() }) {
            choices.forEachIndexed { index, (choice, label) ->
                DropdownMenuItem(text = { Text((if (choice == value) "✓  " else "") + label) },
                    onClick = { onChange(choice);open = false;focus.requestFocus() },
                    modifier = Modifier.testTag("$tag-choice-$index"))
            }
        }
    }
}

@Composable
internal fun SettingsFooterButton(title: String, onClick: () -> Unit, enabled: Boolean,
    modifier: Modifier = Modifier, primary: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, enabled = enabled,
        modifier = modifier.heightIn(min = 42.dp).onFocusChanged { focused = it.isFocused },
        color = if (primary && enabled) colors.primary else if (focused) colors.primaryContainer else colors.surfaceContainerHigh,
        contentColor = if (primary && enabled) colors.onPrimary else colors.onSurface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(if (focused) 3.dp else 1.dp,
            if (focused) { if (primary) colors.onSurface else colors.primary } else colors.outline)) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(title, style = MaterialTheme.typography.labelLarge)
        }
    }
}