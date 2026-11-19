package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.AccountConfig

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("volam_idle_manager_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CLUSTER = "pref_cluster"
        private const val KEY_FPS = "pref_fps"
        private const val KEY_RX_OFFSET = "pref_rx_offset"
        private const val KEY_TX_OFFSET = "pref_tx_offset"
        private const val KEY_LEGACY_USER_PREFIX = "pref_user_tab_"
        private const val KEY_LEGACY_PASS_PREFIX = "pref_pass_tab_"
        private const val KEY_LEGACY_AUTO_PREFIX = "pref_auto_tab_"
        private const val KEY_LEGACY_URL_PREFIX = "pref_url_tab_"

        private const val KEY_AUTO_CFG = "pref_auto_cfg"
        private const val KEY_AUTO_CFG_TAB_PREFIX = "pref_auto_cfg_tab_"

        private const val KEY_TAB_COUNT = "pref_tab_count"
        private const val KEY_TAB_WARN_SHOWN = "pref_tab_warn_shown"
        private const val KEY_HIDE_MAP = "pref_hide_map"
        private const val KEY_HIDE_GAME = "pref_hide_game"

        private const val KEY_DND_OVERRIDE_ACTIVE = "sleep_dnd_override_active"
        private const val KEY_DND_PREV_FILTER = "sleep_dnd_prev_filter"
        private const val KEY_DND_PREV_CATEGORIES = "sleep_dnd_prev_categories"
        private const val KEY_DND_PREV_CALL_SENDERS = "sleep_dnd_prev_call_senders"
        private const val KEY_DND_PREV_MSG_SENDERS = "sleep_dnd_prev_msg_senders"
        private const val KEY_DND_PREV_VISUAL_EFFECTS = "sleep_dnd_prev_visual_effects"
        private const val KEY_DND_PREV_CONV_SENDERS = "sleep_dnd_prev_conv_senders"
        private const val KEY_SLEEP_WAKE_PROMPT_SHOWN = "sleep_wake_prompt_shown"

        private const val GAME_HOST = "volamidle.pages.dev"

        fun isTrustedGameUrl(url: String): Boolean {
            val trimmed = url.trim()
            if (trimmed.isEmpty()) return true
            val uri = try {
                android.net.Uri.parse(trimmed)
            } catch (_: Exception) {
                return false
            }
            if (uri.scheme?.lowercase() != "https") return false
            val host = uri.host?.lowercase() ?: return false
            return host == GAME_HOST || host.endsWith(".$GAME_HOST")
        }
    }

    fun getAutoConfig(): String = prefs.getString(KEY_AUTO_CFG, "") ?: ""

    fun setAutoConfig(json: String) {
        prefs.edit().putString(KEY_AUTO_CFG, json).commit()
    }

    fun getAutoConfig(tabIndex: Int): String {
        val perTab = prefs.getString("$KEY_AUTO_CFG_TAB_PREFIX$tabIndex", null)
        return perTab ?: ""
    }

    fun setAutoConfig(tabIndex: Int, json: String) {
        prefs.edit().putString("$KEY_AUTO_CFG_TAB_PREFIX$tabIndex", json).commit()
    }

    private fun getClusterKey(cluster: String): String {
        return cluster.replace(" ", "_").lowercase()
    }

    fun getCluster(): String {
        return prefs.getString(KEY_CLUSTER, "Cụm 1") ?: "Cụm 1"
    }

    fun setCluster(cluster: String) {
        prefs.edit().putString(KEY_CLUSTER, cluster).commit()
    }

    fun getClusterUrl(cluster: String): String {
        return when (cluster) {
            "Cụm 2" -> "https://volamidle.pages.dev"
            "Cụm 3" -> "https://volamidle.pages.dev"
            else -> "https://volamidle.pages.dev"
        }
    }

    fun getFps(): Int {
        return prefs.getInt(KEY_FPS, 30)
    }

    fun setFps(fps: Int) {
        prefs.edit().putInt(KEY_FPS, fps).apply()
    }

    fun getHideMap(): Boolean = prefs.getBoolean(KEY_HIDE_MAP, false)

    fun setHideMap(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_MAP, enabled).apply()
    }

    fun getHideGame(): Boolean = prefs.getBoolean(KEY_HIDE_GAME, false)

    fun setHideGame(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_GAME, enabled).apply()
    }

    fun getAccount(tabIndex: Int, cluster: String = getCluster()): AccountConfig {
        val cKey = getClusterKey(cluster)
        val clusterNum = when (cluster) {
            "Cụm 2" -> "2"
            "Cụm 3" -> "3"
            else -> "1"
        }
        val clusterUserKey = "user_cluster_${cKey}_tab_$tabIndex"
        val clusterPassKey = "pass_cluster_${cKey}_tab_$tabIndex"
        val clusterAutoKey = "auto_cluster_${cKey}_tab_$tabIndex"
        val clusterUrlKey = "url_cluster_${cKey}_tab_$tabIndex"

        var user = prefs.getString(clusterUserKey, "") ?: ""
        var pass = prefs.getString(clusterPassKey, "") ?: ""
        var auto = prefs.getBoolean(clusterAutoKey, true)
        var customUrl = prefs.getString(clusterUrlKey, "") ?: ""

        if (user.isBlank()) {
            val numUser = prefs.getString("user_cluster_${clusterNum}_tab_$tabIndex", "") ?: ""
            if (numUser.isNotBlank()) {
                user = numUser
                pass = prefs.getString("pass_cluster_${clusterNum}_tab_$tabIndex", "") ?: ""
                auto = prefs.getBoolean("auto_cluster_${clusterNum}_tab_$tabIndex", true)
                customUrl = prefs.getString("url_cluster_${clusterNum}_tab_$tabIndex", "") ?: ""
            }
        }

        if (user.isBlank()) {
            val directUser = prefs.getString("user_cluster_${cluster}_tab_$tabIndex", "") ?: ""
            if (directUser.isNotBlank()) {
                user = directUser
                pass = prefs.getString("pass_cluster_${cluster}_tab_$tabIndex", "") ?: ""
                auto = prefs.getBoolean("auto_cluster_${cluster}_tab_$tabIndex", true)
                customUrl = prefs.getString("url_cluster_${cluster}_tab_$tabIndex", "") ?: ""
            }
        }

        if (user.isBlank()) {
            val legacyUser = prefs.getString("$KEY_LEGACY_USER_PREFIX$tabIndex", "") ?: ""
            if (legacyUser.isNotBlank()) {
                user = legacyUser
                pass = prefs.getString("$KEY_LEGACY_PASS_PREFIX$tabIndex", "") ?: ""
                auto = prefs.getBoolean("$KEY_LEGACY_AUTO_PREFIX$tabIndex", true)
                customUrl = prefs.getString("$KEY_LEGACY_URL_PREFIX$tabIndex", "") ?: ""
            }
        }

        if (user.isBlank()) {
            val bareUser = prefs.getString("user_tab_$tabIndex", "") ?: ""
            if (bareUser.isNotBlank()) {
                user = bareUser
                pass = prefs.getString("pass_tab_$tabIndex", "") ?: ""
            }
        }

        return AccountConfig(tabIndex, user.trim(), pass.trim(), auto, customUrl.trim())
    }

    fun saveAccount(config: AccountConfig, cluster: String = getCluster()) {
        val cKey = getClusterKey(cluster)
        val clusterNum = when (cluster) {
            "Cụm 2" -> "2"
            "Cụm 3" -> "3"
            else -> "1"
        }
        val cleanUser = config.username.trim()
        val cleanPass = config.password.trim()
        val cleanUrl = config.customUrl.trim()

        prefs.edit()
            .putString("user_cluster_${cKey}_tab_${config.tabIndex}", cleanUser)
            .putString("pass_cluster_${cKey}_tab_${config.tabIndex}", cleanPass)
            .putBoolean("auto_cluster_${cKey}_tab_${config.tabIndex}", config.autoLoginEnabled)
            .putString("url_cluster_${cKey}_tab_${config.tabIndex}", cleanUrl)
            .putString("user_cluster_${clusterNum}_tab_${config.tabIndex}", cleanUser)
            .putString("pass_cluster_${clusterNum}_tab_${config.tabIndex}", cleanPass)
            .putBoolean("auto_cluster_${clusterNum}_tab_${config.tabIndex}", config.autoLoginEnabled)
            .putString("url_cluster_${clusterNum}_tab_${config.tabIndex}", cleanUrl)
            .putString("user_cluster_${cluster}_tab_${config.tabIndex}", cleanUser)
            .putString("pass_cluster_${cluster}_tab_${config.tabIndex}", cleanPass)
            .putString("$KEY_LEGACY_USER_PREFIX${config.tabIndex}", cleanUser)
            .putString("$KEY_LEGACY_PASS_PREFIX${config.tabIndex}", cleanPass)
            .putBoolean("$KEY_LEGACY_AUTO_PREFIX${config.tabIndex}", config.autoLoginEnabled)
            .putString("$KEY_LEGACY_URL_PREFIX${config.tabIndex}", cleanUrl)
            .putString("user_tab_${config.tabIndex}", cleanUser)
            .putString("pass_tab_${config.tabIndex}", cleanPass)
            .commit()
    }

    fun clearAccountEverywhere(tabIndex: Int) {
        val editor = prefs.edit()

        val clusterIds = listOf("1", "2", "3") +
            listOf("Cụm 1", "Cụm 2", "Cụm 3") +
            listOf("Cụm 1", "Cụm 2", "Cụm 3").map { getClusterKey(it) }
        for (id in clusterIds.distinct()) {
            editor.putString("user_cluster_${id}_tab_$tabIndex", "")
            editor.putString("pass_cluster_${id}_tab_$tabIndex", "")
            editor.putString("url_cluster_${id}_tab_$tabIndex", "")
            editor.putBoolean("auto_cluster_${id}_tab_$tabIndex", false)
        }

        editor.putString("$KEY_LEGACY_USER_PREFIX$tabIndex", "")
        editor.putString("$KEY_LEGACY_PASS_PREFIX$tabIndex", "")
        editor.putString("$KEY_LEGACY_URL_PREFIX$tabIndex", "")
        editor.putBoolean("$KEY_LEGACY_AUTO_PREFIX$tabIndex", false)
        editor.putString("user_tab_$tabIndex", "")
        editor.putString("pass_tab_$tabIndex", "")
        editor.putString("url_tab_$tabIndex", "")
        editor.putBoolean("auto_tab_$tabIndex", false)

        editor.commit()
    }

    fun getRxOffset(): Long = prefs.getLong(KEY_RX_OFFSET, 0L)
    fun getTxOffset(): Long = prefs.getLong(KEY_TX_OFFSET, 0L)

    fun getTabCount(): Int = com.example.model.TabLimits.clamp(
        prefs.getInt(KEY_TAB_COUNT, com.example.model.TabLimits.DEFAULT)
    )

    fun setTabCount(count: Int) {
        prefs.edit().putInt(KEY_TAB_COUNT, com.example.model.TabLimits.clamp(count)).commit()
    }

    fun isTabWarnShown(): Boolean = prefs.getBoolean(KEY_TAB_WARN_SHOWN, false)

    fun setTabWarnShown(shown: Boolean) {
        prefs.edit().putBoolean(KEY_TAB_WARN_SHOWN, shown).apply()
    }

    fun resetTrafficCounters(currentRx: Long, currentTx: Long) {
        prefs.edit()
            .putLong(KEY_RX_OFFSET, currentRx)
            .putLong(KEY_TX_OFFSET, currentTx)
            .apply()
    }

    data class SavedDndSnapshot(
        val interruptionFilter: Int,
        val priorityCategories: Int,
        val priorityCallSenders: Int,
        val priorityMessageSenders: Int,
        val suppressedVisualEffects: Int,
        val priorityConversationSenders: Int
    )

    fun isDndOverrideActive(): Boolean = prefs.getBoolean(KEY_DND_OVERRIDE_ACTIVE, false)

    fun saveDndSnapshot(snapshot: SavedDndSnapshot) {
        prefs.edit()
            .putBoolean(KEY_DND_OVERRIDE_ACTIVE, true)
            .putInt(KEY_DND_PREV_FILTER, snapshot.interruptionFilter)
            .putInt(KEY_DND_PREV_CATEGORIES, snapshot.priorityCategories)
            .putInt(KEY_DND_PREV_CALL_SENDERS, snapshot.priorityCallSenders)
            .putInt(KEY_DND_PREV_MSG_SENDERS, snapshot.priorityMessageSenders)
            .putInt(KEY_DND_PREV_VISUAL_EFFECTS, snapshot.suppressedVisualEffects)
            .putInt(KEY_DND_PREV_CONV_SENDERS, snapshot.priorityConversationSenders)
            .commit()
    }

    fun getDndSnapshot(): SavedDndSnapshot? {
        if (!isDndOverrideActive()) return null
        return SavedDndSnapshot(
            interruptionFilter = prefs.getInt(KEY_DND_PREV_FILTER, 1),
            priorityCategories = prefs.getInt(KEY_DND_PREV_CATEGORIES, 0),
            priorityCallSenders = prefs.getInt(KEY_DND_PREV_CALL_SENDERS, 0),
            priorityMessageSenders = prefs.getInt(KEY_DND_PREV_MSG_SENDERS, 0),
            suppressedVisualEffects = prefs.getInt(KEY_DND_PREV_VISUAL_EFFECTS, 0),
            priorityConversationSenders = prefs.getInt(KEY_DND_PREV_CONV_SENDERS, 0)
        )
    }

    fun clearDndSnapshot() {
        prefs.edit()
            .putBoolean(KEY_DND_OVERRIDE_ACTIVE, false)
            .remove(KEY_DND_PREV_FILTER)
            .remove(KEY_DND_PREV_CATEGORIES)
            .remove(KEY_DND_PREV_CALL_SENDERS)
            .remove(KEY_DND_PREV_MSG_SENDERS)
            .remove(KEY_DND_PREV_VISUAL_EFFECTS)
            .remove(KEY_DND_PREV_CONV_SENDERS)
            .commit()
    }

    fun isSleepWakePromptShown(): Boolean = prefs.getBoolean(KEY_SLEEP_WAKE_PROMPT_SHOWN, false)

    fun setSleepWakePromptShown(shown: Boolean) {
        prefs.edit().putBoolean(KEY_SLEEP_WAKE_PROMPT_SHOWN, shown).apply()
    }
}
