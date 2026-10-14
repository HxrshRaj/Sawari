package com.hxrsh.sawari

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.hxrsh.sawari.ui.MainViewModel
import com.hxrsh.sawari.ui.SawariRoot
import com.hxrsh.sawari.ui.SessionState
import com.hxrsh.sawari.ui.theme.SawariTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { mainViewModel.sessionState.value is SessionState.Loading }
        enableEdgeToEdge()
        setContent {
            SawariTheme { SawariRoot(mainViewModel) }
        }
    }
}
