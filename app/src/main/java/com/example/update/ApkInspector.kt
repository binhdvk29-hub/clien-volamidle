package com.example.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.example.R
import java.io.File
import java.security.MessageDigest

class ApkInspector(private val context: Context) {

    data class Inspected(
        val packageName: String,
        val versionCode: Long,
        val signerDigests: Set<String>
    )

    fun preflight(apk: File, currentVersionCode: Int): Inspected {
        if (!apk.exists() || apk.length() <= 0L) {
            throw UpdateFailure(R.string.update_error_invalid_apk, kind = UpdateFailure.Kind.INVALID_APK)
        }

        val pm = context.packageManager
        val archive = try {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apk.absolutePath, archiveFlags())
        } catch (_: Exception) {
            null
        } ?: throw UpdateFailure(R.string.update_error_invalid_apk, kind = UpdateFailure.Kind.INVALID_APK)

        if (archive.packageName != context.packageName) {
            throw UpdateFailure(R.string.update_error_invalid_apk, kind = UpdateFailure.Kind.INVALID_APK)
        }

        val archiveCode = versionCodeOf(archive)
        if (archiveCode <= currentVersionCode.toLong()) {
            throw UpdateFailure(R.string.update_error_downgrade, kind = UpdateFailure.Kind.DOWNGRADE)
        }

        val archiveSigners = signerDigestsOf(archive)
        val installedSigners = installedSignerDigests()
        if (archiveSigners.isEmpty() || installedSigners.isEmpty()) {
            throw UpdateFailure(R.string.update_error_invalid_apk, kind = UpdateFailure.Kind.INVALID_APK)
        }
        if (archiveSigners != installedSigners) {
            throw UpdateFailure(R.string.update_error_signature, kind = UpdateFailure.Kind.SIGNATURE)
        }

        return Inspected(archive.packageName, archiveCode, archiveSigners)
    }

    private fun installedSignerDigests(): Set<String> {
        val pm = context.packageManager
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }
            signerDigestsOf(info)
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun archiveFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

    private fun versionCodeOf(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }

    private fun signerDigestsOf(info: PackageInfo): Set<String> {
        val out = LinkedHashSet<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo
            if (signing != null) {
                val current = signing.apkContentsSigners
                val history = signing.signingCertificateHistory
                (current?.asList() ?: history?.asList()).orEmpty().forEach { sig ->
                    out.add(sha256Hex(sig.toByteArray()))
                }
            }
        }
        if (out.isEmpty()) {
            @Suppress("DEPRECATION")
            info.signatures?.forEach { sig -> out.add(sha256Hex(sig.toByteArray())) }
        }
        return out
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
