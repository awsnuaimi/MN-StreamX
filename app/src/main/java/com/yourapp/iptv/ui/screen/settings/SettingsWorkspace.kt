package com.yourapp.iptv.ui.screen.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.yourapp.iptv.domain.model.HomeLanguage
import com.yourapp.iptv.ui.component.common.*
import androidx.compose.ui.input.key.*

/** UI navigation only. Draft persistence and protected destinations stay with their existing owners. */
@Composable
internal fun SettingsWorkspace(labels: SettingsLabels, profileName: String, dirty: Boolean, saving: Boolean,
    saved: Boolean, error: Boolean, canSave: Boolean, onSave: () -> Unit, onReset: () -> Unit, onDiscard: () -> Unit,
    onAction: (SettingsAction) -> Unit, focusScope: String = profileName,
    content: @Composable ColumnScope.(SettingsCategory, FocusRequester, (SettingsAction) -> Unit) -> Unit) {
    val focus = rememberContextFocus("settings:$focusScope")
    var categoryName by rememberSaveable { mutableStateOf(SettingsCategory.APPEARANCE.name) }
    val selected = SettingsCategory.entries.firstOrNull { it.name == categoryName } ?: SettingsCategory.APPEARANCE
    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var detailOpen by rememberSaveable { mutableStateOf(true) }
    var pending by remember { mutableStateOf<SettingsIntent?>(null) }
    var waitForSave by remember { mutableStateOf(false) }
    var contentFocused by remember { mutableStateOf(false) }
    var initializedLayout by remember(focus) { mutableStateOf<Pair<Boolean, Boolean>?>(null) }
    val rail = remember { SettingsCategory.entries.associateWith { FocusRequester() } }
    val first = remember { FocusRequester() };val search = remember { FocusRequester() }
    val detailEntry = remember(selected) { FocusRequester() }
    val detailContext = "detail:${selected.name}"
    val contexts = setOf("rail", "tools", detailContext)
    focus.Resume(contexts, "rail", selected.name, initial = false)
    val toolKeys = listOf("search", "back") + (if (searchOpen) listOf("query") else emptyList()) +
        if (selected == SettingsCategory.APPEARANCE && canSave && !saving) listOf("save", "reset") else emptyList()
    val needsEditor = (focus.pending?.preferred ?: focus.bookmarks.get("tools")?.key) in listOf("save", "reset")
    focus.Restore("tools", toolKeys, ready = !saving && (!needsEditor || canSave))
    focus.Restore("rail", SettingsCategory.entries.map { it.name }, ready = !saving)
    val saveFocus = remember { FocusRequester() }
    val searchButton = remember { FocusRequester() }
    val holder = rememberSaveableStateHolder()
    fun execute(intent: SettingsIntent) {
        pending = null;waitForSave = false
        when (intent) {
            is SettingsIntent.Category -> { categoryName = intent.value.name;detailOpen = true;query = "";searchOpen = false;focus.request("detail:${intent.value.name}") }
            is SettingsIntent.Action -> onAction(intent.value)
        }
    }
    fun request(intent: SettingsIntent) {
        if (saving) return
        if (intent is SettingsIntent.Category && intent.value == selected) { detailOpen = true;query = "";searchOpen = false;focus.request(detailContext);return }
        if (intent is SettingsIntent.Category) focus.record("rail", intent.value.name, intent.value.ordinal)
        if (dirty) { focus.cancel(); pending = intent } else execute(intent)
    }
    val action: (SettingsAction) -> Unit = { request(SettingsIntent.Action(it)) }
    LaunchedEffect(saved, waitForSave, saving, error) {
        if (waitForSave && !saving) {
            if (saved) pending?.let(::execute) else if (error) waitForSave = false
        }
    }
    val direction = if (labels.language == HomeLanguage.ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        BoxWithConstraints(Modifier.fillMaxSize().then(focus.inputModifier()).background(MaterialTheme.colorScheme.background).testTag("settings-workspace")) {
            val wide = maxWidth.value / LocalDensity.current.fontScale.coerceAtLeast(1f) >= 620f
            val railWidth = (maxWidth * 0.29f).coerceIn(220.dp, 330.dp)
            fun returnToRail() {
                if (!wide) detailOpen = false
                else runCatching { rail.getValue(selected).requestFocus() }
            }
            BackHandler {
                when {
                    saving || pending != null -> Unit
                    searchOpen -> { searchOpen = false;query = "";runCatching { searchButton.requestFocus() } }
                    contentFocused || (!wide && detailOpen) -> returnToRail()
                    else -> action(SettingsAction.BACK)
                }
            }
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(labels.text("الإعدادات", "Settings", "Einstellungen"), style = MaterialTheme.typography.headlineSmall)
                        Text(labels.text("خصّص تجربتك", "Make it yours", "Dein Erlebnis gestalten"), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (dirty) Text(labels.text("تغييرات غير محفوظة", "Unsaved changes", "Nicht gespeichert"),
                        color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.testTag("settings-dirty"))
                    TextButton(onClick = { searchOpen = !searchOpen;if (!searchOpen) query = "" }, enabled = !saving,
                        modifier = Modifier.tvFocusIndicator(MaterialTheme.shapes.medium).height(36.dp).focusRequester(searchButton).then(focus.target("tools", "search", 0)).testTag("settings-search-toggle")) {
                        Text(labels.text("بحث", "Search", "Suchen"))
                    }
                    TextButton(onClick = { action(SettingsAction.BACK) }, enabled = !saving,
                        modifier = Modifier.tvFocusIndicator(MaterialTheme.shapes.medium).height(36.dp).then(focus.target("tools", "back", 1)).testTag("settings-back")) { Text(labels.back()) }
                    if (wide) Text("MN StreamX", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (searchOpen) {
                    OutlinedTextField(query, { focus.cancel(); query = it.take(80) }, singleLine = true,
                        label = { Text(labels.text("البحث في الإعدادات", "Search settings", "Einstellungen durchsuchen")) },
                        modifier = Modifier.fillMaxWidth().focusRequester(search).then(focus.target("tools", "query", 4)).testTag("settings-search"))
                    LaunchedEffect(Unit) { focus.request("tools", "query") }
                }
                val matches = SettingsCatalog.search(query, labels)
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (wide || !detailOpen || searchOpen) Surface(
                        modifier = (if (wide) Modifier.width(railWidth) else Modifier.weight(1f))
                            .fillMaxHeight().testTag("settings-rail"), shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.padding(8.dp).verticalScroll(rememberScrollState()).focusGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (profileName.isNotBlank()) Text(labels.text("الملف: ", "Profile: ", "Profil: ") + profileName,
                                Modifier.padding(8.dp), style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (matches.isEmpty()) Text(labels.text("لا توجد نتائج", "No results", "Keine Ergebnisse"), Modifier.padding(12.dp))
                            matches.forEach { category ->
                                key(category) { SettingsCategoryButton(category, selected == category, labels, rail.getValue(category),
                                    if (wide) detailEntry else null, enabled = !saving,
                                    focusModifier = focus.target("rail", category.name, category.ordinal),
                                    onEnter = { focus.request(detailContext) },
                                    onClick = { request(SettingsIntent.Category(category)) }) }
                            }
                        }
                    }
                    if (wide || (detailOpen && !searchOpen)) Surface(Modifier.weight(1f).fillMaxHeight()
                        .onFocusChanged { contentFocused = it.hasFocus }.focusGroup().testTag("settings-detail"),
                        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(labels.title(selected), Modifier.weight(1f).testTag("settings-detail-title"), style = MaterialTheme.typography.titleLarge)
                                Text(labels.scope(selected), Modifier.widthIn(max = 200.dp), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                            Text(labels.description(selected), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            var detailMounted by remember(focus, detailContext) { mutableStateOf(false) }
                            LaunchedEffect(focus, detailContext) { withFrameNanos { }; detailMounted = true }
                            val detailReady = detailMounted && !saving && (selected != SettingsCategory.APPEARANCE || canSave)
                            focus.Restore(detailContext, focus.registered(detailContext), ready = detailReady,
                                onEmpty = { runCatching { detailEntry.requestFocus() } })
                            focus.Maintain(detailContext, focus.registered(detailContext), ready = detailReady)
                            holder.SaveableStateProvider(selected.name) {
                                CompositionLocalProvider(LocalSettingsFocus provides SettingsFocusBinding(focus, detailContext)) {
                                Column(Modifier.weight(1f).fillMaxWidth()
                                    .rememberedFocusGroup(detailEntry, first, scrollable = true) { direction ->
                                        val towardRail = if (labels.language == HomeLanguage.ARABIC) FocusDirection.Right else FocusDirection.Left
                                        if (wide && direction == towardRail) rail.getValue(selected) else FocusRequester.Default
                                    }.verticalScroll(rememberScrollState())
                                    .testTag("settings-options"),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)) { content(selected, first, action) }
                                }
                            }
                            if (selected == SettingsCategory.APPEARANCE) {
                                if (saved && !dirty) Text(labels.text("تم الحفظ", "Saved", "Gespeichert"),
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.testTag("settings-saved"))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SettingsFooterButton(if (saving) labels.text("جارٍ الحفظ…", "Saving…", "Speichern…") else labels.save(),
                                        { focus.record("tools", "save", 2);focus.request("tools", "save");onSave() }, enabled = canSave && !saving,
                                        modifier = Modifier.weight(1f).focusRequester(saveFocus).then(focus.target("tools", "save", 2)).testTag("settings-save"), primary = true)
                                    SettingsFooterButton(labels.text("استعادة الافتراضي", "Restore defaults", "Standardwerte"), onReset,
                                        enabled = canSave && !saving, modifier = Modifier.weight(1f).then(focus.target("tools", "reset", 3)).testTag("settings-reset"))
                                }
                            }
                        }
                    }
                }
                Text("OK · " + labels.text("اختيار", "Select", "Auswählen") + "     ↩ · " + labels.back(),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LaunchedEffect(focus, wide, detailOpen, canSave) {
                val layout = wide to detailOpen
                if (initializedLayout == layout || (!wide && detailOpen && !canSave)) return@LaunchedEffect
                runCatching {
                    if (initializedLayout == null) focus.restore(contexts, if (wide || !detailOpen) "rail" else detailContext, selected.name.takeIf { wide || !detailOpen })
                    else focus.request(if (wide || !detailOpen) "rail" else detailContext, selected.name.takeIf { wide || !detailOpen })
                }.onSuccess { initializedLayout = layout }
            }
        }
    }
    pending?.let { intent ->
        SettingsExitPrompt(labels, saving, error,
            onSave = { waitForSave = true;onSave() },
            onDiscard = { onDiscard();execute(intent) },
            onStay = { pending = null;waitForSave = false;focus.restore(contexts, "rail", selected.name) })
    }
}

@Composable
private fun SettingsCategoryButton(category: SettingsCategory, selected: Boolean, labels: SettingsLabels,
    requester: FocusRequester, content: FocusRequester?, enabled: Boolean, focusModifier: Modifier = Modifier, onEnter: () -> Unit = {}, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, enabled = enabled,
        modifier = Modifier.then(focusModifier).tvFocusIndicator(MaterialTheme.shapes.medium).fillMaxWidth().heightIn(min = 42.dp).focusRequester(requester)
            .onPreviewKeyEvent { event ->
                if (content == null || event.key != if (rtl) Key.DirectionLeft else Key.DirectionRight) false
                else { if (event.type == KeyEventType.KeyDown) onEnter();true }
            }
            .focusProperties { if (content != null) { if (rtl) left = content else right = content } }
            .onFocusChanged { focused = it.isFocused }.testTag("settings-category-${category.name}")
            .semantics { this.selected = selected;contentDescription = labels.title(category) },
        color = when { focused -> colors.primaryContainer; selected -> colors.secondaryContainer; else -> colors.surfaceContainerLow },
        contentColor = colors.onSurface, shape = MaterialTheme.shapes.medium,
        border = BorderStroke(if (focused) 2.dp else 1.dp, if (focused) colors.primary else if (selected) colors.primary.copy(alpha = 0.5f) else colors.surfaceContainerLow)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsIcon(category, if (selected || focused) colors.primary else colors.onSurfaceVariant)
            Text(labels.title(category), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (rtl) "‹" else "›", color = colors.onSurfaceVariant)
        }
    }
}