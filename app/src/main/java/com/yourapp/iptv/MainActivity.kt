package com.yourapp.iptv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.navigation.AppNavGraph
import com.yourapp.iptv.ui.component.common.AppLanguageHost
import com.yourapp.iptv.ui.component.common.FocusInputWindow
import com.yourapp.iptv.ui.screen.backup.BackupStartupGate
import com.yourapp.iptv.ui.screen.parental.ParentalNavigationStores
import com.yourapp.iptv.ui.screen.parental.ParentalSessionHost
import com.yourapp.iptv.ui.screen.recovery.AppResetHost
import com.yourapp.iptv.ui.theme.IPTVAppTheme
import com.yourapp.iptv.viewmodel.*
import com.yourapp.iptv.util.LoadTimings
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appReset: AppResetViewModel by viewModels()
    private val startupPlayback: StartupPlaybackViewModel by viewModels()
    private val homeAppearance: HomeAppearanceViewModel by viewModels()
    private val backupStartup: BackupStartupViewModel by viewModels()
    private val parental: ParentalViewModel by viewModels()
    private val profiles: ProfilesViewModel by viewModels()
    private val navigation: ParentalNavigationStores by viewModels()
    private var normalSession = false
    private var activityTiming: Long? = null
    private var focusInput: FocusInputWindow? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        focusInput = FocusInputWindow.install(window)
        activityTiming = LoadTimings.beginActivity()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            val systemLanguage = when (LocalConfiguration.current.locales[0].language) {
                "de" -> HomeLanguage.GERMAN;"en" -> HomeLanguage.ENGLISH;else -> HomeLanguage.ARABIC
            }
            var recoveryLanguage by remember { mutableStateOf(systemLanguage) }
            val reset by appReset.state.collectAsState()
            val blocked = reset.blocked
            SideEffect { if (blocked) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
            IPTVAppTheme {
                AppResetHost(appReset,recoveryLanguage,::quarantine) {
                    NormalContent(savedInstanceState) { recoveryLanguage = it }
                }
            }
        }
    }

    @Composable
    private fun NormalContent(savedInstanceState: Bundle?, onLanguage: (HomeLanguage) -> Unit) {
        // These Activity-owned models are deliberately created only AFTER the reset startup gate.
        val appearance by homeAppearance.appearance.collectAsStateWithLifecycle()
        val maintenance by backupStartup.state.collectAsStateWithLifecycle()
        val protection by parental.access.collectAsState()
        val people by profiles.people.collectAsState()
        val contentReady = people.ready && !people.failed && !people.switching && appearance?.profileId == people.catalog.activeId
        SideEffect {
            normalSession = true
            appearance?.language?.let(onLanguage)
            if (appearance == null && protection.storageError) onLanguage(HomeLanguage.ARABIC)
            if (!protection.ready || protection.configured || protection.storageError)
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        LaunchedEffect(Unit) {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) parental.foreground()
        }
        var startupInitialized by remember { mutableStateOf(false) }
        LaunchedEffect(maintenance,protection.ready,protection.storageError) {
            if (maintenance == BackupMaintenance.READY && protection.ready && !protection.storageError && !startupInitialized) {
                startupInitialized = true
                startupPlayback.initialize(freshLaunch = savedInstanceState == null)
            }
        }
        IPTVAppTheme(isDarkTheme = appearance?.isDarkTheme ?: true,isNightMode = appearance?.isNightMode ?: false,
            theme = appearance?.theme ?: HomeTheme.DARK_BLUE) {
            AppLanguageHost(fallbackLanguage = if (protection.storageError) HomeLanguage.ARABIC else null) {
                Surface(Modifier.fillMaxSize(),color = MaterialTheme.colorScheme.background) {
                    BackupStartupGate(maintenance,appearance?.language ?: HomeLanguage.ARABIC,backupStartup::retry,
                        protection.storageFailure) {
                        ParentalSessionHost(parental,appearance?.language ?: HomeLanguage.ARABIC,contentReady) { initial, consumed ->
                            AppNavGraph(startupPlayback,initial,consumed)
                        }
                    }
                }
            }
        }
    }

    private fun quarantine() {
        if (!normalSession) return
        normalSession = false
        parental.cancelExternal();parental.background();startupPlayback.cancel();navigation.clear()
    }
    override fun onUserInteraction() {
        if (normalSession) startupPlayback.cancel()
        super.onUserInteraction()
    }
    override fun onPause() {
        LoadTimings.cancelActivity(activityTiming)
        appReset.cancel()
        if (normalSession) { parental.background();startupPlayback.cancel() }
        super.onPause()
    }
    override fun onResume() {
        super.onResume()
        if (normalSession) parental.foreground()
    }
    override fun onDestroy() {
        focusInput?.close(window)
        focusInput = null
        LoadTimings.cancelActivity(activityTiming)
        super.onDestroy()
    }
}