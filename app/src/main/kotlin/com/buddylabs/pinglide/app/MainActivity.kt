package com.buddylabs.pinglide.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.buddylabs.pinglide.data.storage.SetupPreferences
import com.buddylabs.pinglide.ui.components.PinGlideTheme
import com.buddylabs.pinglide.ui.navigation.SetupNavigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val progress = SetupPreferences(applicationContext)
        setContent {
            PinGlideTheme {
                SetupNavigation(progress = progress, onBackground = { moveTaskToBack(true) })
            }
        }
    }
}
