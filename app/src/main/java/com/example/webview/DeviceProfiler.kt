package com.example.webview

import android.os.Build

/**
 * Nhận diện môi trường giả lập (LDPlayer, Nox, BlueStacks, Android Emulator) để áp dụng
 * cấu hình nhẹ CPU/RAM/GPU khi người dùng mở nhiều tab giả lập cùng lúc.
 */
object DeviceProfiler {

    val isEmulator: Boolean by lazy {
        detectEmulator(
            fingerprint = Build.FINGERPRINT ?: "",
            model = Build.MODEL ?: "",
            manufacturer = Build.MANUFACTURER ?: "",
            brand = Build.BRAND ?: "",
            device = Build.DEVICE ?: "",
            product = Build.PRODUCT ?: "",
            hardware = Build.HARDWARE ?: "",
            abis = Build.SUPPORTED_ABIS?.toList().orEmpty()
        )
    }

    internal fun detectEmulator(
        fingerprint: String,
        model: String,
        manufacturer: String,
        brand: String,
        device: String,
        product: String,
        hardware: String,
        abis: List<String>
    ): Boolean {
        val combined = listOf(
            fingerprint,
            model,
            manufacturer,
            brand,
            device,
            product,
            hardware
        ).joinToString(" ").lowercase()

        val emulatorMarkers = listOf(
            "ldplayer", "ttvm", "vbox", "vbox86", "nox", "bluestacks",
            "memu", "genymotion", "goldfish", "ranchu", "sdk_gphone",
            "emulator", "android sdk built for", "generic_x86", "generic_x86_64"
        )
        if (emulatorMarkers.any { combined.contains(it) }) return true

        if (brand.startsWith("generic", ignoreCase = true) &&
            device.startsWith("generic", ignoreCase = true)
        ) {
            return true
        }

        // LDPlayer và phần lớn giả lập Windows chạy ABI x86/x86_64.
        if (abis.any { it.equals("x86", ignoreCase = true) || it.equals("x86_64", ignoreCase = true) }) {
            return true
        }

        return false
    }

    fun tabStaggerMs(): Long = if (isEmulator) 1800L else 1350L

    fun loginStepStaggerMs(): Long = if (isEmulator) 800L else 650L

    fun loginRoundIntervalMs(): Long = if (isEmulator) 2600L else 2200L

    fun watchdogTickMs(activeTabCount: Int): Long {
        val count = activeTabCount.coerceAtLeast(1)
        val basePerRound = if (isEmulator) 6400L else 5200L
        return (basePerRound / count).coerceAtLeast(if (isEmulator) 2500L else 2000L)
    }

    fun gcTickMs(): Long = if (isEmulator) 20_000L else 25_000L

    fun autoFpsThresholdTabCount(): Int = if (isEmulator) 2 else 3
}
