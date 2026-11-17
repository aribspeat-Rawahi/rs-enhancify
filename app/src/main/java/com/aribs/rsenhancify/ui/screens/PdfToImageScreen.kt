package com.aribs.rsenhancify.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aribs.rsenhancify.engine.PageRange
import com.aribs.rsenhancify.engine.PdfPages
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
import com.aribs.rsenhancify.ui.components.glass
import com.aribs.rsenhancify.util.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Dpi(val label: String, val dpi: Int) { LOW("Small · 100", 100), MED("Good · 150", 150), HIGH("Sharp · 300", 300) }

@Composable
fun PdfToImageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pdfUri by remember { mutableStateOf<Uri?>(null) }
    var pdfName by remember { mutableStateOf("") }
    var pageCount by remember { mutableStateOf(0) }
    var range by remember { mutableStateOf("") }
    var dpi by remember { mutableStateOf(Dpi.MED) }
    var job by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf("") }
    val saved = remember { mutableStateListOf<Pair<Uri, Bitmap>>() }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val running = job != null

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        saved.clear(); message = null; range = ""
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

    fun convert() {
        val uri = pdfUri ?: return
        val pages = try {
            PageRange.parse(range, pageCount)
        } catch (e: IllegalArgumentException) {
            message = (e.message ?: "Invalid page range") to true
            return
        }
        saved.clear(); message = null; progress = 0f
        job = scope.launch {
            try {
                withContext(Dispatchers.Default) {
                    PdfPages(context, uri).use { pdf ->
                        val stamp = Files.timestamp()
                        pages.forEachIndexed { i, page ->
                            ensureActive()
                            status = "Page ${page + 1} (${i + 1} of ${pages.size})"
                            val bmp = pdf.render(page, dpi.dpi)
                            try {
                                val name = "${pdfName}_p${(page + 1).toString().padStart(3, '0')}_$stamp"
                                val u = Files.saveImage(context, bmp, name, Bitmap.CompressFormat.JPEG, 92)
                                val thumb = Bitmap.createScaledBitmap(
                                    bmp, 240, (240f * bmp.height / bmp.width).toInt().coerceAtLeast(1), true
                                )
                                withContext(Dispatchers.Main) { saved.add(u to thumb) }
                            } finally {
                                bmp.recycle()
                            }
                            progress = (i + 1) / pages.size.toFloat()
                        }
                    }
                }
                message = "${saved.size} image(s) saved to Pictures/Rs Enhancify" to false
            } catch (e: CancellationException) {
                message = "Cancelled · ${saved.size} image(s) were saved" to true
            } catch (t: Throwable) {
                message = Files.friendly(t) to true
            } finally {
                job = null
            }
        }
    }

    val picker2 = { picker.launch(arrayOf("application/pdf")) }

    ToolScaffold("PDF to JPG", "Pages → images", onBack) {
        if (pdfUri == null) {
            GlassCard(Modifier.fillMaxWidth().height(200.dp), onClick = picker2) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.UploadFile, null, tint = Color.White, modifier = Modifier.size(46.dp))
                        VGap(10.dp)
                        Text("Tap to choose a PDF", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        } else {
            PdfInfoCard(pdfName, pageCount, enabled = !running, onChange = picker2)
            VGap(14.dp)
            SectionLabel("Image quality (DPI)")
            GlassSegmented(Dpi.entries.map { it to it.label }, dpi, enabled = !running) { dpi = it }
            VGap(8.dp)
            SectionLabel("Pages")
            GlassTextField(range, "All pages (or e.g. 1-3, 5)", enabled = !running) { range = it }
            VGap(16.dp)
            if (running) {
                GlassProgress("Exporting $status", progress, onCancel = { job?.cancel() })
            } else {
                GlassButton("Convert to JPG", Modifier.fillMaxWidth(), Icons.Rounded.Image) { convert() }
            }
            if (saved.isNotEmpty()) {
                VGap(16.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Saved (${saved.size})")
                    Spacer(Modifier.weight(1f))
                    if (!running) {
                        GlassOutlineButton("Share all", icon = Icons.Rounded.Share) {
                            Files.share(context, saved.map { it.first }, "image/jpeg")
                        }
                    }
                }
                VGap(8.dp)
                saved.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        row.forEach { (u, thumb) ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(0.72f)
                                    .glass(RoundedCornerShape(14.dp))
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { Files.open(context, u, "image/jpeg") }
                            ) {
                                Image(thumb.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        message?.let { (text, isError) ->
            VGap(12.dp)
            MessageCard(text, isError)
        }
    }
}

@Composable
fun PdfInfoCard(name: String, pages: Int, enabled: Boolean, onChange: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x33FF4FA3)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.PictureAsPdf, null, tint = Color.White) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text("$pages page(s)", color = GlassColors.TextMuted, fontSize = 12.sp)
        }
        GlassOutlineButton("Change", enabled = enabled, onClick = onChange)
    }
}

@Composable
fun GlassTextField(value: String, placeholder: String, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        enabled = enabled,
        singleLine = true,
        placeholder = { Text(placeholder, color = GlassColors.TextMuted) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            disabledTextColor = GlassColors.TextMuted,
            focusedContainerColor = Color(0x1FFFFFFF),
            unfocusedContainerColor = Color(0x14FFFFFF),
            disabledContainerColor = Color(0x0DFFFFFF),
            focusedBorderColor = GlassColors.Cyan,
            unfocusedBorderColor = Color(0x40FFFFFF),
            disabledBorderColor = Color(0x22FFFFFF),
            cursorColor = GlassColors.Cyan
        )
    )
}
