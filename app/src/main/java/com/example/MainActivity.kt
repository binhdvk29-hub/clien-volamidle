package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.sleep.SleepWakeController
import com.example.ui.MainViewModel
import com.example.update.UpdateCheckState
import com.example.ui.components.AccountDialog
import com.example.ui.components.AutoConfigDialog
import com.example.ui.components.ControlSheet
import com.example.ui.components.GameWebViewContainer
import com.example.ui.components.OledBlackScreen
import com.example.ui.components.SidebarView
import com.example.ui.components.SleepWakeSetupDialog
import com.example.ui.components.hasDownloadTraffic
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val screenWakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!viewModel.isSleepMode.value) return
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    applySleepBrightness(true)
                }
            }
        }
    }

    private fun applySleepBrightness(low: Boolean) {
        val layoutParams = window.attributes
        val target = if (low) 0.01f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        if (layoutParams.screenBrightness != target) {
            layoutParams.screenBrightness = target
            window.attributes = layoutParams
        }
    }

    private fun applyFullscreenMode(fullscreen: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (fullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val wakeFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        runCatching {
            ContextCompat.registerReceiver(
                this,
                screenWakeReceiver,
                wakeFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        setContent {
            MyApplicationTheme(darkTheme = true) {
                MainScreen(
                    viewModel = viewModel,
                    onSetWindowBrightnessLow = { low ->
                        applySleepBrightness(low)
                    },
                    onSetFullscreen = { fs ->
                        applyFullscreenMode(fs)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onActivityResumed()
        viewModel.tabManager.onAppForegrounded()
        if (viewModel.isSleepMode.value) {
            applySleepBrightness(!viewModel.isSleepWakeSetupDialogOpen.value)
        }
        applyFullscreenMode(viewModel.isPcMode.value)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            if (viewModel.isSleepMode.value) {
                applySleepBrightness(!viewModel.isSleepWakeSetupDialogOpen.value)
            }
            if (viewModel.isPcMode.value) {
                applyFullscreenMode(true)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.tabManager.onAppBackgrounded()
    }

    override fun onStop() {
        super.onStop()
        viewModel.tabManager.onAppBackgrounded()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenWakeReceiver) }
        if (isFinishing && !isChangingConfigurations && viewModel.isSleepMode.value) {
            viewModel.setSleepMode(false)
        }
        super.onDestroy()
    }
}

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onSetWindowBrightnessLow: (Boolean) -> Unit,
    onSetFullscreen: (Boolean) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    val isMasterAutoRunning by viewModel.isMasterAutoRunning.collectAsState()
    val characterStatsList by viewModel.characterStatsList.collectAsState()
    val gameLogs by viewModel.gameLogs.collectAsState()
    val selectedTabIndex by viewModel.selectedTabIndex.collectAsState()
    val networkData by viewModel.networkData.collectAsState()
    val isDownloading = hasDownloadTraffic(networkData.downloadSpeedFormatted)
    val fps by viewModel.fps.collectAsState()
    val isSleepMode by viewModel.isSleepMode.collectAsState()
    val hasDndPolicyAccess by viewModel.hasDndPolicyAccess.collectAsState()
    val isNotificationWakePaused by viewModel.isNotificationWakePaused.collectAsState()
    val isSleepWakeSetupDialogOpen by viewModel.isSleepWakeSetupDialogOpen.collectAsState()
    val isSidebarVisible by viewModel.isSidebarVisible.collectAsState()
    val layoutMode by viewModel.layoutMode.collectAsState()
    val editingAccount by viewModel.editingAccount.collectAsState()
    val isBlackScreenSynced by viewModel.isBlackScreenSynced.collectAsState()
    val isHideMapSynced by viewModel.isHideMapSynced.collectAsState()
    val isHideGameSynced by viewModel.isHideGameSynced.collectAsState()
    val isPcMode by viewModel.isPcMode.collectAsState()
    val ultraLightTabs by viewModel.ultraLightTabs.collectAsState()
    val isPcModeEffective = isPcMode
    val autoConfigs by viewModel.autoConfigs.collectAsState()
    val autoDialogTabIndex by viewModel.autoDialogTabIndex.collectAsState()
    val tabCount by viewModel.tabCount.collectAsState()
    val tabCountWarnShown by viewModel.tabCountWarnShown.collectAsState()
    val updateState by viewModel.updateState.collectAsState()

    val isControlSheetOpen by viewModel.isControlSheetOpen.collectAsState()
    val controlSheetTabIndex by viewModel.controlSheetTabIndex.collectAsState()
    val sheetTabStats = characterStatsList.getOrElse(controlSheetTabIndex) {
        com.example.model.CharacterStats(tabIndex = controlSheetTabIndex)
    }

    val context = LocalContext.current

    LaunchedEffect(isSleepMode, isSleepWakeSetupDialogOpen) {
        onSetWindowBrightnessLow(isSleepMode && !isSleepWakeSetupDialogOpen)
    }

    LaunchedEffect(isPcModeEffective) {
        onSetFullscreen(isPcModeEffective)
        viewModel.tabManager.applyPcMode(isPcModeEffective)
    }

    BackHandler(enabled = isSleepWakeSetupDialogOpen || isSleepMode || editingAccount != null || autoDialogTabIndex != null || isControlSheetOpen || (isPortrait && isSidebarVisible)) {
        when {
            isSleepWakeSetupDialogOpen -> viewModel.dismissSleepWakeSetupDialog()
            isSleepMode -> viewModel.setSleepMode(false)
            isControlSheetOpen -> viewModel.dismissControlSheet()
            editingAccount != null -> viewModel.dismissAccountDialog()
            autoDialogTabIndex != null -> viewModel.dismissAutoConfigDialog()
            isPortrait && isSidebarVisible -> viewModel.toggleSidebar()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D14)),
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isPcModeEffective) {
                            Modifier
                        } else {
                            Modifier.windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                                )
                            )
                        }
                    )
            ) {
                if (isPortrait) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        GameWebViewContainer(
                            tabManager = viewModel.tabManager,
                            tabCount = tabCount,
                            characterStatsList = characterStatsList,
                            selectedTabIndex = selectedTabIndex,
                            layoutMode = layoutMode,
                            isDownloading = isDownloading,
                            onSelectTab = { viewModel.selectTab(it) },
                            onOpenControlSheet = { viewModel.openControlSheet(it) },
                            onOpenSidebar = {
                                viewModel.tabManager.markUiInteraction()
                                viewModel.toggleSidebar()
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        if (isSidebarVisible) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .clickable { viewModel.toggleSidebar() }
                            )

                            SidebarView(
                                isMasterAutoRunning = isMasterAutoRunning,
                                tabCount = tabCount,
                                characterStatsList = characterStatsList,
                                selectedTabIndex = selectedTabIndex,
                                onSelectTab = { viewModel.selectTab(it) },
                                onLoginAll = { viewModel.onMasterLoginAllClicked() },
                                onStopAll = { viewModel.onMasterStopAllClicked() },
                                onEditAccount = { viewModel.startEditingAccount(it) },
                                onToggleSidebar = { viewModel.toggleSidebar() },
                                getAccount = { viewModel.prefs.getAccount(it) },
                                isBlackScreenSynced = isBlackScreenSynced,
                                onSyncBlackScreen = { viewModel.syncBlackScreen(it) },
                                isHideMapSynced = isHideMapSynced,
                                onSyncHideMap = { viewModel.syncHideMap(it) },
                                isHideGameSynced = isHideGameSynced,
                                onSyncHideGame = { viewModel.syncHideGame(it) },
                                isPcMode = isPcMode,
                                isLandscape = !isPortrait,
                                onTogglePcMode = { viewModel.setPcMode(!isPcMode) },
                                onSyncSellExcess = { viewModel.syncSellExcessItems() },
                                onExportBackup = { viewModel.exportAccountsBackup() },
                                onImportBackup = { viewModel.importAccountsBackup() },
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(320.dp)
                                    .align(Alignment.CenterStart)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        if (isSidebarVisible) {
                            SidebarView(
                                isMasterAutoRunning = isMasterAutoRunning,
                                tabCount = tabCount,
                                characterStatsList = characterStatsList,
                                selectedTabIndex = selectedTabIndex,
                                onSelectTab = { viewModel.selectTab(it) },
                                onLoginAll = { viewModel.onMasterLoginAllClicked() },
                                onStopAll = { viewModel.onMasterStopAllClicked() },
                                onEditAccount = { viewModel.startEditingAccount(it) },
                                onToggleSidebar = { viewModel.toggleSidebar() },
                                getAccount = { viewModel.prefs.getAccount(it) },
                                isBlackScreenSynced = isBlackScreenSynced,
                                onSyncBlackScreen = { viewModel.syncBlackScreen(it) },
                                isHideMapSynced = isHideMapSynced,
                                onSyncHideMap = { viewModel.syncHideMap(it) },
                                isHideGameSynced = isHideGameSynced,
                                onSyncHideGame = { viewModel.syncHideGame(it) },
                                isPcMode = isPcMode,
                                isLandscape = !isPortrait,
                                onTogglePcMode = { viewModel.setPcMode(!isPcMode) },
                                onSyncSellExcess = { viewModel.syncSellExcessItems() },
                                onExportBackup = { viewModel.exportAccountsBackup() },
                                onImportBackup = { viewModel.importAccountsBackup() },
                                modifier = Modifier
                                    .width(265.dp)
                                    .windowInsetsPadding(
                                        WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                                    )
                            )
                        }

                        GameWebViewContainer(
                            tabManager = viewModel.tabManager,
                            tabCount = tabCount,
                            characterStatsList = characterStatsList,
                            selectedTabIndex = selectedTabIndex,
                            layoutMode = layoutMode,
                            isDownloading = isDownloading,
                            onSelectTab = { viewModel.selectTab(it) },
                            onOpenControlSheet = { viewModel.openControlSheet(it) },
                            onOpenSidebar = {
                                viewModel.tabManager.markUiInteraction()
                                viewModel.toggleSidebar()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }

            ControlSheet(
                isOpen = isControlSheetOpen,
                tabIndex = controlSheetTabIndex,
                tabLabel = sheetTabStats.characterName,
                layoutMode = layoutMode,
                fps = fps,
                networkData = networkData,
                onOpenTab = {
                    viewModel.selectTab(controlSheetTabIndex)
                    viewModel.tabManager.openSingleTab(controlSheetTabIndex)
                },
                onReloadTab = { viewModel.reloadTab(controlSheetTabIndex) },
                onStopTab = { viewModel.stopSingleTab(controlSheetTabIndex) },
                onChangeLayoutMode = { viewModel.setLayoutMode(it) },
                onToggleFps = { viewModel.toggleFps() },
                onActivateSleepMode = { viewModel.onSleepModeRequested() },
                onOpenSidebar = {
                    viewModel.dismissControlSheet()
                    viewModel.toggleSidebar()
                },
                onResetNetworkStats = { viewModel.resetNetworkCounters() },
                onSyncAutoMode = { viewModel.syncAutoMode(it) },
                isAutoPilotOn = autoConfigs.getOrNull(controlSheetTabIndex)?.on == true,
                onOpenAutoConfig = {
                    viewModel.dismissControlSheet()
                    viewModel.openAutoConfigDialog(controlSheetTabIndex)
                },
                tabCount = tabCount,
                maxTabCount = viewModel.maxTabCount,
                isLowRam = viewModel.isLowRam,
                tabCountWarnShown = tabCountWarnShown,
                hasBusyTabsInRange = { from, toExclusive ->
                    viewModel.hasBusyTabsInRange(from, toExclusive)
                },
                onSetTabCount = { viewModel.setTabCount(it) },
                onMarkTabCountWarned = { viewModel.markTabCountWarned() },
                appVersionName = viewModel.appVersionName,
                onDismiss = { viewModel.dismissControlSheet() },
                onInteract = { viewModel.tabManager.markUiInteraction() },
                hasDndPolicyAccess = hasDndPolicyAccess,
                onOpenSleepWakeSetup = { viewModel.openSleepWakeSetupDialog() },
                isHideMap = isHideMapSynced,
                onToggleHideMap = { viewModel.syncHideMap(!isHideMapSynced) },
                isHideGame = isHideGameSynced,
                onToggleHideGame = { viewModel.syncHideGame(!isHideGameSynced) },
                isPcMode = isPcMode,
                onTogglePcMode = { viewModel.setPcMode(!isPcMode) },
                isUltraLightTab = ultraLightTabs.getOrNull(controlSheetTabIndex) == true,
                onToggleUltraLightTab = {
                    val cur = ultraLightTabs.getOrNull(controlSheetTabIndex) == true
                    viewModel.setTabUltraLight(controlSheetTabIndex, !cur)
                },
                isTestChannel = viewModel.isTestChannel,
                gameLogs = gameLogs,
                onClearGameLogs = { viewModel.clearGameLogs() },
                updateStatusLabel = when (val u = updateState) {
                    is UpdateCheckState.Checking -> "⏳ Đang kiểm..."
                    is UpdateCheckState.Downloading -> "⬇️ ${u.progressPercent}%"
                    is UpdateCheckState.Available -> "⬆️ Có bản mới!"
                    is UpdateCheckState.UpToDate -> "✅ Mới nhất"
                    else -> "🔄 Cập nhật"
                },
                onCheckUpdate = { viewModel.checkAppUpdate(silent = false) }
            )

            if (isSleepMode) {
                OledBlackScreen(
                    onUnlock = { viewModel.setSleepMode(false) },
                    isNotificationWakePaused = isNotificationWakePaused,
                    hasDndPolicyAccess = hasDndPolicyAccess,
                    onOpenWakeSetup = { viewModel.openSleepWakeSetupDialog() }
                )
            }

            if (isSleepWakeSetupDialogOpen) {
                SleepWakeSetupDialog(
                    hasDndPolicyAccess = hasDndPolicyAccess,
                    isAlreadyInSleepMode = isSleepMode,
                    onOpenDndSettings = {
                        runCatching {
                            context.startActivity(SleepWakeController.dndPolicySettingsIntent())
                        }
                    },
                    onOpenDisplaySettings = {
                        runCatching {
                            context.startActivity(SleepWakeController.displaySettingsIntent())
                        }
                    },
                    onConfirmSleepMode = { viewModel.confirmEnterSleepModeFromSetup() },
                    onDismiss = { viewModel.dismissSleepWakeSetupDialog() }
                )
            }

            editingAccount?.let { account ->
                AccountDialog(
                    accountConfig = account,
                    onSave = { updated -> viewModel.saveEditingAccount(updated) },
                    onDelete = { deleted -> viewModel.deleteAccount(deleted.tabIndex) },
                    onDismiss = { viewModel.dismissAccountDialog() }
                )
            }

            autoDialogTabIndex?.let { dialogTab ->
                val safeTab = dialogTab.coerceIn(0, (tabCount - 1).coerceAtLeast(0))
                val tabCharName = characterStatsList.getOrNull(safeTab)?.characterName.orEmpty()
                val tabSects = characterStatsList.map { it.sect }
                AutoConfigDialog(
                    tabIndex = safeTab,
                    tabCount = tabCount,
                    configs = autoConfigs,
                    characterName = tabCharName,
                    characterSects = tabSects,
                    onSelectTab = { newTab -> viewModel.switchAutoDialogTab(newTab) },
                    onToggleAuto = { tIdx, enabled, updated ->
                        viewModel.setAutoEnabled(tIdx, enabled, updated)
                    },
                    onSave = { tIdx, updated -> viewModel.saveAutoConfig(tIdx, updated) },
                    onSaveAll = { updated -> viewModel.saveAllAutoConfig(updated) },
                    onResetCounters = { tIdx -> viewModel.resetAutoDailyCounters(tIdx) },
                    onDismiss = { viewModel.dismissAutoConfigDialog() }
                )
            }

            when (val uState = updateState) {
                is UpdateCheckState.Available -> {
                    val rel = uState.release
                    val channelLabel = if (rel.isTestChannel) "Kênh TEST (${rel.repo})" else "Kênh CHÍNH (${rel.repo})"
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissUpdateDialog() },
                        title = { Text("Cập nhật mới ${rel.tagName}") },
                        text = {
                            Text(
                                buildString {
                                    append("$channelLabel\n")
                                    append("Hiện tại: v${viewModel.appVersionName} → Mới: v${rel.versionName}\n")
                                    if (rel.releaseNotes.isNotBlank()) {
                                        append("\n${rel.releaseNotes}")
                                    }
                                }
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { viewModel.startUpdateDownload(rel) }) {
                                Text("Tải & Cài ngay")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { viewModel.dismissUpdateDialog() }) {
                                Text("Để sau")
                            }
                        }
                    )
                }
                is UpdateCheckState.Downloading -> {
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text("Đang tải bản cập nhật...") },
                        text = { Text("Đang tải ${uState.release.apkFileName}: ${uState.progressPercent}%") },
                        confirmButton = {}
                    )
                }
                is UpdateCheckState.Error -> {
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissUpdateDialog() },
                        title = { Text("Kiểm tra cập nhật") },
                        text = { Text(uState.message) },
                        confirmButton = {
                            TextButton(onClick = { viewModel.dismissUpdateDialog() }) {
                                Text("Đóng")
                            }
                        }
                    )
                }
                else -> Unit
            }
        }
    }
}
