package com.aribs.rsenhancify.engine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.aribs.rsenhancify.util.Files
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Compress / resize / convert images to JPEG.
 * - Target-size mode: finds the highest JPEG quality that fits under [targetBytes]; if even low quality
 *   is too big, the image is scaled down step by step until it fits.
 * - Quality mode: plain JPEG at a fixed quality.
 * EXIF metadata (incl. GPS location) is not copied to the output.
 */
object ImageCompressor {

    data class Result(val bytes: ByteArray, val width: Int, val height: Int, val quality: Int, val fitsTarget: Boolean)

    private const val MIN_QUALITY = 25
    private const val MAX_QUALITY = 95
    private const val MIN_SIDE = 240

    /** [maxSide] = 0 keeps the original resolution (still capped at 8000 px for memory safety). */
    fun compress(
        context: Context,
        uri: Uri,
        maxSide: Int,
        targetBytes: Long?,
        quality: Int,
        isCancelled: () -> Boolean
    ): Result {
        var bmp = Files.decodeBitmap(context, uri, if (maxSide > 0) maxSide else 8000)
        try {
            if (targetBytes == null) {
                val bytes = encode(bmp, quality)
                return Result(bytes, bmp.width, bmp.height, quality, true)
            }
            while (true) {
                if (isCancelled()) throw java.util.concurrent.CancellationException("Cancelled")
                // Binary search the best quality that fits
                var lo = MIN_QUALITY
                var hi = MAX_QUALITY
                var best: ByteArray? = null
                var bestQ = MIN_QUALITY
                while (lo <= hi) {
                    val mid = (lo + hi) / 2
                    val bytes = encode(bmp, mid)
                    if (bytes.size <= targetBytes) {
                        best = bytes; bestQ = mid; lo = mid + 1
                    } else {
                        hi = mid - 1
                    }
                }
                if (best != null) return Result(best, bmp.width, bmp.height, bestQ, true)

                // Still too big at minimum quality -> shrink the image and try again
                val longest = max(bmp.width, bmp.height)
                if (longest <= MIN_SIDE) {
                    val bytes = encode(bmp, MIN_QUALITY)
                    return Result(bytes, bmp.width, bmp.height, MIN_QUALITY, bytes.size <= targetBytes)
                }
                val scale = 0.8f
                val smaller = Bitmap.createScaledBitmap(
                    bmp,
                    (bmp.width * scale).roundToInt().coerceAtLeast(1),
                    (bmp.height * scale).roundToInt().coerceAtLeast(1),
                    true
                )
                if (smaller !== bmp) bmp.recycle()
                bmp = smaller
            }
        } finally {
            bmp.recycle()
        }
    }

    private fun encode(bmp: Bitmap, quality: Int): ByteArray {
        val out = ByteArrayOutputStream(256 * 1024)
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }
}
