package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.KeepAliveService
import com.example.VolamIdleApp
import com.example.data.PreferencesManager
import com.example.model.AccountConfig
import com.example.model.CharacterStats
import com.example.model.NetworkDataInfo
import com.example.model.TabLimits
import com.example.model.ViewLayoutMode
import com.example.network.TrafficMonitor
import com.example.sleep.SleepWakeController
import com.example.sleep.SleepWakePauseStatus
import com.example.update.UpdateRepository
import com.example.update.UpdateUiState
import com.example.webview.AutoConfig
import com.example.webview.GameTabManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val prefs = PreferencesManager(application)
    val trafficMonitor = TrafficMonitor(application)
    val tabManager = GameTabManager(application, prefs, viewModelScope)
    val sleepWakeController = SleepWakeController(application, prefs)

    // ===== Tự động cập nhật =====
    // Repository sống ở Application (process-scoped): vòng kiểm tra 12h không được chết
    // theo Activity, và kết quả cài đặt của process trước nằm trong prefs nó đọc lúc tạo.
    private val updateRepository: UpdateRepository? =
        (application as? VolamIdleApp)?.updateRepository

    /** Trạng thái UI; `null` repository (môi trường test) thì đứng yên ở Idle. */
    val updateState: StateFlow<UpdateUiState> =
        updateRepository?.state ?: MutableStateFlow(UpdateUiState.Idle)

    val appVersionName: String = BuildConfig.VERSION_NAME

    private val _networkData = MutableStateFlow(NetworkDataInfo())
    val networkData: StateFlow<NetworkDataInfo> = _networkData.asStateFlow()

    private val _selectedTabIndex = MutableStateFlow(0)
    val selectedTabIndex: StateFlow<Int> = _selectedTabIndex.asStateFlow()

    private val _fps = MutableStateFlow(prefs.getFps())
    val fps: StateFlow<Int> = _fps.asStateFlow()

    private val _isSleepMode = MutableStateFlow(false)
    val isSleepMode: StateFlow<Boolean> = _isSleepMode.asStateFlow()

    private val _hasDndPolicyAccess = MutableStateFlow(sleepWakeController.hasDndPolicyAccess())
    val hasDndPolicyAccess: StateFlow<Boolean> = _hasDndPolicyAccess.asStateFlow()

    private val _isNotificationWakePaused = MutableStateFlow(false)
    val isNotificationWakePaused: StateFlow<Boolean> = _isNotificationWakePaused.asStateFlow()

    private val _isSleepWakeSetupDialogOpen = MutableStateFlow(false)
    val isSleepWakeSetupDialogOpen: StateFlow<Boolean> = _isSleepWakeSetupDialogOpen.asStateFlow()

    // Nhịp tiền cảnh: +1 ở mỗi onResume. Hộp thoại nhắc cập nhật key LaunchedEffect theo giá trị
    // này để lần quay lại app nào cũng được xét, không chỉ lần composition đầu.
    private val _foregroundTick = MutableStateFlow(0)

    // Drawer mở mặc định ở bản cũ che 89% màn hình 360dp ngay lần đầu, đúng thứ thiết kế này
    // đang cố loại bỏ. Mặc định đóng; mở bằng ô ☰ trên dải hoặc nút trong bảng điều khiển nhanh.
    private val _isSidebarVisible = MutableStateFlow(false)
    val isSidebarVisible: StateFlow<Boolean> = _isSidebarVisible.asStateFlow()

    private val _layoutMode = MutableStateFlow(ViewLayoutMode.FOUR_COLUMNS)
    val layoutMode: StateFlow<ViewLayoutMode> = _layoutMode.asStateFlow()

    private val _cluster = MutableStateFlow(prefs.getCluster())
    val cluster: StateFlow<String> = _cluster.asStateFlow()

    private val _editingAccount = MutableStateFlow<AccountConfig?>(null)
    val editingAccount: StateFlow<AccountConfig?> = _editingAccount.asStateFlow()

    private val _isBlackScreenSynced = MutableStateFlow(false)
    val isBlackScreenSynced: StateFlow<Boolean> = _isBlackScreenSynced.asStateFlow()

    private val _isHideMapSynced = MutableStateFlow(prefs.getHideMap())
    val isHideMapSynced: StateFlow<Boolean> = _isHideMapSynced.asStateFlow()

    // Cấu hình 🤖 Tự động tách riêng cho từng tab: nguồn sự thật là pref_auto_cfg_tab_<tabIndex>.
    private val _autoConfigs = MutableStateFlow(
        (0 until TabLimits.SLOT_COUNT).map { i -> AutoConfig.fromJson(prefs.getAutoConfig(i)) }
    )
    val autoConfigs: StateFlow<List<AutoConfig>> = _autoConfigs.asStateFlow()

    private val _autoDialogTabIndex = MutableStateFlow<Int?>(null)
    val autoDialogTabIndex: StateFlow<Int?> = _autoDialogTabIndex.asStateFlow()

    // ===== Bảng điều khiển nhanh (bottom sheet) =====
    // Nội dung 44dp header cũ (FPS, treo máy, chế độ xem, nút per-tab, traffic, drawer)
    // chuyển hết vào đây để chrome luôn hiện chỉ còn đúng dải 32dp ở đáy.
    // `controlSheetTabIndex` là tab đích cho nhóm nút per-tab (▶ Mở / ↻ / ⏹).
    private val _isControlSheetOpen = MutableStateFlow(false)
    val isControlSheetOpen: StateFlow<Boolean> = _isControlSheetOpen.asStateFlow()

    private val _controlSheetTabIndex = MutableStateFlow(0)
    val controlSheetTabIndex: StateFlow<Int> = _controlSheetTabIndex.asStateFlow()

    // ===== Số tab 1..8 =====
    // Nguồn sự thật là GameTabManager (owner duy nhất của clamp/persist/apply). UI chỉ đọc
    // StateFlow của manager ⇒ không bao giờ hiển thị một con số khác con số đang chạy.
    val tabCount: StateFlow<Int> = tabManager.tabCountState
    val maxTabCount: Int = tabManager.maxTabCount
    val isLowRam: Boolean = tabManager.isLowRam

    private val _tabCountWarnShown = MutableStateFlow(prefs.isTabWarnShown())
    val tabCountWarnShown: StateFlow<Boolean> = _tabCountWarnShown.asStateFlow()

    val isSystemRunning: StateFlow<Boolean> = tabManager.isSystemRunning
    val isMasterAutoRunning: StateFlow<Boolean> = tabManager.isSystemRunning
    val characterStatsList: StateFlow<List<CharacterStats>> = tabManager.characterStatsList

    init {
        // Manager tự hạ 60 → 30 FPS khi vượt ngưỡng; UI phải thấy giá trị mới, nếu không
        // nút FPS trong sheet hiển thị sai và lần bấm kế tiếp sẽ "tăng" lên 60.
        tabManager.onFpsChanged = { newFps -> _fps.value = newFps }
        (getApplication<Application>() as? VolamIdleApp)?.onMemoryTrimRequested = { level ->
            tabManager.trimMemory(level)
        }

        // Module 5: Traffic stats loop (3s active, 6s sleep to reduce background wakeups)
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                val rxOffset = prefs.getRxOffset()
                val txOffset = prefs.getTxOffset()
                val sampled = trafficMonitor.sample(rxOffset, txOffset)
                _networkData.value = sampled
                delay(if (_isSleepMode.value) 6000L else 3000L)
            }
        }
    }

    // MODULE 2: Master Login All
    fun onMasterLoginAllClicked() {
        tabManager.loginAll()
        KeepAliveService.start(getApplication())
    }

    // MODULE 2: Master Stop All
    fun onMasterStopAllClicked() {
        tabManager.stopAll()
        KeepAliveService.stop(getApplication())
    }

    fun selectTab(index: Int) {
        _selectedTabIndex.value = index
    }

    fun toggleSidebar() {
        _isSidebarVisible.value = !_isSidebarVisible.value
    }

    fun setLayoutMode(mode: ViewLayoutMode) {
        _layoutMode.value = mode
    }

    // Toggle 30 FPS / 60 FPS
    fun toggleFps() {
        val nextFps = if (_fps.value == 60) 30 else 60
        _fps.value = nextFps
        tabManager.applyFps(nextFps)
    }

    // Toggle Sleep Mode (OLED black screen)
    fun onSleepModeRequested() {
        refreshSleepWakePermissions()
        if (!_hasDndPolicyAccess.value && !prefs.isSleepWakePromptShown()) {
            _isSleepWakeSetupDialogOpen.value = true
            return
        }
        setSleepMode(true)
    }

    fun openSleepWakeSetupDialog() {
        refreshSleepWakePermissions()
        _isSleepWakeSetupDialogOpen.value = true
    }

    fun dismissSleepWakeSetupDialog() {
        _isSleepWakeSetupDialogOpen.value = false
    }

    fun confirmEnterSleepModeFromSetup() {
        prefs.setSleepWakePromptShown(true)
        _isSleepWakeSetupDialogOpen.value = false
        setSleepMode(true)
    }

    fun refreshSleepWakePermissions() {
        val granted = sleepWakeController.hasDndPolicyAccess()
        _hasDndPolicyAccess.value = granted
        if (_isSleepMode.value && granted && !sleepWakeController.isNotificationWakeSuppressed()) {
            val status = sleepWakeController.pauseAutoScreenWake()
            _isNotificationWakePaused.value =
                status == SleepWakePauseStatus.SUPPRESSED || status == SleepWakePauseStatus.ALREADY_SUPPRESSED
        } else {
            _isNotificationWakePaused.value =
                _isSleepMode.value && sleepWakeController.isNotificationWakeSuppressed()
        }
    }

    fun setSleepMode(enabled: Boolean) {
        _isSleepMode.value = enabled
        tabManager.setSleepMode(enabled)
        if (enabled) {
            KeepAliveService.start(getApplication())
            val status = sleepWakeController.pauseAutoScreenWake()
            _hasDndPolicyAccess.value = sleepWakeController.hasDndPolicyAccess()
            _isNotificationWakePaused.value =
                status == SleepWakePauseStatus.SUPPRESSED || status == SleepWakePauseStatus.ALREADY_SUPPRESSED
        } else {
            sleepWakeController.restoreAutoScreenWake()
            _isNotificationWakePaused.value = false
        }
    }

    fun changeCluster(newCluster: String) {
        _cluster.value = newCluster
        tabManager.switchCluster(newCluster)
    }

    // MODULE 7: Sync auto mode (Nhiệm Vụ, Phó Bản, Luyện Công)
    fun syncAutoMode(mode: String) {
        tabManager.syncAutoMode(mode)
    }

    // Đồng bộ Màn hình đen (Hệ thống -> Tuỳ Chọn -> Màn hình đen) cho các Tab
    fun syncBlackScreen(enabled: Boolean) {
        _isBlackScreenSynced.value = enabled
        tabManager.syncBlackScreen(enabled)
    }

    // Đồng bộ Xoá hiển thị bản đồ cho tất cả các Tab
    fun syncHideMap(enabled: Boolean) {
        _isHideMapSynced.value = enabled
        tabManager.syncHideMap(enabled)
    }

    // Đồng bộ Bán đồ thừa (Hành trang -> Bán thừa x -> Bán) cho tất cả các Tab
    fun syncSellExcessItems() {
        tabManager.syncSellExcessItems()
    }

    fun resetNetworkCounters() {
        val current = _networkData.value
        prefs.resetTrafficCounters(current.totalRxBytes, current.totalTxBytes)
        val refreshed = trafficMonitor.sample(current.totalRxBytes, current.totalTxBytes)
        _networkData.value = refreshed
    }

    // ===== MODULE 8: Tự động trong game (bảng 🤖 riêng cho từng tab) =====
    fun openAutoConfigDialog(tabIndex: Int) {
        val safeTab = tabIndex.coerceIn(0, (tabManager.tabCountState.value - 1).coerceAtLeast(0))
        refreshAutoConfigFromPrefs(safeTab)
        _autoDialogTabIndex.value = safeTab
    }

    fun switchAutoDialogTab(tabIndex: Int) {
        val safeTab = tabIndex.coerceIn(0, (tabManager.tabCountState.value - 1).coerceAtLeast(0))
        refreshAutoConfigFromPrefs(safeTab)
        _autoDialogTabIndex.value = safeTab
    }

    private fun refreshAutoConfigFromPrefs(tabIndex: Int) {
        if (tabIndex !in 0 until TabLimits.SLOT_COUNT) return
        val loaded = AutoConfig.fromJson(prefs.getAutoConfig(tabIndex))
        val mutable = _autoConfigs.value.toMutableList()
        if (tabIndex in mutable.indices) {
            mutable[tabIndex] = loaded
            _autoConfigs.value = mutable
        }
    }

    fun dismissAutoConfigDialog() {
        _autoDialogTabIndex.value = null
    }

    fun saveAutoConfig(tabIndex: Int, config: AutoConfig) {
        if (tabIndex !in 0 until TabLimits.SLOT_COUNT) return
        val json = config.toJson().toString()
        prefs.setAutoConfig(tabIndex, json)
        val mutable = _autoConfigs.value.toMutableList()
        if (tabIndex in mutable.indices) {
            mutable[tabIndex] = config
            _autoConfigs.value = mutable
        }
        _autoDialogTabIndex.value = null
        // Đẩy ngay xuống đúng tab đang cấu hình và kích hoạt ngay lập tức nếu đang bật.
        tabManager.updateAutoConfig(tabIndex, json, immediate = true)
    }

    fun saveAllAutoConfig(config: AutoConfig) {
        val count = tabManager.tabCountState.value.coerceIn(1, TabLimits.SLOT_COUNT)
        val json = config.toJson().toString()
        val mutable = _autoConfigs.value.toMutableList()
        for (i in 0 until count) {
            prefs.setAutoConfig(i, json)
            if (i in mutable.indices) {
                mutable[i] = config
            }
            tabManager.updateAutoConfig(i, json, immediate = true)
        }
        _autoConfigs.value = mutable
        _autoDialogTabIndex.value = null
    }

    fun resetAutoDailyCounters(tabIndex: Int) {
        tabManager.resetAutoDailyCounters(tabIndex)
    }

    // ===== Bảng điều khiển nhanh =====
    fun openControlSheet(tabIndex: Int) {
        // Một cửa duy nhất cho mọi đường mở sheet, nên chặn synthetic click luôn ở đây:
        // mở sheet là một cú chạm chrome, không đi qua WebView nên lastTouchTimeMs không tự tiến.
        tabManager.markUiInteraction()
        _controlSheetTabIndex.value = tabIndex.coerceIn(0, (tabManager.tabCountState.value - 1).coerceAtLeast(0))
        _isControlSheetOpen.value = true
    }

    fun dismissControlSheet() {
        _isControlSheetOpen.value = false
    }

    fun reloadTab(tabIndex: Int) {
        tabManager.getOrCreateWebView(tabIndex).reload()
    }

    fun stopSingleTab(tabIndex: Int) {
        tabManager.stopSingleTab(tabIndex)
    }

    fun startEditingAccount(tabIndex: Int) {
        _editingAccount.value = prefs.getAccount(tabIndex)
    }

    fun saveEditingAccount(config: AccountConfig) {
        prefs.saveAccount(config)
        _editingAccount.value = null
        tabManager.onAccountChanged(config.tabIndex)
    }

    fun deleteAccount(tabIndex: Int) {
        // Chạm chrome: đẩy lastTouchTimeMs cho mọi slot TRƯỚC khi xoá, để vòng đăng nhập 850ms
        // và watchdog không bấm/click tổng hợp đúng lúc trang đang bị đăng xuất.
        tabManager.markUiInteraction()
        // Xoá ở MỌI cụm: chỉ ghi đè cụm hiện tại thì tài khoản hiện lại ngay khi người dùng
        // chuyển cụm (getAccount đọc một hình dạng khoá khác).
        prefs.clearAccountEverywhere(tabIndex)
        _editingAccount.value = null
        tabManager.clearAccountSession(tabIndex)
    }

    fun dismissAccountDialog() {
        _editingAccount.value = null
    }

    // ===== Số tab =====

    /**
     * Owner duy nhất là `GameTabManager.setTabCount`. UI mirror giá trị TRẢ VỀ, không mirror
     * giá trị yêu cầu: nếu máy RAM thấp chặn ở 4 thì nút hiển thị đúng 4 ngay.
     */
    fun setTabCount(requested: Int) {
        val actual = tabManager.setTabCount(requested)
        _selectedTabIndex.value = _selectedTabIndex.value.coerceIn(0, (actual - 1).coerceAtLeast(0))
        _controlSheetTabIndex.value =
            _controlSheetTabIndex.value.coerceIn(0, (actual - 1).coerceAtLeast(0))
    }

    fun markTabCountWarned() {
        prefs.setTabWarnShown(true)
        _tabCountWarnShown.value = true
    }

    /** Có tab nào trong [from, to) đang IN_GAME/LOGGING_IN? Dùng để hỏi trước khi giảm. */
    fun hasBusyTabsInRange(from: Int, toExclusive: Int): Boolean {
        val stats = characterStatsList.value
        for (i in from until toExclusive) {
            when (stats.getOrNull(i)?.state) {
                com.example.model.TabLifecycleState.IN_GAME,
                com.example.model.TabLifecycleState.LOGGING_IN -> return true
                else -> Unit
            }
        }
        return false
    }

    // ===== Tự động cập nhật: 3 cú chạm riêng biệt, không cú nào tự động hoá cú sau =====
    // 1. kiểm tra (có thể tự chạy nền) 2. tải về 3. cài đặt. Repo không bao giờ làm 2 và 3
    // mà không có lời gọi từ đây, và đây chỉ được gọi từ tap trong ControlSheet.

    fun checkForUpdate() {
        viewModelScope.launch { updateRepository?.checkForUpdate(manual = true) }
    }

    fun startUpdateDownload() {
        viewModelScope.launch { updateRepository?.download() }
    }

    fun installUpdate() {
        viewModelScope.launch { updateRepository?.install() }
    }

    fun onActivityResumed() {
        // Kiểm lại AppOp cài đặt (không tin người dùng đã bật) + không tự cài.
        updateRepository?.onAppResumed()
        refreshSleepWakePermissions()
        // Mỗi lần quay lại tiền cảnh là một nhịp để MainActivity hỏi "có bản mới chưa báo không".
        // Đây là bộ đếm, không phải cờ: hộp thoại chỉ hiện khi còn bản chưa nhắc (xem takeUpdateNotice).
        _foregroundTick.value += 1
    }

    /**
     * Số lần Activity resume. Compose dùng làm khoá `LaunchedEffect` để kiểm tra lại ở MỖI lần
     * mở app / quay lại tiền cảnh, thay vì chỉ một lần lúc composition đầu.
     */
    val foregroundTick: StateFlow<Int> = _foregroundTick.asStateFlow()

    /**
     * Hỏi "có nên hiện hộp thoại nhắc cập nhật không" và ĐÁNH DẤU đã hiện ngay khi trả về tên bản.
     *
     * Chỉ trả tên khi có release mới hơn bản đang chạy ĐÃ BIẾT (đọc từ cache lúc process khởi
     * động, hoặc từ lần kiểm tra gần nhất) và chưa từng nhắc bản đó. Nhờ đọc release đã cache,
     * thông báo vẫn hiện được khi offline mà không phải chờ request 3 giây của vòng nền.
     *
     * Treo máy: không trả gì và KHÔNG đánh dấu — màn hình cố ý đen, người dùng đang cố tắt
     * mọi thứ hiển thị; đến lần mở sau (hoặc khi thoát Treo máy) mới nhắc.
     */
    fun takeUpdateNotice(): String? {
        if (_isSleepMode.value) return null
        val repository = updateRepository ?: return null
        if (!repository.shouldShowUpdateDialog()) return null
        val versionName = repository.newerReleaseName() ?: return null
        repository.markUpdateDialogShown()
        return versionName
    }

    /**
     * "Cập nhật" trong hộp thoại nhắc đầu phiên.
     *
     * Đây là cú chạm của người dùng, không phải tự động: mở bảng điều khiển nơi tiến trình
     * tải/cài được hiển thị, rồi đi đúng luồng tải hiện có. Cài đặt vẫn là một cú chạm thứ hai
     * trong sheet (bất biến "không tự cài" giữ nguyên).
     */
    fun onUpdateNoticeAccepted() {
        openControlSheet(_selectedTabIndex.value)
        // Nếu luồng tải/cài đã đi được một bước (APK đã verify xong, đang tải, đang chờ quyền)
        // thì KHÔNG tải lại: bảng điều khiển đã hiện đúng nút của bước đó ("Cài đặt", "Mở cài
        // đặt"…). Gọi download() ở đây sẽ ghi đè tệp vừa verify và đẩy người dùng lùi một bước.
        val state = updateState.value
        val inProgress = state is UpdateUiState.ReadyToInstall ||
            state is UpdateUiState.Downloading ||
            state is UpdateUiState.Verifying ||
            state is UpdateUiState.Installing ||
            state is UpdateUiState.NeedsPermission
        if (inProgress) return

        if (updateRepository?.hasNewerRelease() == true) {
            viewModelScope.launch { updateRepository?.download() }
        } else {
            // Hiếm: cache vừa bị xoá giữa lúc hiện hộp thoại — kiểm tra lại rồi để sheet dẫn tiếp.
            checkForUpdate()
        }
    }

    fun releasesPageUrl(): String =
        updateRepository?.releasesPageUrl() ?: "https://github.com/gvhoang/volam-idle-manager/releases"

    fun shouldShowUpdateBanner(): Boolean = updateRepository?.shouldShowBanner() ?: false

    fun markUpdateBannerShown() {
        updateRepository?.markBannerShown()
    }

    /** Thông điệp "đã cập nhật" của process trước; đọc một lần rồi xoá. */
    fun consumeLaunchMessage(): String? = updateRepository?.consumeLaunchMessage()

    override fun onCleared() {
        sleepWakeController.restoreAutoScreenWake()
        tabManager.cleanup()
        super.onCleared()
    }
}
