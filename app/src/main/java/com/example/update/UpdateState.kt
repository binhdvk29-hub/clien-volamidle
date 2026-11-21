package com.example.update

data class UpdateRelease(
    val versionCode: Int,
    val versionName: String,
    val tagName: String,
    val notes: String,
    val htmlUrl: String,
    val assetName: String,
    val assetUrl: String,
    val assetSize: Long,
    val sha256: String?
) {
    companion object
}

sealed interface UpdateUiState {

    data object Idle : UpdateUiState

    data object Checking : UpdateUiState

    data class UpToDate(val versionName: String) : UpdateUiState

    data class Available(
        val versionName: String,
        val sizeBytes: Long,
        val notes: String
    ) : UpdateUiState

    data class Downloading(val bytes: Long, val total: Long) : UpdateUiState

    data object Verifying : UpdateUiState

    data class ReadyToInstall(val versionName: String, val sizeBytes: Long = 0L) : UpdateUiState

    data object NeedsPermission : UpdateUiState

    data object Installing : UpdateUiState

    data class Failed(val reason: String) : UpdateUiState

    data object NoNetwork : UpdateUiState

    data class RateLimited(val retryAtMillis: Long) : UpdateUiState

    data class Unparsable(val releasesUrl: String) : UpdateUiState
}

class UpdateFailure(
    val messageRes: Int,
    val args: List<Any> = emptyList(),
    val kind: Kind = Kind.GENERIC,
    val retryAtMillis: Long = 0L
) : Exception("update-failure:$kind") {

    enum class Kind {
        NETWORK,
        RATE_LIMITED,
        SERVER,
        HTTP,
        BAD_HOST,
        NO_ASSET,
        SHA,
        STORAGE,
        INVALID_APK,
        SIGNATURE,
        DOWNGRADE,
        PERMISSION,
        GENERIC
    }
}
