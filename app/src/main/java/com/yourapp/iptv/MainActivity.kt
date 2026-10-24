package com.yourapp.iptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.yourapp.iptv.viewmodel.StartupPlaybackViewModel
import com.yourapp.iptv.viewmodel.HomeAppearanceViewModel
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.yourapp.iptv.ui.component.common.AppLanguageHost
import com.yourapp.iptv.navigation.AppNavGraph
import com.yourapp.iptv.ui.theme.IPTVAppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val startupPlayback: StartupPlaybackViewModel by viewModels()
    private val homeAppearance: HomeAppearanceViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startupPlayback.initialize(freshLaunch = savedInstanceState == null)
        setContent {
            val appearance by homeAppearance.appearance.collectAsStateWithLifecycle()
            IPTVAppTheme(isDarkTheme = appearance?.isDarkTheme ?: true) {
                AppLanguageHost {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        AppNavGraph(startupPlayback)
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
        startupPlayback.cancel()
        super.onPause()
    }
}