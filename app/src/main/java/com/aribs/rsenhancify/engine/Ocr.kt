package com.aribs.rsenhancify.engine

import android.content.Context
import android.net.Uri
import com.aribs.rsenhancify.util.Files
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.CancellationException

/** Offline OCR (ML Kit, bundled Latin model). Blocking — call from a background thread. */
object Ocr {

    fun readImages(
        context: Context,
        images: List<Uri>,
        onProgress: (Int, Int) -> Unit,
        isCancelled: () -> Boolean
    ): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val parts = mutableListOf<String>()
            images.forEachIndexed { i, uri ->
                if (isCancelled()) throw CancellationException("Cancelled")
                onProgress(i, images.size)
                val bmp = Files.decodeBitmap(context, uri, 2500)
                try {
                    val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bmp, 0)))
                    val text = result.textBlocks.joinToString("\n\n") { block ->
                        block.lines.joinToString("\n") { it.text }
                    }.trim()
                    if (text.isNotEmpty()) parts += text
                } finally {
                    bmp.recycle()
                }
            }
            onProgress(images.size, images.size)
            return parts.joinToString("\n\n")
        } finally {
            runCatching { recognizer.close() }
        }
    }
}
