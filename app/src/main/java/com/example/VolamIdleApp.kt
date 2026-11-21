package com.example

import android.app.Application
import android.content.ComponentCallbacks2
import com.example.data.PreferencesManager
import com.example.sleep.SleepWakeController
import com.example.update.UpdateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class VolamIdleApp : Application() {

    val processScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var updateRepository: UpdateRepository
        private set

    @Volatile
    var onMemoryTrimRequested: ((Int) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        updateRepository = UpdateRepository(this, processScope)
        if (!isUnitTestRuntime()) {
            SleepWakeController(this, PreferencesManager(this)).restoreIfOrphaned()
            startUpdateLoop()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            onMemoryTrimRequested?.invoke(level)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        onMemoryTrimRequested?.invoke(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
    }

    private fun isUnitTestRuntime(): Boolean =
        android.os.Build.FINGERPRINT.contains("robolectric", ignoreCase = true)

    private fun startUpdateLoop() {
        processScope.launch {
            delay(FIRST_CHECK_DELAY_MS)
            runCatching { updateRepository.checkForUpdate(manual = false) }

            while (true) {
                val jitter = Random.nextLong(-AUTO_CHECK_JITTER_MS, AUTO_CHECK_JITTER_MS + 1)
                delay(UpdateRepository.AUTO_CHECK_INTERVAL_MS + jitter)
                runCatching { updateRepository.checkForUpdate(manual = false) }
            }
        }
    }

    companion object {
        private const val FIRST_CHECK_DELAY_MS = 3_000L
        private const val AUTO_CHECK_JITTER_MS = UpdateRepository.AUTO_CHECK_JITTER_MS
    }
}
