package com.aribs.rsenhancify.engine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.concurrent.CancellationException

enum class WordMode(val label: String) {
    EDITABLE("Editable text"),
    EXACT("Exact layout")
}

object PdfToWord {

    data class Stats(val pages: Int, val ocrPages: Int)

    /**
     * EDITABLE: extracts real text (PDFBox). Pages without a text layer (scans/photos) are OCR'd offline
     *           with ML Kit when [ocr] is on. Result is fully editable in Word.
     * EXACT:    every page is embedded as a high-quality image -> looks identical, but is not editable.
     */
    fun convert(
        context: Context,
        pdf: Uri,
        mode: WordMode,
        ocr: Boolean,
        out: OutputStream,
        onProgress: (Float, String) -> Unit,
        isCancelled: () -> Boolean
    ): Stats = when (mode) {
        WordMode.EXACT -> exact(context, pdf, out, onProgress, isCancelled)
        WordMode.EDITABLE -> editable(context, pdf, ocr, out, onProgress, isCancelled)
    }

    private fun exact(
        context: Context, pdf: Uri, out: OutputStream,
        onProgress: (Float, String) -> Unit, isCancelled: () -> Boolean
    ): Stats {
        PdfPages(context, pdf).use { pages ->
            val n = pages.pageCount
            if (n == 0) error("This PDF has no pages")
            DocxWriter(out).use { docx ->
                val jpeg = ByteArrayOutputStream()
                for (i in 0 until n) {
                    if (isCancelled()) throw CancellationException("Cancelled")
                    onProgress(i / n.toFloat(), "Page ${i + 1} of $n")
                    val (wPt, hPt) = pages.pageSizePt(i)
                    val bmp = pages.render(i, dpi = 150, maxPx = 2400)
                    jpeg.reset()
                    bmp.compress(Bitmap.CompressFormat.JPEG, 88, jpeg)
                    bmp.recycle()
                    docx.fullPageImage(jpeg.toByteArray(), wPt.toFloat(), hPt.toFloat(), isLast = i == n - 1)
                }
                onProgress(1f, "Finishing…")
            }
            return Stats(n, 0)
        }
    }

    private fun editable(
        context: Context, pdf: Uri, ocr: Boolean, out: OutputStream,
        onProgress: (Float, String) -> Unit, isCancelled: () -> Boolean
    ): Stats {
        PDFBoxResourceLoader.init(context.applicationContext)
        val input = context.contentResolver.openInputStream(pdf) ?: error("Cannot open PDF")
        val doc = input.use { PDDocument.load(it) }
        var ocrPages = 0
        val recognizer = if (ocr) TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) else null
        var renderer: PdfPages? = null
        try {
            val n = doc.numberOfPages
            if (n == 0) error("This PDF has no pages")
            val stripper = PDFTextStripper()
            stripper.setSortByPosition(true)
            stripper.setLineSeparator("\n")
            stripper.setParagraphStart("")
            stripper.setParagraphEnd("\n")
            DocxWriter(out).use { docx ->
                for (p in 1..n) {
                    if (isCancelled()) throw CancellationException("Cancelled")
                    onProgress((p - 1) / n.toFloat(), "Page $p of $n")
                    stripper.setStartPage(p)
                    stripper.setEndPage(p)
                    var text = runCatching { stripper.getText(doc) }.getOrDefault("")

                    if (recognizer != null && text.count { it.isLetterOrDigit() } < 15) {
                        // Scanned page -> offline OCR
                        onProgress((p - 1) / n.toFloat(), "Page $p of $n · reading scan (OCR)")
                        val pages = renderer ?: PdfPages(context, pdf).also { renderer = it }
                        val bmp = pages.render(p - 1, dpi = 220, maxPx = 3000)
                        try {
                            val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bmp, 0)))
                            val ocrText = result.textBlocks.joinToString("\n\n") { block ->
                                block.lines.joinToString("\n") { it.text }
                            }
                            if (ocrText.isNotBlank()) {
                                text = ocrText
                                ocrPages++
                            }
                        } finally {
                            bmp.recycle()
                        }
                    }

                    TextReflow.write(docx, text)
                    if (p < n) docx.pageBreak()
                }
                onProgress(1f, "Finishing…")
            }
            return Stats(n, ocrPages)
        } finally {
            runCatching { doc.close() }
            runCatching { recognizer?.close() }
            runCatching { renderer?.close() }
        }
    }
}
