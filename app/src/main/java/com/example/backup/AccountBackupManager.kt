package com.example.backup

import android.content.Context
import android.os.Environment
import com.example.data.PreferencesManager
import com.example.model.AccountConfig
import com.example.model.TabLimits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class BackupTransferResult(
    val ok: Boolean,
    val message: String,
    val accountCount: Int = 0
)

class AccountBackupManager(private val context: Context) {

    fun getBackupFile(): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val folder = File(downloads, "VoLamIdleBackup").apply { mkdirs() }
        return File(folder, "volam_accounts_backup.json")
    }

    suspend fun exportAll(prefs: PreferencesManager, tabCount: Int): Result<BackupTransferResult> =
        withContext(Dispatchers.IO) {
            try {
                val root = JSONObject()
                root.put("version", 1)
                root.put("exportedAt", System.currentTimeMillis())
                root.put("cluster", prefs.getCluster())
                root.put("tabCount", tabCount.coerceIn(TabLimits.MIN, TabLimits.HARD_MAX))

                val accArr = JSONArray()
                val autoArr = JSONArray()
                var nonEmpty = 0

                for (i in 0 until TabLimits.SLOT_COUNT) {
                    val acc = prefs.getAccount(i)
                    val aObj = JSONObject()
                    aObj.put("tabIndex", acc.tabIndex)
                    aObj.put("username", acc.username)
                    aObj.put("password", acc.password)
                    aObj.put("customUrl", acc.customUrl)
                    aObj.put("rawAccount", acc.toString())
                    accArr.put(aObj)
                    if (acc.username.isNotBlank() || acc.password.isNotBlank()) {
                        nonEmpty++
                    }

                    val cfgRaw = prefs.getAutoConfig(i)
                    autoArr.put(cfgRaw ?: "")
                }

                root.put("accounts", accArr)
                root.put("autoConfigs", autoArr)

                val outFile = getBackupFile()
                outFile.writeText(root.toString(2), java.nio.charset.StandardCharsets.UTF_8)

                Result.success(
                    BackupTransferResult(
                        ok = true,
                        message = "Đã lưu $nonEmpty tài khoản vào ${outFile.name}",
                        accountCount = nonEmpty
                    )
                )
            } catch (e: Throwable) {
                Result.failure(e)
            }
        }

    suspend fun importAll(prefs: PreferencesManager): Result<BackupTransferResult> =
        withContext(Dispatchers.IO) {
            try {
                val file = getBackupFile()
                if (!file.exists() || !file.isFile) {
                    return@withContext Result.success(
                        BackupTransferResult(
                            ok = false,
                            message = "Chưa tìm thấy file sao lưu tại Download/VoLamIdleBackup/volam_accounts_backup.json"
                        )
                    )
                }

                val raw = file.readText(java.nio.charset.StandardCharsets.UTF_8)
                val root = JSONObject(raw)

                root.optString("cluster", "").takeIf { it.isNotBlank() }?.let { cl ->
                    prefs.setCluster(cl)
                }
                val savedTabCount = root.optInt("tabCount", 0)
                if (savedTabCount in TabLimits.MIN..TabLimits.HARD_MAX) {
                    prefs.setTabCount(savedTabCount)
                }

                val accArr = root.optJSONArray("accounts")
                var restoredCount = 0
                if (accArr != null) {
                    for (idx in 0 until accArr.length()) {
                        val obj = accArr.optJSONObject(idx) ?: continue
                        val tIdx = obj.optInt("tabIndex", idx)
                        if (tIdx !in 0 until TabLimits.SLOT_COUNT) continue
                        val user = obj.optString("username", "")
                        val pass = obj.optString("password", "")
                        val cfg = AccountConfig(
                            tabIndex = tIdx,
                            username = user,
                            password = pass,
                            customUrl = obj.optString("customUrl", "")
                        )
                        prefs.saveAccount(cfg)
                        if (user.isNotBlank() || pass.isNotBlank()) {
                            restoredCount++
                        }
                    }
                }

                val autoArr = root.optJSONArray("autoConfigs")
                if (autoArr != null) {
                    for (i in 0 until minOf(autoArr.length(), TabLimits.SLOT_COUNT)) {
                        val cfgStr = autoArr.optString(i, "")
                        if (cfgStr.isNotBlank()) {
                            prefs.setAutoConfig(i, cfgStr)
                        }
                    }
                }

                Result.success(
                    BackupTransferResult(
                        ok = true,
                        message = "Đã khôi phục $restoredCount tài khoản + cấu hình auto",
                        accountCount = restoredCount
                    )
                )
            } catch (e: Throwable) {
                Result.failure(e)
            }
        }
}
