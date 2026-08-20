package com.music.wallpaper.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.music.wallpaper.ui.screens.SettingsScreen
import com.music.wallpaper.ui.theme.GlassTheme
import com.music.wallpaper.ui.viewmodel.SettingsViewModel

class ComposeSettingsActivity : ComponentActivity() {

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val palette by viewModel.currentPalette.collectAsState()
            val accentColor = remember(palette) {
                val colors = palette.allColors
                if (colors.isNotEmpty()) {
                    val c = if (colors.size > 1) colors[1] else colors[0]
                    Color(c)
                } else {
                    Color(0xFF818CF8)
                }
            }

            GlassTheme(accentColor = accentColor) {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { finish() }
                )
            }
        }
    }
}
