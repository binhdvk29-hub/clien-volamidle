package com.example.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.example.BuildConfig
import com.example.R
import com.example.VolamIdleApp
import com.example.data.PreferencesManager

class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
        val rawMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = parcelableExtra(intent, Intent.EXTRA_INTENT)
            if (confirm != null && AppVisibility.isForeground) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { app.startActivity(confirm) }
            }
            return
        }

        val mapped = mapStatus(app, status, rawMessage)
        val record = buildString {
            append(status)
            append('|')
            append(mapped.replace('|', '¦'))
            append('|')
            append(BuildConfig.VERSION_NAME)
            append('|')
            append(System.currentTimeMillis())
        }
        PreferencesManager(app).setInstallResult(record)

        if (status != PackageInstaller.STATUS_SUCCESS) {
            (app as? VolamIdleApp)?.updateRepository?.onInstallResultReceived(mapped)
        }
    }

    private fun mapStatus(context: Context, status: Int, rawMessage: String): String {
        val upper = rawMessage.uppercase()
        if (upper.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE")) {
            return context.getString(R.string.update_error_signature)
        }
        if (upper.contains("INSTALL_FAILED_VERSION_DOWNGRADE")) {
            return context.getString(R.string.update_error_downgrade)
        }
        return when (status) {
            PackageInstaller.STATUS_SUCCESS ->
                context.getString(R.string.update_result_success, BuildConfig.VERSION_NAME)

            PackageInstaller.STATUS_FAILURE_ABORTED ->
                context.getString(R.string.update_error_aborted)

            PackageInstaller.STATUS_FAILURE_BLOCKED ->
                context.getString(R.string.update_error_blocked)

            PackageInstaller.STATUS_FAILURE_CONFLICT ->
                context.getString(R.string.update_result_conflict)

            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                context.getString(R.string.update_error_signature)

            PackageInstaller.STATUS_FAILURE_INVALID ->
                context.getString(R.string.update_error_invalid_apk)

            PackageInstaller.STATUS_FAILURE_STORAGE ->
                context.getString(R.string.update_result_storage)

            PackageInstaller.STATUS_FAILURE_TIMEOUT ->
                context.getString(R.string.update_result_timeout)

            else -> context.getString(R.string.update_error_generic, status.toString())
        }
    }

    private fun parcelableExtra(intent: Intent, key: String): Intent? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(key, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(key) as? Intent
        }
    } catch (_: Exception) {
        null
    }

    companion object {
        const val ACTION_INSTALL_RESULT = "com.example.update.action.INSTALL_RESULT"
    }
}
