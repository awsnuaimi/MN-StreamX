package com.yourapp.iptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.yourapp.iptv.viewmodel.StartupPlaybackViewModel
import com.yourapp.iptv.viewmodel.HomeAppearanceViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.yourapp.iptv.ui.component.common.AppLanguageHost
import com.yourapp.iptv.navigation.AppNavGraph
import com.yourapp.iptv.ui.theme.IPTVAppTheme
import dagger.hilt.android.AndroidEntryPoint
import com.yourapp.iptv.domain.model.BackupMaintenance
import com.yourapp.iptv.domain.model.HomeLanguage
import com.yourapp.iptv.ui.screen.backup.BackupStartupGate
import com.yourapp.iptv.viewmodel.BackupStartupViewModel
import com.yourapp.iptv.viewmodel.ParentalViewModel
import com.yourapp.iptv.ui.screen.parental.ParentalSessionHost
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import android.view.WindowManager

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val startupPlayback: StartupPlaybackViewModel by viewModels()
    private val homeAppearance: HomeAppearanceViewModel by viewModels()
    private val backupStartup: BackupStartupViewModel by viewModels()
    private val parental: ParentalViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val appearance by homeAppearance.appearance.collectAsStateWithLifecycle()
            val maintenance by backupStartup.state.collectAsStateWithLifecycle()
            val protection by parental.access.collectAsState()
            SideEffect {
                if (!protection.ready || protection.configured || protection.storageError)
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            var startupInitialized by remember { mutableStateOf(false) }
            LaunchedEffect(maintenance,protection.ready,protection.storageError) {
                if (maintenance == BackupMaintenance.READY && protection.ready && !protection.storageError && !startupInitialized) {
                    startupInitialized = true
                    startupPlayback.initialize(freshLaunch = savedInstanceState == null)
                }
            }
            IPTVAppTheme(isDarkTheme = appearance?.isDarkTheme ?: true,
                isNightMode = appearance?.isNightMode ?: false,
                theme = appearance?.theme ?: com.yourapp.iptv.domain.model.HomeTheme.DARK_BLUE) {
                AppLanguageHost {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        BackupStartupGate(maintenance, appearance?.language ?: HomeLanguage.ARABIC, backupStartup::retry) {
                            ParentalSessionHost(parental,appearance?.language ?: HomeLanguage.ARABIC) { initial, consumed ->
                                AppNavGraph(startupPlayback,initial,consumed)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onUserInteraction() {
        startupPlayback.cancel()
        super.onUserInteraction()
    }

    override fun onPause() {
        parental.background()
        startupPlayback.cancel()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        parental.foreground()
    }
}