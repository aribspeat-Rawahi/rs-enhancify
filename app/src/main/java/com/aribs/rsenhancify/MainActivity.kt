package com.aribs.rsenhancify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.aribs.rsenhancify.ui.components.GlassBackground
import com.aribs.rsenhancify.ui.components.GlassColors
import com.aribs.rsenhancify.ui.screens.EnhanceScreen
import com.aribs.rsenhancify.ui.screens.HomeScreen
import com.aribs.rsenhancify.ui.screens.ImageToPdfScreen
import com.aribs.rsenhancify.ui.screens.PdfToImageScreen
import com.aribs.rsenhancify.ui.screens.PdfToWordScreen

enum class Screen { HOME, ENHANCE, IMAGE_TO_PDF, PDF_TO_IMAGE, PDF_TO_WORD }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@Composable
fun App() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = GlassColors.Violet,
            secondary = GlassColors.Cyan,
            background = GlassColors.BgTop,
            surface = GlassColors.BgBottom
        )
    ) {
        var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
        BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }
        GlassBackground {
            AnimatedContent(
                targetState = screen,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "nav"
            ) { s ->
                val back = { screen = Screen.HOME }
                when (s) {
                    Screen.HOME -> HomeScreen(onOpen = { screen = it })
                    Screen.ENHANCE -> EnhanceScreen(onBack = back)
                    Screen.IMAGE_TO_PDF -> ImageToPdfScreen(onBack = back)
                    Screen.PDF_TO_IMAGE -> PdfToImageScreen(onBack = back)
                    Screen.PDF_TO_WORD -> PdfToWordScreen(onBack = back)
                }
            }
        }
    }
}
