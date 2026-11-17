package com.aribs.rsenhancify.engine

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.CancellationException
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * On-device AI upscaler (Real-ESRGAN realesr-general-x4v3, TFLite FP16).
 *
 * The model has a fixed 256x256 input. Large images are processed in overlapping tiles
 * (16 px context on each side) so there are no visible seams and memory stays bounded.
 *
 * Everything (interpreter creation, inference, close) happens inside one blocking call,
 * so the GPU delegate is always used from the thread that created it.
 */
class Upscaler(private val context: Context) {

    data class Result(val bitmap: Bitmap, val backend: String, val millis: Long)

    companion object {
        private const val MODEL = "realesr_x4_fp16.tflite"
        const val TILE = 256
        private const val PAD = 16
        const val MODEL_SCALE = 4
        private const val PREFS = "rs_enhancify_prefs"
        private const val KEY_GPU_DISABLED = "gpu_disabled"
        private const val KEY_GPU_PENDING = "gpu_init_pending"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    init {
        // If the app died while initialising the GPU last time, that GPU driver is unsafe: use CPU from now on.
        if (prefs.getBoolean(KEY_GPU_PENDING, false)) {
            prefs.edit().putBoolean(KEY_GPU_PENDING, false).putBoolean(KEY_GPU_DISABLED, true).commit()
        }
    }

    var gpuEnabled: Boolean
        get() = !prefs.getBoolean(KEY_GPU_DISABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_GPU_DISABLED, !value).apply()
        }

    private class Engine(val interpreter: Interpreter, val delegate: GpuDelegate?, val name: String) {
        fun close() {
            runCatching { interpreter.close() }
            runCatching { delegate?.close() }
        }
    }

    private fun loadModel(): MappedByteBuffer =
        context.assets.openFd(MODEL).use { afd ->
            FileInputStream(afd.fileDescriptor).use { fis ->
                fis.channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
            }
        }

    private fun newInput() = ByteBuffer.allocateDirect(4 * TILE * TILE * 3).order(ByteOrder.nativeOrder())
    private fun newOutput() =
        ByteBuffer.allocateDirect(4 * TILE * MODEL_SCALE * TILE * MODEL_SCALE * 3).order(ByteOrder.nativeOrder())

    private fun createEngine(input: ByteBuffer, output: ByteBuffer): Engine {
        val model = loadModel()
        if (gpuEnabled) {
            var delegate: GpuDelegate? = null
            var interpreter: Interpreter? = null
            val compat = CompatibilityList()
            try {
                if (compat.isDelegateSupportedOnThisDevice) {
                    prefs.edit().putBoolean(KEY_GPU_PENDING, true).commit()
                    val gpu = GpuDelegate(compat.bestOptionsForThisDevice)
                    delegate = gpu
                    val interp = Interpreter(model, Interpreter.Options().addDelegate(gpu))
                    interpreter = interp
                    // Warm-up run proves the delegate really works on this device
                    input.rewind(); output.rewind()
                    interp.run(input, output)
                    prefs.edit().putBoolean(KEY_GPU_PENDING, false).commit()
                    return Engine(interp, gpu, "GPU")
                }
            } catch (t: Throwable) {
                runCatching { interpreter?.close() }
                runCatching { delegate?.close() }
                prefs.edit().putBoolean(KEY_GPU_PENDING, false).putBoolean(KEY_GPU_DISABLED, true).commit()
            } finally {
                runCatching { compat.close() }
            }
        }
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        val options = Interpreter.Options().setNumThreads(threads).setUseXNNPACK(true)
        return Engine(Interpreter(model, options), null, "CPU")
    }

    /**
     * Upscale [src] by [outScale] (2 or 4).
     * [onProgress] gets 0..1 and an ETA in seconds (null until known).
     * [isCancelled] is polled between tiles; cancellation throws [CancellationException].
     */
    fun upscale(
        src: Bitmap,
        outScale: Int,
        onProgress: (Float, Int?) -> Unit,
        isCancelled: () -> Boolean
    ): Result {
        require(outScale == 2 || outScale == 4)
        val started = SystemClock.elapsedRealtime()
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val input = newInput()
        val output = newOutput()
        val engine = createEngine(input, output)
        try {
            val big = Bitmap.createBitmap(w * MODEL_SCALE, h * MODEL_SCALE, Bitmap.Config.ARGB_8888)
            val step = TILE - 2 * PAD
            val tilesX = ceil(w / step.toDouble()).toInt()
            val tilesY = ceil(h / step.toDouble()).toInt()
            val total = tilesX * tilesY
            val outTile = IntArray(step * MODEL_SCALE * step * MODEL_SCALE)
            val outRowStride = TILE * MODEL_SCALE
            val loopStart = SystemClock.elapsedRealtime()
            var done = 0
            onProgress(0f, null)

            for (ty in 0 until tilesY) {
                for (tx in 0 until tilesX) {
                    if (isCancelled()) {
                        big.recycle()
                        throw CancellationException("Cancelled")
                    }
                    val x0 = tx * step
                    val y0 = ty * step

                    // Fill input tile (edge pixels are replicated outside the image)
                    input.rewind()
                    for (j in 0 until TILE) {
                        val sy = (y0 - PAD + j).coerceIn(0, h - 1)
                        val row = sy * w
                        for (i in 0 until TILE) {
                            val sx = (x0 - PAD + i).coerceIn(0, w - 1)
                            val p = pixels[row + sx]
                            input.putFloat(((p shr 16) and 0xFF) / 255f)
                            input.putFloat(((p shr 8) and 0xFF) / 255f)
                            input.putFloat((p and 0xFF) / 255f)
                        }
                    }
                    input.rewind()
                    output.rewind()
                    engine.interpreter.run(input, output)
                    output.rewind()
                    val fo = output.asFloatBuffer()

                    // Copy the centre (without the context border) into the big bitmap
                    val cw = min(step, w - x0) * MODEL_SCALE
                    val ch = min(step, h - y0) * MODEL_SCALE
                    val off = PAD * MODEL_SCALE
                    for (j in 0 until ch) {
                        val base = ((off + j) * outRowStride + off) * 3
                        val dst = j * cw
                        for (i in 0 until cw) {
                            val idx = base + i * 3
                            val r = to255(fo.get(idx))
                            val g = to255(fo.get(idx + 1))
                            val b = to255(fo.get(idx + 2))
                            outTile[dst + i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                        }
                    }
                    big.setPixels(outTile, 0, cw, x0 * MODEL_SCALE, y0 * MODEL_SCALE, cw, ch)

                    done++
                    val elapsed = SystemClock.elapsedRealtime() - loopStart
                    val eta = ((elapsed.toDouble() / done) * (total - done) / 1000.0).roundToInt()
                    onProgress(done / total.toFloat(), eta)
                }
            }

            val result = if (outScale == MODEL_SCALE) big else {
                val scaled = Bitmap.createScaledBitmap(big, w * outScale, h * outScale, true)
                if (scaled !== big) big.recycle()
                scaled
            }
            return Result(result, engine.name, SystemClock.elapsedRealtime() - started)
        } finally {
            engine.close()
        }
    }

    private fun to255(v: Float): Int = (v * 255f + 0.5f).toInt().coerceIn(0, 255)
}
