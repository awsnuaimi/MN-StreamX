package com.yourapp.iptv.ui.screen.parental

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.parental.*
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.viewmodel.ParentalViewModel

@Composable
fun ParentalSettingsScreen(onBack: () -> Unit,
    model: ParentalViewModel = requireNotNull(LocalParentalSession.current.model)) {
    val access by model.access.collectAsState()
    val editor by model.editor.collectAsState()
    val labels = ParentalLabels(LocalParentalSession.current.language)
    BackHandler(onBack = onBack)
    key(access.epoch,editor.sourceKey) { ParentalSettingsForm(model,access,editor,labels,onBack) }
}

@Composable
private fun ParentalSettingsForm(model: ParentalViewModel, access: ParentalState,
    editor: com.yourapp.iptv.viewmodel.ParentalEditorState, labels: ParentalLabels, onBack: () -> Unit) {
    var sections by remember { mutableStateOf(access.rules.sections) }
    var groups by remember { mutableStateOf(access.rules.groups[editor.sourceKey].orEmpty()) }
    var otherGroups by remember { mutableStateOf(access.rules.groups.filterKeys { it != editor.sourceKey }) }
    var query by remember { mutableStateOf("") }
    val workflow = remember { PinSetupWorkflow() }
    var mode by remember { mutableStateOf<PinSetupMode?>(null) }
    var mismatch by remember { mutableStateOf(false) }
    DisposableEffect(workflow) { onDispose { workflow.clear() } }
    LaunchedEffect(access.configured,access.unlocked) {
        if (access.configured && access.unlocked) model.loadGroups()
    }
    Column(Modifier.fillMaxSize().padding(18.dp).testTag("parental-settings"),verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppButton(labels.back(),onBack)
            Text(labels.title(),style = MaterialTheme.typography.headlineSmall)
        }
        LazyColumn(Modifier.weight(1f).testTag("parental-settings-list"),verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(labels.text("الرمز من 6 أرقام. يُعاد القفل عند مغادرة التطبيق. احتفظ بالرمز؛ لا يوجد رمز رئيسي لاسترجاعه.",
                    "Use 6 digits. Protection locks when leaving the app. Keep your PIN; there is no master recovery PIN.",
                    "6 Ziffern verwenden. Beim Verlassen der App wird gesperrt. PIN merken; es gibt keine Master-PIN."))
            }
            if (!access.configured) {
                item { AppButton(labels.newPin(),{ workflow.clear(); mode = PinSetupMode.CREATE_NEW },enabled = !editor.busy) }
            } else {
                item { Text(labels.text("الأقسام المحمية","Protected sections","Geschützte Bereiche"),style = MaterialTheme.typography.titleMedium) }
                items(ParentalSection.entries.toList(),key = { it.name }) { section ->
                    AppButton((if (section in sections) "☑ " else "☐ ") + labels.section(section),{
                        sections = if (section in sections) sections - section else sections + section
                    },enabled = !editor.busy)
                }
                item { Text(labels.text("مجموعات القنوات الأصلية — للاشتراك الحالي فقط","Original channel groups — current subscription only",
                    "Originale Sendergruppen — nur für das aktuelle Abo"),style = MaterialTheme.typography.titleMedium) }
                item { Text(labels.text("تُحجب المجموعة في القنوات والبحث والمفضلة والسجل والدليل. إدارة الاشتراكات والنسخ الاحتياطي والتنظيم تتطلب فتح PIN دائمًا.",
                    "Groups are hidden from channels, search, favorites, history and guide. Subscription, backup and organization management always require PIN access.",
                    "Gruppen werden in Sendern, Suche, Favoriten, Verlauf und Programm ausgeblendet. Abo-, Sicherungs- und Ordnerverwaltung benötigen immer PIN-Zugriff.")) }
                if (editor.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (editor.groupError) item { AppButton(labels.text("إعادة قراءة المجموعات","Reload groups","Gruppen erneut laden"),model::loadGroups) }
                item { OutlinedTextField(query,{ query = it.take(100) },singleLine = true,
                    label = { Text(labels.text("بحث في المجموعات","Find groups","Gruppen suchen")) },modifier = Modifier.fillMaxWidth()) }
                val selected = groups
                val names = (editor.groups + selected).distinct().filter { query.isBlank() || it.contains(query,ignoreCase = true) }
                if (!editor.loading && editor.groups.isEmpty()) item {
                    Text(labels.text("حمّل القنوات أولًا لرؤية مجموعاتها. يمكن حفظ قفل الأقسام الآن.",
                        "Load channels first to list groups. Section locks can be saved now.",
                        "Sender zuerst laden, um Gruppen anzuzeigen. Bereichssperren können jetzt gespeichert werden."))
                }
                items(names,key = { "group:$it" }) { name ->
                    val allowed = name.length <= 512
                    AppButton((if (name in selected) "☑ " else "☐ ") + name.ifBlank { labels.text("بدون مجموعة","Ungrouped","Ohne Gruppe") },{
                        if (name in selected) groups = groups - name
                        else if (otherGroups.values.sumOf { it.size } + groups.size < 512)
                            groups = groups + name
                    },enabled = !editor.busy && allowed)
                }
                item { Text(labels.text("${groups.size} مجموعة مقفولة من حد إجمالي 512","${groups.size} locked groups; total limit 512",
                    "${groups.size} gesperrte Gruppen; Gesamtlimit 512")) }
                if (otherGroups.isNotEmpty()) item {
                    AppButton(labels.text("إزالة قفل مجموعات الاشتراكات الأخرى عند الحفظ",
                        "Remove other subscriptions' group locks on save","Gruppensperren anderer Abos beim Speichern entfernen"),{
                        otherGroups = emptyMap()
                    },enabled = !editor.busy)
                }
                item { AppButton(labels.text("حفظ وقفل الآن","Save and lock now","Speichern und jetzt sperren"),{
                    val key = editor.sourceKey ?: return@AppButton
                    val saved = otherGroups.toMutableMap().apply { if (groups.isNotEmpty()) put(key,groups) }
                    model.saveRules(ParentalRules(sections,saved))
                },enabled = !editor.busy && editor.sourceKey != null) }
                item { AppButton(labels.text("تغيير PIN","Change PIN","PIN ändern"),{
                    workflow.clear(); mode = PinSetupMode.CHANGE_OLD
                },enabled = !editor.busy) }
                item { AppButton(labels.text("إيقاف الحماية — يتطلب PIN","Disable protection — PIN required","Schutz deaktivieren — PIN erforderlich"),{
                    workflow.clear(); mode = PinSetupMode.DISABLE
                },enabled = !editor.busy) }
            }
            if (editor.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            val message = labels.message(editor.result)
            if (message.isNotEmpty()) item { Text(message,color = MaterialTheme.colorScheme.error) }
        }
    }
    mode?.let { current ->
        val title = when (current) {
            PinSetupMode.CREATE_NEW,PinSetupMode.CHANGE_NEW -> labels.newPin()
            PinSetupMode.CREATE_REPEAT,PinSetupMode.CHANGE_REPEAT -> labels.repeat()
            else -> labels.pin()
        }
        key(current) { PinEntryDialog(title,labels,editor.busy,if (mismatch) labels.mismatch() else "",{
            workflow.clear(); mode = null; mismatch = false
        },{ input ->
            mismatch = false
            when (current) {
                PinSetupMode.CREATE_NEW,PinSetupMode.CHANGE_NEW -> {
                    workflow.setNew(input)
                    mode = if (current == PinSetupMode.CREATE_NEW) PinSetupMode.CREATE_REPEAT else PinSetupMode.CHANGE_REPEAT
                }
                PinSetupMode.CHANGE_OLD -> { workflow.setOld(input); mode = PinSetupMode.CHANGE_NEW }
                PinSetupMode.DISABLE -> { model.changePin(input,null); mode = null }
                PinSetupMode.CREATE_REPEAT,PinSetupMode.CHANGE_REPEAT -> {
                    if (workflow.matches(input)) {
                        val fresh = workflow.takeNew()
                        if (current == PinSetupMode.CREATE_REPEAT) model.create(fresh)
                        else model.changePin(workflow.takeOld(),fresh)
                        workflow.clear(); mode = null
                    } else {
                        workflow.clearNew(); mismatch = true
                        mode = if (current == PinSetupMode.CREATE_REPEAT) PinSetupMode.CREATE_NEW else PinSetupMode.CHANGE_NEW
                    }
                }
            }
        }) }
    }
}