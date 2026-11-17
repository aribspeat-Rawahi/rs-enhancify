package com.aribs.rsenhancify.engine

/** Pure-Kotlin text reflow used by PDF -> Word (no Android deps, unit-testable). */
object TextReflow {

    private val bulletStart = Regex("^\\s*([•▪◦●■\\-–*]|\\(?\\d{1,3}[.)]|[a-zA-Z][.)])\\s+")
    private val sentenceEnd = Regex("[.!?:;]['\")\\]]?$")

    /**
     * PDF text comes as hard-wrapped lines. Re-join wrapped lines into real paragraphs so the
     * Word file is pleasant to edit, while keeping short lines, lists and headings on their own.
     */
    fun write(docx: DocxWriter, raw: String) {
        val lines = raw.replace("\r", "").split('\n').map { it.trimEnd() }
        val nonEmpty = lines.filter { it.isNotBlank() }
        if (nonEmpty.isEmpty()) {
            docx.paragraph("")
            return
        }
        val typical = nonEmpty.map { it.length }.sorted().let { it[(it.size * 0.8).toInt().coerceAtMost(it.size - 1)] }
        val wrapThreshold = (typical * 0.7).toInt().coerceAtLeast(25)

        val para = StringBuilder()
        var prevWrapped = false
        fun flush() {
            if (para.isNotBlank()) {
                val t = para.toString().trim()
                val heading = t.length <= 60 && !sentenceEnd.containsMatchIn(t) &&
                    t.count { it.isLetter() } > 2 && t == t.uppercase() && t.any { it.isLetter() }
                docx.paragraph(t, bold = heading, sizePt = if (heading) 13f else 11f)
            }
            para.setLength(0)
            prevWrapped = false
        }

        for (line in lines) {
            val l = line.trim()
            if (l.isEmpty()) {
                flush(); continue
            }
            val startsNew = bulletStart.containsMatchIn(l)
            if (para.isNotEmpty() && prevWrapped && !startsNew) {
                // Continuation of a wrapped line
                if (para.endsWith("-") && l.first().isLowerCase()) para.setLength(para.length - 1) else para.append(' ')
                para.append(l)
            } else {
                flush()
                para.append(l)
            }
            // A long line was most likely wrapped by the PDF layout; a short one ends the paragraph
            prevWrapped = l.length >= wrapThreshold
        }
        flush()
    }
}
