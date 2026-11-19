package com.example

import android.app.Application
import android.content.ComponentCallbacks2
import com.example.data.PreferencesManager
import com.example.sleep.SleepWakeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class VolamIdleApp : Application() {

    val processScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    var onMemoryTrimRequested: ((Int) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        if (!isUnitTestRuntime()) {
            SleepWakeController(this, PreferencesManager(this)).restoreIfOrphaned()
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
}
