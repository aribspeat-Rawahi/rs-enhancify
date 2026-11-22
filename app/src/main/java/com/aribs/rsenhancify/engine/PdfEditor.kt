package com.aribs.rsenhancify.engine

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import java.io.OutputStream
import java.util.concurrent.CancellationException

/** One page in an "organize" result: which source page, and extra clockwise rotation (0/90/180/270). */
data class PageOp(val index: Int, val rotation: Int = 0)

/** Merge / extract / split / organize PDFs with PDFBox. All functions are blocking. */
object PdfEditor {

    private fun load(context: Context, uri: Uri): PDDocument {
        PDFBoxResourceLoader.init(context.applicationContext)
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open PDF")
        val doc = input.use { PDDocument.load(it) }
        // PDFs that only have an owner password (print/copy limits) can still be edited
        doc.isAllSecurityToBeRemoved = true
        return doc
    }

    /** Copies [page] into [dest] keeping inherited resources/boxes, plus [extraRotation] degrees. */
    private fun copyPage(dest: PDDocument, page: PDPage, extraRotation: Int = 0) {
        val imported = dest.importPage(page)
        imported.resources = page.resources
        imported.mediaBox = page.mediaBox
        imported.cropBox = page.cropBox
        imported.rotation = ((page.rotation + extraRotation) % 360 + 360) % 360
    }

    fun merge(
        context: Context,
        pdfs: List<Uri>,
        out: OutputStream,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean
    ): Int {
        require(pdfs.size >= 2) { "Choose at least 2 PDFs to merge" }
        val dest = PDDocument()
        val sources = mutableListOf<PDDocument>()
        try {
            val merger = PDFMergerUtility()
            pdfs.forEachIndexed { i, uri ->
                if (isCancelled()) throw CancellationException("Cancelled")
                val src = load(context, uri)
                sources += src
                merger.appendDocument(dest, src)
                onProgress((i + 1) / (pdfs.size + 1f))
            }
            dest.save(out)
            onProgress(1f)
            return dest.numberOfPages
        } finally {
            runCatching { dest.close() }
            sources.forEach { runCatching { it.close() } }
        }
    }

    /** Page count via PDFBox (works for PDFs Android's renderer can't open). */
    fun pageCount(context: Context, uri: Uri): Int = load(context, uri).use { it.numberOfPages }

    /** Builds one new PDF from [ops] (keeps order, applies rotation, omits pages not listed). */
    fun organize(
        context: Context,
        pdf: Uri,
        ops: List<PageOp>,
        out: OutputStream,
        isCancelled: () -> Boolean
    ) {
        require(ops.isNotEmpty()) { "No pages left to save" }
        load(context, pdf).use { src ->
            PDDocument().use { dest ->
                for (op in ops) {
                    if (isCancelled()) throw CancellationException("Cancelled")
                    copyPage(dest, src.getPage(op.index), op.rotation)
                }
                dest.save(out)
            }
        }
    }

    /**
     * Saves each page in [pages] (0-based) as its own PDF. [save] receives the 1-based page number
     * and a writer that writes the single-page PDF to the given stream.
     */
    fun splitEach(
        context: Context,
        pdf: Uri,
        pages: List<Int>,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean,
        save: (pageNumber: Int, write: (OutputStream) -> Unit) -> Unit
    ) {
        load(context, pdf).use { src ->
            pages.forEachIndexed { i, index ->
                if (isCancelled()) throw CancellationException("Cancelled")
                PDDocument().use { dest ->
                    copyPage(dest, src.getPage(index))
                    save(index + 1) { os -> dest.save(os) }
                }
                onProgress((i + 1) / pages.size.toFloat())
            }
        }
    }
}
