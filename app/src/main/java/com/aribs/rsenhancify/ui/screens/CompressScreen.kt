package com.aribs.rsenhancify.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aribs.rsenhancify.engine.ImageCompressor
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

private enum class CompressMode(val label: String) { TARGET("Target size"), QUALITY("Quality") }

/** Target sizes in KB (0 = custom, added in the UI). */
private val TARGETS = listOf(100, 200, 500, 1024)
private val MAX_SIDES = listOf(0 to "Original", 2048 to "2048", 1600 to "1600", 1280 to "1280", 800 to "800")

private data class Picked(val uri: Uri, val name: String, val size: Long)
private data class Done(val uri: Uri, val name: String, val before: Long, val after: Long, val w: Int, val h: Int, val ok: Boolean)

@Composable
fun CompressScreen(onBack: () -> Unit, initial: List<Uri> = emptyList()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picked = remember { mutableStateListOf<Picked>() }
    var mode by remember { mutableStateOf(CompressMode.TARGET) }
    var targetKb by remember { mutableIntStateOf(200) }
    var customKb by remember { mutableStateOf("") }
    var quality by remember { mutableFloatStateOf(80f) }
    var maxSide by remember { mutableIntStateOf(0) }
    var job by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf("") }
    val done = remember { mutableStateListOf<Done>() }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val running = job != null

    fun add(uris: List<Uri>) {
        done.clear(); message = null
        scope.launch {
            val items = withContext(Dispatchers.IO) {
                uris.filter { u -> picked.none { it.uri == u } }
                    .map { Picked(it, Files.baseName(context, it, "image"), Files.size(context, it)) }
            }
            picked.addAll(items)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) add(uris)
    }
    fun pick() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    LaunchedEffect(Unit) { if (initial.isNotEmpty()) add(initial) }

    fun start() {
        val list = picked.toList()
        val target: Long? = if (mode == CompressMode.TARGET) {
            val kb = if (targetKb == 0) customKb.trim().toIntOrNull() else targetKb
            if (kb == null || kb < 10 || kb > 50_000) {
                message = "Enter a target size between 10 and 50000 KB" to true
                return
            }
            kb * 1024L
        } else null
        val q = quality.toInt()
        val side = maxSide
        done.clear(); message = null; progress = 0f
        job = scope.launch {
            try {
                withContext(Dispatchers.Default) {
                    val worker = this
                    val stamp = Files.timestamp()
                    list.forEachIndexed { i, p ->
                        status = "Compressing ${i + 1} of ${list.size}"
                        val r = ImageCompressor.compress(context, p.uri, side, target, q) { !worker.isActive }
                        val name = "${p.name}_small_${stamp}_${i + 1}"
                        val saved = Files.saveImageBytes(context, r.bytes, name)
                        val item = Done(saved, p.name, p.size, r.bytes.size.toLong(), r.width, r.height, r.fitsTarget)
                        withContext(Dispatchers.Main) { done.add(item) }
                        progress = (i + 1f) / list.size
                    }
                }
                val before = done.sumOf { it.before.coerceAtLeast(0) }
                val after = done.sumOf { it.after }
                val saving = if (before > 0) " · ${100 - (after * 100 / before)}% smaller" else ""
                message = "${done.size} image(s) saved to Pictures/Rs Enhancify$saving" to false
            } catch (e: CancellationException) {
                message = "Cancelled · ${done.size} image(s) were saved" to true
            } catch (t: Throwable) {
                message = Files.friendly(t) to true
            } finally {
                job = null
            }
        }
    }

    ToolScaffold("Compress Image", "Reduce KB · resize · convert to JPG", onBack) {
        if (picked.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth().height(200.dp), onClick = { pick() }) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.Icon(Icons.Rounded.AddPhotoAlternate, null, tint = Color.White, modifier = Modifier.size(46.dp))
                        VGap(10.dp)
                        Text("Tap to choose photos", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("Great for forms that need \"under 200 KB\"", color = GlassColors.TextMuted, fontSize = 12.sp)
                    }
                }
            }
        } else {
            SectionLabel("Photos (${picked.size})")
            picked.forEachIndexed { i, p ->
                PickedRow(p, enabled = !running) { picked.removeAt(i); done.clear() }
                Spacer(Modifier.height(8.dp))
            }
            GlassOutlineButton("Add more photos", Modifier.fillMaxWidth(), Icons.Rounded.AddPhotoAlternate, enabled = !running) { pick() }
            VGap(16.dp)

            SectionLabel("Mode")
            GlassSegmented(CompressMode.entries.map { it to it.label }, mode, enabled = !running) { mode = it; done.clear() }
            VGap(10.dp)
            if (mode == CompressMode.TARGET) {
                SectionLabel("Maximum file size")
                GlassSegmented(
                    TARGETS.map { it to (if (it >= 1024) "${it / 1024} MB" else "$it KB") } + (0 to "Custom"),
                    targetKb, enabled = !running
                ) { targetKb = it; done.clear() }
                if (targetKb == 0) {
                    VGap(8.dp)
                    GlassTextField(customKb, "Size in KB, e.g. 150", enabled = !running) { v ->
                        customKb = v.filter { it.isDigit() }.take(5); done.clear()
                    }
                }
            } else {
                SectionLabel("JPEG quality: ${quality.toInt()}%")
                Slider(
                    value = quality, onValueChange = { quality = it; done.clear() },
                    valueRange = 30f..95f, enabled = !running,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = GlassColors.Cyan,
                        inactiveTrackColor = Color(0x33FFFFFF)
                    )
                )
            }
            VGap(8.dp)
            SectionLabel("Max width / height (px)")
            GlassSegmented(MAX_SIDES, maxSide, enabled = !running) { maxSide = it; done.clear() }
            Text(
                "Output is JPG. Location and camera info (EXIF) are removed.",
                color = GlassColors.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )
            VGap(16.dp)

            if (running) {
                GlassProgress(status, progress, onCancel = { job?.cancel() })
            } else if (done.isEmpty()) {
                GlassButton("Compress ${picked.size} photo(s)", Modifier.fillMaxWidth(), Icons.Rounded.Compress) { start() }
            }

            if (done.isNotEmpty()) {
                VGap(14.dp)
                SectionLabel("Results")
                done.forEach { d ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .glass(RoundedCornerShape(16.dp))
                            .clickable { Files.open(context, d.uri, "image/jpeg") }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(d.name, color = Color.White, fontSize = 14.sp, maxLines = 1)
                            Text("${d.w}×${d.h}", color = GlassColors.TextMuted, fontSize = 12.sp)
                        }
                        Text(
                            "${Files.formatSize(d.before)} → ${Files.formatSize(d.after)}",
                            color = if (d.ok) GlassColors.Mint else GlassColors.Amber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (done.any { !it.ok }) {
                    Text(
                        "Orange = could not reach the target even at the smallest size.",
                        color = GlassColors.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp)
                    )
                    VGap(6.dp)
                }
                if (!running) {
                    Row {
                        GlassButton("Share all", Modifier.weight(1f), Icons.Rounded.Share) {
                            Files.share(context, done.map { it.uri }, "image/jpeg")
                        }
                        Gap()
                        GlassOutlineButton("New batch", Modifier.weight(1f)) {
                            picked.clear(); done.clear(); message = null
                        }
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
private fun PickedRow(p: Picked, enabled: Boolean, onRemove: () -> Unit) {
    val context = LocalContext.current
    val info by produceState<Pair<Bitmap?, String>>(null to "", p.uri) {
        value = withContext(Dispatchers.IO) {
            val thumb = runCatching { Files.decodeBitmap(context, p.uri, 160) }.getOrNull()
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            runCatching { context.contentResolver.openInputStream(p.uri)?.use { BitmapFactory.decodeStream(it, null, opts) } }
            val dims = if (opts.outWidth > 0) "${opts.outWidth}×${opts.outHeight} · " else ""
            thumb to dims
        }
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
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x22FFFFFF))
        ) {
            info.first?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, color = Color.White, fontSize = 14.sp, maxLines = 1)
            Text("${info.second}${Files.formatSize(p.size)}", color = GlassColors.TextMuted, fontSize = 12.sp)
        }
        SmallIcon(Icons.Rounded.Close, enabled, onRemove)
    }
}
