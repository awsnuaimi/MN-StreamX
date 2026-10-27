package com.yourapp.iptv.ui.screen.parental

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.HomeLanguage
import com.yourapp.iptv.domain.parental.ParentalSection
import com.yourapp.iptv.ui.component.common.AppButton

@Composable
fun ParentalRouteGate(section: ParentalSection?, resumeRoute: String, onBack: () -> Unit,
    content: @Composable () -> Unit) {
    val session = LocalParentalSession.current
    val locked = session.state.configured && !session.state.unlocked &&
        (section == null || !session.state.allows(section))
    if (!locked) { content(); return }
    val labels = ParentalLabels(session.language)
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize(),contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(labels.text("هذا القسم محمي برمز PIN","This section is protected by a PIN","Dieser Bereich ist PIN-geschützt"))
            AppButton(labels.unlock(),{ session.model?.requestPin(resumeRoute) })
            AppButton(labels.back(),onBack)
        }
    }
}

@Composable
fun ParentalHomeControl() {
    val session = LocalParentalSession.current
    if (!session.state.configured) return
    val labels = ParentalLabels(session.language)
    AppButton(if (session.state.unlocked) labels.lock() else labels.unlock(),{
        if (session.state.unlocked) session.model?.lock() else session.model?.requestPin()
    })
}