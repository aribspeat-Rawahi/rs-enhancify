package com.aribs.rsenhancify.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MergeType
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aribs.rsenhancify.engine.PageOp
import com.aribs.rsenhancify.engine.PageRange
import com.aribs.rsenhancify.engine.PdfEditor
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PDF = "application/pdf"
private const val MAX_ORGANIZE_PAGES = 500

private enum class PdfToolMode(val label: String) { MERGE("Merge"), SPLIT("Split"), ORGANIZE("Organize") }
private enum class SplitMode(val label: String) { ONE("One PDF"), EACH("Each page") }

private data class PdfItem(val uri: Uri, val name: String, val pages: Int)
private data class PageItem(val index: Int, val rotation: Int, val thumb: Bitmap?)

@Composable
fun PdfToolsScreen(onBack: () -> Unit, initial: List<Uri> = emptyList()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(if (initial.size >= 2) PdfToolMode.MERGE else PdfToolMode.ORGANIZE) }

    // Shared job/result state
    var job by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf("") }
    val results = remember { mutableStateListOf<Uri>() }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val running = job != null

    // MERGE state
    val mergeList = remember { mutableStateListOf<PdfItem>() }
    // SPLIT / ORGANIZE state (one source PDF)
    var single by remember { mutableStateOf<PdfItem?>(null) }
    var range by remember { mutableStateOf("") }
    var splitMode by remember { mutableStateOf(SplitMode.ONE) }
    val pages = remember { mutableStateListOf<PageItem>() }

    fun resetResults() { results.clear(); message = null }

    suspend fun describe(uri: Uri): PdfItem = withContext(Dispatchers.IO) {
        val count = runCatching { PdfPages(context, uri).use { it.pageCount } }
            .getOrElse { PdfEditor.pageCount(context, uri) }
        PdfItem(uri, Files.baseName(context, uri, "document"), count)
    }

    fun loadThumbnails(item: PdfItem) {
        pages.clear()
        if (item.pages > MAX_ORGANIZE_PAGES) {
            message = "Organize supports up to $MAX_ORGANIZE_PAGES pages (this PDF has ${item.pages})." to true
            return
        }
        progress = 0f
        status = "Loading pages…"
        job = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    PdfPages(context, item.uri).use { pdf ->
                        for (i in 0 until pdf.pageCount) {
                            if (!isActive) break
                            val thumb = runCatching { pdf.render(i, dpi = 40, maxPx = 160) }.getOrNull()
                            withContext(Dispatchers.Main) { pages.add(PageItem(i, 0, thumb)) }
                            progress = (i + 1f) / pdf.pageCount
                        }
                    }
                }
            } catch (e: CancellationException) {
                // leaving the screen
            } catch (t: Throwable) {
                // Android's renderer can't read it (e.g. unusual encryption): still allow organizing without previews
                pages.clear()
                for (i in 0 until item.pages) pages.add(PageItem(i, 0, null))
            } finally {
                job = null
            }
        }
    }

    fun addMergeFiles(uris: List<Uri>) {
        scope.launch {
            for (u in uris) {
                if (mergeList.any { it.uri == u }) continue
                try {
                    mergeList.add(describe(u))
                } catch (t: Throwable) {
                    message = "Could not open a PDF: ${Files.friendly(t)}" to true
                }
            }
        }
    }

    fun openSingle(uri: Uri) {
        resetResults()
        scope.launch {
            try {
                val item = describe(uri)
                single = item
                range = ""
                if (mode == PdfToolMode.ORGANIZE) loadThumbnails(item)
            } catch (t: Throwable) {
                single = null
                message = "Could not open PDF: ${Files.friendly(t)}" to true
            }
        }
    }

    val pickMany = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) { resetResults(); addMergeFiles(uris) }
    }
    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) openSingle(uri)
    }

    LaunchedEffect(Unit) {
        when {
            initial.size >= 2 -> addMergeFiles(initial)
            initial.size == 1 -> openSingle(initial[0])
        }
    }

    fun runJob(label: String, block: suspend (isCancelled: () -> Boolean) -> String) {
        resetResults()
        progress = 0f
        status = label
        job = scope.launch {
            try {
                val done = withContext(Dispatchers.Default) {
                    val worker = this
                    block { !worker.isActive }
                }
                message = done to false
            } catch (e: CancellationException) {
                message = "Cancelled" to true
            } catch (e: IllegalArgumentException) {
                message = (e.message ?: "Invalid input") to true
            } catch (t: Throwable) {
                message = Files.friendly(t) to true
            } finally {
                job = null
            }
        }
    }

    fun merge() {
        val list = mergeList.toList()
        runJob("Merging ${list.size} PDFs…") { cancelled ->
            var total = 0
            val uri = Files.saveDocument(context, "Merged_${Files.timestamp()}.pdf", PDF) { os ->
                total = PdfEditor.merge(context, list.map { it.uri }, os, { progress = it }, cancelled)
            }
            withContext(Dispatchers.Main) { results.add(uri) }
            "Merged PDF ($total pages) saved to Download/Rs Enhancify"
        }
    }

    fun split() {
        val item = single ?: return
        val selected = try {
            PageRange.parse(range, item.pages)
        } catch (e: IllegalArgumentException) {
            message = (e.message ?: "Invalid page range") to true
            return
        }
        val each = splitMode == SplitMode.EACH
        runJob(if (each) "Splitting pages…" else "Extracting pages…") { cancelled ->
            val stamp = Files.timestamp()
            if (each) {
                PdfEditor.splitEach(context, item.uri, selected, { progress = it }, cancelled) { pageNo, write ->
                    val u = Files.saveDocument(context, "${item.name}_page${pageNo}_$stamp.pdf", PDF, write)
                    scope.launch(Dispatchers.Main) { results.add(u) }
                }
                "${selected.size} PDF file(s) saved to Download/Rs Enhancify"
            } else {
                val uri = Files.saveDocument(context, "${item.name}_pages_$stamp.pdf", PDF) { os ->
                    PdfEditor.organize(context, item.uri, selected.map { PageOp(it) }, os, cancelled)
                }
                withContext(Dispatchers.Main) { results.add(uri) }
                "PDF with ${selected.size} page(s) saved to Download/Rs Enhancify"
            }
        }
    }

    fun saveOrganized() {
        val item = single ?: return
        val ops = pages.map { PageOp(it.index, it.rotation) }
        runJob("Saving PDF…") { cancelled ->
            val uri = Files.saveDocument(context, "${item.name}_edited_${Files.timestamp()}.pdf", PDF) { os ->
                PdfEditor.organize(context, item.uri, ops, os, cancelled)
            }
            withContext(Dispatchers.Main) { results.add(uri) }
            "Edited PDF (${ops.size} pages) saved to Download/Rs Enhancify"
        }
    }

    ToolScaffold("PDF Tools", "Merge · Split · Organize", onBack) {
        GlassSegmented(PdfToolMode.entries.map { it to it.label }, mode, enabled = !running) {
            if (it != mode) {
                mode = it
                resetResults()
                val s = single
                if (it == PdfToolMode.ORGANIZE && s != null && pages.isEmpty()) loadThumbnails(s)
            }
        }
        VGap(14.dp)

        when (mode) {
            PdfToolMode.MERGE -> {
                if (mergeList.isEmpty()) {
                    PickCard("Choose PDFs to merge", "Pick 2 or more · order can be changed") { pickMany.launch(arrayOf(PDF)) }
                } else {
                    SectionLabel("Files (${mergeList.size}) · merged in this order")
                    mergeList.forEachIndexed { i, item ->
                        FileRow(
                            title = item.name, subtitle = "${item.pages} page(s)",
                            canUp = i > 0 && !running, canDown = i < mergeList.size - 1 && !running, canRemove = !running,
                            onUp = { mergeList.add(i - 1, mergeList.removeAt(i)); resetResults() },
                            onDown = { mergeList.add(i + 1, mergeList.removeAt(i)); resetResults() },
                            onRemove = { mergeList.removeAt(i); resetResults() }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    GlassOutlineButton("Add PDFs", Modifier.fillMaxWidth(), Icons.Rounded.UploadFile, enabled = !running) {
                        pickMany.launch(arrayOf(PDF))
                    }
                    VGap(14.dp)
                    if (!running && results.isEmpty()) {
                        GlassButton(
                            "Merge ${mergeList.size} PDFs", Modifier.fillMaxWidth(), Icons.Rounded.MergeType,
                            enabled = mergeList.size >= 2
                        ) { merge() }
                    }
                }
            }

            PdfToolMode.SPLIT, PdfToolMode.ORGANIZE -> {
                val item = single
                if (item == null) {
                    PickCard("Choose a PDF", if (mode == PdfToolMode.SPLIT) "Extract or split pages" else "Reorder, rotate or delete pages") {
                        pickOne.launch(arrayOf(PDF))
                    }
                } else {
                    PdfInfoCard(item.name, item.pages, enabled = !running) { pickOne.launch(arrayOf(PDF)) }
                    VGap(14.dp)
                    if (mode == PdfToolMode.SPLIT) {
                        SectionLabel("Pages")
                        GlassTextField(range, "All pages (or e.g. 1-3, 5)", enabled = !running) { range = it; resetResults() }
                        VGap(10.dp)
                        SectionLabel("Save as")
                        GlassSegmented(SplitMode.entries.map { it to it.label }, splitMode, enabled = !running) {
                            splitMode = it; resetResults()
                        }
                        Text(
                            if (splitMode == SplitMode.ONE) "Selected pages are saved together in one new PDF."
                            else "Every selected page becomes its own PDF file.",
                            color = GlassColors.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                        )
                        VGap(14.dp)
                        if (!running && results.isEmpty()) {
                            GlassButton("Split PDF", Modifier.fillMaxWidth(), Icons.Rounded.ContentCut) { split() }
                        }
                    } else {
                        if (pages.isNotEmpty()) {
                            SectionLabel("Pages (${pages.size}) · ↑↓ move · ⟳ rotate · ✕ delete")
                            pages.forEachIndexed { i, p ->
                                PageEditRow(
                                    p, position = i + 1, enabled = !running,
                                    canUp = i > 0, canDown = i < pages.size - 1,
                                    onUp = { pages.add(i - 1, pages.removeAt(i)); resetResults() },
                                    onDown = { pages.add(i + 1, pages.removeAt(i)); resetResults() },
                                    onRotate = { pages[i] = p.copy(rotation = (p.rotation + 90) % 360); resetResults() },
                                    onRemove = { pages.removeAt(i); resetResults() }
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            VGap(6.dp)
                            if (!running && results.isEmpty()) {
                                Row {
                                    GlassOutlineButton("Reset", Modifier.weight(1f), enabled = !running) { loadThumbnails(item) }
                                    Gap()
                                    GlassButton("Save PDF", Modifier.weight(1.6f), Icons.Rounded.Save, enabled = pages.isNotEmpty()) {
                                        saveOrganized()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (running) {
            VGap(12.dp)
            GlassProgress(status, progress, onCancel = { job?.cancel() })
        }
        if (results.isNotEmpty() && !running) {
            VGap(12.dp)
            Row {
                GlassButton("Open", Modifier.weight(1f), Icons.Rounded.OpenInNew) { Files.open(context, results[0], PDF) }
                Gap()
                GlassOutlineButton(if (results.size > 1) "Share all" else "Share", Modifier.weight(1f), Icons.Rounded.Share) {
                    Files.share(context, results.toList(), PDF)
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
fun PickCard(title: String, subtitle: String, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth().height(200.dp), onClick = onClick) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.UploadFile, null, tint = Color.White, modifier = Modifier.size(46.dp))
                VGap(10.dp)
                Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, color = GlassColors.TextMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun FileRow(
    title: String,
    subtitle: String,
    canUp: Boolean,
    canDown: Boolean,
    canRemove: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(18.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x33FF4FA3)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.PictureAsPdf, null, tint = Color.White) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, maxLines = 1)
            Text(subtitle, color = GlassColors.TextMuted, fontSize = 12.sp)
        }
        SmallIcon(Icons.Rounded.KeyboardArrowUp, canUp, onUp)
        SmallIcon(Icons.Rounded.KeyboardArrowDown, canDown, onDown)
        SmallIcon(Icons.Rounded.Close, canRemove, onRemove)
    }
}

@Composable
private fun PageEditRow(
    page: PageItem,
    position: Int,
    enabled: Boolean,
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRotate: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(18.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(width = 54.dp, height = 70.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x22FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            val thumb = page.thumb
            if (thumb != null) {
                Image(
                    thumb.asImageBitmap(), null,
                    Modifier.fillMaxSize().rotate(page.rotation.toFloat()),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text("${page.index + 1}", color = Color.White, fontSize = 16.sp)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Page $position", color = Color.White, fontSize = 14.sp)
            val note = buildString {
                if (page.index + 1 != position) append("was ${page.index + 1}")
                if (page.rotation != 0) { if (isNotEmpty()) append(" · "); append("${page.rotation}°") }
            }
            if (note.isNotEmpty()) Text(note, color = GlassColors.TextMuted, fontSize = 11.sp)
        }
        SmallIcon(Icons.Rounded.KeyboardArrowUp, enabled && canUp, onUp)
        SmallIcon(Icons.Rounded.KeyboardArrowDown, enabled && canDown, onDown)
        SmallIcon(Icons.Rounded.RotateRight, enabled, onRotate)
        SmallIcon(Icons.Rounded.Close, enabled, onRemove)
    }
}
