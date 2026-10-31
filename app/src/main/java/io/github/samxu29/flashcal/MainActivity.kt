package io.github.samxu29.flashcal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.github.samxu29.flashcal.ui.FlashCalLaunchHost
import io.github.samxu29.flashcal.ui.theme.FlashCalTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FlashCalTheme {
                FlashCalLaunchHost()
            }
        }
    }
}
