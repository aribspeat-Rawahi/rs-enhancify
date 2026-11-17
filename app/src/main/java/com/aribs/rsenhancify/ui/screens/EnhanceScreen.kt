package com.aribs.rsenhancify.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapHoriz
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aribs.rsenhancify.engine.Upscaler
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
import kotlin.math.max
import kotlin.math.roundToInt

private enum class Quality(val label: String, val maxInput: Int) {
    // Multiples of the 224 px useful tile area -> no wasted partial tiles
    FAST("Fast", 448),   // 2x2 tiles
    BEST("Best", 896)    // 4x4 tiles
}

private enum class OutFormat(val label: String) { JPG("JPG"), PNG("PNG") }

/** Downscaled copy for on-screen display (keeps UI memory small). */
private fun previewOf(b: Bitmap, maxSide: Int = 1600): Bitmap {
    val longest = max(b.width, b.height)
    if (longest <= maxSide) return b
    val s = maxSide.toFloat() / longest
    return Bitmap.createScaledBitmap(b, (b.width * s).roundToInt(), (b.height * s).roundToInt(), true)
}

@Composable
fun EnhanceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val upscaler = remember { Upscaler(context.applicationContext) }

    var srcUri by remember { mutableStateOf<Uri?>(null) }
    var srcPreview by remember { mutableStateOf<Bitmap?>(null) }
    var srcSize by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableStateOf(4) }
    var quality by remember { mutableStateOf(Quality.FAST) }
    var format by remember { mutableStateOf(OutFormat.JPG) }
    var useGpu by remember { mutableStateOf(upscaler.gpuEnabled) }

    var job by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var eta by remember { mutableStateOf<Int?>(null) }
    var status by remember { mutableStateOf("") }

    var beforeImg by remember { mutableStateOf<Bitmap?>(null) }
    var result by remember { mutableStateOf<Bitmap?>(null) }
    var resultPreview by remember { mutableStateOf<Bitmap?>(null) }
    var resultInfo by remember { mutableStateOf("") }
    var savedUri by remember { mutableStateOf<Uri?>(null) }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    val running = job?.isActive == true

    fun clearResult() {
        result = null; resultPreview = null; beforeImg = null; savedUri = null; resultInfo = ""
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        clearResult()
        message = null
        srcUri = uri
        scope.launch {
            try {
                val (prev, size) = withContext(Dispatchers.IO) {
                    val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
                    val p = Files.decodeBitmap(context, uri, 1400)
                    var ow = opts.outWidth.coerceAtLeast(p.width)
                    var oh = opts.outHeight.coerceAtLeast(p.height)
                    // Bounds are pre-EXIF-rotation; match the orientation of the decoded preview
                    if ((ow > oh) != (p.width > p.height) && p.width != p.height) { val t = ow; ow = oh; oh = t }
                    p to IntSize(ow, oh)
                }
                srcPreview = prev
                srcSize = size
            } catch (t: Throwable) {
                srcUri = null
                message = "Could not open this image: ${Files.friendly(t)}" to true
            }
        }
    }

    fun start() {
        val uri = srcUri ?: return
        clearResult()
        message = null
        progress = 0f; eta = null
        status = "Preparing…"
        job = scope.launch {
            try {
                val out = withContext(Dispatchers.Default) {
                    val worker = this
                    val input = Files.decodeBitmap(context, uri, quality.maxInput)
                    status = "Enhancing ${input.width}×${input.height} → ${input.width * scale}×${input.height * scale}"
                    val r = upscaler.upscale(
                        input, scale,
                        onProgress = { p, e -> progress = p; eta = e },
                        isCancelled = { !worker.isActive }
                    )
                    Triple(input, r, previewOf(r.bitmap))
                }
                beforeImg = out.first
                result = out.second.bitmap
                resultPreview = out.third
                resultInfo = "${out.second.bitmap.width}×${out.second.bitmap.height} · ${out.second.backend} · " +
                    "${(out.second.millis / 100) / 10.0}s"
                useGpu = upscaler.gpuEnabled
            } catch (e: CancellationException) {
                message = "Cancelled" to true
            } catch (t: Throwable) {
                message = Files.friendly(t) to true
            } finally {
                job = null
            }
        }
    }

    fun save(shareAfter: Boolean) {
        val bmp = result ?: return
        val existing = savedUri
        val mime = if (format == OutFormat.PNG) "image/png" else "image/jpeg"
        if (existing != null) {
            if (shareAfter) Files.share(context, listOf(existing), mime)
            return
        }
        scope.launch {
            try {
                val uri = withContext(Dispatchers.IO) {
                    val base = srcUri?.let { Files.baseName(context, it, "image") } ?: "image"
                    Files.saveImage(
                        context, bmp, "Enhanced_${base}_${scale}x_${Files.timestamp()}",
                        if (format == OutFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                        95
                    )
                }
                savedUri = uri
                message = "Saved to Pictures/Rs Enhancify" to false
                if (shareAfter) Files.share(context, listOf(uri), mime)
            } catch (t: Throwable) {
                message = "Save failed: ${Files.friendly(t)}" to true
            }
        }
    }

    ToolScaffold("AI Enhance", "Real-ESRGAN · on-device", onBack) {
        // Image area
        val after = resultPreview
        val before = beforeImg
        val preview = srcPreview
        if (after != null && before != null) {
            CompareSlider(before, after)
            VGap(8.dp)
            Text(resultInfo, color = GlassColors.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
        } else if (preview != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio((preview.width.toFloat() / preview.height).coerceIn(0.5f, 2f))
                    .glass()
                    .padding(6.dp)
                    .clip(RoundedCornerShape(18.dp))
            ) {
                Image(preview.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
            VGap(8.dp)
            Text(
                "Original ${srcSize.width}×${srcSize.height}",
                color = GlassColors.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp)
            )
        } else {
            GlassCard(Modifier.fillMaxWidth().height(220.dp), onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.AddPhotoAlternate, null, tint = Color.White, modifier = Modifier.size(48.dp))
                        VGap(10.dp)
                        Text("Tap to choose a photo", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("Blurry, small or old photos work best", color = GlassColors.TextMuted, fontSize = 12.sp)
                    }
                }
            }
        }
        VGap(16.dp)

        if (srcUri != null) {
            SectionLabel("Upscale")
            GlassSegmented(listOf(2 to "2×", 4 to "4×"), scale, enabled = !running) { scale = it; clearResult() }
            VGap(10.dp)
            SectionLabel("Quality")
            GlassSegmented(Quality.entries.map { it to it.label }, quality, enabled = !running) { quality = it; clearResult() }
            Text(
                if (quality == Quality.FAST) "Processes up to 448 px of detail — quick, good for most photos."
                else "Processes up to 896 px of detail — sharper, takes about 4× longer.",
                color = GlassColors.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )
            VGap(10.dp)
            GlassSwitchRow(
                "GPU acceleration",
                "Much faster. Turns itself off if your phone doesn't support it.",
                useGpu, enabled = !running
            ) { useGpu = it; upscaler.gpuEnabled = it }
            VGap(16.dp)

            if (running) {
                GlassProgress("$status ${formatEta(eta)}", progress, onCancel = { job?.cancel() })
            } else if (result == null) {
                Row {
                    GlassOutlineButton("Change", Modifier.weight(1f), Icons.Rounded.AddPhotoAlternate) {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    Gap()
                    GlassButton("Enhance", Modifier.weight(1.6f), Icons.Rounded.AutoAwesome) { start() }
                }
            } else {
                SectionLabel("Save as")
                GlassSegmented(OutFormat.entries.map { it to it.label }, format) {
                    if (it != format) { format = it; savedUri = null }
                }
                VGap(12.dp)
                Row {
                    GlassButton("Save", Modifier.weight(1f), Icons.Rounded.Download) { save(false) }
                    Gap()
                    GlassOutlineButton("Share", Modifier.weight(1f), Icons.Rounded.Share) { save(true) }
                }
                VGap(10.dp)
                GlassOutlineButton("Enhance another photo", Modifier.fillMaxWidth(), Icons.Rounded.AddPhotoAlternate) {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
        }
        message?.let { (text, isError) ->
            VGap(12.dp)
            MessageCard(text, isError)
        }
    }
}

/** Before/after viewer: drag the handle (or tap) to compare. */
@Composable
private fun CompareSlider(before: Bitmap, after: Bitmap) {
    var fraction by remember { mutableFloatStateOf(0.5f) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val beforeImg = remember(before) { before.asImageBitmap() }
    val afterImg = remember(after) { after.asImageBitmap() }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio((after.width.toFloat() / after.height).coerceIn(0.5f, 2f))
            .glass()
            .padding(6.dp)
            .clip(RoundedCornerShape(18.dp))
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                detectTapGestures { o -> if (boxSize.width > 0) fraction = (o.x / boxSize.width).coerceIn(0f, 1f) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    if (boxSize.width > 0) fraction = (change.position.x / boxSize.width).coerceIn(0f, 1f)
                }
            }
    ) {
        Image(afterImg, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        Image(
            beforeImg, null,
            Modifier
                .fillMaxSize()
                .drawWithContent { clipRect(right = size.width * fraction) { this@drawWithContent.drawContent() } },
            contentScale = ContentScale.Fit
        )
        // Divider + handle
        val xDp = with(density) { (boxSize.width * fraction).toDp() }
        Box(
            Modifier
                .offset(x = xDp - 1.dp)
                .width(2.dp)
                .fillMaxHeight()
                .background(Color.White)
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = xDp - 18.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xCC1A1240))
                .border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.SwapHoriz, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Label("Before", Modifier.align(Alignment.TopStart))
        Label("After", Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun Label(text: String, modifier: Modifier) {
    Text(
        text,
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x80000000))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
