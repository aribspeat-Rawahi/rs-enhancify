package com.aribs.rsenhancify.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aribs.rsenhancify.engine.ImageToPdf
import com.aribs.rsenhancify.engine.PageSize
import com.aribs.rsenhancify.engine.PdfMargin
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class PdfQuality(val label: String, val maxSide: Int) {
    STANDARD("Standard", 1600),
    HIGH("High", 2480)
}

@Composable
fun ImageToPdfScreen(onBack: () -> Unit, initial: List<Uri> = emptyList()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val images = remember { mutableStateListOf<Uri>() }
    var pageSize by remember { mutableStateOf(PageSize.A4) }
    var margin by remember { mutableStateOf(PdfMargin.SMALL) }
    var quality by remember { mutableStateOf(PdfQuality.STANDARD) }
    var job by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var savedUri by remember { mutableStateOf<Uri?>(null) }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val running = job != null

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) {
            images.addAll(uris.filter { it !in images })
            savedUri = null
            message = null
        }
    }
    fun pick() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    LaunchedEffect(Unit) { if (initial.isNotEmpty()) images.addAll(initial.filter { it !in images }) }

    fun create() {
        if (images.isEmpty()) return
        val list = images.toList()
        message = null
        savedUri = null
        progress = 0f
        job = scope.launch {
            try {
                val uri = withContext(Dispatchers.Default) {
                    val worker = this
                    val name = "Images_${Files.timestamp()}.pdf"
                    Files.saveDocument(context, name, "application/pdf") { os ->
                        ImageToPdf.create(
                            context, list, pageSize, margin, quality.maxSide, os,
                            onProgress = { progress = it },
                            isCancelled = { !worker.isActive }
                        )
                    }
                }
                savedUri = uri
                message = "PDF with ${list.size} page(s) saved to Download/Rs Enhancify" to false
            } catch (e: CancellationException) {
                message = "Cancelled" to true
            } catch (t: Throwable) {
                message = Files.friendly(t) to true
            } finally {
                job = null
            }
        }
    }

    ToolScaffold("JPG to PDF", "Photos → one PDF", onBack) {
        if (images.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth().height(200.dp), onClick = { pick() }) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.AddPhotoAlternate, null, tint = Color.White, modifier = Modifier.size(46.dp))
                        VGap(10.dp)
                        Text("Tap to choose photos", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("JPG, PNG, WEBP, HEIC · pick many at once", color = GlassColors.TextMuted, fontSize = 12.sp)
                    }
                }
            }
        } else {
            SectionLabel("Pages (${images.size}) · order = page order")
            images.forEachIndexed { index, uri ->
                PageRow(
                    uri = uri,
                    number = index + 1,
                    canUp = index > 0 && !running,
                    canDown = index < images.size - 1 && !running,
                    canRemove = !running,
                    onUp = { images.add(index - 1, images.removeAt(index)); savedUri = null },
                    onDown = { images.add(index + 1, images.removeAt(index)); savedUri = null },
                    onRemove = { images.removeAt(index); savedUri = null }
                )
                Spacer(Modifier.height(8.dp))
            }
            GlassOutlineButton("Add more photos", Modifier.fillMaxWidth(), Icons.Rounded.AddPhotoAlternate, enabled = !running) { pick() }
            VGap(16.dp)
            SectionLabel("Page size")
            GlassSegmented(PageSize.entries.map { it to it.label }, pageSize, enabled = !running) { pageSize = it; savedUri = null }
            VGap(8.dp)
            SectionLabel("Margin")
            GlassSegmented(PdfMargin.entries.map { it to it.label }, margin, enabled = !running) { margin = it; savedUri = null }
            VGap(8.dp)
            SectionLabel("Image quality")
            GlassSegmented(PdfQuality.entries.map { it to it.label }, quality, enabled = !running) { quality = it; savedUri = null }
            VGap(18.dp)

            val saved = savedUri
            when {
                running -> GlassProgress("Creating PDF…", progress, onCancel = { job?.cancel() })
                saved != null -> Row {
                    GlassButton("Open", Modifier.weight(1f), Icons.Rounded.OpenInNew) { Files.open(context, saved, "application/pdf") }
                    Gap()
                    GlassOutlineButton("Share", Modifier.weight(1f), Icons.Rounded.Share) {
                        Files.share(context, listOf(saved), "application/pdf")
                    }
                }
                else -> GlassButton("Create PDF", Modifier.fillMaxWidth(), Icons.Rounded.PictureAsPdf) { create() }
            }
            if (saved != null && !running) {
                VGap(10.dp)
                GlassOutlineButton("Start new PDF", Modifier.fillMaxWidth()) {
                    images.clear(); savedUri = null; message = null
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
private fun PageRow(
    uri: Uri,
    number: Int,
    canUp: Boolean,
    canDown: Boolean,
    canRemove: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val thumb by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { runCatching { Files.decodeBitmap(context, uri, 200) }.getOrNull() }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(18.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x22FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            thumb?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Spacer(Modifier.width(12.dp))
        Text("Page $number", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        SmallIcon(Icons.Rounded.KeyboardArrowUp, canUp, onUp)
        SmallIcon(Icons.Rounded.KeyboardArrowDown, canDown, onDown)
        SmallIcon(Icons.Rounded.Close, canRemove, onRemove)
    }
}

@Composable
fun SmallIcon(icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = 4.dp)
            .size(36.dp)
            .alpha(if (enabled) 1f else 0.3f)
            .clip(CircleShape)
            .background(Color(0x1FFFFFFF))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}
