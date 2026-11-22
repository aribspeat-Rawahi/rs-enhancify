package com.aribs.rsenhancify.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aribs.rsenhancify.engine.DocxWriter
import com.aribs.rsenhancify.engine.Ocr
import com.aribs.rsenhancify.engine.TextReflow
import com.aribs.rsenhancify.ui.components.GlassButton
import com.aribs.rsenhancify.ui.components.GlassColors
import com.aribs.rsenhancify.ui.components.GlassOutlineButton
import com.aribs.rsenhancify.ui.components.GlassProgress
import com.aribs.rsenhancify.ui.components.Gap
import com.aribs.rsenhancify.ui.components.MessageCard
import com.aribs.rsenhancify.ui.components.SectionLabel
import com.aribs.rsenhancify.ui.components.VGap
import com.aribs.rsenhancify.util.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

@Composable
fun ImageToTextScreen(onBack: () -> Unit, initial: List<Uri> = emptyList()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val images = remember { mutableStateListOf<Uri>() }
    var text by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var hasResult by remember { mutableStateOf(false) }
    val running = job != null

    fun read(uris: List<Uri>) {
        images.clear(); images.addAll(uris)
        message = null; progress = 0f; hasResult = false
        status = "Reading text…"
        job = scope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    val worker = this
                    Ocr.readImages(
                        context, uris,
                        onProgress = { i, n -> progress = i / n.toFloat(); status = "Reading image ${minOf(i + 1, n)} of $n" },
                        isCancelled = { !worker.isActive }
                    )
                }
                text = result
                hasResult = true
                message = if (result.isBlank()) "No text found. Try a sharper, well-lit photo." to true else null
            } catch (e: CancellationException) {
                message = "Cancelled" to true
            } catch (t: Throwable) {
                message = Files.friendly(t) to true
            } finally {
                job = null
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) read(uris)
    }
    fun pick() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    LaunchedEffect(Unit) { if (initial.isNotEmpty()) read(initial) }

    fun saveWord() {
        val content = text
        scope.launch {
            try {
                val base = images.firstOrNull()?.let { Files.baseName(context, it, "text") } ?: "text"
                val uri = withContext(Dispatchers.IO) {
                    Files.saveDocument(context, "${base}_text_${Files.timestamp()}.docx", DOCX) { os ->
                        DocxWriter(os).use { TextReflow.write(it, content) }
                    }
                }
                message = "Word file saved to Download/Rs Enhancify" to false
                Files.open(context, uri, DOCX)
            } catch (t: Throwable) {
                message = "Save failed: ${Files.friendly(t)}" to true
            }
        }
    }

    ToolScaffold("Image to Text", "Copy text from photos (OCR)", onBack) {
        if (!hasResult && !running) {
            PickCard("Choose photos with text", "Receipts, documents, cards · English & Latin scripts") { pick() }
        }
        if (running) {
            GlassProgress(status, progress, onCancel = { job?.cancel() })
        }
        if (hasResult && !running) {
            SectionLabel("Text from ${images.size} image(s) · you can edit it")
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 460.dp),
                shape = RoundedCornerShape(18.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = Color.White),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0x1FFFFFFF),
                    unfocusedContainerColor = Color(0x14FFFFFF),
                    focusedBorderColor = GlassColors.Cyan,
                    unfocusedBorderColor = Color(0x40FFFFFF),
                    cursorColor = GlassColors.Cyan
                )
            )
            Text(
                "${text.length} characters",
                color = GlassColors.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )
            VGap(12.dp)
            Row {
                GlassButton("Copy", Modifier.weight(1f), Icons.Rounded.ContentCopy, enabled = text.isNotBlank()) {
                    copyToClipboard(context, text)
                }
                Gap()
                GlassOutlineButton("Share", Modifier.weight(1f), Icons.Rounded.Share, enabled = text.isNotBlank()) {
                    shareText(context, text)
                }
            }
            VGap(10.dp)
            Row {
                GlassOutlineButton("Save as Word", Modifier.weight(1f), Icons.Rounded.Description, enabled = text.isNotBlank()) {
                    saveWord()
                }
                Gap()
                GlassOutlineButton("New scan", Modifier.weight(1f), Icons.Rounded.AddPhotoAlternate) { pick() }
            }
        }
        message?.let { (msg, isError) ->
            VGap(12.dp)
            MessageCard(msg, isError)
        }
        if (!hasResult && !running) {
            VGap(12.dp)
            Row(Modifier.padding(start = 4.dp)) {
                androidx.compose.material3.Icon(
                    Icons.Rounded.TextFields, null, tint = GlassColors.TextMuted,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text(
                    "Works offline. Bengali and Arabic text are not supported yet.",
                    color = GlassColors.TextMuted, fontSize = 12.sp
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return
    cm.setPrimaryClip(ClipData.newPlainText("Rs Enhancify text", text))
    // Android 13+ shows its own "Copied" confirmation
    if (android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Share text").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
