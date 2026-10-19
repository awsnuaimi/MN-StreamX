package com.yourapp.iptv.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextDirection
import com.yourapp.iptv.ui.presentation.ProgramPresentation
import com.yourapp.iptv.ui.component.common.AppButton

@Composable
fun HomeHeader(
    visibleCount: Int, totalCount: Int, loading: Boolean,
    onRefresh: () -> Unit, onSettings: () -> Unit, onGuide: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 680.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                HomeHeading(visibleCount, totalCount, Modifier.weight(1f))
                HomeHeaderActions(totalCount, loading, onRefresh, onSettings, onGuide)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HomeHeading(visibleCount, totalCount, Modifier.fillMaxWidth())
                HomeHeaderActions(totalCount, loading, onRefresh, onSettings, onGuide)
            }
        }
    }
}

@Composable
private fun HomeHeading(visible: Int, total: Int, modifier: Modifier) {
    Column(modifier) {
        Text("MN StreamX", style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary)
        Text(ProgramPresentation.channelCount(visible, total),
            style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Rtl))
    }
}

@Composable
private fun HomeHeaderActions(
    total: Int, loading: Boolean, onRefresh: () -> Unit, onSettings: () -> Unit, onGuide: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppButton("دليل البرامج", onGuide, enabled = total > 0 && !loading)
        AppButton("تحديث", onRefresh, enabled = !loading)
        AppButton("الإعدادات", onSettings)
    }
}