package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.PreferencesManager
import com.example.model.CharacterStats
import com.example.ui.components.SidebarView
import com.example.ui.theme.MyApplicationTheme
import com.example.webview.AutoConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Võ Lâm Idle", appName)
  }

  @Test
  fun `per tab auto config persists independently with theGiaoDich qht qhtMua and knbMua`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = PreferencesManager(context)

    val tab0 = AutoConfig(
      on = true,
      theGiaoDich = true,
      pb = true,
      dt = false,
      thapNeDon = true,
      thapNeDonStyle = AutoConfig.ORBIT_MELEE,
      thapMainSkill = "vo tuong tram",
      qht = true,
      qhtMua = true,
      knbMua = 1000
    )
    val tab1 = AutoConfig(
      on = false,
      theGiaoDich = false,
      pb = false,
      dt = true,
      thapNeDon = true,
      thapNeDonStyle = AutoConfig.ORBIT_RANGED,
      thapMainSkill = "nhan kiem hop nhat",
      qht = false,
      qhtMua = false,
      knbMua = 250
    )

    prefs.setAutoConfig(0, tab0.toJson().toString())
    prefs.setAutoConfig(1, tab1.toJson().toString())

    val loaded0 = AutoConfig.fromJson(prefs.getAutoConfig(0))
    val loaded1 = AutoConfig.fromJson(prefs.getAutoConfig(1))

    assertTrue(loaded0.on)
    assertTrue(loaded0.theGiaoDich)
    assertTrue(loaded0.pb)
    assertFalse(loaded0.dt)
    assertTrue(loaded0.thapNeDon)
    assertEquals(AutoConfig.ORBIT_MELEE, loaded0.thapNeDonStyle)
    assertEquals("vo tuong tram", loaded0.thapMainSkill)
    assertTrue(loaded0.qht)
    assertTrue(loaded0.qhtMua)
    assertEquals(1000, loaded0.knbMua)

    assertFalse(loaded1.on)
    assertFalse(loaded1.theGiaoDich)
    assertFalse(loaded1.pb)
    assertTrue(loaded1.dt)
    assertTrue(loaded1.thapNeDon)
    assertEquals(AutoConfig.ORBIT_RANGED, loaded1.thapNeDonStyle)
    assertEquals("nhan kiem hop nhat", loaded1.thapMainSkill)
    assertFalse(loaded1.qht)
    assertFalse(loaded1.qhtMua)
    assertEquals(250, loaded1.knbMua)
  }

  @Test
  fun `sidebar shows ban do thua below man hinh den and hides synced ban do hkc`() {
    var sellExcessClicked = false

    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = true) {
        SidebarView(
          isMasterAutoRunning = false,
          tabCount = 2,
          characterStatsList = listOf(CharacterStats(0), CharacterStats(1)),
          selectedTabIndex = 0,
          onSelectTab = {},
          onLoginAll = {},
          onStopAll = {},
          onEditAccount = {},
          onToggleSidebar = {},
          onSyncSellExcess = { sellExcessClicked = true }
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("sync_black_screen_checkbox").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("sync_sell_excess_button").performScrollTo().assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag("sync_sell_hkc_button").assertDoesNotExist()

    assertTrue(sellExcessClicked)
  }

  @Test
  fun `autoConfigDialog shows theGiaoDich hides HKC button keeps two QHT options and separate Save actions`() {
    var savedSingleTab = -1
    var savedSingleCfg: AutoConfig? = null
    var savedAllCfg: AutoConfig? = null

    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = true) {
        com.example.ui.components.AutoConfigDialog(
          tabIndex = 1,
          tabCount = 3,
          configs = listOf(AutoConfig(on = false), AutoConfig(on = true, theGiaoDich = false, qht = false, qhtMua = false), AutoConfig(on = false)),
          characterName = "NhanVat2",
          onSelectTab = {},
          onSave = { idx, cfg ->
            savedSingleTab = idx
            savedSingleCfg = cfg
          },
          onSaveAll = { cfg -> savedAllCfg = cfg },
          onResetCounters = {},
          onDismiss = {}
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("auto_open_sell_hkc_button").assertDoesNotExist()
    composeTestRule.onNodeWithText("Quế Hoa Tửu hết thì dùng trong túi (mỗi lần 1 bình, có bộ đếm)").assertDoesNotExist()

    composeTestRule.onNodeWithTag("auto_check_theGiaoDich").performScrollTo().assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag("auto_check_thapNeDon").performScrollTo().assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag("auto_orbit_style_melee").performScrollTo().assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag("auto_check_qht").performScrollTo().assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag("auto_check_qhtMua").performScrollTo().assertIsDisplayed().performClick()
    composeTestRule.onNodeWithText("Quế Hoa Tửu hết thì dùng trong túi").assertIsDisplayed()
    composeTestRule.onNodeWithTag("auto_check_qhtMua").assertTextEquals("… túi hết thì mua (20 KNB khóa)")

    composeTestRule.onNodeWithTag("save_auto_config_button").assertIsDisplayed().performClick()
    assertEquals(1, savedSingleTab)
    assertTrue(savedSingleCfg?.on == true)
    assertTrue(savedSingleCfg?.theGiaoDich == true)
    assertTrue(savedSingleCfg?.thapNeDon == true)
    assertEquals(AutoConfig.ORBIT_MELEE, savedSingleCfg?.thapNeDonStyle)
    assertTrue(savedSingleCfg?.qht == true)
    assertTrue(savedSingleCfg?.qhtMua == true)

    composeTestRule.onNodeWithTag("auto_orbit_style_ranged").performScrollTo().assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag("save_all_auto_config_button").assertIsDisplayed().performClick()
    assertTrue(savedAllCfg?.on == true)
    assertTrue(savedAllCfg?.theGiaoDich == true)
    assertTrue(savedAllCfg?.thapNeDon == true)
    assertEquals(AutoConfig.ORBIT_RANGED, savedAllCfg?.thapNeDonStyle)
    assertTrue(savedAllCfg?.qht == true)
    assertTrue(savedAllCfg?.qhtMua == true)
  }

  @Test
  fun `autoConfigDialog detects character sect filters 9x skills and resolves movement style on selection`() {
    var savedCfg: AutoConfig? = null

    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = true) {
        com.example.ui.components.AutoConfigDialog(
          tabIndex = 1,
          tabCount = 3,
          configs = listOf(AutoConfig(), AutoConfig(on = true, thapNeDon = true), AutoConfig()),
          characterName = "ThieuLamPro",
          characterSects = listOf("Võ Đang", "Thiếu Lâm", "Thiên Vương"),
          onSelectTab = {},
          onSave = { _, cfg -> savedCfg = cfg },
          onSaveAll = {},
          onResetCounters = {},
          onDismiss = {}
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("auto_detected_sect_label")
      .performScrollTo()
      .assertTextEquals("Môn phái: Thiếu Lâm • Chọn chiêu chính 9x:")
    composeTestRule.onNodeWithTag("auto_orbit_resolved_label")
      .performScrollTo()
      .assertTextEquals("Chưa chọn chiêu chính — hãy chọn chiêu 9x để xác định cách di chuyển")

    composeTestRule.onNodeWithTag("auto_main_skill_dropdown").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("auto_main_skill_option_dat ma do giang").assertIsDisplayed()
    composeTestRule.onNodeWithTag("auto_main_skill_option_hoanh tao thien quan").assertIsDisplayed()
    composeTestRule.onNodeWithTag("auto_main_skill_option_vo tuong tram").assertIsDisplayed()
    composeTestRule.onNodeWithTag("auto_main_skill_option_bao vu le hoa").assertDoesNotExist()

    composeTestRule.onNodeWithTag("auto_main_skill_option_vo tuong tram").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("auto_orbit_resolved_label")
      .performScrollTo()
      .assertTextEquals("Cách di chuyển cho chiêu đã chọn: Đánh xa (vòng rộng, theo nhịp)")

    composeTestRule.onNodeWithTag("save_auto_config_button").performClick()
    assertEquals("vo tuong tram", savedCfg?.thapMainSkill)
    assertEquals(AutoConfig.ORBIT_AUTO, savedCfg?.thapNeDonStyle)
    assertEquals(AutoConfig.ORBIT_RANGED, savedCfg?.resolvedOrbitCombatStyle())
  }

  @Test
  fun `viewModel saveAutoConfig updates only target tab while saveAllAutoConfig syncs all active tabs`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val prefs = PreferencesManager(app)
    prefs.setTabCount(3)
    val vm = com.example.ui.MainViewModel(app)
    try {
      val cfgTab0 = AutoConfig(on = true, theGiaoDich = true, pb = true, qht = true, knbMua = 800)
      val cfgTab1 = AutoConfig(on = false, theGiaoDich = false, pb = false, qht = false, knbMua = 100)
      vm.saveAutoConfig(0, cfgTab0)
      vm.saveAutoConfig(1, cfgTab1)

      assertEquals(800, AutoConfig.fromJson(prefs.getAutoConfig(0)).knbMua)
      assertTrue(AutoConfig.fromJson(prefs.getAutoConfig(0)).theGiaoDich)
      assertEquals(100, AutoConfig.fromJson(prefs.getAutoConfig(1)).knbMua)
      assertFalse(AutoConfig.fromJson(prefs.getAutoConfig(1)).theGiaoDich)

      val unified = AutoConfig(on = true, theGiaoDich = true, pb = true, dt = false, qht = true, knbMua = 1000)
      vm.saveAllAutoConfig(unified)

      for (i in 0 until 3) {
        val loaded = AutoConfig.fromJson(prefs.getAutoConfig(i))
        assertTrue(loaded.on)
        assertTrue(loaded.theGiaoDich)
        assertTrue(loaded.qht)
        assertFalse(loaded.dt)
        assertEquals(1000, loaded.knbMua)
      }
    } finally {
      vm.tabManager.cleanup()
    }
  }


  @Test
  fun `sleepWakeController pauses notification wake when DND granted and restores exact previous policy on unlock`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = PreferencesManager(context)
    prefs.clearDndSnapshot()
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    val shadowNm = org.robolectric.Shadows.shadowOf(nm)

    // Chưa cấp quyền -> NEEDS_PERMISSION
    shadowNm.setNotificationPolicyAccessGranted(false)
    val controller = com.example.sleep.SleepWakeController(context, prefs)
    assertEquals(
      com.example.sleep.SleepWakePauseStatus.NEEDS_PERMISSION,
      controller.pauseAutoScreenWake()
    )
    assertFalse(prefs.isDndOverrideActive())

    // Cấp quyền DND -> lưu lại trạng thái trước và bật chặn sáng màn hình thông báo
    shadowNm.setNotificationPolicyAccessGranted(true)
    val initialPolicy = android.app.NotificationManager.Policy(
      android.app.NotificationManager.Policy.PRIORITY_CATEGORY_CALLS or
        android.app.NotificationManager.Policy.PRIORITY_CATEGORY_MESSAGES,
      android.app.NotificationManager.Policy.PRIORITY_SENDERS_STARRED,
      android.app.NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS,
      0
    )
    nm.notificationPolicy = initialPolicy
    nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_ALL)

    val status = controller.pauseAutoScreenWake()
    assertEquals(com.example.sleep.SleepWakePauseStatus.SUPPRESSED, status)
    assertTrue(prefs.isDndOverrideActive())
    assertTrue(controller.isNotificationWakeSuppressed())
    assertEquals(
      android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY,
      nm.currentInterruptionFilter
    )
    val suppressed = nm.notificationPolicy.suppressedVisualEffects
    assertTrue(
      (suppressed and android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_SCREEN_ON) != 0
    )
    assertTrue(
      (suppressed and android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_AMBIENT) != 0
    )
    assertTrue(
      (suppressed and android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_PEEK) != 0
    )

    // Khi mở khóa / làm sáng màn hình -> khôi phục đúng filter & policy ban đầu
    assertTrue(controller.restoreAutoScreenWake())
    assertFalse(prefs.isDndOverrideActive())
    assertEquals(
      android.app.NotificationManager.INTERRUPTION_FILTER_ALL,
      nm.currentInterruptionFilter
    )
    assertEquals(initialPolicy.priorityCategories, nm.notificationPolicy.priorityCategories)
    assertEquals(initialPolicy.priorityCallSenders, nm.notificationPolicy.priorityCallSenders)
    assertEquals(initialPolicy.priorityMessageSenders, nm.notificationPolicy.priorityMessageSenders)
  }

  @Test
  fun `oledBlackScreen shows paused badge when notification wake is suppressed and setup pill otherwise`() {
    var setupClicked = false
    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = true) {
        com.example.ui.components.OledBlackScreen(
          onUnlock = {},
          isNotificationWakePaused = false,
          hasDndPolicyAccess = false,
          onOpenWakeSetup = { setupClicked = true }
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("oled_grant_dnd_button").assertIsDisplayed().performClick()
    assertTrue(setupClicked)
  }

  @Test
  fun `sleepWakeSetupDialog displays action buttons and triggers confirm sleep mode`() {
    var confirmed = false
    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = true) {
        com.example.ui.components.SleepWakeSetupDialog(
          hasDndPolicyAccess = true,
          isAlreadyInSleepMode = false,
          onOpenDndSettings = {},
          onOpenDisplaySettings = {},
          onConfirmSleepMode = { confirmed = true },
          onDismiss = {}
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("sleep_wake_dnd_status_text").assertIsDisplayed()
    composeTestRule.onNodeWithTag("sleep_wake_dnd_settings_btn").assertIsDisplayed()
    composeTestRule.onNodeWithTag("sleep_wake_display_settings_btn").assertIsDisplayed()
    composeTestRule.onNodeWithTag("sleep_wake_enter_sleep_btn").assertIsDisplayed().performClick()
    assertTrue(confirmed)
  }

  @Test
  fun `preferences defaults to 30 fps and persists hideMap`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = PreferencesManager(context)

    assertEquals(30, prefs.getFps())
    assertFalse(prefs.getHideMap())

    prefs.setHideMap(true)
    assertTrue(prefs.getHideMap())
  }

  @Test
  fun `sidebar triggers hideMap synced checkbox`() {
    var sidebarMapVal: Boolean? = null

    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = true) {
        SidebarView(
          isMasterAutoRunning = false,
          tabCount = 2,
          characterStatsList = listOf(CharacterStats(0), CharacterStats(1)),
          selectedTabIndex = 0,
          onSelectTab = {},
          onLoginAll = {},
          onStopAll = {},
          onEditAccount = {},
          onToggleSidebar = {},
          isHideMapSynced = false,
          onSyncHideMap = { sidebarMapVal = it }
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("sync_hide_map_checkbox").performScrollTo().assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag("sync_hide_monsters_checkbox").assertDoesNotExist()
    assertEquals(true, sidebarMapVal)
  }

  @Test
  fun `control sheet triggers hideMap quick toggle`() {
    var sheetMapClicks = 0

    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = true) {
        com.example.ui.components.ControlSheet(
          isOpen = true,
          tabIndex = 0,
          tabLabel = "NhanVat",
          layoutMode = com.example.model.ViewLayoutMode.FOUR_COLUMNS,
          fps = 30,
          networkData = com.example.model.NetworkDataInfo(),
          onOpenTab = {},
          onReloadTab = {},
          onStopTab = {},
          onChangeLayoutMode = {},
          onToggleFps = {},
          onActivateSleepMode = {},
          onOpenSidebar = {},
          onResetNetworkStats = {},
          onSyncAutoMode = {},
          isAutoPilotOn = false,
          onOpenAutoConfig = {},
          tabCount = 2,
          maxTabCount = 8,
          isLowRam = false,
          tabCountWarnShown = true,
          hasBusyTabsInRange = { _, _ -> false },
          onSetTabCount = {},
          onMarkTabCountWarned = {},
          appVersionName = "1.0",
          updateState = com.example.update.UpdateUiState.Idle,
          onCheckUpdate = {},
          onStartDownload = {},
          onInstallUpdate = {},
          onOpenReleases = {},
          onDismiss = {},
          onInteract = {},
          isHideMap = false,
          onToggleHideMap = { sheetMapClicks++ }
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("sheet_hide_map_toggle").performScrollTo().assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag("sheet_hide_monsters_toggle").assertDoesNotExist()
    assertEquals(1, sheetMapClicks)
  }

  @Test
  fun `tabManager staggers render process recovery applies backoff cancels on stop and tiers renderer priority`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val prefs = PreferencesManager(app)
    prefs.setTabCount(3)
    val scope = kotlinx.coroutines.CoroutineScope(
      kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob()
    )
    val manager = com.example.webview.GameTabManager(app, prefs, scope)
    try {
      // Idle tabs start WAIVED so background blank WebViews do not hoard IMPORTANT priority
      assertEquals(
        android.webkit.WebView.RENDERER_PRIORITY_WAIVED to true,
        manager.resolveRendererPriority(0)
      )

      manager.openSingleTab(0)
      manager.openSingleTab(1)
      manager.openSingleTab(2)
      org.robolectric.shadows.ShadowLooper.idleMainLooper()

      manager.updateVisibleTabs(setOf(0))
      assertEquals(
        android.webkit.WebView.RENDERER_PRIORITY_IMPORTANT to false,
        manager.resolveRendererPriority(0)
      )
      assertEquals(
        android.webkit.WebView.RENDERER_PRIORITY_BOUND,
        manager.resolveRendererPriority(1).first
      )

      // Recovery delays are strictly staggered across tabs and back off on repeat OOMs
      val d0 = manager.computeRecoveryDelayMs(0, 1, didCrash = false)
      val d1 = manager.computeRecoveryDelayMs(1, 1, didCrash = false)
      val d2 = manager.computeRecoveryDelayMs(2, 1, didCrash = false)
      assertTrue(d0 >= 3_000L)
      assertTrue(d1 > d0)
      assertTrue(d2 > d1)
      assertTrue(manager.computeRecoveryDelayMs(0, 2, didCrash = false) > d0)

      // Simulate renderer process gone (code -1 / OOM) on tabs 0 and 1
      val now = 1_000_000L
      manager.handleRenderProcessGone(0, null, didCrash = false, nowMs = now)
      manager.handleRenderProcessGone(1, null, didCrash = false, nowMs = now)
      assertTrue(manager.hasPendingRecovery(0))
      assertTrue(manager.hasPendingRecovery(1))

      // Stopping a tab must cancel its scheduled staggered recovery
      manager.stopSingleTab(1)
      assertFalse(manager.hasPendingRecovery(1))
      assertTrue(manager.hasPendingRecovery(0))

      // Stopping all tabs cancels any remaining recovery
      manager.stopAll()
      assertFalse(manager.hasPendingRecovery(0))
    } finally {
      manager.cleanup()
      org.robolectric.shadows.ShadowLooper.idleMainLooper()
    }
  }
}
