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
import com.example.webview.AutoConfig
import com.example.update.AppReleaseInfo
import com.example.update.AppUpdateManager
import android.widget.Toast
import com.example.backup.AccountBackupManager
import com.example.update.UpdateCheckState
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

    val updateManager = AppUpdateManager(application)
    val backupManager = AccountBackupManager(application)
    val appVersionName: String = BuildConfig.VERSION_NAME
    val isTestChannel: Boolean = BuildConfig.IS_TEST_CHANNEL
    val updateRepoName: String = BuildConfig.UPDATE_REPO

    private val _updateState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateState: StateFlow<UpdateCheckState> = _updateState.asStateFlow()

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

    private val _isHideGameSynced = MutableStateFlow(prefs.getHideGame())
    val isHideGameSynced: StateFlow<Boolean> = _isHideGameSynced.asStateFlow()

    private val _isPcMode = MutableStateFlow(prefs.getPcMode())
    val isPcMode: StateFlow<Boolean> = _isPcMode.asStateFlow()

    private val _autoConfigs = MutableStateFlow(
        (0 until TabLimits.SLOT_COUNT).map { i -> AutoConfig.fromJson(prefs.getAutoConfig(i)) }
    )
    val autoConfigs: StateFlow<List<AutoConfig>> = _autoConfigs.asStateFlow()

    private val _autoDialogTabIndex = MutableStateFlow<Int?>(null)
    val autoDialogTabIndex: StateFlow<Int?> = _autoDialogTabIndex.asStateFlow()

    private val _isControlSheetOpen = MutableStateFlow(false)
    val isControlSheetOpen: StateFlow<Boolean> = _isControlSheetOpen.asStateFlow()

    private val _controlSheetTabIndex = MutableStateFlow(0)
    val controlSheetTabIndex: StateFlow<Int> = _controlSheetTabIndex.asStateFlow()

    val tabCount: StateFlow<Int> = tabManager.tabCountState
    val maxTabCount: Int = tabManager.maxTabCount
    val isLowRam: Boolean = tabManager.isLowRam

    private val _tabCountWarnShown = MutableStateFlow(prefs.isTabWarnShown())
    val tabCountWarnShown: StateFlow<Boolean> = _tabCountWarnShown.asStateFlow()

    val isSystemRunning: StateFlow<Boolean> = tabManager.isSystemRunning
    val isMasterAutoRunning: StateFlow<Boolean> = tabManager.isSystemRunning
    val characterStatsList: StateFlow<List<CharacterStats>> = tabManager.characterStatsList

    init {
        tabManager.onFpsChanged = { newFps -> _fps.value = newFps }
        (getApplication<Application>() as? VolamIdleApp)?.onMemoryTrimRequested = { level ->
            tabManager.trimMemory(level)
        }

        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                val rxOffset = prefs.getRxOffset()
                val txOffset = prefs.getTxOffset()
                val sampled = trafficMonitor.sample(rxOffset, txOffset)
                _networkData.value = sampled
                delay(if (_isSleepMode.value) 6000L else 3000L)
            }
        }

        // Tự động kiểm tra bản cập nhật mới theo đúng kênh repo riêng (tool-test hoặc clien-volamidle)
        checkAppUpdate(silent = true)
    }

    fun checkAppUpdate(silent: Boolean = false) {
        if (_updateState.value is UpdateCheckState.Checking || _updateState.value is UpdateCheckState.Downloading) return
        viewModelScope.launch {
            if (!silent) _updateState.value = UpdateCheckState.Checking
            val res = updateManager.checkLatestRelease()
            res.onSuccess { rel ->
                if (rel != null) {
                    _updateState.value = UpdateCheckState.Available(rel)
                } else if (!silent) {
                    _updateState.value = UpdateCheckState.UpToDate(updateRepoName)
                }
            }.onFailure { err ->
                if (!silent) {
                    _updateState.value = UpdateCheckState.Error(err.message ?: "Không thể kiểm tra cập nhật")
                }
            }
        }
    }

    fun startUpdateDownload(release: AppReleaseInfo) {
        if (_updateState.value is UpdateCheckState.Downloading) return
        viewModelScope.launch {
            _updateState.value = UpdateCheckState.Downloading(release, 0)
            val res = updateManager.downloadAndInstall(release) { pct ->
                _updateState.value = UpdateCheckState.Downloading(release, pct)
            }
            res.onSuccess {
                _updateState.value = UpdateCheckState.Idle
            }.onFailure { err ->
                _updateState.value = UpdateCheckState.Error(err.message ?: "Lỗi tải bản cập nhật")
            }
        }
    }

    fun dismissUpdateDialog() {
        if (_updateState.value !is UpdateCheckState.Downloading) {
            _updateState.value = UpdateCheckState.Idle
        }
    }

    fun onMasterLoginAllClicked() {
        tabManager.loginAll()
        KeepAliveService.start(getApplication())
    }

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

    fun toggleFps() {
        val nextFps = if (_fps.value == 60) 30 else 60
        _fps.value = nextFps
        tabManager.applyFps(nextFps)
    }

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

    fun syncAutoMode(mode: String) {
        tabManager.syncAutoMode(mode)
    }

    fun syncBlackScreen(enabled: Boolean) {
        _isBlackScreenSynced.value = enabled
        tabManager.syncBlackScreen(enabled)
    }

    fun syncHideMap(enabled: Boolean) {
        _isHideMapSynced.value = enabled
        prefs.setHideMap(enabled)
        tabManager.syncHideMap(enabled)
    }

    fun syncHideGame(enabled: Boolean) {
        _isHideGameSynced.value = enabled
        prefs.setHideGame(enabled)
        tabManager.syncHideGame(enabled)
    }

    fun setPcMode(enabled: Boolean) {
        _isPcMode.value = enabled
        prefs.setPcMode(enabled)
    }

    fun syncSellExcessItems() {
        tabManager.syncSellExcessItems()
    }

    fun resetNetworkCounters() {
        val current = _networkData.value
        prefs.resetTrafficCounters(current.totalRxBytes, current.totalTxBytes)
        val refreshed = trafficMonitor.sample(current.totalRxBytes, current.totalTxBytes)
        _networkData.value = refreshed
    }

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
        // Chỉ lưu các thay đổi lựa chọn, giữ nguyên trạng thái Bật/Tắt hiện tại,
        // không đóng cửa sổ nổi và không tự chạy auto app.
        val existingOn = _autoConfigs.value.getOrNull(tabIndex)?.on == true
        val saved = config.copy(on = existingOn)
        val json = saved.toJson().toString()
        prefs.setAutoConfig(tabIndex, json)
        val mutable = _autoConfigs.value.toMutableList()
        if (tabIndex in mutable.indices) {
            mutable[tabIndex] = saved
            _autoConfigs.value = mutable
        }
        tabManager.saveAutoConfigOnly(tabIndex, json)
    }

    fun saveAllAutoConfig(config: AutoConfig) {
        val count = tabManager.tabCountState.value.coerceIn(1, TabLimits.SLOT_COUNT)
        val mutable = _autoConfigs.value.toMutableList()
        for (i in 0 until count) {
            // Lưu các lựa chọn cho tất cả tab nhưng giữ nguyên trạng thái Bật/Tắt riêng từng tab,
            // không đóng cửa sổ nổi và không tự bật/chạy auto app.
            val existingOn = mutable.getOrNull(i)?.on == true
            val perTab = config.copy(on = existingOn)
            val json = perTab.toJson().toString()
            prefs.setAutoConfig(i, json)
            if (i in mutable.indices) {
                mutable[i] = perTab
            }
            tabManager.saveAutoConfigOnly(i, json)
        }
        _autoConfigs.value = mutable
    }

    fun setAutoEnabled(tabIndex: Int, enabled: Boolean, config: AutoConfig) {
        if (tabIndex !in 0 until TabLimits.SLOT_COUNT) return
        val updated = config.copy(on = enabled)
        val json = updated.toJson().toString()
        prefs.setAutoConfig(tabIndex, json)
        val mutable = _autoConfigs.value.toMutableList()
        if (tabIndex in mutable.indices) {
            mutable[tabIndex] = updated
            _autoConfigs.value = mutable
        }
        tabManager.updateAutoConfig(tabIndex, json, immediate = enabled)
    }

    fun resetAutoDailyCounters(tabIndex: Int) {
        tabManager.resetAutoDailyCounters(tabIndex)
    }

    fun openControlSheet(tabIndex: Int) {
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
        autoBackupSilently()
    }

    private fun autoBackupSilently() {
        viewModelScope.launch {
            runCatching { backupManager.exportAll(prefs, tabManager.tabCountState.value) }
        }
    }

    fun exportAccountsBackup() {
        viewModelScope.launch {
            val res = backupManager.exportAll(prefs, tabManager.tabCountState.value)
            res.onSuccess { r ->
                runCatching {
                    Toast.makeText(getApplication(), "💾 ${r.message}", Toast.LENGTH_LONG).show()
                }
            }.onFailure { err ->
                runCatching {
                    Toast.makeText(getApplication(), "❌ Lỗi sao lưu: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun importAccountsBackup() {
        viewModelScope.launch {
            val res = backupManager.importAll(prefs)
            res.onSuccess { r ->
                val refreshed = (0 until TabLimits.SLOT_COUNT).map { i -> AutoConfig.fromJson(prefs.getAutoConfig(i)) }
                _autoConfigs.value = refreshed
                _cluster.value = prefs.getCluster()
                val actualCount = tabManager.setTabCount(prefs.getTabCount())
                for (idx in 0 until actualCount) {
                    val cfgJson = prefs.getAutoConfig(idx)
                    if (!cfgJson.isNullOrBlank()) {
                        tabManager.saveAutoConfigOnly(idx, cfgJson)
                    }
                }
                runCatching {
                    Toast.makeText(getApplication(), "📥 ${r.message}", Toast.LENGTH_LONG).show()
                }
            }.onFailure { err ->
                runCatching {
                    Toast.makeText(getApplication(), "❌ Lỗi khôi phục: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun deleteAccount(tabIndex: Int) {
        tabManager.markUiInteraction()
        prefs.clearAccountEverywhere(tabIndex)
        _editingAccount.value = null
        tabManager.clearAccountSession(tabIndex)
    }

    fun dismissAccountDialog() {
        _editingAccount.value = null
    }

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

    fun onActivityResumed() {
        refreshSleepWakePermissions()
    }

    override fun onCleared() {
        sleepWakeController.restoreAutoScreenWake()
        tabManager.cleanup()
        super.onCleared()
    }
}
