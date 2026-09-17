package com.neoplayer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neoplayer.app.ui.MainViewModel
import com.neoplayer.app.ui.NeoPlayerEnhancedApp
import com.neoplayer.app.ui.NeoPlusViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            NeoPlayerEnhancedApp(
                mainViewModel = viewModel<MainViewModel>(),
                plusViewModel = viewModel<NeoPlusViewModel>()
            )
        }
    }
}
