package com.example

import com.example.webview.AutoConfig
import com.example.webview.DeviceProfiler
import com.example.webview.GameAutoPilot
import com.example.webview.GameScriptInjector
import com.example.webview.VnResetSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class AutoPilotAndOptTest {

    private fun vnEpochMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT+07:00"))
        cal.set(Calendar.MILLISECOND, 0)
        cal.set(year, month - 1, day, hour, minute, second)
        return cal.timeInMillis
    }

    @Test
    fun vnResetSchedule_switchesDayExactlyAt000001Gmt7() {
        val beforeReset235959 = vnEpochMillis(2026, 5, 10, 23, 59, 59)
        val atMidnight000000 = vnEpochMillis(2026, 5, 11, 0, 0, 0)
        val atReset000001 = vnEpochMillis(2026, 5, 11, 0, 0, 1)
        val afterReset000002 = vnEpochMillis(2026, 5, 11, 0, 0, 2)

        assertEquals(
            VnResetSchedule.vnGameDayId(beforeReset235959),
            VnResetSchedule.vnGameDayId(atMidnight000000)
        )
        assertNotEquals(
            VnResetSchedule.vnGameDayId(atMidnight000000),
            VnResetSchedule.vnGameDayId(atReset000001)
        )
        assertEquals(
            VnResetSchedule.vnGameDayId(atReset000001),
            VnResetSchedule.vnGameDayId(afterReset000002)
        )
        assertEquals(1_000L, VnResetSchedule.millisUntilNextReset(atMidnight000000))
        assertEquals(86_400_000L, VnResetSchedule.millisUntilNextReset(atReset000001))

        assertNotEquals(
            VnResetSchedule.vnCalendarDayId(beforeReset235959),
            VnResetSchedule.vnCalendarDayId(atMidnight000000)
        )
        assertEquals(
            VnResetSchedule.vnCalendarDayId(atMidnight000000),
            VnResetSchedule.vnCalendarDayId(atReset000001)
        )
        assertEquals(1_000L, VnResetSchedule.millisUntilMidnight000000(beforeReset235959))
        assertEquals(86_400_000L, VnResetSchedule.millisUntilMidnight000000(atMidnight000000))
    }

    @Test
    fun deviceProfiler_detectsLdPlayerAndPhysicalDevicesAccurately() {
        assertTrue(
            DeviceProfiler.detectEmulator(
                fingerprint = "LDPlayer/aosp/ldplayer:9",
                model = "SM-G977N",
                manufacturer = "samsung",
                brand = "samsung",
                device = "ldplayer",
                product = "ldplayer",
                hardware = "ttvm",
                abis = listOf("x86_64", "x86", "arm64-v8a")
            )
        )
        assertTrue(
            DeviceProfiler.detectEmulator(
                fingerprint = "generic/vbox86p",
                model = "Asus",
                manufacturer = "Asus",
                brand = "generic",
                device = "generic",
                product = "vbox86p",
                hardware = "vbox86",
                abis = listOf("x86")
            )
        )
        assertFalse(
            DeviceProfiler.detectEmulator(
                fingerprint = "RedMagic/NX729J/NX729J:14",
                model = "NX729J",
                manufacturer = "nubia",
                brand = "nubia",
                device = "NX729J",
                product = "NX729J",
                hardware = "qcom",
                abis = listOf("arm64-v8a", "armeabi-v7a")
            )
        )
    }

    @Test
    fun blackScreenScript_targetsTuyChonPathAndPreservesInvariants() {
        val script = GameScriptInjector.buildSyncBlackScreenScript(true)
        assertTrue(script.contains("findTuyChonTab"))
        assertTrue(script.contains("tuy chon"))
        assertTrue(script.contains("findHeThongButton"))
        assertTrue(script.contains("findHienThiTab"))
        assertTrue(script.contains("findBlackScreenCheckbox"))
        assertTrue(script.contains("findBlackScreenButton"))
        assertTrue(script.contains("applyBlackScreenControl"))
        assertTrue(script.contains("man hinh den"))
        assertTrue(script.contains("closeModalIfOpenedByUs"))
        assertTrue(script.endsWith("(true);"))
    }

    @Test
    fun fpsLimiterScript_appliesEmulatorGuardAndSleepModeWithoutAudioContext() {
        val emuScript = GameScriptInjector.buildFpsLimiterScript(30, isEmulator = true)
        assertTrue(emuScript.contains("window.__isEmulatorEnv = true"))
        assertTrue(emuScript.contains("__isSleepMode"))
        assertFalse(emuScript.contains("AudioContext"))
        assertFalse(emuScript.contains("initSilentKeepAliveAudio"))
    }

    @Test
    fun autoScript_executesTradeCardFirstAt000000GatesKnbAndFixesConsumables() {
        val cfgJson = """{"on":true,"theGiaoDich":true,"dt":true,"pb":true,"qua":true,"qht":true,"qhtMua":true,"knbMua":1000,"doiNguyenLieu":["Đại Hồng Bao","Hồng Bao"]}"""
        val pilot = GameAutoPilot(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            tabCount = { 2 },
            initialConfigJsonForTab = { null },
            webViewAt = { null },
            isTabActive = { false },
            isSystemRunning = { true }
        )
        assertFalse(pilot.isEnabled(0))
        assertFalse(pilot.isEnabled(1))
        assertFalse(pilot.isAnyEnabled())
        assertFalse(AutoConfig().theGiaoDich)
        assertTrue(AutoConfig(theGiaoDich = true).theGiaoDich)
        assertFalse(AutoConfig().qht)
        assertFalse(AutoConfig().qhtMua)
        assertTrue(AutoConfig(qht = true, qhtMua = true).qhtMua)
        assertFalse(AutoConfig().thapNeDon)
        assertEquals(AutoConfig.ORBIT_AUTO, AutoConfig().thapNeDonStyle)
        assertEquals(AutoConfig.ORBIT_MELEE, AutoConfig.normalizeOrbitStyle("melee"))
        assertEquals(AutoConfig.ORBIT_RANGED, AutoConfig.normalizeOrbitStyle(" RANGED "))
        assertEquals(AutoConfig.ORBIT_AUTO, AutoConfig.normalizeOrbitStyle("unknown"))

        val script = pilot.buildAutoScript(
            tabIndex = 1,
            cfgJson = cfgJson,
            touchMs = System.currentTimeMillis(),
            immediate = true
        )
        assertTrue(script.contains("var TAB = 1;"))
        assertTrue(script.contains("A.immediate = true"))
        assertTrue(script.contains("var fns = [muaTheGiaoDich, qua, doiNguyenLieuThua, muaKnb"))
        assertTrue(script.contains("onMidnight000000"))
        assertTrue(script.contains("vnTradeDay"))
        assertTrue(script.contains("findTheGiaoDichBuyButton"))
        assertTrue(script.contains("'giao dich'"))
        assertTrue(script.contains("'the giao dich'"))
        assertTrue(script.contains("A.cfg.theGiaoDich && dayGet('theGd') < 1"))
        assertTrue(script.contains("suDungVatPhamTieuHao"))
        assertTrue(script.contains("canRunHiddenConsumable"))
        assertTrue(script.contains("isBagSlotOrContentRendered"))
        assertTrue(script.contains("BUFF_SCAN_MS = 15 * 60000"))
        assertTrue(script.contains("buff_scan_15m"))
        assertTrue(script.contains("scanHudBuffs"))
        assertTrue(script.contains("classifyBuffKind"))
        assertTrue(script.contains("scanAndUseOneFromHiddenBagDom"))
        assertTrue(script.contains("bagConfirmedEmpty"))
        assertTrue(script.contains("moBangKnbKhoa"))
        assertTrue(script.contains("clickKtcSubTab"))
        assertTrue(script.contains("findKtcBuyQtyInput"))
        assertTrue(script.contains("findKtcBuyConfirmButton"))
        assertTrue(script.contains("useOneBottleFromHiddenBagDom"))
        assertTrue(script.contains("buyOneBottleFromHiddenKtcDom"))
        assertTrue(script.contains("queHoaTuu"))
        assertTrue(script.contains("A.cfg.qhtMua"))
        assertTrue(script.contains("n: 1"))
        assertTrue(script.contains("dayAdd('qht', 1)"))
        assertTrue(script.contains("findKnbQtyInput"))
        assertTrue(script.contains("findKnbConfirmButton"))
        assertTrue(script.contains("moBangKnb"))
        assertTrue(script.contains("switchMode"))
        assertTrue(script.contains("prevKey"))
        assertTrue(script.contains("var VER = 'vlmauto-10';"))
        assertEquals("vlmauto-10", GameAutoPilot.SCRIPT_VER)
        assertTrue(script.contains("orbitTick"))
        assertTrue(script.contains("isInTowerNow"))
        assertTrue(script.contains("isOrbitEligible"))
        assertTrue(script.contains("computeOrbitParams"))
        assertTrue(script.contains("resolveOrbitStyle"))
        assertTrue(script.contains("installArenaCanvasSpy"))
        assertTrue(script.contains("resolveArenaPosition"))
        assertTrue(script.contains("computeCenterAnchoredVector"))
        assertTrue(script.contains("finishOrbitMovePulse"))
        assertTrue(script.contains("ensureGameAutoOn"))
        assertTrue(script.contains("findGameAutoButton"))
        assertTrue(script.contains("detectGameAutoState"))
        assertTrue(script.contains("rTarget"))
        assertTrue(script.contains("rMax"))
        assertTrue(script.contains("thapMainSkill"))
        assertFalse(script.contains("detectMainSkillMeta"))
        assertTrue(script.contains("pauseOrbitMovement"))
        assertTrue(script.contains("ORBIT_MELEE_SKILLS"))
        assertTrue(script.contains("ORBIT_RANGED_SKILLS"))
        assertTrue(script.contains("dat ma do giang"))
        assertTrue(script.contains("vo tuong tram"))
        assertTrue(script.contains("nhan kiem hop nhat"))
        assertTrue(script.contains("phi long tai thien"))
        assertTrue(script.contains("thien ha vo cau"))
        assertTrue(script.contains("truy tinh truc nguyet"))
        assertTrue(script.contains("releaseOrbitKeys"))
        assertTrue(script.contains("ORBIT_KEY_GROUPS"))
        assertTrue(script.contains("ArrowUp"))
        assertTrue(script.contains("ArrowDown"))
        assertTrue(script.contains("ArrowLeft"))
        assertTrue(script.contains("ArrowRight"))
        assertFalse(script.contains("KeyW"))
        assertFalse(script.contains("KeyA"))
        assertFalse(script.contains("KeyS"))
        assertFalse(script.contains("KeyD"))
        assertTrue(script.contains("pulseJoystickOrbit"))
        assertFalse(script.contains("pulseCanvasOrJoystickOrbit"))
        assertFalse(script.contains("banThua"))
        assertFalse(script.contains("ban thua"))
    }

    @Test
    fun towerMainSkill_filtersBySectAndMatchesStrictlyBySkillNameNotWeapon() {
        assertEquals(23, AutoConfig.ALL_9X_SKILLS.size)
        assertTrue(AutoConfig.ALL_9X_SKILLS.none { it.displayName.contains("(") })

        // Tuyệt đối không xác định theo tên vũ khí
        assertEquals(null, AutoConfig.findMainSkill("Đao"))
        assertEquals(null, AutoConfig.findMainSkill("Kiếm"))
        assertEquals(null, AutoConfig.findMainSkill("Quyền"))
        assertEquals(null, AutoConfig.findMainSkill("Thương"))
        assertEquals("dat ma do giang", AutoConfig.findMainSkill("Đạt Ma Độ Giang (Quyền)")?.id)

        val thieuLam = AutoConfig.skills9xForSect("Thiếu Lâm")
        assertEquals(3, thieuLam.size)
        assertEquals(AutoConfig.ORBIT_MELEE, AutoConfig.findMainSkill("Đạt Ma Độ Giang")?.combatStyle)
        assertEquals(AutoConfig.ORBIT_MELEE, AutoConfig.findMainSkill("Hoành Tảo Thiên Quân")?.combatStyle)
        assertEquals(AutoConfig.ORBIT_RANGED, AutoConfig.findMainSkill("Vô Tướng Trảm")?.combatStyle)

        val thienVuong = AutoConfig.skills9xForSect("Thiên Vương")
        assertEquals(3, thienVuong.size)
        assertTrue(thienVuong.all { it.combatStyle == AutoConfig.ORBIT_MELEE })

        val thuyYen = AutoConfig.skills9xForSect("Thúy Yên")
        assertEquals(2, thuyYen.size)
        assertTrue(thuyYen.all { it.combatStyle == AutoConfig.ORBIT_RANGED })

        val caiBang = AutoConfig.skills9xForSect("Cái Bang")
        assertEquals(2, caiBang.size)
        assertTrue(caiBang.all { it.combatStyle == AutoConfig.ORBIT_RANGED })

        val voDang = AutoConfig.skills9xForSect("Võ Đang")
        assertEquals(2, voDang.size)
        assertEquals(AutoConfig.ORBIT_RANGED, AutoConfig.findMainSkill("Thiên Địa Vô Cực")?.combatStyle)
        assertEquals(AutoConfig.ORBIT_MELEE, AutoConfig.findMainSkill("Nhân Kiếm Hợp Nhất")?.combatStyle)

        assertTrue(AutoConfig.skills9xForSect("Chưa rõ").isEmpty())

        // Khi chưa chọn chiêu 9x ở chế độ Theo chiêu, app không tự chọn cách di chuyển
        assertEquals(null, AutoConfig(thapNeDon = true, thapMainSkill = "").resolvedOrbitCombatStyle())
        assertEquals(
            AutoConfig.ORBIT_RANGED,
            AutoConfig(thapNeDon = true, thapMainSkill = "vo tuong tram").resolvedOrbitCombatStyle()
        )
        assertEquals(
            AutoConfig.ORBIT_MELEE,
            AutoConfig(thapNeDon = true, thapMainSkill = "dat ma do giang").resolvedOrbitCombatStyle()
        )
        assertEquals(
            AutoConfig.ORBIT_MELEE,
            AutoConfig(thapNeDon = true, thapMainSkill = "nhan kiem hop nhat").resolvedOrbitCombatStyle()
        )
    }

    @Test
    fun scannerScript_tracksQueHoaTuuAndTheGiaoDichCounters() {
        val scan = GameScriptInjector.buildPeriodicWatchdogAndScanScript(0)
        assertTrue(scan.contains("getStat('theGd')"))
        assertTrue(scan.contains("Thẻ GD"))
        assertTrue(scan.contains("getStat('qht')"))
        assertTrue(scan.contains("Quế Hoa Tửu("))
        assertTrue(scan.contains("__vlm-stealth"))
    }

    @Test
    fun autoScript_usesStealthHiddenModalForBackgroundActions() {
        val cfgJson = """{"on":true,"dt":true,"pb":true,"qua":true,"pd":true,"knbMua":1000,"doiNguyenLieu":["Đại Hồng Bao"]}"""
        val pilot = GameAutoPilot(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            tabCount = { 1 },
            initialConfigJsonForTab = { null },
            webViewAt = { null },
            isTabActive = { false },
            isSystemRunning = { true }
        )
        val script = pilot.buildAutoScript(0, cfgJson, 0L, immediate = true)
        assertTrue(script.contains("__vlm-stealth"))
        assertTrue(script.contains("__vlmStealth"))
        assertTrue(script.contains("closeModalCleanly"))
        assertTrue(script.contains("opacity: 0.001 !important"))
        assertTrue(script.contains("left: -99999px !important"))
        assertTrue(script.contains("pointer-events: none !important"))
    }

    @Test
    fun sellExcessScript_coversHiddenBagFlow() {
        val excessScript = GameScriptInjector.buildSellExcessItemsScript(2, "tok_ex_2")
        assertTrue(excessScript.contains("const tabIdx = 2;"))
        assertTrue(excessScript.contains("ban thua"))
        assertTrue(excessScript.contains("openBagModalHidden"))
        assertTrue(excessScript.contains("openBagContainerHidden"))
        assertTrue(excessScript.contains("findVisibleBagTabPane"))
        assertTrue(excessScript.contains("parseFloat(st.opacity) === 0"))
        assertTrue(excessScript.contains("findBagButtonWithOverflow"))
        assertTrue(excessScript.contains("u20more"))
        assertTrue(excessScript.contains("findSellConfirmBtn(excessBtn, preBtns)"))
        assertTrue(excessScript.contains("onExcessSellResult"))
        assertTrue(excessScript.contains("xac dinh"))
        assertTrue(excessScript.contains("window.confirm = function() { return true; }"))
        assertTrue(excessScript.contains("__vlm-stealth"))
        assertTrue(excessScript.contains("#cfModal"))
        assertTrue(excessScript.contains("#confirmModal"))
        assertTrue(excessScript.contains(".modal-backdrop"))
        assertTrue(excessScript.contains("#u20pop"))
        assertTrue(excessScript.contains("#t-inv"))
        assertTrue(excessScript.contains("#t-bag"))
        assertTrue(excessScript.contains("#p-inv"))
        assertTrue(excessScript.contains("left: -99999px !important"))
        assertTrue(excessScript.contains("opacity: 0.001 !important"))
    }

    @Test
    fun loginScriptAndDeviceProfiler_enforceSlowNetworkSafePacing() {
        val loginScript = GameScriptInjector.buildAutoLoginStepScript(
            tabIndex = 0,
            username = "testuser",
            password = "secret"
        )
        assertTrue(loginScript.contains("isBtnBusyOrDisabled"))
        assertTrue(loginScript.contains("timeSinceLastSubmit < 4500"))
        assertTrue(loginScript.contains("__lastCharGoTime || 0) < 4500"))
        assertTrue(loginScript.contains("__lastStartBtnTime || 0) < 3000"))
        assertTrue(loginScript.contains("__lastDialogActionTime || 0) < 2500"))
        assertTrue(DeviceProfiler.tabStaggerMs() >= 1350L)
        assertTrue(DeviceProfiler.loginStepStaggerMs() >= 650L)
        assertTrue(DeviceProfiler.loginRoundIntervalMs() >= 2200L)
    }

    @Test
    fun renderOptimizationAndThermalScripts_configureMapHidingAndBackgroundSkip() {
        val enabledScript = GameScriptInjector.buildRenderOptimizationScript(
            hideMap = true
        )
        assertTrue(enabledScript.contains("__vlmHideMap = !!hideMap"))
        assertFalse(enabledScript.contains("__vlmHideMonsters"))
        assertTrue(enabledScript.contains("__vlm-hide-map"))
        assertFalse(enabledScript.contains("__vlm-hide-monsters"))
        assertTrue(enabledScript.contains("ensureClearedForFrame"))
        assertTrue(enabledScript.contains("shouldSkipAllRender"))
        assertTrue(enabledScript.contains("WebGLRenderingContext"))
        assertTrue(enabledScript.endsWith("(true);"))

        val disabledScript = GameScriptInjector.buildRenderOptimizationScript(
            hideMap = false
        )
        assertTrue(disabledScript.endsWith("(false);"))

        val fpsScript = GameScriptInjector.buildFpsLimiterScript(30, isEmulator = false)
        assertTrue(fpsScript.contains("window.__isSleepMode ? 340 : 240"))
        assertTrue(fpsScript.contains("__vlmFrameSeq"))

        val gcScript = GameScriptInjector.buildDomGarbageCollectionScript()
        assertTrue(gcScript.contains("items.length > 12"))
        assertTrue(gcScript.contains("const maxFloating = 4;"))
    }
}
