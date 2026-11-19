package com.example.sleep

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.example.data.PreferencesManager

enum class SleepWakePauseStatus {
    SUPPRESSED,
    ALREADY_SUPPRESSED,
    NEEDS_PERMISSION,
    FAILED
}

/**
 * Tạm thời chặn các thông báo làm sáng màn hình (thông qua NotificationManager DND Policy)
 * trong suốt thời gian người chơi bật Chế độ Treo máy, và khôi phục chính xác trạng thái
 * trước đó ngay khi người chơi thao tác mở khóa / làm sáng màn hình.
 *
 * Lưu ý giới hạn hệ điều hành Android: các ứng dụng thông thường không được phép tự ý
 * thay đổi công tắc phần cứng "Nhấc máy sáng màn hình" (Settings.Secure / OEM sensor),
 * nên controller cung cấp kèm Intent mở nhanh Cài đặt Hiển thị và app tự khóa màn hình
 * đen OLED 1% độ sáng để tránh vào màn hình khóa.
 */
class SleepWakeController(
    private val context: Context,
    private val prefs: PreferencesManager
) {
    private val notificationManager: NotificationManager?
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    fun hasDndPolicyAccess(): Boolean {
        val nm = notificationManager ?: return false
        return runCatching { nm.isNotificationPolicyAccessGranted }.getOrDefault(false)
    }

    fun isNotificationWakeSuppressed(): Boolean {
        return prefs.isDndOverrideActive() && hasDndPolicyAccess()
    }

    fun pauseAutoScreenWake(): SleepWakePauseStatus {
        val nm = notificationManager ?: return SleepWakePauseStatus.FAILED
        if (!hasDndPolicyAccess()) return SleepWakePauseStatus.NEEDS_PERMISSION

        return try {
            val prevFilter = nm.currentInterruptionFilter
            val prevPolicy = nm.notificationPolicy

            if (!prefs.isDndOverrideActive()) {
                val convSenders = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    prevPolicy?.priorityConversationSenders
                        ?: NotificationManager.Policy.CONVERSATION_SENDERS_ANYONE
                } else {
                    0
                }
                prefs.saveDndSnapshot(
                    PreferencesManager.SavedDndSnapshot(
                        interruptionFilter = prevFilter,
                        priorityCategories = prevPolicy?.priorityCategories ?: 0,
                        priorityCallSenders = prevPolicy?.priorityCallSenders
                            ?: NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                        priorityMessageSenders = prevPolicy?.priorityMessageSenders
                            ?: NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                        suppressedVisualEffects = prevPolicy?.suppressedVisualEffects ?: 0,
                        priorityConversationSenders = convSenders
                    )
                )
            }

            val existingEffects = prevPolicy?.suppressedVisualEffects ?: 0
            val suppressedEffects = buildWakeSuppressVisualEffects(existingEffects, Build.VERSION.SDK_INT)

            // Giữ lại báo thức hệ thống nếu chưa ở chế độ Im lặng hoàn toàn, đồng thời chặn toàn bộ
            // thông báo cuộc gọi/tin nhắn/ứng dụng bật sáng màn hình hoặc hiện popup.
            val targetPolicy = createPolicy(
                priorityCategories = NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS,
                priorityCallSenders = prevPolicy?.priorityCallSenders
                    ?: NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                priorityMessageSenders = prevPolicy?.priorityMessageSenders
                    ?: NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                suppressedVisualEffects = suppressedEffects,
                priorityConversationSenders = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    NotificationManager.Policy.CONVERSATION_SENDERS_NONE
                } else {
                    0
                }
            )
            nm.notificationPolicy = targetPolicy

            val targetFilter = if (prevFilter == NotificationManager.INTERRUPTION_FILTER_NONE) {
                NotificationManager.INTERRUPTION_FILTER_NONE
            } else {
                NotificationManager.INTERRUPTION_FILTER_PRIORITY
            }
            nm.setInterruptionFilter(targetFilter)

            SleepWakePauseStatus.SUPPRESSED
        } catch (_: SecurityException) {
            SleepWakePauseStatus.NEEDS_PERMISSION
        } catch (_: Throwable) {
            SleepWakePauseStatus.FAILED
        }
    }

    fun restoreAutoScreenWake(): Boolean {
        if (!prefs.isDndOverrideActive()) return false
        val snapshot = prefs.getDndSnapshot()
        prefs.clearDndSnapshot()

        val nm = notificationManager ?: return false
        if (snapshot == null || !hasDndPolicyAccess()) return false

        return try {
            val restoredPolicy = createPolicy(
                priorityCategories = snapshot.priorityCategories,
                priorityCallSenders = snapshot.priorityCallSenders,
                priorityMessageSenders = snapshot.priorityMessageSenders,
                suppressedVisualEffects = snapshot.suppressedVisualEffects,
                priorityConversationSenders = snapshot.priorityConversationSenders
            )
            nm.notificationPolicy = restoredPolicy
            nm.setInterruptionFilter(snapshot.interruptionFilter)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun restoreIfOrphaned(): Boolean {
        return restoreAutoScreenWake()
    }

    companion object {
        fun buildWakeSuppressVisualEffects(
            existingEffects: Int,
            sdkInt: Int = Build.VERSION.SDK_INT
        ): Int {
            val wakeEffects = if (sdkInt >= Build.VERSION_CODES.P) {
                NotificationManager.Policy.SUPPRESSED_EFFECT_SCREEN_ON or
                    NotificationManager.Policy.SUPPRESSED_EFFECT_PEEK or
                    NotificationManager.Policy.SUPPRESSED_EFFECT_AMBIENT or
                    NotificationManager.Policy.SUPPRESSED_EFFECT_LIGHTS or
                    NotificationManager.Policy.SUPPRESSED_EFFECT_FULL_SCREEN_INTENT
            } else {
                @Suppress("DEPRECATION")
                (NotificationManager.Policy.SUPPRESSED_EFFECT_SCREEN_ON or
                    NotificationManager.Policy.SUPPRESSED_EFFECT_SCREEN_OFF)
            }
            return existingEffects or wakeEffects
        }

        fun createPolicy(
            priorityCategories: Int,
            priorityCallSenders: Int,
            priorityMessageSenders: Int,
            suppressedVisualEffects: Int,
            priorityConversationSenders: Int
        ): NotificationManager.Policy {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                NotificationManager.Policy(
                    priorityCategories,
                    priorityCallSenders,
                    priorityMessageSenders,
                    suppressedVisualEffects,
                    priorityConversationSenders
                )
            } else {
                @Suppress("DEPRECATION")
                NotificationManager.Policy(
                    priorityCategories,
                    priorityCallSenders,
                    priorityMessageSenders,
                    suppressedVisualEffects
                )
            }
        }

        fun dndPolicySettingsIntent(): Intent =
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

        fun displaySettingsIntent(): Intent =
            Intent(Settings.ACTION_DISPLAY_SETTINGS)
    }
}
