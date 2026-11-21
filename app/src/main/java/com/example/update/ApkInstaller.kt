package com.example.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.example.R
import java.io.File

class ApkInstaller(private val context: Context) {

    fun canInstallPackages(): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            @Suppress("DEPRECATION")
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.INSTALL_NON_MARKET_APPS,
                0
            ) == 1
        }
    } catch (_: Exception) {
        false
    }

    fun openInstallPermissionSettings() {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                add(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                add(Intent(Settings.ACTION_SECURITY_SETTINGS))
            }
            add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            add(Intent(Settings.ACTION_SETTINGS))
        }
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
    }

    fun install(apk: File) {
        if (!canInstallPackages()) {
            throw UpdateFailure(R.string.update_state_needs_permission, kind = UpdateFailure.Kind.PERMISSION)
        }
        if (!apk.exists() || apk.length() <= 0L) {
            throw UpdateFailure(R.string.update_error_invalid_apk, kind = UpdateFailure.Kind.INVALID_APK)
        }

        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        params.setSize(apk.length())

        val sessionId = try {
            installer.createSession(params)
        } catch (_: SecurityException) {
            throw UpdateFailure(R.string.update_state_needs_permission, kind = UpdateFailure.Kind.PERMISSION)
        } catch (t: Throwable) {
            throw UpdateFailure(
                R.string.update_error_generic,
                args = listOf(t.javaClass.simpleName),
                kind = UpdateFailure.Kind.GENERIC
            )
        }

        var committed = false
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0L, apk.length()).use { out ->
                    apk.inputStream().use { input ->
                        input.copyTo(out, 64 * 1024)
                    }
                    session.fsync(out)
                }
                session.commit(resultPendingIntent().intentSender)
                committed = true
            }
        } catch (_: SecurityException) {
            throw UpdateFailure(R.string.update_state_needs_permission, kind = UpdateFailure.Kind.PERMISSION)
        } catch (t: Throwable) {
            throw UpdateFailure(
                R.string.update_error_generic,
                args = listOf(t.javaClass.simpleName),
                kind = UpdateFailure.Kind.GENERIC
            )
        } finally {
            if (!committed) {
                runCatching { installer.abandonSession(sessionId) }
            }
        }
    }

    private fun resultPendingIntent(): PendingIntent {
        val base = Intent(context, InstallResultReceiver::class.java)
            .setPackage(context.packageName)
            .setAction(InstallResultReceiver.ACTION_INSTALL_RESULT)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_MUTABLE
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, base, flags)
    }

    companion object {
        private const val REQUEST_CODE = 8421
    }
}
