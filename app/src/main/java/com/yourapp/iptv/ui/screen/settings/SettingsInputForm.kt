package com.yourapp.iptv.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.AppSettings
import com.yourapp.iptv.domain.model.PlaylistSource

@Composable
fun SettingsInputForm(
    settings: AppSettings,
    enabled: Boolean,
    onSourceChanged: (PlaylistSource) -> Unit,
    onM3uChanged: (String) -> Unit,
    onServerChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onEpgChanged: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("مصدر القنوات")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(selected = settings.source == PlaylistSource.M3U,
                onClick = { onSourceChanged(PlaylistSource.M3U) }, enabled = enabled, label = { Text("M3U") })
            FilterChip(selected = settings.source == PlaylistSource.XTREAM,
                onClick = { onSourceChanged(PlaylistSource.XTREAM) }, enabled = enabled, label = { Text("Xtream Codes") })
        }
        if (settings.source == PlaylistSource.M3U) {
            SettingsField("رابط قائمة القنوات M3U", settings.m3uUrl, enabled, onM3uChanged,
                placeholder = "https://example.com/playlist.m3u", uri = true)
        } else {
            SettingsField("سيرفر Xtream", settings.xtreamServer, enabled, onServerChanged,
                placeholder = "https://example.com:8080", uri = true)
            SettingsField("اسم مستخدم Xtream", settings.xtreamUsername, enabled, onUsernameChanged)
            SettingsField("كلمة مرور Xtream", settings.xtreamPassword, enabled, onPasswordChanged, password = true)
            Text("سيتم التحقق من الحساب وتحميل القنوات والتصنيفات عند الحفظ.")
        }
        SettingsField("رابط EPG (اختياري)", settings.epgUrl, enabled, onEpgChanged, uri = true)
    }
}

@Composable
private fun SettingsField(
    label: String,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    uri: Boolean = false,
    password: Boolean = false
) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = when { password -> KeyboardType.Password; uri -> KeyboardType.Uri; else -> KeyboardType.Text },
            imeAction = ImeAction.Next
        ),
        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) })
    )
}