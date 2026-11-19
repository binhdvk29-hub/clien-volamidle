package com.example.update

object AppVisibility {

    @Volatile
    var isForeground: Boolean = false
        private set

    fun onActivityStarted() {
        isForeground = true
    }

    fun onActivityStopped() {
        isForeground = false
    }
}
