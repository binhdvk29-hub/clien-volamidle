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
import androidx.compose.material3.Scaffold
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
import com.example.sleep.SleepWakeController
import com.example.ui.MainViewModel
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
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && viewModel.isSleepMode.value) {
            applySleepBrightness(!viewModel.isSleepWakeSetupDialogOpen.value)
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
    onSetWindowBrightnessLow: (Boolean) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    val isMasterAutoRunning by viewModel.isMasterAutoRunning.collectAsState()
    val characterStatsList by viewModel.characterStatsList.collectAsState()
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
    val autoConfigs by viewModel.autoConfigs.collectAsState()
    val autoDialogTabIndex by viewModel.autoDialogTabIndex.collectAsState()
    val tabCount by viewModel.tabCount.collectAsState()
    val tabCountWarnShown by viewModel.tabCountWarnShown.collectAsState()

    val isControlSheetOpen by viewModel.isControlSheetOpen.collectAsState()
    val controlSheetTabIndex by viewModel.controlSheetTabIndex.collectAsState()
    val sheetTabStats = characterStatsList.getOrElse(controlSheetTabIndex) {
        com.example.model.CharacterStats(tabIndex = controlSheetTabIndex)
    }

    val context = LocalContext.current

    LaunchedEffect(isSleepMode, isSleepWakeSetupDialogOpen) {
        onSetWindowBrightnessLow(isSleepMode && !isSleepWakeSetupDialogOpen)
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
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                        )
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
                                onSyncSellExcess = { viewModel.syncSellExcessItems() },
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
                                onSyncSellExcess = { viewModel.syncSellExcessItems() },
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
                onToggleHideGame = { viewModel.syncHideGame(!isHideGameSynced) }
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
                    onSave = { tIdx, updated -> viewModel.saveAutoConfig(tIdx, updated) },
                    onSaveAll = { updated -> viewModel.saveAllAutoConfig(updated) },
                    onResetCounters = { tIdx -> viewModel.resetAutoDailyCounters(tIdx) },
                    onDismiss = { viewModel.dismissAutoConfigDialog() }
                )
            }
        }
    }
}
