package com.example.webview

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.KeepAliveService
import com.example.data.PreferencesManager
import com.example.model.CharacterStats
import com.example.model.TabLifecycleState
import com.example.model.TabLimits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

class KeepAliveWebView(context: Context) : WebView(context) {
    var keepAliveInBackground: Boolean = false
    var lastTouchTimeMs: Long = 0L

    override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
        if (ev != null) {
            lastTouchTimeMs = System.currentTimeMillis()
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchWindowVisibilityChanged(visibility: Int) {
        if (keepAliveInBackground && visibility != View.VISIBLE) {
            super.dispatchWindowVisibilityChanged(View.VISIBLE)
        } else {
            super.dispatchWindowVisibilityChanged(visibility)
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        if (keepAliveInBackground && visibility != View.VISIBLE) {
            super.onWindowVisibilityChanged(View.VISIBLE)
        } else {
            super.onWindowVisibilityChanged(visibility)
        }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        if (keepAliveInBackground && visibility != View.VISIBLE) {
            super.onVisibilityChanged(changedView, View.VISIBLE)
        } else {
            super.onVisibilityChanged(changedView, visibility)
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        if (keepAliveInBackground) {
            super.onWindowFocusChanged(true)
        } else {
            super.onWindowFocusChanged(hasWindowFocus)
        }
    }
}

class GameTabManager(
    private val context: Context,
    private val prefs: PreferencesManager,
    private val coroutineScope: CoroutineScope
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private val contextWrappers = Array(TabLimits.SLOT_COUNT) {
        android.content.MutableContextWrapper(context)
    }

    fun updateActivityContext(activityContext: Context) {
        for (wrapper in contextWrappers) {
            if (wrapper.baseContext !== activityContext) {
                wrapper.baseContext = activityContext
            }
        }
    }

    private val _isSystemRunning = MutableStateFlow(false)
    val isSystemRunning: StateFlow<Boolean> = _isSystemRunning.asStateFlow()

    private val _characterStatsList = MutableStateFlow<List<CharacterStats>>(
        (0 until TabLimits.SLOT_COUNT).map {
            CharacterStats(tabIndex = it, state = TabLifecycleState.IDLE)
        }
    )
    val characterStatsList: StateFlow<List<CharacterStats>> = _characterStatsList.asStateFlow()

    private val webViews = arrayOfNulls<KeepAliveWebView>(TabLimits.SLOT_COUNT)
    private val isTabActive = BooleanArray(TabLimits.SLOT_COUNT) { false }

    val isLowRam: Boolean = isLowRamDevice()
    val isConstrainedMemory: Boolean = isConstrainedMemoryDevice()
    val maxTabCount: Int = if (isLowRam) TabLimits.RECOMMENDED_MAX else TabLimits.HARD_MAX

    @Volatile
    private var hasRecoveredFromOom: Boolean = false

    @Volatile
    private var tabCount: Int = prefs.getTabCount()
        .coerceIn(TabLimits.MIN, maxTabCount)

    private val _tabCountState = MutableStateFlow(tabCount)
    val tabCountState: StateFlow<Int> = _tabCountState.asStateFlow()

    var onFpsChanged: ((Int) -> Unit)? = null

    private var loginQueueJob: Job? = null
    private var periodicWatchdogJob: Job? = null
    private var garbageCollectionJob: Job? = null

    private val mainFrameRetryCount = IntArray(TabLimits.SLOT_COUNT) { 0 }
    private val lastGameLogAtMs = LongArray(TabLimits.SLOT_COUNT) { 0L }
    private val lastLoginStepAtMs = LongArray(TabLimits.SLOT_COUNT) { 0L }
    private val lastScanHeartbeatMs = LongArray(TabLimits.SLOT_COUNT) { System.currentTimeMillis() }
    private val gameLogStickyMs = 8_000L
    private val lastMainFrameError = arrayOfNulls<Int>(TabLimits.SLOT_COUNT)
    private val retryRunnables = arrayOfNulls<Runnable>(TabLimits.SLOT_COUNT)
    private val recoveryRunnables = arrayOfNulls<Runnable>(TabLimits.SLOT_COUNT)
    private val slotAttachers = arrayOfNulls<(WebView) -> Unit>(TabLimits.SLOT_COUNT)

    @Volatile
    private var isSleepModeActive = false

    @Volatile
    private var isAppInBackgroundFlag = false

    @Volatile
    private var isHideMapSynced: Boolean = prefs.getHideMap()

    @Volatile
    private var isHideGameSynced: Boolean = prefs.getHideGame()

    @Volatile
    private var isPcModeActive: Boolean = false

    @Volatile
    private var visibleTabsInLayout: Set<Int> = (0 until TabLimits.SLOT_COUNT).toSet()

    private val recoveryHistory =
        Array(TabLimits.SLOT_COUNT) { java.util.ArrayDeque<Long>() }
    private val recoveryBlocked = BooleanArray(TabLimits.SLOT_COUNT) { false }

    private val excessStaggerRunnables = mutableListOf<Runnable>()
    private var excessSellToken: String = ""
    private val excessPendingTabs = mutableSetOf<Int>()
    private var excessOkCount: Int = 0
    private var excessFailCount: Int = 0
    private var excessSummaryTimeoutRunnable: Runnable? = null

    private val autoPilot: GameAutoPilot = GameAutoPilot(
        scope = coroutineScope,
        tabCount = { tabCount },
        initialConfigJsonForTab = { i -> prefs.getAutoConfig(i) },
        webViewAt = { i -> webViews.getOrNull(i) },
        isTabActive = { i -> isTabActive.getOrNull(i) == true },
        isSystemRunning = { _isSystemRunning.value }
    )

    fun updateAutoConfig(tabIndex: Int, json: String?, immediate: Boolean = true) {
        autoPilot.updateConfig(tabIndex, json, immediate = immediate)
    }

    fun resetAutoDailyCounters(tabIndex: Int? = null) {
        autoPilot.resetDailyCounters(tabIndex)
    }

    fun updateVisibleTabs(visibleIndices: Set<Int>) {
        if (visibleTabsInLayout == visibleIndices) return
        visibleTabsInLayout = visibleIndices
        val bg = isAppInBackgroundFlag
        for (i in 0 until tabCount) {
            val wv = webViews[i] ?: continue
            val keepBg = bg || !visibleIndices.contains(i)
            wv.keepAliveInBackground = keepBg
            if (isTabActive[i]) {
                try {
                    wv.onResume()
                    wv.resumeTimers()
                    applyRendererPriority(i, wv)
                    if (wv.url != null && wv.url != "about:blank") {
                        wv.evaluateJavascript("window.__isAppInBackground = $keepBg;", null)
                    }
                } catch (_: Exception) {}
            } else {
                applyRendererPriority(i, wv)
            }
        }
    }

    fun registerSlot(tabIndex: Int, attacher: (WebView) -> Unit) {
        if (tabIndex in webViews.indices) {
            slotAttachers[tabIndex] = attacher
        }
    }

    fun markUiInteraction() {
        val now = System.currentTimeMillis()
        for (i in webViews.indices) {
            webViews[i]?.lastTouchTimeMs = now
        }
    }

    companion object {
        private const val MAX_MAIN_FRAME_RETRY = 5
        private const val MAIN_FRAME_RETRY_BASE_MS = 2500L
        private const val HEARTBEAT_STALE_MS = 180_000L
        private const val MAX_RECOVERIES_IN_WINDOW = 3
        private const val RECOVERY_WINDOW_MS = 120_000L
        private const val RECOVERY_BASE_DELAY_MS = 3_000L
        private const val RECOVERY_ATTEMPT_BACKOFF_MS = 3_500L
        private const val RECOVERY_TAB_STAGGER_MS = 1_800L
        private const val CONSTRAINED_RAM_BYTES = 4L * 1024L * 1024L * 1024L
        private const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Tablet) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 VoLamIdle/1.0"
    }

    private fun isLowRamDevice(): Boolean = try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        am?.isLowRamDevice == true
    } catch (_: Exception) {
        false
    }

    private fun isConstrainedMemoryDevice(): Boolean = try {
        if (isLowRam || DeviceProfiler.isEmulator) {
            true
        } else {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (am != null) {
                val mi = ActivityManager.MemoryInfo()
                am.getMemoryInfo(mi)
                mi.totalMem in 1L..CONSTRAINED_RAM_BYTES
            } else {
                false
            }
        }
    } catch (_: Exception) {
        false
    }

    internal fun resolveRendererPriority(tabIndex: Int): Pair<Int, Boolean> {
        val active = isTabActive.getOrNull(tabIndex) == true
        if (!active) {
            return WebView.RENDERER_PRIORITY_WAIVED to true
        }
        val visible = !isAppInBackgroundFlag && visibleTabsInLayout.contains(tabIndex)
        return if (visible) {
            WebView.RENDERER_PRIORITY_IMPORTANT to false
        } else {
            val waiveWhenHidden = (isConstrainedMemory || hasRecoveredFromOom) && !isAppInBackgroundFlag
            WebView.RENDERER_PRIORITY_BOUND to waiveWhenHidden
        }
    }

    private fun applyRendererPriority(tabIndex: Int, wv: WebView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val (prio, waived) = resolveRendererPriority(tabIndex)
            try {
                wv.setRendererPriorityPolicy(prio, waived)
            } catch (_: Exception) {}
        }
    }

    internal fun computeRecoveryDelayMs(tabIndex: Int, attemptInWindow: Int, didCrash: Boolean): Long {
        val attempt = attemptInWindow.coerceAtLeast(1)
        val oomExtra = if (didCrash) 0L else 1_200L
        return RECOVERY_BASE_DELAY_MS +
            (attempt - 1) * RECOVERY_ATTEMPT_BACKOFF_MS +
            (tabIndex.coerceAtLeast(0) * RECOVERY_TAB_STAGGER_MS) +
            oomExtra
    }

    init {
        mainHandler.post {
            for (i in 0 until tabCount) {
                getOrCreateWebView(i)
            }
        }
        startBackgroundLoops()
        autoPilot.start()
    }

    fun setTabCount(requested: Int): Int {
        val target = requested.coerceIn(TabLimits.MIN, maxTabCount)
        val old = tabCount
        if (target == old) return target

        if (target < old) {
            for (i in target until old) {
                isTabActive[i] = false
            }
        }

        tabCount = target
        prefs.setTabCount(target)
        _tabCountState.value = target

        mainHandler.post {
            if (target < old) {
                for (i in target until old) {
                    if (i >= tabCount) {
                        tearDownSlot(i)
                    }
                }
            }

            val fpsThreshold = DeviceProfiler.autoFpsThresholdTabCount()
            if ((tabCount > TabLimits.RECOMMENDED_MAX || (DeviceProfiler.isEmulator && tabCount >= fpsThreshold)) &&
                prefs.getFps() != 30
            ) {
                applyFps(30)
                onFpsChanged?.invoke(30)
                runCatching {
                    Toast.makeText(
                        context,
                        "Đã hạ xuống 30 FPS để giữ ổn định khi chạy $tabCount tab.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            if (target > old && _isSystemRunning.value) {
                val stagger = DeviceProfiler.tabStaggerMs()
                for (i in old until target) {
                    mainHandler.postDelayed({
                        if (i < tabCount) openSingleTab(i)
                    }, stagger * (i - old))
                }
            }

            adjustAllViewports()
        }
        return target
    }

    private fun tearDownSlot(i: Int) {
        if (i !in webViews.indices) return
        isTabActive[i] = false
        retryRunnables[i]?.let { mainHandler.removeCallbacks(it) }
        retryRunnables[i] = null
        recoveryRunnables[i]?.let { mainHandler.removeCallbacks(it) }
        recoveryRunnables[i] = null
        try {
            val old = webViews[i]
            (old?.parent as? ViewGroup)?.removeView(old)
        } catch (_: Exception) {}
        try {
            webViews[i]?.destroy()
        } catch (_: Exception) {}
        webViews[i] = null
        mainFrameRetryCount[i] = 0
        lastMainFrameError[i] = null
        setTabState(i, TabLifecycleState.OFFLINE)
        updateStatus(i, "Đã dừng (Offline)")
        autoPilot.onTabRemoved(i)
    }

    private fun getClusterId(): String {
        return when (prefs.getCluster()) {
            "Cụm 2" -> "2"
            "Cụm 3" -> "3"
            else -> "1"
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreateWebView(tabIndex: Int): WebView {
        require(tabIndex in webViews.indices) { "tabIndex $tabIndex ngoài dải slot" }
        val existing = webViews[tabIndex]
        if (existing != null) {
            return existing
        }

        val webView = KeepAliveWebView(contextWrappers[tabIndex])
        val clusterId = getClusterId()
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            try {
                WebViewCompat.setProfile(webView, "Isolated_Cluster_${clusterId}_Tab_${tabIndex}")
            } catch (_: Exception) {}
        }

        applyRendererPriority(tabIndex, webView)

        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = false
            allowContentAccess = false
            setNeedInitialFocus(false)
            setGeolocationEnabled(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = MOBILE_USER_AGENT
            javaScriptCanOpenWindowsAutomatically = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                offscreenPreRaster = false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = false
            }
        }

        webView.addJavascriptInterface(AndroidGameBridge(tabIndex), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                lastMainFrameError[tabIndex] = null
                lastScanHeartbeatMs[tabIndex] = System.currentTimeMillis()
                if (url != "about:blank" && isTabActive[tabIndex]) {
                    updateStatus(tabIndex, "Đang tải trang...")
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val fps = prefs.getFps()
                val hiddenOrBg = isAppInBackgroundFlag || !visibleTabsInLayout.contains(tabIndex)
                (view as? KeepAliveWebView)?.keepAliveInBackground = hiddenOrBg
                view?.evaluateJavascript(
                    GameScriptInjector.buildFpsLimiterScript(fps, DeviceProfiler.isEmulator),
                    null
                )
                view?.evaluateJavascript("window.__isAppInBackground = $hiddenOrBg;", null)
                view?.evaluateJavascript("window.__isSleepMode = $isSleepModeActive;", null)
                view?.evaluateJavascript(GameScriptInjector.buildAdjustViewportScript(), null)
                view?.evaluateJavascript(
                    GameScriptInjector.buildRenderOptimizationScript(isHideMapSynced),
                    null
                )
                view?.evaluateJavascript(
                    buildHideGameScript(isHideGameSynced),
                    null
                )
                view?.evaluateJavascript(
                    buildPcModeScript(isPcModeActive),
                    null
                )
                if (url != null && url != "about:blank") {
                    autoPilot.onTabRestarted(tabIndex)
                }

                if (url == null || url == "about:blank") {
                    updateStatus(tabIndex, "Chờ mở tab (Idle)")
                } else if (isTabActive[tabIndex]) {
                    if (lastMainFrameError[tabIndex] == null) {
                        mainFrameRetryCount[tabIndex] = 0
                        updateStatus(tabIndex, "Trang đã sẵn sàng")
                    }
                    if (_isSystemRunning.value) {
                        val account = prefs.getAccount(tabIndex)
                        if (account.username.isNotBlank() && account.password.isNotBlank()) {
                            mainHandler.postDelayed({
                                if (_isSystemRunning.value && isTabActive[tabIndex]) {
                                    val currentStats = _characterStatsList.value.getOrNull(tabIndex)
                                    val targetWv = webViews.getOrNull(tabIndex) ?: view
                                    if (currentStats?.state == TabLifecycleState.LOGGING_IN && targetWv != null) {
                                        lastLoginStepAtMs[tabIndex] = System.currentTimeMillis()
                                        wakeWebViewForAutomation(tabIndex, targetWv)
                                        val script = GameScriptInjector.buildAutoLoginStepScript(
                                            tabIndex = tabIndex,
                                            username = account.username,
                                            password = account.password
                                        )
                                        targetWv.evaluateJavascript(script, null)
                                    }
                                }
                            }, 900L)
                        }
                    }
                }
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: android.webkit.SslErrorHandler?,
                error: android.net.http.SslError?
            ) {
                handler?.proceed()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame != true || !isTabActive[tabIndex]) return

                val code = error?.errorCode ?: 0
                lastMainFrameError[tabIndex] = code

                if (code == WebViewClient.ERROR_HOST_LOOKUP) {
                    updateStatus(tabIndex, "❌ Không tìm thấy máy chủ")
                    return
                }

                val attempt = mainFrameRetryCount[tabIndex] + 1
                if (attempt > MAX_MAIN_FRAME_RETRY) {
                    updateStatus(tabIndex, "❌ Máy chủ không phản hồi sau $MAX_MAIN_FRAME_RETRY lần thử")
                    return
                }
                mainFrameRetryCount[tabIndex] = attempt

                val delayMs = MAIN_FRAME_RETRY_BASE_MS * attempt
                updateStatus(tabIndex, "Đang kết nối lại máy chủ... (lần $attempt/$MAX_MAIN_FRAME_RETRY)")

                retryRunnables[tabIndex]?.let { mainHandler.removeCallbacks(it) }
                val retryRunnable = Runnable {
                    retryRunnables[tabIndex] = null
                    if (tabIndex >= tabCount) return@Runnable
                    if (isTabActive[tabIndex] && _isSystemRunning.value) {
                        val account = prefs.getAccount(tabIndex)
                        val targetUrl = account.customUrl.ifBlank { prefs.getClusterUrl(prefs.getCluster()) }
                        try {
                            (webViews[tabIndex] ?: view)?.loadUrl(targetUrl)
                        } catch (_: Exception) {}
                    }
                }
                retryRunnables[tabIndex] = retryRunnable
                mainHandler.postDelayed(retryRunnable, delayMs)
            }

            override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                val didCrash = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    detail?.didCrash() == true
                } else {
                    true
                }
                handleRenderProcessGone(tabIndex, view, didCrash)
                return true
            }
        }

        webView.webChromeClient = WebChromeClient()

        webViews[tabIndex] = webView
        slotAttachers[tabIndex]?.invoke(webView)
        return webView
    }

    fun loginAll() {
        _isSystemRunning.value = true
        KeepAliveService.start(context)
        val cluster = prefs.getCluster()
        val defaultUrl = prefs.getClusterUrl(cluster)

        startLoginStepLoop()
        coroutineScope.launch(Dispatchers.Main) {
            val total = tabCount
            for (i in 0 until total) {
                if (!_isSystemRunning.value) break
                if (i >= tabCount) break
                recoveryRunnables[i]?.let { mainHandler.removeCallbacks(it) }
                recoveryRunnables[i] = null
                recoveryBlocked[i] = false
                isTabActive[i] = true
                lastLoginStepAtMs[i] = 0L
                val account = prefs.getAccount(i)
                val targetUrl = account.customUrl.ifBlank { defaultUrl }
                val wv = getOrCreateWebView(i)
                wakeWebViewForAutomation(i, wv)

                setTabState(i, TabLifecycleState.LOGGING_IN)
                updateStatus(i, "Đang kết nối máy chủ...")

                val currentUrl = wv.url
                val isGamePageLoaded = currentUrl != null &&
                    currentUrl != "about:blank" &&
                    (currentUrl.contains("volamidle") || currentUrl.contains("vlidle") || (account.customUrl.isNotBlank() && currentUrl.startsWith("http")))
                if (!isGamePageLoaded) {
                    wv.loadUrl(targetUrl)
                } else if (account.username.isNotBlank() && account.password.isNotBlank()) {
                    lastLoginStepAtMs[i] = System.currentTimeMillis()
                    val script = GameScriptInjector.buildAutoLoginStepScript(
                        tabIndex = i,
                        username = account.username,
                        password = account.password
                    )
                    wv.evaluateJavascript(script, null)
                }
                if (i < total - 1) {
                    delay(DeviceProfiler.tabStaggerMs())
                }
            }
        }
    }

    fun openSingleTab(tabIndex: Int) {
        if (tabIndex !in 0 until tabCount) return
        recoveryRunnables[tabIndex]?.let { mainHandler.removeCallbacks(it) }
        recoveryRunnables[tabIndex] = null
        recoveryBlocked[tabIndex] = false
        recoveryHistory[tabIndex].clear()

        _isSystemRunning.value = true
        isTabActive[tabIndex] = true
        KeepAliveService.start(context)
        if (loginQueueJob?.isActive != true) {
            startLoginStepLoop()
        }
        val cluster = prefs.getCluster()
        val defaultUrl = prefs.getClusterUrl(cluster)
        val account = prefs.getAccount(tabIndex)
        val targetUrl = account.customUrl.ifBlank { defaultUrl }

        mainHandler.post {
            if (tabIndex >= tabCount) return@post
            val wv = getOrCreateWebView(tabIndex)
            wakeWebViewForAutomation(tabIndex, wv)
            setTabState(tabIndex, TabLifecycleState.LOGGING_IN)
            updateStatus(tabIndex, "Đang mở tab...")

            val currentUrl = wv.url
            val isGamePageLoaded = currentUrl != null &&
                currentUrl != "about:blank" &&
                (currentUrl.contains("volamidle") || currentUrl.contains("vlidle") || (account.customUrl.isNotBlank() && currentUrl.startsWith("http")))
            if (!isGamePageLoaded) {
                wv.loadUrl(targetUrl)
            } else if (account.username.isNotBlank() && account.password.isNotBlank()) {
                val script = GameScriptInjector.buildAutoLoginStepScript(
                    tabIndex = tabIndex,
                    username = account.username,
                    password = account.password
                )
                wv.evaluateJavascript(script, null)
            }
        }
    }

    fun reopenAndReloginTab(tabIndex: Int) {
        if (tabIndex !in 0 until tabCount) return
        if (!_isSystemRunning.value || !isTabActive[tabIndex]) return
        val cluster = prefs.getCluster()
        val defaultUrl = prefs.getClusterUrl(cluster)
        val account = prefs.getAccount(tabIndex)
        val targetUrl = account.customUrl.ifBlank { defaultUrl }

        mainHandler.post {
            if (tabIndex >= tabCount || !_isSystemRunning.value || !isTabActive[tabIndex]) return@post
            lastScanHeartbeatMs[tabIndex] = System.currentTimeMillis()
            val wv = getOrCreateWebView(tabIndex)
            wakeWebViewForAutomation(tabIndex, wv)
            setTabState(tabIndex, TabLifecycleState.LOGGING_IN)
            updateStatus(tabIndex, "⚡ Tự động mở lại...")
            wv.loadUrl(targetUrl)
            autoPilot.onTabRestarted(tabIndex)
        }
    }

    internal fun handleRenderProcessGone(
        tabIndex: Int,
        view: WebView?,
        didCrash: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val cleanupView = Runnable {
            if (tabIndex in webViews.indices) {
                retryRunnables[tabIndex]?.let { mainHandler.removeCallbacks(it) }
                retryRunnables[tabIndex] = null
                recoveryRunnables[tabIndex]?.let { mainHandler.removeCallbacks(it) }
                recoveryRunnables[tabIndex] = null
                if (view == null || webViews[tabIndex] === view) {
                    webViews[tabIndex] = null
                }
            }
            autoPilot.onTabRemoved(tabIndex)
            try {
                (view?.parent as? ViewGroup)?.removeView(view)
            } catch (_: Exception) {}
            try {
                view?.destroy()
            } catch (_: Exception) {}
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            cleanupView.run()
        } else {
            if (tabIndex in webViews.indices && (view == null || webViews[tabIndex] === view)) {
                webViews[tabIndex] = null
            }
            mainHandler.post(cleanupView)
        }

        if (!didCrash) {
            hasRecoveredFromOom = true
            mainHandler.post {
                for (i in 0 until tabCount) {
                    if (i == tabIndex) continue
                    val survivor = webViews[i] ?: continue
                    applyRendererPriority(i, survivor)
                    try {
                        survivor.clearCache(false)
                    } catch (_: Exception) {}
                }
            }
        }

        if (tabIndex !in 0 until tabCount || !_isSystemRunning.value || !isTabActive[tabIndex]) {
            if (tabIndex in webViews.indices) {
                setTabState(tabIndex, TabLifecycleState.OFFLINE)
                updateStatus(tabIndex, "Đã dừng (Offline)")
            }
            return
        }

        if (recoveryBlocked[tabIndex]) {
            setTabState(tabIndex, TabLifecycleState.OFFLINE)
            return
        }

        val history = recoveryHistory[tabIndex]
        while (history.isNotEmpty() && nowMs - history.first() > RECOVERY_WINDOW_MS) {
            history.removeFirst()
        }
        history.addLast(nowMs)
        if (history.size >= MAX_RECOVERIES_IN_WINDOW) {
            recoveryBlocked[tabIndex] = true
            setTabState(tabIndex, TabLifecycleState.OFFLINE)
            updateStatus(tabIndex, "⚠️ Máy hết RAM – hãy giảm số tab")
            return
        }

        val delayMs = computeRecoveryDelayMs(tabIndex, history.size, didCrash)
        val sec = ((delayMs + 500L) / 1000L).coerceAtLeast(1L)
        updateStatus(
            tabIndex,
            if (didCrash) {
                "⚠️ Tiến trình WebView gặp sự cố – tự mở lại sau ${sec}s..."
            } else {
                "⚠️ Tiến trình WebView bị thu hồi RAM – tự mở lại sau ${sec}s..."
            }
        )

        val recoverRun = Runnable {
            if (tabIndex in recoveryRunnables.indices) {
                recoveryRunnables[tabIndex] = null
            }
            reopenAndReloginTab(tabIndex)
        }
        if (tabIndex in recoveryRunnables.indices) {
            recoveryRunnables[tabIndex] = recoverRun
        }
        mainHandler.postDelayed(recoverRun, delayMs)
    }

    internal fun hasPendingRecovery(tabIndex: Int): Boolean =
        tabIndex in recoveryRunnables.indices && recoveryRunnables[tabIndex] != null

    fun stopAll() {
        _isSystemRunning.value = false
        for (i in webViews.indices) {
            isTabActive[i] = false
            retryRunnables[i]?.let { mainHandler.removeCallbacks(it) }
            retryRunnables[i] = null
            recoveryRunnables[i]?.let { mainHandler.removeCallbacks(it) }
            recoveryRunnables[i] = null
        }
        mainHandler.post {
            for (i in 0 until tabCount) {
                isTabActive[i] = false
                val wv = webViews[i]
                if (wv != null) {
                    try {
                        wv.stopLoading()
                        wv.loadUrl("about:blank")
                        wv.pauseTimers()
                        wv.clearCache(false)
                        applyRendererPriority(i, wv)
                    } catch (_: Exception) {}
                }
                setTabState(i, TabLifecycleState.OFFLINE)
                updateStatus(i, "Đã dừng (Offline)")
                autoPilot.onTabRemoved(i)
            }
            runCatching { CookieManager.getInstance().flush() }
        }
    }

    fun stopSingleTab(tabIndex: Int) {
        if (tabIndex !in webViews.indices) return
        isTabActive[tabIndex] = false
        retryRunnables[tabIndex]?.let { mainHandler.removeCallbacks(it) }
        retryRunnables[tabIndex] = null
        recoveryRunnables[tabIndex]?.let { mainHandler.removeCallbacks(it) }
        recoveryRunnables[tabIndex] = null
        mainHandler.post {
            val wv = webViews[tabIndex]
            if (wv != null) {
                try {
                    wv.stopLoading()
                    wv.loadUrl("about:blank")
                    wv.clearCache(false)
                    applyRendererPriority(tabIndex, wv)
                } catch (_: Exception) {}
            }
            setTabState(tabIndex, TabLifecycleState.OFFLINE)
            updateStatus(tabIndex, "Đã dừng (Offline)")
            autoPilot.onTabRemoved(tabIndex)
        }
    }

    fun clearAccountSession(tabIndex: Int) {
        if (tabIndex !in webViews.indices) return
        mainHandler.post {
            val wv = webViews[tabIndex]
                ?: if (tabIndex < tabCount) getOrCreateWebView(tabIndex) else null

            isTabActive[tabIndex] = false
            setTabState(tabIndex, TabLifecycleState.IDLE)
            updateStatus(tabIndex, "Chờ mở tab (Idle)")

            if (wv == null) {
                return@post
            }

            val multiProfile = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
            var finished = false
            val closePage = {
                if (!finished) {
                    finished = true
                    try {
                        wv.stopLoading()
                        wv.loadUrl("about:blank")
                    } catch (_: Exception) {}
                    setTabState(tabIndex, TabLifecycleState.IDLE)
                    updateStatus(tabIndex, "Chờ mở tab (Idle)")
                }
            }

            var pending = if (multiProfile) 2 else 1
            val finish = {
                pending--
                if (pending <= 0) closePage()
            }

            if (multiProfile) {
                try {
                    val profile = WebViewCompat.getProfile(wv)
                    profile.cookieManager.removeAllCookies { finish() }
                    profile.webStorage.deleteAllData()
                } catch (_: Exception) {
                    finish()
                }
            }

            wv.evaluateJavascript(GameScriptInjector.buildClearStorageScript()) { finish() }

            mainHandler.postDelayed({ closePage() }, 3_000L)
        }
    }

    fun onAccountChanged(tabIndex: Int) {
        if (tabIndex !in webViews.indices) return
        mainHandler.post {
            val wv = webViews[tabIndex]
            val account = prefs.getAccount(tabIndex)
            if (wv != null && account.username.isNotBlank() && account.password.isNotBlank()) {
                val script = GameScriptInjector.buildAutoLoginStepScript(
                    tabIndex = tabIndex,
                    username = account.username,
                    password = account.password
                )
                wv.evaluateJavascript(script, null)
            }
            if (_isSystemRunning.value && isTabActive[tabIndex]) {
                reopenAndReloginTab(tabIndex)
            }
        }
    }

    fun adjustAllViewports() {
        mainHandler.post {
            val script = GameScriptInjector.buildAdjustViewportScript()
            for (i in 0 until tabCount) {
                val wv = webViews[i] ?: continue
                if (wv.url == null || wv.url == "about:blank") continue
                wv.evaluateJavascript(script, null)
                if (_isSystemRunning.value && isTabActive[i]) {
                    val currentStats = _characterStatsList.value.getOrNull(i)
                    val now = System.currentTimeMillis()
                    if (currentStats?.state == TabLifecycleState.LOGGING_IN &&
                        now - lastLoginStepAtMs[i] >= DeviceProfiler.loginRoundIntervalMs()
                    ) {
                        val account = prefs.getAccount(i)
                        if (account.username.isNotBlank() && account.password.isNotBlank()) {
                            lastLoginStepAtMs[i] = now
                            val loginScript = GameScriptInjector.buildAutoLoginStepScript(
                                tabIndex = i,
                                username = account.username,
                                password = account.password
                            )
                            wv.evaluateJavascript(loginScript, null)
                        }
                    }
                }
            }
        }
    }

    fun onAppBackgrounded() {
        isAppInBackgroundFlag = true
        if (_isSystemRunning.value || isTabActive.any { it }) {
            KeepAliveService.start(context)
        }
        mainHandler.post {
            for (i in 0 until tabCount) {
                val wv = webViews[i] ?: continue
                wv.keepAliveInBackground = true
                if (isTabActive[i]) {
                    try {
                        wv.onResume()
                        wv.resumeTimers()
                        applyRendererPriority(i, wv)
                        if (wv.url != null && wv.url != "about:blank") {
                            wv.evaluateJavascript("window.__isAppInBackground = true;", null)
                        }
                    } catch (_: Exception) {}
                } else {
                    applyRendererPriority(i, wv)
                }
            }
            runCatching { CookieManager.getInstance().flush() }
        }
    }

    fun onAppForegrounded() {
        isAppInBackgroundFlag = false
        mainHandler.post {
            val gcScript = GameScriptInjector.buildDomGarbageCollectionScript()
            val visibleSnapshot = visibleTabsInLayout
            for (i in 0 until tabCount) {
                val wv = webViews[i] ?: continue
                val isHiddenTab = !visibleSnapshot.contains(i)
                wv.keepAliveInBackground = isHiddenTab
                if (isTabActive[i]) {
                    try {
                        wv.onResume()
                        wv.resumeTimers()
                        applyRendererPriority(i, wv)
                        if (wv.url != null && wv.url != "about:blank") {
                            wv.evaluateJavascript("window.__isAppInBackground = $isHiddenTab;", null)
                            wv.evaluateJavascript(gcScript, null)
                        }
                    } catch (_: Exception) {}
                } else {
                    applyRendererPriority(i, wv)
                }
            }
        }
    }

    fun syncAutoMode(modeText: String) {
        mainHandler.post {
            val script = GameScriptInjector.buildSyncAutoModeScript(modeText)
            for (i in 0 until tabCount) {
                val wv = webViews[i]
                if (wv != null && isTabActive[i]) {
                    wv.evaluateJavascript(script) { result ->
                        val cleaned = result?.trim('"') ?: ""
                        if (cleaned.startsWith("SELECTED_OPTION:") ||
                            cleaned.startsWith("CLICKED_DIRECT:") ||
                            cleaned.startsWith("ALREADY_IN_MODE:") ||
                            cleaned.startsWith("OPENED_TRIGGER_AND_SELECTING:")
                        ) {
                            updateStatus(i, "⚡ Chế độ: $modeText")
                        }
                    }
                } else {
                    wv?.evaluateJavascript(script, null)
                }
            }
        }
    }

    fun syncBlackScreen(enabled: Boolean) {
        mainHandler.post {
            val script = GameScriptInjector.buildSyncBlackScreenScript(enabled)
            for (i in 0 until tabCount) {
                val wv = webViews[i]
                if (wv != null && isTabActive[i]) {
                    wv.evaluateJavascript(script) { _ ->
                        updateStatus(i, if (enabled) "🌑 Màn hình đen: Đang bật" else "☀️ Màn hình đen: Đã tắt")
                    }
                } else {
                    wv?.evaluateJavascript(script, null)
                }
            }
        }
    }

    fun syncHideMap(enabled: Boolean) {
        isHideMapSynced = enabled
        prefs.setHideMap(enabled)
        applyRenderOptimizationToAllTabs(
            statusMessage = if (enabled) "🗺️ Đã xoá hiển thị bản đồ" else "🗺️ Đã bật lại bản đồ"
        )
    }

    fun syncHideGame(enabled: Boolean) {
        isHideGameSynced = enabled
        prefs.setHideGame(enabled)
        val statusMsg = if (enabled) "👁️ Đã bật Ẩn game (giảm tải GPU)" else "👁️ Đã tắt Ẩn game"
        mainHandler.post {
            val script = buildHideGameScript(enabled)
            for (i in 0 until tabCount) {
                val wv = webViews[i] ?: continue
                try {
                    if (wv.url != null && wv.url != "about:blank") {
                        wv.evaluateJavascript(script, null)
                    }
                    if (isTabActive[i]) {
                        updateStatus(i, statusMsg)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun applyPcMode(activeInLandscape: Boolean) {
        if (isPcModeActive == activeInLandscape) return
        isPcModeActive = activeInLandscape
        mainHandler.post {
            val script = buildPcModeScript(activeInLandscape)
            val statusMsg = if (activeInLandscape) "🖥️ Chế độ PC: Toàn màn hình (70%)" else "🖥️ Đã tắt Chế độ PC"
            for (i in 0 until tabCount) {
                val wv = webViews[i] ?: continue
                try {
                    if (wv.url != null && wv.url != "about:blank") {
                        wv.evaluateJavascript(script, null)
                    }
                    if (isTabActive[i]) {
                        updateStatus(i, statusMsg)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun buildPcModeScript(enabled: Boolean): String {
        val flag = if (enabled) "true" else "false"
        return """
            (function(pcOn) {
                try {
                    var id = '__vlm_pc_mode_css';
                    var el = document.getElementById(id);
                    if (pcOn) {
                        if (!el) {
                            el = document.createElement('style');
                            el.id = id;
                            el.textContent = 'html { zoom: 0.70 !important; }';
                            (document.head || document.documentElement).appendChild(el);
                        }
                    } else if (el) {
                        el.remove();
                    }
                    setTimeout(function() {
                        try { window.dispatchEvent(new Event('resize')); } catch (e) {}
                    }, 80);
                } catch (e) {}
            })($flag);
        """.trimIndent()
    }

    private fun buildHideGameScript(enabled: Boolean): String {
        val flag = if (enabled) "true" else "false"
        return """
            (function(hide) {
                try {
                    var id = '__vlm_hide_game_css';
                    var el = document.getElementById(id);
                    if (hide) {
                        if (!el) {
                            el = document.createElement('style');
                            el.id = id;
                            el.textContent = 'canvas { opacity: 0.001 !important; pointer-events: none !important; }';
                            (document.head || document.documentElement).appendChild(el);
                        }
                    } else if (el) {
                        el.remove();
                    }
                } catch (e) {}
            })($flag);
        """.trimIndent()
    }

    private fun applyRenderOptimizationToAllTabs(statusMessage: String) {
        mainHandler.post {
            val script = GameScriptInjector.buildRenderOptimizationScript(
                hideMap = isHideMapSynced
            )
            for (i in 0 until tabCount) {
                val wv = webViews[i] ?: continue
                try {
                    if (wv.url != null && wv.url != "about:blank") {
                        wv.evaluateJavascript(script, null)
                    }
                    if (isTabActive[i]) {
                        updateStatus(i, statusMessage)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun isTabReadyForBagAction(tabIndex: Int): Boolean {
        val wv = webViews.getOrNull(tabIndex) ?: return false
        val url = wv.url ?: return false
        if (url == "about:blank") return false
        val st = _characterStatsList.value.getOrNull(tabIndex)?.state
        return isTabActive[tabIndex] ||
            st == TabLifecycleState.IN_GAME ||
            st == TabLifecycleState.LOGGING_IN
    }

    private fun wakeWebViewForAutomation(tabIndex: Int, wv: WebView) {
        try {
            wv.onResume()
            wv.resumeTimers()
            applyRendererPriority(tabIndex, wv)
        } catch (_: Exception) {}
    }

    private fun clearRunnables(list: MutableList<Runnable>) {
        for (r in list) {
            mainHandler.removeCallbacks(r)
        }
        list.clear()
    }

    private fun setTabStatusSticky(tabIndex: Int, status: String) {
        val now = System.currentTimeMillis()
        if (tabIndex in lastGameLogAtMs.indices) {
            lastGameLogAtMs[tabIndex] = now
        }
        updateStatus(tabIndex, status)
    }

    fun syncSellExcessItems() {
        mainHandler.post {
            clearRunnables(excessStaggerRunnables)
            excessSummaryTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            val readyTabs = mutableListOf<Int>()
            for (i in 0 until tabCount) {
                if (isTabReadyForBagAction(i)) {
                    readyTabs.add(i)
                }
            }
            if (readyTabs.isEmpty()) {
                runCatching {
                    Toast.makeText(context, "Chưa có tab nào đang vào game để bán đồ thừa.", Toast.LENGTH_SHORT).show()
                }
                return@post
            }

            val token = UUID.randomUUID().toString()
            excessSellToken = token
            excessPendingTabs.clear()
            excessPendingTabs.addAll(readyTabs)
            excessOkCount = 0
            excessFailCount = 0

            readyTabs.forEachIndexed { offset, tabIdx ->
                val wv = webViews[tabIdx] ?: return@forEachIndexed
                wakeWebViewForAutomation(tabIdx, wv)
                setTabStatusSticky(tabIdx, "🧹 Đang bán ẩn đồ thừa...")
                val run = Runnable {
                    if (excessSellToken != token) return@Runnable
                    val targetWv = webViews.getOrNull(tabIdx)
                    if (targetWv == null || !isTabReadyForBagAction(tabIdx)) {
                        handleExcessTabResult(tabIdx, token, false, "⚠️ Tab ${tabIdx + 1}: Chưa sẵn sàng")
                        return@Runnable
                    }
                    wakeWebViewForAutomation(tabIdx, targetWv)
                    targetWv.evaluateJavascript(
                        GameScriptInjector.buildSellExcessItemsScript(tabIdx, token),
                        null
                    )
                }
                excessStaggerRunnables.add(run)
                if (offset == 0) {
                    run.run()
                } else {
                    mainHandler.postDelayed(run, 220L * offset)
                }
            }

            val total = readyTabs.size
            val timeoutRun = Runnable {
                if (excessSellToken == token && excessPendingTabs.isNotEmpty()) {
                    val timedOut = excessPendingTabs.toList()
                    excessPendingTabs.clear()
                    for (tIdx in timedOut) {
                        setTabStatusSticky(tIdx, "⚠️ Hết thời gian chờ bán đồ thừa")
                        excessFailCount++
                    }
                    runCatching {
                        val msg = "🧹 Bán đồ thừa: $excessOkCount/$total tab xong ($excessFailCount tab lỗi/chờ)."
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            excessSummaryTimeoutRunnable = timeoutRun
            mainHandler.postDelayed(timeoutRun, 10_000L + 220L * readyTabs.size)
        }
    }

    private fun handleExcessTabResult(tabIdx: Int, token: String, ok: Boolean, message: String) {
        if (token.isNotBlank() && excessSellToken != token) return
        if (excessPendingTabs.remove(tabIdx)) {
            if (ok) excessOkCount++ else excessFailCount++
            if (excessPendingTabs.isEmpty()) {
                excessSummaryTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                val total = excessOkCount + excessFailCount
                runCatching {
                    val summary = if (excessFailCount == 0) {
                        "🧹 Đã xong bán đồ thừa ($excessOkCount/$total tab)"
                    } else {
                        "🧹 Bán đồ thừa: $excessOkCount/$total tab xong, $excessFailCount tab lỗi"
                    }
                    Toast.makeText(context, summary, Toast.LENGTH_SHORT).show()
                }
            }
        }
        if (message.isNotBlank()) {
            setTabStatusSticky(tabIdx, message)
        }
    }

    fun applyFps(fps: Int) {
        prefs.setFps(fps)
        mainHandler.post {
            val script = GameScriptInjector.buildFpsLimiterScript(fps, DeviceProfiler.isEmulator)
            for (i in 0 until tabCount) {
                webViews[i]?.evaluateJavascript(script, null)
            }
        }
    }

    fun setSleepMode(enabled: Boolean) {
        isSleepModeActive = enabled
        mainHandler.post {
            val gcScript = if (enabled) GameScriptInjector.buildDomGarbageCollectionScript() else null
            for (i in 0 until tabCount) {
                val wv = webViews[i] ?: continue
                try {
                    wv.evaluateJavascript("window.__isSleepMode = $enabled;", null)
                    if (gcScript != null && isTabActive[i]) {
                        wv.evaluateJavascript(gcScript, null)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun trimMemory(level: Int) {
        mainHandler.post {
            val gcScript = GameScriptInjector.buildDomGarbageCollectionScript()
            val aggressive = level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                level == ComponentCallbacks2.TRIM_MEMORY_COMPLETE
            for (i in webViews.indices) {
                val wv = webViews[i] ?: continue
                try {
                    if (isTabActive[i] && wv.url != null && wv.url != "about:blank") {
                        wv.evaluateJavascript(gcScript, null)
                    }
                    if (aggressive) {
                        wv.clearCache(false)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun switchCluster(newCluster: String) {
        prefs.setCluster(newCluster)
        if (_isSystemRunning.value) {
            mainHandler.post {
                for (i in webViews.indices) {
                    retryRunnables[i]?.let { mainHandler.removeCallbacks(it) }
                    retryRunnables[i] = null
                    recoveryRunnables[i]?.let { mainHandler.removeCallbacks(it) }
                    recoveryRunnables[i] = null
                    mainFrameRetryCount[i] = 0
                    lastMainFrameError[i] = null
                    try {
                        val old = webViews[i]
                        (old?.parent as? ViewGroup)?.removeView(old)
                    } catch (_: Exception) {}
                    try {
                        webViews[i]?.destroy()
                    } catch (_: Exception) {}
                    webViews[i] = null
                }
                loginAll()
            }
        }
    }

    private fun startLoginStepLoop() {
        loginQueueJob?.cancel()
        val staggerMs = DeviceProfiler.loginStepStaggerMs()
        val perTabIntervalMs = DeviceProfiler.loginRoundIntervalMs()
        loginQueueJob = coroutineScope.launch(Dispatchers.Main) {
            var cursor = 0
            while (true) {
                delay(staggerMs)
                if (!_isSystemRunning.value) continue
                val count = tabCount
                if (count <= 0) continue

                val now = System.currentTimeMillis()
                var picked = -1
                for (offset in 0 until count) {
                    val idx = (cursor + offset) % count
                    if (!isTabActive[idx]) continue
                    val currentStats = _characterStatsList.value.getOrNull(idx)
                    if (currentStats?.state != TabLifecycleState.LOGGING_IN) continue
                    val wv = webViews[idx] ?: continue
                    if (now - wv.lastTouchTimeMs < 5000L) continue
                    if (now - lastLoginStepAtMs[idx] < perTabIntervalMs) continue
                    if (wv.url == null || wv.url == "about:blank") continue
                    picked = idx
                    cursor = (idx + 1) % count
                    break
                }

                if (picked >= 0) {
                    val wv = webViews[picked] ?: continue
                    lastLoginStepAtMs[picked] = now
                    wakeWebViewForAutomation(picked, wv)
                    val account = prefs.getAccount(picked)
                    val script = GameScriptInjector.buildAutoLoginStepScript(
                        tabIndex = picked,
                        username = account.username,
                        password = account.password
                    )
                    wv.evaluateJavascript(script, null)
                }
            }
        }
    }

    private fun startBackgroundLoops() {
        startLoginStepLoop()

        periodicWatchdogJob = coroutineScope.launch(Dispatchers.Main) {
            var nextTab = 0
            while (true) {
                val activeCount = (0 until tabCount).count { isTabActive[it] }.coerceAtLeast(1)
                delay(DeviceProfiler.watchdogTickMs(activeCount))
                if (_isSystemRunning.value) {
                    val count = tabCount
                    if (count <= 0) continue
                    val i = nextTab % count
                    nextTab = (nextTab + 1) % count
                    val wv = webViews[i]
                    val now = System.currentTimeMillis()
                    if (wv != null && now - wv.lastTouchTimeMs >= 5000L && isTabActive[i]) {
                        val currentStats = _characterStatsList.value.getOrNull(i)
                        if (currentStats?.state == TabLifecycleState.IN_GAME &&
                            wv.url != null && wv.url != "about:blank"
                        ) {
                            if (now - lastScanHeartbeatMs[i] > HEARTBEAT_STALE_MS) {
                                lastScanHeartbeatMs[i] = now
                                updateStatus(i, "⚠️ Mất phản hồi kéo dài, đang tự kết nối lại...")
                                reopenAndReloginTab(i)
                            } else {
                                wv.evaluateJavascript(GameScriptInjector.buildPeriodicWatchdogAndScanScript(i), null)
                            }
                        }
                    }
                }
            }
        }

        garbageCollectionJob = coroutineScope.launch(Dispatchers.Main) {
            var nextTab = 0
            while (true) {
                delay(DeviceProfiler.gcTickMs())
                val count = tabCount
                if (count <= 0) continue
                val i = nextTab % count
                nextTab = (nextTab + 1) % count
                val wv = webViews[i] ?: continue
                if (wv.url == null || wv.url == "about:blank") continue
                if (System.currentTimeMillis() - wv.lastTouchTimeMs < 5000L) continue
                wv.evaluateJavascript(GameScriptInjector.buildDomGarbageCollectionScript(), null)
            }
        }
    }

    private fun setTabState(tabIndex: Int, state: TabLifecycleState) {
        val currentList = _characterStatsList.value
        if (tabIndex !in currentList.indices) return
        val mutable = currentList.toMutableList()
        val existing = mutable[tabIndex] ?: CharacterStats(tabIndex = tabIndex)
        mutable[tabIndex] = existing.copy(
            state = state,
            isOnline = (state == TabLifecycleState.IN_GAME),
            lastUpdateEpoch = System.currentTimeMillis()
        )
        _characterStatsList.value = mutable
    }

    private fun updateStatus(tabIndex: Int, status: String) {
        val currentList = _characterStatsList.value
        if (tabIndex !in currentList.indices) return
        val mutable = currentList.toMutableList()
        val existing = mutable[tabIndex] ?: CharacterStats(tabIndex = tabIndex)
        mutable[tabIndex] = existing.copy(
            lastStatus = status,
            lastUpdateEpoch = System.currentTimeMillis()
        )
        _characterStatsList.value = mutable
    }

    fun cleanup() {
        loginQueueJob?.cancel()
        periodicWatchdogJob?.cancel()
        garbageCollectionJob?.cancel()
        autoPilot.shutdown()
        mainHandler.post {
            for (i in webViews.indices) {
                retryRunnables[i]?.let { mainHandler.removeCallbacks(it) }
                retryRunnables[i] = null
                recoveryRunnables[i]?.let { mainHandler.removeCallbacks(it) }
                recoveryRunnables[i] = null
                try {
                    val old = webViews[i]
                    (old?.parent as? ViewGroup)?.removeView(old)
                } catch (_: Exception) {}
                try {
                    webViews[i]?.destroy()
                } catch (_: Exception) {}
                webViews[i] = null
            }
        }
    }

    inner class AndroidGameBridge(private val tabIndex: Int) {
        @JavascriptInterface
        fun onInGameDetected(idx: Int) {
            mainHandler.post {
                lastScanHeartbeatMs[tabIndex] = System.currentTimeMillis()
                setTabState(tabIndex, TabLifecycleState.IN_GAME)
                updateStatus(tabIndex, "🟢 Đã vào game")
            }
        }

        @JavascriptInterface
        fun onGameKickedDetected(idx: Int) {
            mainHandler.post {
                if (tabIndex >= tabCount) return@post
                val now = System.currentTimeMillis()
                lastScanHeartbeatMs[tabIndex] = now
                if (_isSystemRunning.value && isTabActive[tabIndex]) {
                    setTabState(tabIndex, TabLifecycleState.LOGGING_IN)
                    updateStatus(tabIndex, "⚡ Tự động đăng nhập...")
                    val account = prefs.getAccount(tabIndex)
                    if (account.username.isNotBlank() && account.password.isNotBlank() &&
                        now - lastLoginStepAtMs[tabIndex] >= 1500L
                    ) {
                        lastLoginStepAtMs[tabIndex] = now
                        webViews[tabIndex]?.let { wakeWebViewForAutomation(tabIndex, it) }
                        val script = GameScriptInjector.buildAutoLoginStepScript(
                            tabIndex = tabIndex,
                            username = account.username,
                            password = account.password
                        )
                        webViews[tabIndex]?.evaluateJavascript(script, null)
                    }
                }
            }
        }

        @JavascriptInterface
        fun onGameStateScanned(idx: Int, jsonStr: String) {
            mainHandler.post {
                try {
                    if (!isTabActive[tabIndex]) return@post
                    if (tabIndex !in _characterStatsList.value.indices) return@post
                    lastScanHeartbeatMs[tabIndex] = System.currentTimeMillis()
                    val json = JSONObject(jsonStr)
                    val currentList = _characterStatsList.value.toMutableList()
                    val prev = currentList[tabIndex] ?: CharacterStats(tabIndex = tabIndex)

                    val charName = json.optString("characterName", prev.characterName)
                        .replace("\n", " ").replace(Regex("\\s+"), " ").trim()
                    val sect = json.optString("sect", prev.sect)
                        .replace("\n", " ").replace(Regex("\\s+"), " ").trim()
                    val level = json.optString("level", prev.level)
                        .replace("\n", " ").replace(Regex("\\s+"), " ").trim()
                    val exp = json.optString("expPercent", prev.expPercent)
                        .replace("\n", " ").replace(Regex("\\s+"), " ").trim()
                    val daiHb = json.optString("daiHongBao", prev.daiHongBao)
                        .replace("\n", " ").replace(Regex("\\s+"), " ").trim()
                    val hb = json.optString("hongBao", prev.hongBao)
                        .replace("\n", " ").replace(Regex("\\s+"), " ").trim()
                    val thap = json.optString("thapRecord", prev.thapRecord)
                        .replace("\n", " ").replace(Regex("\\s+"), " ").trim()
                    val boss = json.optString("bossHoangKim", prev.bossHoangKim)
                        .replace("\n", " ").replace(Regex("\\s+"), " ").trim()
                    val daTau = json.optString("daTauQuest", prev.daTauQuest)
                        .replace("\n", " ").replace(Regex("\\s+"), " ").trim()
                    val tasksCompleted = json.optString("tasksCompleted", prev.tasksCompleted)
                        .replace("\n", " ").replace(Regex("\\s+"), " ").trim()
                    val recentLog = System.currentTimeMillis() - lastGameLogAtMs[tabIndex] < gameLogStickyMs
                    val status = if (recentLog) {
                        prev.lastStatus
                    } else {
                        json.optString("lastStatus", prev.lastStatus)
                            .replace("\n", " ").replace(Regex("\\s+"), " ").trim()
                    }

                    currentList[tabIndex] = prev.copy(
                        state = TabLifecycleState.IN_GAME,
                        isOnline = true,
                        characterName = if (charName.isNotBlank() && !charName.matches(Regex("^\\d+$"))) charName else prev.characterName,
                        sect = if (sect.isNotBlank()) sect else prev.sect,
                        level = if (level.isNotBlank()) level else prev.level,
                        expPercent = if (exp.isNotBlank()) exp else prev.expPercent,
                        daiHongBao = if (daiHb.isNotBlank()) daiHb else prev.daiHongBao,
                        hongBao = if (hb.isNotBlank()) hb else prev.hongBao,
                        thapRecord = if (thap.isNotBlank()) thap else prev.thapRecord,
                        bossHoangKim = if (boss.isNotBlank()) boss else prev.bossHoangKim,
                        daTauQuest = if (daTau.isNotBlank()) daTau else prev.daTauQuest,
                        tasksCompleted = if (tasksCompleted.isNotBlank()) tasksCompleted else prev.tasksCompleted,
                        lastStatus = status,
                        lastUpdateEpoch = System.currentTimeMillis()
                    )
                    val updated = currentList[tabIndex]
                    val changed = updated.state != prev.state ||
                        updated.characterName != prev.characterName ||
                        updated.sect != prev.sect ||
                        updated.level != prev.level ||
                        updated.expPercent != prev.expPercent ||
                        updated.daiHongBao != prev.daiHongBao ||
                        updated.hongBao != prev.hongBao ||
                        updated.thapRecord != prev.thapRecord ||
                        updated.bossHoangKim != prev.bossHoangKim ||
                        updated.daTauQuest != prev.daTauQuest ||
                        updated.tasksCompleted != prev.tasksCompleted ||
                        updated.lastStatus != prev.lastStatus
                    if (changed) {
                        _characterStatsList.value = currentList
                    }
                } catch (_: Exception) {}
            }
        }

        @JavascriptInterface
        fun onGameLog(idx: Int, message: String) {
            mainHandler.post {
                if (tabIndex !in _characterStatsList.value.indices) return@post
                val now = System.currentTimeMillis()
                lastScanHeartbeatMs[tabIndex] = now
                lastGameLogAtMs[tabIndex] = now
                val currentList = _characterStatsList.value.toMutableList()
                val prev = currentList[tabIndex] ?: CharacterStats(tabIndex = tabIndex)
                currentList[tabIndex] = prev.copy(
                    lastStatus = message.replace("\n", " ").replace(Regex("\\s+"), " ").trim(),
                    lastUpdateEpoch = System.currentTimeMillis()
                )
                _characterStatsList.value = currentList
            }
        }

        @JavascriptInterface
        fun onExcessSellResult(idx: Int, token: String, ok: Boolean, soldCount: Int, message: String) {
            mainHandler.post {
                val cleaned = message.replace("\n", " ").replace(Regex("\\s+"), " ").trim()
                handleExcessTabResult(tabIndex, token, ok, cleaned)
            }
        }
    }
}
