package com.aribs.rsenhancify.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aribs.rsenhancify.engine.PdfPages
import com.aribs.rsenhancify.engine.PdfToWord
import com.aribs.rsenhancify.engine.WordMode
import com.aribs.rsenhancify.ui.components.GlassButton
import com.aribs.rsenhancify.ui.components.GlassCard
import com.aribs.rsenhancify.ui.components.GlassColors
import com.aribs.rsenhancify.ui.components.GlassOutlineButton
import com.aribs.rsenhancify.ui.components.GlassProgress
import com.aribs.rsenhancify.ui.components.GlassSegmented
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

private const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

@Composable
fun PdfToWordScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pdfUri by remember { mutableStateOf<Uri?>(null) }
    var pdfName by remember { mutableStateOf("") }
    var pageCount by remember { mutableStateOf(0) }
    var mode by remember { mutableStateOf(WordMode.EDITABLE) }
    var ocr by remember { mutableStateOf(true) }
    var job by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf("") }
    var savedUri by remember { mutableStateOf<Uri?>(null) }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val running = job != null

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        savedUri = null; message = null
        scope.launch {
            try {
                val count = withContext(Dispatchers.IO) { PdfPages(context, uri).use { it.pageCount } }
                pdfUri = uri
                pageCount = count
                pdfName = Files.baseName(context, uri, "document")
            } catch (t: Throwable) {
                pdfUri = null
                message = "Could not open PDF: ${Files.friendly(t)}" to true
            }
        }
    }
    val pick = { picker.launch(arrayOf("application/pdf")) }

    fun convert() {
        val uri = pdfUri ?: return
        savedUri = null; message = null; progress = 0f; status = "Opening…"
        job = scope.launch {
            try {
                val (out, stats) = withContext(Dispatchers.Default) {
                    val worker = this
                    var st: PdfToWord.Stats? = null
                    val u = Files.saveDocument(context, "${pdfName}_${Files.timestamp()}.docx", DOCX_MIME) { os ->
                        st = PdfToWord.convert(
                            context, uri, mode, ocr, os,
                            onProgress = { p, s -> progress = p; status = s },
                            isCancelled = { !worker.isActive }
                        )
                    }
                    u to st
                }
                savedUri = out
                val ocrNote = if ((stats?.ocrPages ?: 0) > 0) " · ${stats?.ocrPages} scanned page(s) read with OCR" else ""
                message = "Word file saved to Download/Rs Enhancify$ocrNote" to false
            } catch (e: CancellationException) {
                message = "Cancelled" to true
            } catch (t: Throwable) {
                message = Files.friendly(t) to true
            } finally {
                job = null
            }
        }
    }

    ToolScaffold("PDF to Word", "PDF → editable .docx", onBack) {
        if (pdfUri == null) {
            GlassCard(Modifier.fillMaxWidth().height(200.dp), onClick = pick) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.UploadFile, null, tint = Color.White, modifier = Modifier.size(46.dp))
                        VGap(10.dp)
                        Text("Tap to choose a PDF", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        } else {
            PdfInfoCard(pdfName, pageCount, enabled = !running, onChange = pick)
            VGap(14.dp)
            SectionLabel("Conversion mode")
            GlassSegmented(WordMode.entries.map { it to it.label }, mode, enabled = !running) { mode = it; savedUri = null }
            Text(
                if (mode == WordMode.EDITABLE)
                    "Real text you can edit. Tables and complex layouts become plain paragraphs."
                else
                    "Each page looks exactly like the PDF (as a picture). Not editable.",
                color = GlassColors.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )
            if (mode == WordMode.EDITABLE) {
                VGap(10.dp)
                GlassSwitchRow(
                    "Read scanned pages (OCR)",
                    "Offline text recognition for photo/scan pages · English & Latin scripts",
                    ocr, enabled = !running
                ) { ocr = it; savedUri = null }
            }
            VGap(16.dp)
            val saved = savedUri
            when {
                running -> GlassProgress(status, progress, onCancel = { job?.cancel() })
                saved != null -> Row {
                    GlassButton("Open", Modifier.weight(1f), Icons.Rounded.OpenInNew) { Files.open(context, saved, DOCX_MIME) }
                    Gap()
                    GlassOutlineButton("Share", Modifier.weight(1f), Icons.Rounded.Share) {
                        Files.share(context, listOf(saved), DOCX_MIME)
                    }
                }
                else -> GlassButton("Convert to Word", Modifier.fillMaxWidth(), Icons.Rounded.Description) { convert() }
            }
        }
        message?.let { (text, isError) ->
            VGap(12.dp)
            MessageCard(text, isError)
        }
    }
}
