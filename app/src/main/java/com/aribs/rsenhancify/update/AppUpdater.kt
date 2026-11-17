package com.aribs.rsenhancify.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.aribs.rsenhancify.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Release info published by CI as latest.json next to the APK. */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val notes: String,
    val sizeBytes: Long = -1
)

/**
 * In-app updater: latest.json -> download APK into app cache (with progress) -> SHA-256 check -> system installer.
 * Only https URLs on GitHub hosts are accepted. Android itself rejects APKs signed with a different key.
 */
object AppUpdater {

    private val TRUSTED_HOST_SUFFIXES = listOf("github.com", "githubusercontent.com")
    private const val APK_FILE_NAME = "rs-enhancify-update.apk"
    private const val MAX_REDIRECTS = 5

    private fun isTrustedUrl(url: String): Boolean {
        val uri = Uri.parse(url)
        if (uri.scheme != "https") return false
        val host = uri.host?.lowercase() ?: return false
        return TRUSTED_HOST_SUFFIXES.any { host == it || host.endsWith(".$it") }
    }

    /** Opens a connection, following redirects manually so every hop is checked against the allow-list. */
    private fun open(startUrl: String): HttpURLConnection {
        var url = startUrl
        repeat(MAX_REDIRECTS + 1) {
            if (!isTrustedUrl(url)) throw IOException("Untrusted update URL")
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "RsEnhancify/${BuildConfig.VERSION_NAME}")
                setRequestProperty("Cache-Control", "no-cache")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val next = conn.getHeaderField("Location")
                conn.disconnect()
                if (next.isNullOrBlank()) throw IOException("Bad redirect")
                url = URL(URL(url), next).toString()
            } else {
                return conn
            }
        }
        throw IOException("Too many redirects")
    }

    /** Returns update info if a newer version exists, null if up to date. Throws on network error. */
    suspend fun fetchNewerVersion(): UpdateInfo? = withContext(Dispatchers.IO) {
        val conn = open(BuildConfig.UPDATE_MANIFEST_URL)
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Update server returned ${conn.responseCode}")
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            if (text.length > 64 * 1024) throw IOException("Invalid update manifest")
            val json = JSONObject(text)
            val info = UpdateInfo(
                versionCode = json.getInt("versionCode"),
                versionName = json.getString("versionName"),
                apkUrl = json.getString("apkUrl"),
                sha256 = json.getString("sha256").lowercase(),
                notes = json.optString("notes", ""),
                sizeBytes = json.optLong("size", -1)
            )
            if (!isTrustedUrl(info.apkUrl) || !info.sha256.matches(Regex("[0-9a-f]{64}"))) {
                throw IOException("Invalid update manifest")
            }
            if (info.versionCode > BuildConfig.VERSION_CODE) info else null
        } finally {
            conn.disconnect()
        }
    }

    /** Downloads the APK into app cache, verifying SHA-256. Reports progress 0f..1f (or -1f if size unknown). */
    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, APK_FILE_NAME)
        val partial = File(dir, "$APK_FILE_NAME.part")
        partial.delete()

        val conn = open(info.apkUrl)
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Download failed (${conn.responseCode})")
            }
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            var lastPercent = -1
            conn.inputStream.use { input ->
                partial.outputStream().buffered(256 * 1024).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        done += read
                        if (done > 300L * 1024 * 1024) throw IOException("Update file is too large")
                        val percent = if (total > 0) ((done * 100) / total).toInt() else -1
                        if (percent != lastPercent) {
                            lastPercent = percent
                            val p = if (total > 0) (done.toFloat() / total).coerceAtMost(1f) else -1f
                            launch(Dispatchers.Main) { onProgress(p) }
                        }
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (actual != info.sha256) {
                throw IOException("Downloaded file is corrupted (checksum mismatch). Please try again.")
            }
            target.delete()
            if (!partial.renameTo(target)) throw IOException("Could not save update file")
            target
        } catch (e: Exception) {
            partial.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }

    /** Remove leftover APKs (e.g. after the update was installed). */
    fun cleanup(context: Context) {
        runCatching { File(context.cacheDir, "updates").listFiles()?.forEach { it.delete() } }
    }

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** One-time Android screen: "Install unknown apps" for Rs Enhancify. */
    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Hands the verified APK to Android's package installer. */
    fun launchInstaller(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
