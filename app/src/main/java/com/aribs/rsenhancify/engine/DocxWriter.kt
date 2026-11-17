package com.aribs.rsenhancify.engine

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal, dependency-free .docx (Office Open XML) writer.
 * Supports: paragraphs (normal / heading / bullet-like text), page breaks,
 * full-page JPEG images and per-section page sizes. Opens in Word, Google Docs and WPS.
 *
 * Images are streamed into the zip as they are added, so memory stays low for long PDFs.
 */
class DocxWriter(out: OutputStream) : AutoCloseable {

    private val zip = ZipOutputStream(out)
    private val body = StringBuilder()
    private val imageRels = StringBuilder()
    private var imageCount = 0
    private var closed = false

    /** Page size/margins in points for the *final* section (used by text documents). */
    var finalPageWidthPt = 595f
    var finalPageHeightPt = 842f
    var finalMarginPt = 72f

    fun paragraph(text: String, bold: Boolean = false, sizePt: Float = 11f, spacingAfterPt: Float = 6f) {
        val runProps = buildString {
            append("<w:rPr>")
            if (bold) append("<w:b/>")
            val half = (sizePt * 2).toInt()
            append("<w:sz w:val=\"$half\"/><w:szCs w:val=\"$half\"/>")
            append("</w:rPr>")
        }
        body.append("<w:p><w:pPr><w:spacing w:after=\"${(spacingAfterPt * 20).toInt()}\"/></w:pPr>")
        // Keep explicit line breaks inside one paragraph
        text.split('\n').forEachIndexed { i, line ->
            if (i > 0) body.append("<w:r><w:br/></w:r>")
            body.append("<w:r>").append(runProps)
                .append("<w:t xml:space=\"preserve\">").append(esc(line)).append("</w:t></w:r>")
        }
        body.append("</w:p>")
    }

    fun pageBreak() {
        body.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
    }

    /**
     * Add a full-page image that becomes its own section with the given page size and zero margins.
     * [isLast] must be true for the final page (its section properties go at body level).
     */
    fun fullPageImage(jpeg: ByteArray, pageWidthPt: Float, pageHeightPt: Float, isLast: Boolean) {
        imageCount++
        val id = imageCount
        val rid = "rIdImg$id"
        val path = "media/image$id.jpeg"
        zip.putNextEntry(ZipEntry("word/$path"))
        zip.write(jpeg)
        zip.closeEntry()
        imageRels.append(
            "<Relationship Id=\"$rid\" " +
                "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" " +
                "Target=\"$path\"/>"
        )
        // Slightly smaller than the page so Word never pushes it onto an extra blank page
        val cx = ((pageWidthPt - 2f) * 12700).toLong()
        val cy = ((pageHeightPt - 6f) * 12700).toLong()
        val drawing = """
            <w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0">
            <wp:extent cx="$cx" cy="$cy"/><wp:docPr id="$id" name="Page $id"/>
            <wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect="1"/></wp:cNvGraphicFramePr>
            <a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
            <pic:pic><pic:nvPicPr><pic:cNvPr id="$id" name="image$id.jpeg"/><pic:cNvPicPr/></pic:nvPicPr>
            <pic:blipFill><a:blip r:embed="$rid"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>
            <pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm>
            <a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>
            </a:graphicData></a:graphic></wp:inline></w:drawing></w:r>
        """.trimIndent().replace("\n", "")

        val sect = sectPr(pageWidthPt, pageHeightPt, 0f)
        if (isLast) {
            body.append("<w:p><w:pPr><w:spacing w:before=\"0\" w:after=\"0\"/><w:jc w:val=\"center\"/></w:pPr>")
                .append(drawing).append("</w:p>")
            finalPageWidthPt = pageWidthPt
            finalPageHeightPt = pageHeightPt
            finalMarginPt = 0f
        } else {
            body.append("<w:p><w:pPr><w:spacing w:before=\"0\" w:after=\"0\"/><w:jc w:val=\"center\"/>")
                .append(sect).append("</w:pPr>").append(drawing).append("</w:p>")
        }
    }

    private fun sectPr(wPt: Float, hPt: Float, marginPt: Float): String {
        val w = (wPt * 20).toInt()
        val h = (hPt * 20).toInt()
        val m = (marginPt * 20).toInt()
        val orient = if (w > h) " w:orient=\"landscape\"" else ""
        return "<w:sectPr><w:pgSz w:w=\"$w\" w:h=\"$h\"$orient/>" +
            "<w:pgMar w:top=\"$m\" w:right=\"$m\" w:bottom=\"$m\" w:left=\"$m\" w:header=\"0\" w:footer=\"0\" w:gutter=\"0\"/>" +
            "</w:sectPr>"
    }

    override fun close() {
        if (closed) return
        closed = true
        put("[Content_Types].xml", CONTENT_TYPES)
        put("_rels/.rels", ROOT_RELS)
        put("word/styles.xml", STYLES)
        put(
            "word/_rels/document.xml.rels",
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rIdStyles\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
                imageRels + "</Relationships>"
        )
        put(
            "word/document.xml",
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" " +
                "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" " +
                "xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" " +
                "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" " +
                "xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" +
                "<w:body>" + body + sectPr(finalPageWidthPt, finalPageHeightPt, finalMarginPt) +
                "</w:body></w:document>"
        )
        zip.finish()
        zip.flush()
    }

    private fun put(name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    companion object {
        /** XML-escape and drop characters that are illegal in XML 1.0. */
        fun esc(s: String): String {
            val sb = StringBuilder(s.length + 16)
            for (ch in s) {
                when {
                    ch == '&' -> sb.append("&amp;")
                    ch == '<' -> sb.append("&lt;")
                    ch == '>' -> sb.append("&gt;")
                    ch == '"' -> sb.append("&quot;")
                    ch == '\t' -> sb.append(' ')
                    ch.code < 0x20 -> {}
                    ch.code in 0xFFFE..0xFFFF -> {}
                    else -> sb.append(ch)
                }
            }
            return sb.toString()
        }

        private const val CONTENT_TYPES =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Default Extension=\"jpeg\" ContentType=\"image/jpeg\"/>" +
                "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
                "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>" +
                "</Types>"

        private const val ROOT_RELS =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
                "</Relationships>"

        private const val STYLES =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<w:styles xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
                "<w:docDefaults><w:rPrDefault><w:rPr>" +
                "<w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\" w:eastAsia=\"Calibri\" w:cs=\"Calibri\"/>" +
                "<w:sz w:val=\"22\"/><w:szCs w:val=\"22\"/><w:lang w:val=\"en-US\"/>" +
                "</w:rPr></w:rPrDefault>" +
                "<w:pPrDefault><w:pPr><w:spacing w:after=\"120\" w:line=\"259\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault>" +
                "</w:docDefaults>" +
                "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/><w:qFormat/></w:style>" +
                "</w:styles>"
    }
}
