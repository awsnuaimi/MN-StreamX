package com.yourapp.iptv.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourapp.iptv.ui.theme.NeonBlue
import com.yourapp.iptv.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onSettingsSaved: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    // عند حفظ الإعدادات بنجاح
    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onSettingsSaved()
            viewModel.resetSaveStatus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "إعدادات التطبيق",
            fontSize = 24.sp,
            color = NeonBlue
        )

        // قسم M3U
        OutlinedTextField(
            value = uiState.m3uUrl,
            onValueChange = { viewModel.updateM3uUrl(it) },
            label = { Text("رابط M3U (اختياري)") },
            placeholder = { Text("https://example.com/playlist.m3u") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // قسم Xtream
        OutlinedTextField(
            value = uiState.xtreamServer,
            onValueChange = { viewModel.updateXtreamServer(it) },
            label = { Text("سيرفر Xtream (اختياري)") },
            placeholder = { Text("http://xtream-server.com") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = uiState.xtreamUsername,
            onValueChange = { viewModel.updateXtreamUsername(it) },
            label = { Text("اسم المستخدم Xtream") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = uiState.xtreamPassword,
            onValueChange = { viewModel.updateXtreamPassword(it) },
            label = { Text("كلمة المرور Xtream") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
        )

        // قسم EPG
        OutlinedTextField(
            value = uiState.epgUrl,
            onValueChange = { viewModel.updateEpgUrl(it) },
            label = { Text("رابط EPG (اختياري)") },
            placeholder = { Text("https://example.com/epg.xml") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // رسالة الخطأ
        // رسالة الخطأ
        val errorMessage = uiState.errorMessage
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp
            )
        }

        // زر الحفظ
        Button(
            onClick = { viewModel.saveSettings() },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonBlue,
                contentColor = androidx.compose.ui.graphics.Color.White
            )
        ) {
            Text("حفظ الإعدادات")
        }

        // مؤشر التحميل (إذا أردت إضافته لاحقاً)
        // if (uiState.isSaving) CircularProgressIndicator()
    }
}