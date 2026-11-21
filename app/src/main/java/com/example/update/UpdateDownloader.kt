package com.example.update

import android.content.Context
import android.os.StatFs
import android.util.Log
import com.example.R
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

class UpdateDownloader(
    private val context: Context,
    private val client: OkHttpClient = HttpHops.downloadClient()
) {

    fun download(release: UpdateRelease, onProgress: (Long, Long) -> Unit): File {
        val dir = updatesDir()
        val part = File(dir, PART_NAME)
        val finalFile = File(dir, APK_NAME)
        assertInside(dir, part)
        assertInside(dir, finalFile)

        if (release.assetUrl.isBlank()) {
            throw UpdateFailure(R.string.update_error_no_asset, kind = UpdateFailure.Kind.NO_ASSET)
        }
        if (release.assetSize > MAX_DOWNLOAD_BYTES) {
            throw UpdateFailure(
                R.string.update_error_storage,
                args = listOf(ByteSize.format(release.assetSize)),
                kind = UpdateFailure.Kind.STORAGE
            )
        }
        requireFreeSpace(release.assetSize)

        runCatching { finalFile.delete() }
        runCatching { part.delete() }

        val limit = if (release.assetSize > 0L) {
            minOf(MAX_DOWNLOAD_BYTES, (release.assetSize * 105L) / 100L)
        } else {
            MAX_DOWNLOAD_BYTES
        }

        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var received = 0L

            val request = Request.Builder()
                .url(release.assetUrl)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            val response = try {
                HttpHops.execute(client, request)
            } catch (io: IOException) {
                throw UpdateFailure(
                    R.string.update_state_nonetwork,
                    kind = UpdateFailure.Kind.NETWORK
                )
            }

            response.use { res ->
                if (res.code != 200) {
                    throw UpdateFailure(
                        R.string.update_error_generic,
                        args = listOf("HTTP ${res.code}"),
                        kind = UpdateFailure.Kind.HTTP
                    )
                }
                val contentType = res.header("Content-Type").orEmpty()
                if (contentType.isNotEmpty() && !contentType.contains(APK_MIME, ignoreCase = true)) {
                    Log.d(
                        TAG,
                        "asset content-type=$contentType (vẫn tiếp tục, host=${res.request.url.host})"
                    )
                }

                val body = res.body ?: throw UpdateFailure(
                    R.string.update_error_invalid_apk,
                    kind = UpdateFailure.Kind.INVALID_APK
                )
                val total = if (release.assetSize > 0L) release.assetSize else body.contentLength()

                FileOutputStream(part).use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(BUFFER)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            received += read
                            if (received > limit) {
                                throw UpdateFailure(
                                    R.string.update_error_storage,
                                    args = listOf(ByteSize.format(limit)),
                                    kind = UpdateFailure.Kind.STORAGE
                                )
                            }
                            digest.update(buffer, 0, read)
                            out.write(buffer, 0, read)
                            onProgress(received, total)
                        }
                    }
                    out.flush()
                    out.fd.sync()
                }

                if (received == 0L) {
                    throw UpdateFailure(
                        R.string.update_error_invalid_apk,
                        kind = UpdateFailure.Kind.INVALID_APK
                    )
                }
                if (release.assetSize > 0L && received != release.assetSize) {
                    throw UpdateFailure(R.string.update_error_sha, kind = UpdateFailure.Kind.SHA)
                }

                val expected = release.sha256
                if (!expected.isNullOrBlank()) {
                    val actual = digest.digest().joinToString("") { "%02x".format(it) }
                    if (!actual.equals(expected, ignoreCase = true)) {
                        throw UpdateFailure(R.string.update_error_sha, kind = UpdateFailure.Kind.SHA)
                    }
                }
            }

            if (!part.renameTo(finalFile)) {
                throw UpdateFailure(R.string.update_error_storage, kind = UpdateFailure.Kind.STORAGE)
            }
            return finalFile
        } catch (t: Throwable) {
            runCatching { part.delete() }
            throw t
        }
    }

    fun cleanupStale(now: Long = System.currentTimeMillis()) {
        val dir = updatesDir()
        val files = dir.listFiles() ?: return
        for (f in files) {
            val stale = now - f.lastModified() > STALE_MS
            if (f.name.endsWith(PART_SUFFIX) || stale) {
                runCatching { f.delete() }
            }
        }
    }

    fun deleteApk() {
        val dir = updatesDir()
        runCatching { File(dir, APK_NAME).delete() }
        runCatching { File(dir, PART_NAME).delete() }
    }

    fun apkFile(): File = File(updatesDir(), APK_NAME)

    private fun updatesDir(): File {
        val dir = File(context.cacheDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun assertInside(dir: File, file: File) {
        val root = dir.canonicalPath + File.separator
        if (!file.canonicalPath.startsWith(root)) {
            throw UpdateFailure(R.string.update_error_storage, kind = UpdateFailure.Kind.STORAGE)
        }
    }

    private fun requireFreeSpace(size: Long) {
        val needed = maxOf(3L * size, MIN_FREE_RESERVE)
        val available = try {
            StatFs(context.cacheDir.path).availableBytes
        } catch (_: Exception) {
            return
        }
        if (available < needed) {
            throw UpdateFailure(
                R.string.update_error_storage,
                args = listOf(ByteSize.format(needed)),
                kind = UpdateFailure.Kind.STORAGE
            )
        }
    }

    companion object {
        const val DIR_NAME = "updates"
        const val APK_NAME = "update.apk"
        const val PART_NAME = "update.apk.part"
        private const val PART_SUFFIX = ".part"
        const val MAX_DOWNLOAD_BYTES = 200L * 1024L * 1024L
        private const val MIN_FREE_RESERVE = 100L * 1024L * 1024L
        private const val STALE_MS = 24L * 60L * 60L * 1000L
        private const val BUFFER = 64 * 1024
        private const val USER_AGENT = "VOLAMIdleManager (Android)"
        private const val TAG = "UpdateDownloader"
        private const val APK_MIME = "application/vnd.android.package-archive"
    }
}
