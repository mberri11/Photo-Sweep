package com.simobr.photosweep

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.simobr.photosweep.ui.PhotoSweepRoot
import com.simobr.photosweep.ui.theme.PhotoSweepTheme

/**
 * The only Activity. Photo Sweep is a single-window app; navigation happens in Compose.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Both bars are forced to the dark (light-content) style rather than left to follow
        // the system theme: the app is black in every configuration, so light icons are
        // always the readable choice.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        setContent {
            PhotoSweepTheme {
                PhotoSweepRoot()
            }
        }
    }
}
