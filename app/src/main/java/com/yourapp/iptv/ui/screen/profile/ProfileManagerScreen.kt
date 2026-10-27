package com.yourapp.iptv.ui.screen.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yourapp.iptv.domain.profile.*
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.screen.parental.LocalParentalSession
import com.yourapp.iptv.viewmodel.ProfilesViewModel

@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun ProfileManagerScreen(onBack: () -> Unit, onParental: () -> Unit, model: ProfilesViewModel = hiltViewModel()) {
    val people by model.people.collectAsState();val operation by model.operation.collectAsState()
    val session = LocalParentalSession.current;val labels = ProfileLabels(session.language)
    var rename by remember { mutableStateOf<String?>(null) };var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(ProfileKind.ADULT) };var remove by remember { mutableStateOf<UserProfile?>(null) }
    val initialFocus = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current;val window = LocalWindowInfo.current
    LaunchedEffect(window.isWindowFocused) { if (window.isWindowFocused) {
        inputMode.requestInputMode(InputMode.Keyboard);withFrameNanos { };initialFocus.requestFocus()
    } }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().padding(18.dp).testTag("profile-manager"),verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppButton(labels.back(),onBack,Modifier.focusRequester(initialFocus));Text(labels.manage(),style = MaterialTheme.typography.headlineSmall)
        }
        LazyColumn(Modifier.weight(1f),verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text(labels.text("ملف الأطفال يبدأ مقفولًا. اختره ثم افتح PIN واضبط الأقسام والمجموعات من الحماية الأبوية.",
                "Child profiles start locked. Select one, unlock PIN, then choose its allowed sections and groups in parental controls.",
                "Kinderprofile starten gesperrt. Profil wählen, PIN entsperren und erlaubte Bereiche und Gruppen im Jugendschutz festlegen.")) }
            item { OutlinedTextField(name,{ name = it.take(32) },singleLine = true,
                label = { Text(labels.text("اسم الملف","Profile name","Profilname")) },modifier = Modifier.fillMaxWidth()) }
            if (rename == null) item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ProfileKind.entries.forEach { value ->
                    AppButton((if (kind == value) "✓ " else "") + labels.kind(value),{ kind = value },enabled = !operation.busy)
                } }
            }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(if (rename == null) labels.text("إنشاء ملف","Create profile","Profil erstellen") else labels.text("حفظ الاسم","Save name","Namen speichern"),{
                    val id = rename;if (id == null) model.create(name,kind) else model.rename(id,name)
                },enabled = !operation.busy && name.isNotBlank())
                if (rename != null) AppButton(labels.text("إلغاء","Cancel","Abbrechen"),{ rename = null;name = "" })
            } }
            item { AppButton(labels.text("إعداد PIN وقيود الملف النشط","PIN and active profile restrictions","PIN und Einschränkungen des aktiven Profils"),onParental) }
            if (operation.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (labels.message(operation.result).isNotEmpty()) item { Text(labels.message(operation.result),color = MaterialTheme.colorScheme.error) }
            if (operation.result == ProfileResult.OK) item { Text(labels.text("تم الحفظ.","Saved.","Gespeichert.")) }
            items(people.catalog.entries,key = { it.id }) { row ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(labels.name(row) + " • " + labels.kind(row.kind) + if (row.id == people.catalog.activeId) " ✓" else "")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppButton(labels.text("تغيير الاسم","Rename","Umbenennen"),{ rename = row.id;name = row.name;model.dismiss() },enabled = !operation.busy)
                        if (row.id != UserProfiles.DEFAULT_ID && row.id != people.catalog.activeId)
                            AppButton(labels.text("حذف","Delete","Löschen"),{ remove = row },enabled = !operation.busy)
                    }
                }
            }
        }
    }
    remove?.let { row -> AlertDialog(onDismissRequest = { remove = null },
        title = { Text(labels.text("حذف الملف؟","Delete profile?","Profil löschen?")) },
        text = { Text(labels.text("سيُحذف سجل ومفضلة ${labels.name(row)} من هذا الجهاز.",
            "${labels.name(row)} and its personal data will be removed from this device.",
            "${labels.name(row)} und die persönlichen Daten werden von diesem Gerät gelöscht.")) },
        confirmButton = { AppButton(labels.text("حذف","Delete","Löschen"),{ model.remove(row.id);remove = null }) },
        dismissButton = { AppButton(labels.text("إلغاء","Cancel","Abbrechen"),{ remove = null }) }) }
}