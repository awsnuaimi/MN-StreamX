package com.yourapp.iptv.ui.screen.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun HomeCategories(groups: List<String>, selected: String?, labels: HomeLabels, modifier: Modifier,
    onSelect: (String?) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(labels.text("التصنيفات", "Categories", "Kategorien"), color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp), contentPadding = PaddingValues(3.dp)) {
            item("all") { CategoryButton(labels.text("كل التصنيفات", "All categories", "Alle Kategorien"), selected == null) { onSelect(null) } }
            items(groups, key = { it }) { group -> CategoryButton(group, selected == group) { onSelect(group) } }
        }
    }
}

@Composable
private fun CategoryButton(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        color = if (selected || focused) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.small, border = if (focused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
        Text(label, Modifier.padding(horizontal = 12.dp, vertical = 13.dp), color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content))
    }
}