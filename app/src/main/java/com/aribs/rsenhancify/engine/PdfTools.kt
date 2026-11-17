package com.aribs.rsenhancify.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.aribs.rsenhancify.util.Files
import java.io.Closeable
import java.io.OutputStream
import java.util.concurrent.CancellationException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class PageSize(val label: String) { A4("A4"), LETTER("Letter"), FIT("Fit image") }
enum class PdfMargin(val label: String, val points: Float) { NONE("None", 0f), SMALL("Small", 18f), NORMAL("Normal", 36f) }

object ImageToPdf {

    /**
     * Build a PDF from images. Each image becomes one page.
     * [maxSide] limits image resolution (keeps file size sane and avoids OOM).
     */
    fun create(
        context: Context,
        images: List<Uri>,
        pageSize: PageSize,
        margin: PdfMargin,
        maxSide: Int,
        out: OutputStream,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean
    ) {
        require(images.isNotEmpty()) { "No images selected" }
        val doc = PdfDocument()
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        try {
            images.forEachIndexed { index, uri ->
                if (isCancelled()) throw CancellationException("Cancelled")
                val bmp = Files.decodeBitmap(context, uri, maxSide)
                try {
                    val landscape = bmp.width > bmp.height
                    val (pw, ph) = when (pageSize) {
                        PageSize.A4 -> if (landscape) 842 to 595 else 595 to 842
                        PageSize.LETTER -> if (landscape) 792 to 612 else 612 to 792
                        PageSize.FIT -> {
                            // Longest side = 842 pt (A4 height), keep image aspect
                            val s = 842f / max(bmp.width, bmp.height)
                            (bmp.width * s).roundToInt().coerceAtLeast(1) +
                                (2 * margin.points).roundToInt() to
                                (bmp.height * s).roundToInt().coerceAtLeast(1) + (2 * margin.points).roundToInt()
                        }
                    }
                    val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, index + 1).create())
                    val canvas = page.canvas
                    canvas.drawColor(Color.WHITE)
                    val availW = pw - 2 * margin.points
                    val availH = ph - 2 * margin.points
                    val scale = min(availW / bmp.width, availH / bmp.height)
                    val dw = bmp.width * scale
                    val dh = bmp.height * scale
                    val left = (pw - dw) / 2f
                    val top = (ph - dh) / 2f
                    canvas.drawBitmap(bmp, null, RectF(left, top, left + dw, top + dh), paint)
                    doc.finishPage(page)
                } finally {
                    bmp.recycle()
                }
                onProgress((index + 1) / images.size.toFloat())
            }
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }
}

/** Thin wrapper around Android's PdfRenderer that always renders on a white background. */
class PdfPages(context: Context, uri: Uri) : Closeable {
    private val pfd: ParcelFileDescriptor =
        context.contentResolver.openFileDescriptor(uri, "r") ?: error("Cannot open PDF")
    private val renderer: PdfRenderer = try {
        PdfRenderer(pfd)
    } catch (t: Throwable) {
        pfd.close()
        throw t
    }

    val pageCount: Int get() = renderer.pageCount

    /** Page size in PDF points (1/72 inch). */
    fun pageSizePt(index: Int): Pair<Int, Int> = renderer.openPage(index).use { it.width to it.height }

    /** Render a page at [dpi], capped so the longest side <= [maxPx]. */
    fun render(index: Int, dpi: Int, maxPx: Int = 4000): Bitmap {
        renderer.openPage(index).use { page ->
            var scale = dpi / 72f
            val longest = max(page.width, page.height) * scale
            if (longest > maxPx) scale *= maxPx / longest
            val w = (page.width * scale).roundToInt().coerceAtLeast(1)
            val h = (page.height * scale).roundToInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE) // PdfRenderer leaves the background transparent
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            return bmp
        }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }
}

object PageRange {
    /**
     * Parse "1-3, 5, 8-" style ranges (1-based) into sorted 0-based indices.
     * Blank = all pages. Throws IllegalArgumentException with a readable message.
     */
    fun parse(text: String, pageCount: Int): List<Int> {
        if (text.isBlank()) return (0 until pageCount).toList()
        val result = sortedSetOf<Int>()
        for (part in text.split(',').map { it.trim() }.filter { it.isNotEmpty() }) {
            val m = Regex("^(\\d+)?\\s*(-)?\\s*(\\d+)?$").matchEntire(part)
                ?: throw IllegalArgumentException("Invalid page range: \"$part\"")
            val a = m.groupValues[1].toIntOrNull()
            val dash = m.groupValues[2].isNotEmpty()
            val b = m.groupValues[3].toIntOrNull()
            val start = a ?: if (dash) 1 else throw IllegalArgumentException("Invalid page range: \"$part\"")
            val end = if (dash) (b ?: pageCount) else start
            if (start < 1 || end > pageCount || start > end) {
                throw IllegalArgumentException("Pages must be between 1 and $pageCount")
            }
            for (p in start..end) result.add(p - 1)
        }
        if (result.isEmpty()) throw IllegalArgumentException("No pages selected")
        return result.toList()
    }
}
