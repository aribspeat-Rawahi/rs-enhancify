package com.aribs.rsenhancify

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import com.aribs.rsenhancify.ui.components.GlassBackground
import com.aribs.rsenhancify.ui.components.GlassColors
import com.aribs.rsenhancify.ui.screens.CompressScreen
import com.aribs.rsenhancify.ui.screens.EnhanceScreen
import com.aribs.rsenhancify.ui.screens.HomeScreen
import com.aribs.rsenhancify.ui.screens.ImageToPdfScreen
import com.aribs.rsenhancify.ui.screens.ImageToTextScreen
import com.aribs.rsenhancify.ui.screens.InputKind
import com.aribs.rsenhancify.ui.screens.PdfToImageScreen
import com.aribs.rsenhancify.ui.screens.PdfToWordScreen
import com.aribs.rsenhancify.ui.screens.PdfToolsScreen
import com.aribs.rsenhancify.ui.screens.ShareChooser
import com.aribs.rsenhancify.ui.screens.SharedFiles
import com.aribs.rsenhancify.ui.screens.Tool
import com.aribs.rsenhancify.update.AppUpdatePrompt

class MainActivity : ComponentActivity() {

    /** Files shared/opened from another app, waiting for the user to pick a tool. */
    private var shared by mutableStateOf<SharedFiles?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIncoming(intent)
        setContent { App(shared = shared, onSharedHandled = { shared = null }) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    private fun handleIncoming(intent: Intent?) {
        intent ?: return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND ->
                listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            else -> return
        }.ifEmpty {
            val clip = intent.clipData
            if (clip == null) emptyList() else (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
        }

        val images = mutableListOf<Uri>()
        val pdfs = mutableListOf<Uri>()
        for (u in uris.distinct()) {
            val mime = runCatching { contentResolver.getType(u) }.getOrNull() ?: intent.type.orEmpty()
            when {
                mime == "application/pdf" || u.lastPathSegment?.endsWith(".pdf", ignoreCase = true) == true -> pdfs += u
                mime.startsWith("image/") -> images += u
            }
        }
        shared = when {
            images.isEmpty() && pdfs.isEmpty() -> {
                if (uris.isNotEmpty()) Toast.makeText(this, "Only photos and PDF files are supported", Toast.LENGTH_LONG).show()
                null
            }
            images.size >= pdfs.size -> SharedFiles(images, InputKind.IMAGE)
            else -> SharedFiles(pdfs, InputKind.PDF)
        }
    }
}

@Composable
fun App(shared: SharedFiles?, onSharedHandled: () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = GlassColors.Violet,
            secondary = GlassColors.Cyan,
            background = GlassColors.BgTop,
            surface = GlassColors.BgBottom
        )
    ) {
        // null = Home
        var current by rememberSaveable { mutableStateOf<Tool?>(null) }
        var input by remember { mutableStateOf<List<Uri>>(emptyList()) }
        var inputVersion by remember { mutableIntStateOf(0) }

        fun open(tool: Tool, files: List<Uri>) {
            input = files
            inputVersion++
            current = tool
        }
        val back = { current = null; input = emptyList() }

        BackHandler(enabled = current != null) { back() }
        GlassBackground {
            // Each (tool, inputVersion) pair is its own screen instance, so a new share
            // restarts the tool with the new files while the old one fades out untouched.
            AnimatedContent(
                targetState = current to inputVersion,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "nav"
            ) { (tool, version) ->
                val files = if (version == inputVersion) input else emptyList()
                when (tool) {
                    null -> HomeScreen(onOpen = { open(it, emptyList()) })
                    Tool.ENHANCE -> EnhanceScreen(onBack = back, initial = files)
                    Tool.COMPRESS -> CompressScreen(onBack = back, initial = files)
                    Tool.IMAGE_TO_TEXT -> ImageToTextScreen(onBack = back, initial = files)
                    Tool.IMAGE_TO_PDF -> ImageToPdfScreen(onBack = back, initial = files)
                    Tool.PDF_TO_IMAGE -> PdfToImageScreen(onBack = back, initial = files)
                    Tool.PDF_TO_WORD -> PdfToWordScreen(onBack = back, initial = files)
                    Tool.PDF_TOOLS -> PdfToolsScreen(onBack = back, initial = files)
                }
            }

            if (shared != null) {
                ShareChooser(
                    shared = shared,
                    onPick = { tool ->
                        open(tool, shared.uris.take(tool.maxShared))
                        onSharedHandled()
                    },
                    onDismiss = onSharedHandled
                )
            }

            // Silent background update check (launch / back online / resume); prompts only if a newer version exists
            AppUpdatePrompt()
        }
    }
}
