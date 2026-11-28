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

    private val _gameLogs = MutableStateFlow<List<String>>(emptyList())
    val gameLogs: StateFlow<List<String>> = _gameLogs.asStateFlow()

    fun appendGameLog(tabIndex: Int, message: String) {
        val clean = message.replace("\n", " ").replace(Regex("\\s+"), " ").trim()
        if (clean.isEmpty()) return
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val entry = "[$ts] [Tab ${tabIndex + 1}] $clean"
        mainHandler.post {
            val next = ArrayList<String>((_gameLogs.value.size + 1).coerceAtMost(251))
            next.add(entry)
            next.addAll(_gameLogs.value.take(249))
            _gameLogs.value = next
        }
    }

    fun clearGameLogs() {
        mainHandler.post {
            _gameLogs.value = emptyList()
        }
    }

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
    private var isPcModeActive: Boolean = prefs.getPcMode()

    private val ultraLightTabs = BooleanArray(TabLimits.SLOT_COUNT) { i -> prefs.getUltraLight(i) }

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

    fun saveAutoConfigOnly(tabIndex: Int, json: String?) {
        autoPilot.saveConfigOnly(tabIndex, json)
        val cfg = autoPilot.currentConfig(tabIndex)
        appendGameLog(tabIndex, "Luu Tab ${tabIndex + 1} (auto=${if (cfg.on) "BAT" else "TAT"}, ChiLT=${if (cfg.thapNeDon) cfg.thapAfterMode else "Tat"}, TBK=${cfg.tbk}, tinVat=${cfg.tbkTinVat}, SauHD=${if (!cfg.afterDailyEnabled) "Tat" else if (cfg.afterDailyFarm) "LC:${cfg.farmMap}" else if (cfg.thap) "LT:${cfg.thapAfterMode}" else "Tat"}, QHT=${cfg.qht}/${cfg.qhtMua})")
    }

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
        mainHandler.postDelayed({ adjustAllViewports() }, 180L)
    }

    fun registerSlot(tabIndex: Int, attacher: (WebView) -> Unit) {
        if (tabIndex in webViews.indices) {
            slotAttachers[tabIndex] = attacher
        }
    }

    fun markUiInteraction(tabIndex: Int? = null) {
        val now = System.currentTimeMillis()
        if (tabIndex != null && tabIndex in webViews.indices) {
            webViews[tabIndex]?.lastTouchTimeMs = now
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
                view?.evaluateJavascript(
                    buildUltraLightScript(ultraLightTabs.getOrNull(tabIndex) == true),
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
            val leavingTower = !modeText.contains("Tháp", ignoreCase = true)
            val releaseJs = if (leavingTower) {
                "(function(){try{if(window.__vlmOrbitRelease)window.__vlmOrbitRelease();}catch(e){}})();"
            } else {
                null
            }
            val staggerMs = if (isConstrainedMemory || isLowRam) 140L else 80L
            for (i in 0 until tabCount) {
                val runTab = Runnable {
                    if (i >= tabCount) return@Runnable
                    val wv = webViews[i] ?: return@Runnable
                    val url = wv.url ?: return@Runnable
                    if (url == "about:blank") return@Runnable
                    if (releaseJs != null) {
                        runCatching { wv.evaluateJavascript(releaseJs, null) }
                    }
                    if (isTabActive[i]) {
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
                        wv.evaluateJavascript(script, null)
                    }
                }
                if (i == 0) {
                    runTab.run()
                } else {
                    mainHandler.postDelayed(runTab, staggerMs * i)
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

    fun applyPcMode(enabled: Boolean) {
        val changed = (isPcModeActive != enabled)
        isPcModeActive = enabled
        mainHandler.post {
            val script = buildPcModeScript(enabled)
            val statusMsg = if (enabled) "🖥️ Chế độ PC: Toàn màn hình (70%)" else "🖥️ Đã tắt Chế độ PC"
            for (i in 0 until tabCount) {
                val wv = webViews[i] ?: continue
                try {
                    if (wv.url != null && wv.url != "about:blank") {
                        wv.evaluateJavascript(script, null)
                    }
                    if (changed && isTabActive[i]) {
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

    fun setTabUltraLight(tabIndex: Int, enabled: Boolean) {
        if (tabIndex !in 0 until TabLimits.SLOT_COUNT) return
        ultraLightTabs[tabIndex] = enabled
        prefs.setUltraLight(tabIndex, enabled)
        val statusMsg = if (enabled) {
            "⚡ Siêu nhẹ: ĐANG BẬT (Ngủ đồ họa + 2 FPS + Ẩn chat)"
        } else {
            "⚡ Siêu nhẹ: Đã tắt"
        }
        appendGameLog(tabIndex, if (enabled) "Bat che do Sieu nhe (Tab ${tabIndex + 1})" else "Tat che do Sieu nhe (Tab ${tabIndex + 1})")
        mainHandler.post {
            val wv = webViews.getOrNull(tabIndex)
            try {
                if (wv != null && wv.url != null && wv.url != "about:blank") {
                    wv.evaluateJavascript(buildUltraLightScript(enabled), null)
                }
                if (isTabActive.getOrNull(tabIndex) == true) {
                    updateStatus(tabIndex, statusMsg)
                }
            } catch (_: Exception) {}
        }
    }

    private fun buildUltraLightScript(enabled: Boolean): String {
        val flag = if (enabled) "true" else "false"
        return """
            (function(ulOn) {
                try {
                    var CSS_ID = '__vlm_ultra_light_css';
                    var S = window.__vlmUltraLight;
                    if (!S) {
                        S = window.__vlmUltraLight = {
                            on: false,
                            lastTouch: 0,
                            wakeTimer: 0,
                            pruneTimer: 0,
                            syncTimer: 0,
                            gameSynced: false,
                            syncTries: 0,
                            rafWrapped: false,
                            touchBound: false,
                            origRaf: null,
                            origCaf: null,
                            lastRafAt: 0,
                            pending: {},
                            nextId: 910000
                        };
                    }
                    S.on = !!ulOn;

                    var root = document.documentElement;
                    var ensureCss = function() {
                        var el = document.getElementById(CSS_ID);
                        if (!el) {
                            el = document.createElement('style');
                            el.id = CSS_ID;
                            (document.head || root).appendChild(el);
                        }
                        el.textContent =
                            'html.__vlm-ul-on #chatBox, html.__vlm-ul-on #chatLines, html.__vlm-ul-on #u20lines, ' +
                            'html.__vlm-ul-on #logBox, html.__vlm-ul-on #u20log, html.__vlm-ul-on #chatWrap, ' +
                            'html.__vlm-ul-on #chatPanel, html.__vlm-ul-on .chat-box, html.__vlm-ul-on .chat-panel { ' +
                            '  display: none !important; ' +
                            '} ' +
                            'html.__vlm-ul-on *, html.__vlm-ul-on *::before, html.__vlm-ul-on *::after { ' +
                            '  animation: none !important; transition: none !important; ' +
                            '  box-shadow: none !important; text-shadow: none !important; ' +
                            '  backdrop-filter: none !important; -webkit-backdrop-filter: none !important; ' +
                            '} ' +
                            'html.__vlm-ul-on:not(.__vlm-ul-awake) canvas { ' +
                            '  opacity: 0.015 !important; ' +
                            '}';
                    };

                    var pruneChatDom = function() {
                        if (!S.on) return;
                        try {
                            var ids = ['chatLines', 'u20lines', 'logBox', 'u20log'];
                            for (var i = 0; i < ids.length; i++) {
                                var box = document.getElementById(ids[i]);
                                if (!box || !box.children) continue;
                                var extra = box.children.length - 15;
                                while (extra > 0 && box.firstElementChild) {
                                    box.removeChild(box.firstElementChild);
                                    extra--;
                                }
                            }
                        } catch (eP) {}
                    };

                    var markUserTouch = function(ev) {
                        if (ev && ev.isTrusted === false) return;
                        S.lastTouch = Date.now();
                        if (!S.on) return;
                        try { if (root) root.classList.add('__vlm-ul-awake'); } catch (eW1) {}
                        if (S.wakeTimer) clearTimeout(S.wakeTimer);
                        S.wakeTimer = setTimeout(function() {
                            S.wakeTimer = 0;
                            if (!S.on) return;
                            if (Date.now() - (S.lastTouch || 0) >= 9800 && !window.__vlmOrbitSpyActive) {
                                try { if (root) root.classList.remove('__vlm-ul-awake'); } catch (eW2) {}
                            }
                        }, 10000);
                    };

                    if (!S.touchBound) {
                        S.touchBound = true;
                        window.addEventListener('pointerdown', markUserTouch, true);
                        window.addEventListener('touchstart', markUserTouch, { capture: true, passive: true });
                    }

                    if (!S.rafWrapped && typeof window.requestAnimationFrame === 'function') {
                        S.rafWrapped = true;
                        S.origRaf = window.requestAnimationFrame.bind(window);
                        S.origCaf = typeof window.cancelAnimationFrame === 'function'
                            ? window.cancelAnimationFrame.bind(window)
                            : function() {};

                        window.requestAnimationFrame = function(cb) {
                            var st = window.__vlmUltraLight;
                            if (!st || !st.on || typeof cb !== 'function') {
                                return S.origRaf(cb);
                            }
                            var now = Date.now();
                            var orbitBusy = !!window.__vlmOrbitSpyActive;
                            var userAwake = (now - (st.lastTouch || 0)) < 10000;
                            var autoScanAwake = !!(root && root.classList.contains('__vlm-stealth')) || !!((window.__vlmModalLockUntil || 0) > now);
                            var modalOpen = autoScanAwake;
                            if (!modalOpen) {
                                try {
                                    var md = document.getElementById('modal');
                                    if (md && !md.classList.contains('hidden')) {
                                        modalOpen = true;
                                    }
                                } catch (eMd) {}
                            }

                            if (orbitBusy || userAwake || modalOpen) {
                                try { if (root && !root.classList.contains('__vlm-ul-awake')) root.classList.add('__vlm-ul-awake'); } catch (eAw1) {}
                                st.lastRafAt = now;
                                return S.origRaf(cb);
                            }
                            try { if (root && root.classList.contains('__vlm-ul-awake')) root.classList.remove('__vlm-ul-awake'); } catch (eAw2) {}

                            var wait = 500 - (now - (st.lastRafAt || 0));
                            if (wait <= 16) {
                                st.lastRafAt = now;
                                return S.origRaf(cb);
                            }
                            var fakeId = ++st.nextId;
                            var entry = { tid: 0, rid: 0, canceled: false };
                            st.pending[fakeId] = entry;
                            entry.tid = setTimeout(function() {
                                entry.tid = 0;
                                if (entry.canceled) { delete st.pending[fakeId]; return; }
                                st.lastRafAt = Date.now();
                                entry.rid = S.origRaf(function(ts) {
                                    delete st.pending[fakeId];
                                    if (!entry.canceled) cb(ts);
                                });
                            }, wait);
                            return fakeId;
                        };

                        window.cancelAnimationFrame = function(id) {
                            var st = window.__vlmUltraLight;
                            var entry = st && st.pending && st.pending[id];
                            if (entry) {
                                entry.canceled = true;
                                if (entry.tid) clearTimeout(entry.tid);
                                if (entry.rid) S.origCaf(entry.rid);
                                delete st.pending[id];
                                return;
                            }
                            return S.origCaf(id);
                        };
                    }

                    var noAcc = function(str) {
                        return String(str || '').toLowerCase().normalize('NFD')
                            .replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd')
                            .replace(/\s+/g, ' ').trim();
                    };

                    var trySyncGameLowSpec = function() {
                        if (!S.on || S.gameSynced) return;
                        if (S.syncTries >= 4) return;
                        var now = Date.now();
                        var hasHud = !!(document.getElementById('modeSw') || document.getElementById('ktcBtn') || document.getElementById('mswBtn'));
                        var modalBusy = !!(window.__vlmModalLockUntil && now < window.__vlmModalLockUntil);
                        var stealthBusy = !!(root && root.classList.contains('__vlm-stealth'));
                        var md = document.getElementById('modal');
                        var modalVisible = !!(md && !md.classList.contains('hidden'));
                        if (!hasHud || modalBusy || stealthBusy || modalVisible) {
                            S.syncTries++;
                            if (S.syncTries < 4 && S.on) {
                                S.syncTimer = setTimeout(trySyncGameLowSpec, 6000);
                            }
                            return;
                        }

                        var findSysBtn = function() {
                            var ids = ['sysBtn', 'heThongBtn', 'btnSys', 'u20sys', 'setBtn', 'optBtn'];
                            for (var i = 0; i < ids.length; i++) {
                                var b = document.getElementById(ids[i]);
                                if (b && !b.disabled && b.offsetParent !== null) return b;
                            }
                            var qs = document.querySelectorAll('button, [role="button"], a');
                            for (var j = 0; j < Math.min(qs.length, 220); j++) {
                                var q = qs[j];
                                if (!q || q.disabled || q.offsetParent === null) continue;
                                if (q.closest && q.closest('#modal, #chatLines, #u20lines, #logBox, #u20log')) continue;
                                var tx = noAcc(q.textContent || q.getAttribute('title') || q.getAttribute('aria-label') || '');
                                if (tx === 'he thong') return q;
                            }
                            return null;
                        };

                        var sysBtn = findSysBtn();
                        if (!sysBtn) {
                            S.syncTries++;
                            if (S.syncTries < 4 && S.on) S.syncTimer = setTimeout(trySyncGameLowSpec, 6000);
                            return;
                        }

                        S.syncTries++;
                        window.__vlmModalLockUntil = Date.now() + 4500;
                        var usedSharedStealth = !!(window.__vlmStealth && typeof window.__vlmStealth.enter === 'function');
                        if (usedSharedStealth) {
                            window.__vlmStealth.enter();
                        } else {
                            try { if (root) root.classList.add('__vlm-stealth'); } catch (eSt) {}
                        }

                        var finishSync = function() {
                            try {
                                var mEnd = document.getElementById('modal');
                                if (mEnd && !mEnd.classList.contains('hidden')) {
                                    var xBtn = document.getElementById('mClose');
                                    if (xBtn) xBtn.click();
                                    else mEnd.click();
                                }
                            } catch (eCl) {}
                            setTimeout(function() {
                                if (usedSharedStealth && window.__vlmStealth && typeof window.__vlmStealth.leave === 'function') {
                                    window.__vlmStealth.leave();
                                } else {
                                    try { if (root) root.classList.remove('__vlm-stealth'); } catch (eSt2) {}
                                }
                                window.__vlmModalLockUntil = 0;
                            }, 160);
                        };

                        try { sysBtn.click(); } catch (eClick) { finishSync(); return; }

                        setTimeout(function() {
                            try {
                                var mBody = document.getElementById('mBody') || document.getElementById('modal');
                                if (!mBody) { finishSync(); return; }
                                var tabs = mBody.querySelectorAll('button, [role="tab"], a');
                                for (var ti = 0; ti < tabs.length; ti++) {
                                    var tt = noAcc(tabs[ti].textContent || '');
                                    if (tt === 'tuy chon' || tt === 'cai dat' || tt === 'hien thi') {
                                        try { tabs[ti].click(); } catch (eTab) {}
                                        break;
                                    }
                                }
                                setTimeout(function() {
                                    try {
                                        var area = document.getElementById('mBody') || document.getElementById('modal');
                                        if (area) {
                                            var cbs = area.querySelectorAll('input[type="checkbox"]');
                                            var changed = 0;
                                            for (var ci = 0; ci < cbs.length; ci++) {
                                                var cb = cbs[ci];
                                                if (!cb || cb.disabled || cb.checked) continue;
                                                var row = cb.closest('label, .qrow, .opt, .row, li, tr, div');
                                                var rt = noAcc(row ? row.textContent : '');
                                                if (!rt || rt.length > 160) continue;
                                                if (rt.indexOf('man hinh den') >= 0 || rt.indexOf('tat man hinh') >= 0 ||
                                                    rt.indexOf('tu dong') >= 0 || rt.indexOf('auto') >= 0 ||
                                                    rt.indexOf('am thanh') >= 0 || rt.indexOf('nhac') >= 0 ||
                                                    rt.indexOf('dang xuat') >= 0 || rt.indexOf('xoa') >= 0) continue;
                                                var wantOn = (rt.indexOf('tiet kiem') >= 0 ||
                                                    rt.indexOf('giam hieu ung') >= 0 ||
                                                    rt.indexOf('an hieu ung') >= 0 ||
                                                    rt.indexOf('tat hieu ung') >= 0 ||
                                                    rt.indexOf('an nguoi choi') >= 0 ||
                                                    rt.indexOf('an nhan vat khac') >= 0 ||
                                                    rt.indexOf('an sat thuong') >= 0 ||
                                                    rt.indexOf('an chu bay') >= 0 ||
                                                    rt.indexOf('che do nhe') >= 0 ||
                                                    rt.indexOf('do hoa thap') >= 0);
                                                if (wantOn) {
                                                    try { cb.click(); changed++; } catch (eCb) {}
                                                }
                                            }
                                            S.gameSynced = true;
                                        }
                                    } finally {
                                        finishSync();
                                    }
                                }, 260);
                            } catch (eIn) {
                                finishSync();
                            }
                        }, 360);
                    };

                    if (S.on) {
                        ensureCss();
                        if (root) {
                            root.classList.add('__vlm-ul-on');
                            root.classList.remove('__vlm-ul-awake');
                        }
                        pruneChatDom();
                        if (S.pruneTimer) clearInterval(S.pruneTimer);
                        S.pruneTimer = setInterval(pruneChatDom, 30000);
                        if (S.syncTimer) clearTimeout(S.syncTimer);
                        if (!S.gameSynced) {
                            S.syncTries = 0;
                            S.syncTimer = setTimeout(trySyncGameLowSpec, 4200);
                        }
                    } else {
                        if (S.pruneTimer) { clearInterval(S.pruneTimer); S.pruneTimer = 0; }
                        if (S.syncTimer) { clearTimeout(S.syncTimer); S.syncTimer = 0; }
                        if (S.wakeTimer) { clearTimeout(S.wakeTimer); S.wakeTimer = 0; }
                        if (root) {
                            root.classList.remove('__vlm-ul-on');
                            root.classList.remove('__vlm-ul-awake');
                        }
                        var cssEl = document.getElementById(CSS_ID);
                        if (cssEl) cssEl.remove();
                    }
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
                if (tabIndex !in 0 until tabCount || !isTabActive[tabIndex]) return@post
                lastScanHeartbeatMs[tabIndex] = System.currentTimeMillis()
                setTabState(tabIndex, TabLifecycleState.IN_GAME)
                updateStatus(tabIndex, "🟢 Đã vào game")
                autoPilot.onTabEnteredGame(tabIndex)
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
                appendGameLog(tabIndex, message)
            }
        }

        @JavascriptInterface
        fun onLiveTelemetry(idx: Int, jsonStr: String) {
            mainHandler.post {
                try {
                    if (tabIndex !in _characterStatsList.value.indices) return@post
                    val now = System.currentTimeMillis()
                    lastScanHeartbeatMs[tabIndex] = now
                    val json = JSONObject(jsonStr)
                    val currentList = _characterStatsList.value.toMutableList()
                    val prev = currentList[tabIndex] ?: CharacterStats(tabIndex = tabIndex)

                    val daiHb = json.optString("daiHongBao", "").trim()
                    val hb = json.optString("hongBao", "").trim()
                    val bossHk = json.optString("bossHoangKim", "").trim()
                    val daTau = json.optString("daTauQuest", "").trim()
                    val knbInfo = json.optString("knbSummary", "").trim()
                    val actionStatus = json.optString("actionStatus", "").trim()

                    val mergedDaTau = if (daTau.isNotBlank() && knbInfo.isNotBlank()) {
                        "$daTau · KNB: $knbInfo"
                    } else if (daTau.isNotBlank()) {
                        daTau
                    } else if (knbInfo.isNotBlank()) {
                        "KNB: $knbInfo"
                    } else {
                        prev.daTauQuest
                    }

                    val nextStatus = if (actionStatus.isNotBlank()) {
                        lastGameLogAtMs[tabIndex] = now
                        actionStatus.replace("\n", " ").replace(Regex("\\s+"), " ").trim()
                    } else {
                        prev.lastStatus
                    }

                    currentList[tabIndex] = prev.copy(
                        daiHongBao = if (daiHb.isNotBlank()) daiHb else prev.daiHongBao,
                        hongBao = if (hb.isNotBlank()) hb else prev.hongBao,
                        bossHoangKim = if (bossHk.isNotBlank()) bossHk else prev.bossHoangKim,
                        daTauQuest = mergedDaTau,
                        lastStatus = nextStatus,
                        lastUpdateEpoch = now
                    )
                    _characterStatsList.value = currentList
                } catch (_: Exception) {}
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
