package com.aribs.rsenhancify.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.Toast
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val APP_FOLDER = "Rs Enhancify"

object Files {

    fun timestamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    /** Display name of a content Uri without extension, safe for use in file names. */
    fun baseName(context: Context, uri: Uri, fallback: String): String {
        var name: String? = null
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) name = c.getString(0)
            }
        }
        val raw = name?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: fallback
        return raw.replace(Regex("[^A-Za-z0-9 _\\-]"), "_").take(60).trim().ifEmpty { fallback }
    }

    /**
     * Decode an image Uri as a mutable-safe software ARGB_8888 bitmap.
     * EXIF rotation is applied by ImageDecoder. Images are sub-sampled so the longest side <= maxSide.
     * Transparent images are flattened onto white.
     */
    fun decodeBitmap(context: Context, uri: Uri, maxSide: Int): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            val w = info.size.width
            val h = info.size.height
            val longest = maxOf(w, h)
            if (longest > maxSide) {
                val scale = maxSide.toFloat() / longest
                decoder.setTargetSize(
                    (w * scale).toInt().coerceAtLeast(1),
                    (h * scale).toInt().coerceAtLeast(1)
                )
            }
        }
        // Normalise to opaque ARGB_8888 (handles HARDWARE/RGBA_F16/alpha images)
        val out = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(decoded, 0f, 0f, null)
        }
        if (decoded !== out) decoded.recycle()
        return out
    }

    /** Save a bitmap to Pictures/Rs Enhancify. Returns the content Uri. */
    fun saveImage(
        context: Context,
        bitmap: Bitmap,
        displayName: String,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        quality: Int = 95
    ): Uri {
        val ext = if (format == Bitmap.CompressFormat.PNG) "png" else "jpg"
        val mime = if (format == Bitmap.CompressFormat.PNG) "image/png" else "image/jpeg"
        val uri = insertMedia(
            context,
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            "$displayName.$ext", mime, "${Environment.DIRECTORY_PICTURES}/$APP_FOLDER"
        )
        writeAndPublish(context, uri) { os ->
            if (!bitmap.compress(format, quality, os)) error("Could not encode image")
        }
        return uri
    }

    /** Save any document (PDF / DOCX) to Download/Rs Enhancify. */
    fun saveDocument(
        context: Context,
        displayName: String,
        mime: String,
        writer: (OutputStream) -> Unit
    ): Uri {
        val uri = insertMedia(
            context,
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            displayName, mime, "${Environment.DIRECTORY_DOWNLOADS}/$APP_FOLDER"
        )
        writeAndPublish(context, uri, writer)
        return uri
    }

    private fun insertMedia(context: Context, collection: Uri, name: String, mime: String, relPath: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return context.contentResolver.insert(collection, values)
            ?: error("Could not create file in $relPath")
    }

    private fun writeAndPublish(context: Context, uri: Uri, writer: (OutputStream) -> Unit) {
        val resolver = context.contentResolver
        try {
            resolver.openOutputStream(uri, "w")?.use { os -> writer(os) }
                ?: error("Could not open output file")
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (t: Throwable) {
            // Never leave a broken half-written file behind
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
    }

    fun open(context: Context, uri: Uri, mime: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    fun share(context: Context, uris: List<Uri>, mime: String) {
        if (uris.isEmpty()) return
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]) }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris)) }
        }.apply {
            type = mime
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(context, "No app found to share", Toast.LENGTH_SHORT).show()
        }
    }

    /** Friendly error text for the UI. */
    fun friendly(t: Throwable): String = when {
        t is OutOfMemoryError -> "Not enough memory. Try the Fast quality setting or a smaller file."
        t is SecurityException || t.javaClass.simpleName.contains("Password", true) ->
            "This PDF is password-protected. Please remove the password first."
        t.message.isNullOrBlank() -> "Something went wrong (${t.javaClass.simpleName})"
        else -> t.message!!
    }
}
