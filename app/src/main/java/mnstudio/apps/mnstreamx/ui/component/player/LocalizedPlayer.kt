package mnstudio.apps.mnstreamx.ui.component.player

import androidx.compose.runtime.Composable
import mnstudio.apps.mnstreamx.domain.model.HomeLanguage
import mnstudio.apps.mnstreamx.ui.component.common.LocalizedResources

@Composable
internal fun LocalizedPlayer(language: HomeLanguage, content: @Composable () -> Unit) =
    LocalizedResources(language, content)