package com.example.update

import android.content.Context
import com.example.BuildConfig
import com.example.R
import com.example.data.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

class UpdateRepository(
    context: Context,
    private val scope: CoroutineScope
) {

    private val appContext = context.applicationContext
    private val prefs = PreferencesManager(appContext)

    private val checker = UpdateChecker(BuildConfig.VERSION_NAME)
    private val downloader = UpdateDownloader(appContext)
    private val inspector = ApkInspector(appContext)
    private val installer = ApkInstaller(appContext)

    private val mutex = Mutex()

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private val _launchMessage = MutableStateFlow<String?>(null)
    val launchMessage: StateFlow<String?> = _launchMessage.asStateFlow()

    private var currentRelease: UpdateRelease? = readCachedRelease()

    val appVersionName: String = BuildConfig.VERSION_NAME

    init {
        restoreInstallResult()
        downloader.cleanupStale()
        val cached = currentRelease
        if (_state.value is UpdateUiState.Idle &&
            cached != null &&
            cached.versionCode > BuildConfig.VERSION_CODE
        ) {
            publishAvailability(cached)
        }
    }

    suspend fun checkForUpdate(manual: Boolean) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (mutex.isLocked) return@withContext
        if (manual) {
            val canRetryImmediately = _state.value is UpdateUiState.NoNetwork ||
                _state.value is UpdateUiState.Failed
            if (!canRetryImmediately && now - prefs.getUpdateLastManualCheck() < MANUAL_CHECK_THROTTLE_MS) {
                currentRelease?.let { publishAvailability(it) }
                return@withContext
            }
        } else {
            if (now - prefs.getUpdateLastAttempt() < AUTO_CHECK_INTERVAL_MS) return@withContext
        }
        val blockedUntil = prefs.getUpdateBlockedUntil()
        if (blockedUntil > now) {
            if (manual) _state.value = UpdateUiState.RateLimited(blockedUntil)
            return@withContext
        }

        mutex.withLock {
            val startedAt = System.currentTimeMillis()
            prefs.setUpdateLastAttempt(startedAt)
            if (manual) prefs.setUpdateLastManualCheck(startedAt)
            if (manual) _state.value = UpdateUiState.Checking

            try {
                when (val outcome = checker.fetchLatest(prefs.getUpdateEtag())) {
                    is UpdateChecker.Outcome.NotModified -> {
                        val cached = currentRelease
                        if (cached != null) {
                            publishAvailability(cached)
                        } else {
                            _state.value = UpdateUiState.UpToDate(BuildConfig.VERSION_NAME)
                        }
                    }

                    is UpdateChecker.Outcome.Found -> {
                        prefs.setUpdateEtag(outcome.etag)
                        currentRelease = outcome.release
                        prefs.setCachedReleaseJson(outcome.release.toJson().toString())
                        publishAvailability(outcome.release)
                    }

                    is UpdateChecker.Outcome.Unparsable -> {
                        if (manual) _state.value = UpdateUiState.Unparsable(outcome.releasesUrl)
                    }
                }
            } catch (f: UpdateFailure) {
                if (f.kind == UpdateFailure.Kind.RATE_LIMITED) {
                    val until = f.retryAtMillis.takeIf { it > 0 }
                        ?: (System.currentTimeMillis() + DEFAULT_RATE_LIMIT_MS)
                    prefs.setUpdateBlockedUntil(until)
                    if (manual) _state.value = UpdateUiState.RateLimited(until)
                } else if (manual) {
                    _state.value = if (f.kind == UpdateFailure.Kind.NETWORK) {
                        UpdateUiState.NoNetwork
                    } else {
                        UpdateUiState.Failed(format(f))
                    }
                }
            }
        }
    }

    private fun publishAvailability(release: UpdateRelease) {
        _state.value = if (release.versionCode > BuildConfig.VERSION_CODE) {
            UpdateUiState.Available(release.versionName, release.assetSize, release.notes)
        } else {
            UpdateUiState.UpToDate(release.versionName)
        }
    }

    suspend fun download() = withContext(Dispatchers.IO) {
        if (!mutex.tryLock()) return@withContext
        try {
            val release = currentRelease
            if (release == null) {
                _state.value = UpdateUiState.Idle
                return@withContext
            }
            var attempt = 0
            while (true) {
                attempt++
                try {
                    _state.value = UpdateUiState.Downloading(0L, release.assetSize)
                    var lastPercent = -1
                    val file = downloader.download(release) { received, total ->
                        val percent = if (total > 0L) ((received * 100L) / total).toInt() else 0
                        if (percent != lastPercent) {
                            lastPercent = percent
                            _state.value = UpdateUiState.Downloading(received, total)
                        }
                    }
                    _state.value = UpdateUiState.Verifying
                    inspector.preflight(file, BuildConfig.VERSION_CODE)
                    _state.value = UpdateUiState.ReadyToInstall(release.versionName, release.assetSize)
                    return@withContext
                } catch (f: UpdateFailure) {
                    val retryable = f.kind == UpdateFailure.Kind.SHA ||
                        f.kind == UpdateFailure.Kind.NETWORK
                    if (retryable && attempt < MAX_DOWNLOAD_ATTEMPTS) continue
                    downloader.deleteApk()
                    _state.value = if (f.kind == UpdateFailure.Kind.NETWORK) {
                        UpdateUiState.NoNetwork
                    } else {
                        UpdateUiState.Failed(format(f))
                    }
                    return@withContext
                }
            }
        } finally {
            mutex.unlock()
        }
    }

    suspend fun install() = withContext(Dispatchers.IO) {
        if (!AppVisibility.isForeground) {
            _state.value = UpdateUiState.Failed(appContext.getString(R.string.update_error_generic, "BAL"))
            return@withContext
        }

        if (!installer.canInstallPackages()) {
            prefs.setPendingAction(PENDING_ACTION_INSTALL)
            _state.value = UpdateUiState.NeedsPermission
            withContext(Dispatchers.Main) { installer.openInstallPermissionSettings() }
            return@withContext
        }

        val apk = downloader.apkFile()
        if (!apk.exists() || apk.length() <= 0L) {
            _state.value = UpdateUiState.Failed(appContext.getString(R.string.update_error_invalid_apk))
            return@withContext
        }
        if (!mutex.tryLock()) return@withContext
        try {
            inspector.preflight(apk, BuildConfig.VERSION_CODE)
            val versionName = currentRelease?.versionName ?: BuildConfig.VERSION_NAME
            _state.value = UpdateUiState.Installing
            installer.install(apk)
            downloader.deleteApk()
            scheduleInstallTimeout(versionName)
        } catch (f: UpdateFailure) {
            _state.value = if (f.kind == UpdateFailure.Kind.PERMISSION) {
                UpdateUiState.NeedsPermission
            } else {
                UpdateUiState.Failed(format(f))
            }
        } finally {
            mutex.unlock()
        }
    }

    private fun scheduleInstallTimeout(versionName: String) {
        scope.launch {
            delay(INSTALL_TIMEOUT_MS)
            if (_state.value is UpdateUiState.Installing) {
                _state.value = UpdateUiState.Failed(
                    appContext.getString(R.string.update_error_generic, versionName)
                )
            }
        }
    }

    fun onAppResumed() {
        if (prefs.getPendingAction() != PENDING_ACTION_INSTALL) return
        val granted = installer.canInstallPackages()
        if (!granted) return
        prefs.setPendingAction(null)
        if (_state.value is UpdateUiState.NeedsPermission) {
            _state.value = if (downloader.apkFile().exists()) {
                UpdateUiState.ReadyToInstall(
                    currentRelease?.versionName ?: BuildConfig.VERSION_NAME,
                    currentRelease?.assetSize ?: 0L
                )
            } else {
                UpdateUiState.Idle
            }
        }
    }

    fun onInstallResultReceived(message: String) {
        _state.value = UpdateUiState.Failed(message)
    }

    fun shouldShowBanner(): Boolean {
        val versionCode = currentRelease?.versionCode ?: return false
        return prefs.getUpdateBannerShownFor() != versionCode
    }

    fun markBannerShown() {
        val versionCode = currentRelease?.versionCode ?: return
        prefs.setUpdateBannerShownFor(versionCode)
    }

    fun hasNewerRelease(): Boolean =
        (currentRelease?.versionCode ?: 0) > BuildConfig.VERSION_CODE

    fun newerReleaseName(): String? {
        val release = currentRelease ?: return null
        return if (release.versionCode > BuildConfig.VERSION_CODE) release.versionName else null
    }

    fun shouldShowUpdateDialog(): Boolean {
        val versionCode = currentRelease?.versionCode ?: return false
        return versionCode > BuildConfig.VERSION_CODE &&
            prefs.getUpdateDialogShownFor() != versionCode
    }

    fun markUpdateDialogShown() {
        val versionCode = currentRelease?.versionCode ?: return
        prefs.setUpdateDialogShownFor(versionCode)
    }

    fun consumeLaunchMessage(): String? {
        val message = _launchMessage.value
        _launchMessage.value = null
        return message
    }

    fun releasesPageUrl(): String {
        val fallback = "https://github.com/binhdvk29-hub/clien-volamidle/releases"
        val url = currentRelease?.htmlUrl ?: return fallback
        return if (url.startsWith("https://github.com/binhdvk29-hub/clien-volamidle/")) url else fallback
    }

    private fun format(f: UpdateFailure): String =
        if (f.args.isEmpty()) {
            appContext.getString(f.messageRes)
        } else {
            appContext.getString(f.messageRes, *f.args.toTypedArray())
        }

    private fun restoreInstallResult() {
        val raw = prefs.getInstallResult() ?: return
        prefs.clearInstallResult()
        val parts = raw.split('|')
        val status = parts.getOrNull(0)?.toIntOrNull()
        val message = parts.getOrNull(1)?.replace('¦', '|') ?: return
        if (status == android.content.pm.PackageInstaller.STATUS_SUCCESS) {
            _launchMessage.value = message
            _state.value = UpdateUiState.UpToDate(BuildConfig.VERSION_NAME)
        } else {
            _state.value = UpdateUiState.Failed(message)
        }
    }

    private fun readCachedRelease(): UpdateRelease? {
        val json = prefs.getCachedReleaseJson() ?: return null
        return try {
            UpdateRelease.fromJson(JSONObject(json))
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val AUTO_CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L
        const val AUTO_CHECK_JITTER_MS = 30L * 60L * 1000L
        private const val MANUAL_CHECK_THROTTLE_MS = 60L * 1000L
        private const val DEFAULT_RATE_LIMIT_MS = 15L * 60L * 1000L
        private const val MAX_DOWNLOAD_ATTEMPTS = 2
        private const val INSTALL_TIMEOUT_MS = 20_000L
        const val PENDING_ACTION_INSTALL = "install"
    }
}

private fun UpdateRelease.toJson(): JSONObject = JSONObject().apply {
    put("versionCode", versionCode)
    put("versionName", versionName)
    put("tagName", tagName)
    put("notes", notes)
    put("htmlUrl", htmlUrl)
    put("assetName", assetName)
    put("assetUrl", assetUrl)
    put("assetSize", assetSize)
    put("sha256", sha256 ?: JSONObject.NULL)
}

private fun UpdateRelease.Companion.fromJson(o: JSONObject): UpdateRelease = UpdateRelease(
    versionCode = o.optInt("versionCode", 0),
    versionName = o.optString("versionName", ""),
    tagName = o.optString("tagName", ""),
    notes = o.optString("notes", ""),
    htmlUrl = o.optString("htmlUrl", ""),
    assetName = o.optString("assetName", ""),
    assetUrl = o.optString("assetUrl", ""),
    assetSize = o.optLong("assetSize", 0L),
    sha256 = o.optString("sha256", "").takeIf { it.isNotBlank() && it != "null" }
)
