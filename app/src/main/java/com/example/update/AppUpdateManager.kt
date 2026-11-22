package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class AppReleaseInfo(
    val repo: String,
    val isTestChannel: Boolean,
    val tagName: String,
    val versionName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkFileName: String
)

sealed class UpdateCheckState {
    data object Idle : UpdateCheckState()
    data object Checking : UpdateCheckState()
    data class UpToDate(val checkedRepo: String) : UpdateCheckState()
    data class Available(val release: AppReleaseInfo) : UpdateCheckState()
    data class Downloading(val release: AppReleaseInfo, val progressPercent: Int) : UpdateCheckState()
    data class Error(val message: String) : UpdateCheckState()
}

class AppUpdateManager(private val context: Context) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val updateRepo: String = BuildConfig.UPDATE_REPO
    val isTestChannel: Boolean = BuildConfig.IS_TEST_CHANNEL

    suspend fun checkLatestRelease(): Result<AppReleaseInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val apiUrl = "https://api.github.com/repos/$updateRepo/releases/latest"
            val req = Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "VoLamIdle-Android/${BuildConfig.VERSION_NAME}")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.code == 404) return@use null
                if (!resp.isSuccessful) {
                    error("HTTP ${resp.code} khi kiểm tra $updateRepo")
                }
                val body = resp.body?.string().orEmpty()
                val json = JSONObject(body)
                val tag = json.optString("tag_name", "").trim()
                if (tag.isBlank()) return@use null

                val remoteVer = tag.removePrefix("v").removePrefix("V").trim()
                if (!isRemoteNewer(remoteVer, BuildConfig.VERSION_NAME)) {
                    return@use null
                }

                val assets = json.optJSONArray("assets")
                var apkUrl = ""
                var apkName = "volamidle-$remoteVer.apk"
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val name = asset.optString("name", "")
                        val url = asset.optString("browser_download_url", "")
                        if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                            apkUrl = url
                            apkName = name
                            break
                        }
                    }
                }
                if (apkUrl.isBlank()) return@use null

                AppReleaseInfo(
                    repo = updateRepo,
                    isTestChannel = isTestChannel,
                    tagName = tag,
                    versionName = remoteVer,
                    releaseNotes = json.optString("body", "").trim().take(600),
                    apkDownloadUrl = apkUrl,
                    apkFileName = apkName
                )
            }
        }
    }

    suspend fun downloadAndInstall(
        release: AppReleaseInfo,
        onProgress: (Int) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val baseDir = context.externalCacheDir ?: context.cacheDir
            val updatesDir = File(baseDir, "updates").apply { mkdirs() }
            updatesDir.listFiles()?.forEach { old ->
                if (old.isFile && old.name.endsWith(".apk", ignoreCase = true)) {
                    runCatching { old.delete() }
                }
            }

            val safeName = release.apkFileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val outFile = File(updatesDir, safeName)

            val req = Request.Builder()
                .url(release.apkDownloadUrl)
                .header("User-Agent", "VoLamIdle-Android/${BuildConfig.VERSION_NAME}")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Tải APK lỗi HTTP ${resp.code}")
                val body = resp.body ?: error("Không đọc được dữ liệu APK")
                val total = body.contentLength()
                var downloaded = 0L
                var lastPct = -1

                body.byteStream().use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            output.write(buf, 0, read)
                            downloaded += read
                            if (total > 0L) {
                                val pct = ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                                if (pct != lastPct) {
                                    lastPct = pct
                                    onProgress(pct)
                                }
                            }
                        }
                        output.flush()
                    }
                }
            }
            onProgress(100)

            withContext(Dispatchers.Main) {
                launchApkInstaller(outFile)
            }
        }
    }

    private fun launchApkInstaller(apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val permIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(permIntent)
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(installIntent)
    }

    companion object {
        fun isRemoteNewer(remoteRaw: String, localRaw: String): Boolean {
            val rNums = parseVersionNums(remoteRaw)
            val lNums = parseVersionNums(localRaw)
            val maxLen = maxOf(rNums.size, lNums.size)
            for (i in 0 until maxLen) {
                val rv = rNums.getOrElse(i) { 0 }
                val lv = lNums.getOrElse(i) { 0 }
                if (rv != lv) return rv > lv
            }
            return false
        }

        private fun parseVersionNums(raw: String): List<Int> {
            val clean = raw.trim().removePrefix("v").removePrefix("V")
                .substringBefore('-')
            return clean.split('.')
                .mapNotNull { part -> part.filter { it.isDigit() }.toIntOrNull() }
        }
    }
}
