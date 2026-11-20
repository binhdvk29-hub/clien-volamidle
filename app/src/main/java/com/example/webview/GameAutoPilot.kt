package com.example.webview

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone

/**
 * Chuẩn thời gian làm mới hoạt động ngày mới của game theo giờ Việt Nam (GMT+7): 00:00:01,
 * và mốc ngày lịch 00:00:00 GMT+7 cho Mua Thẻ Giao Dịch ưu tiên đầu ngày.
 */
object VnResetSchedule {
    private const val VN_OFFSET_MS = 7L * 3600L * 1000L
    private const val RESET_OFFSET_MS = 1_000L // 00:00:01 GMT+7
    private const val ONE_DAY_MS = 86_400_000L

    fun vnCalendarDayId(epochMs: Long): String {
        val shifted = epochMs + VN_OFFSET_MS
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = shifted
        }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        return "$year-$month-$day"
    }

    fun millisUntilMidnight000000(epochMs: Long): Long {
        val shifted = epochMs + VN_OFFSET_MS
        val mod = ((shifted % ONE_DAY_MS) + ONE_DAY_MS) % ONE_DAY_MS
        val remaining = ONE_DAY_MS - mod
        return if (remaining <= 0L) ONE_DAY_MS else remaining
    }

    fun vnGameDayId(epochMs: Long): String {
        val shifted = epochMs + VN_OFFSET_MS - RESET_OFFSET_MS
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = shifted
        }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        return "$year-$month-$day"
    }

    fun millisUntilNextReset(epochMs: Long): Long {
        val shifted = epochMs + VN_OFFSET_MS - RESET_OFFSET_MS
        val mod = ((shifted % ONE_DAY_MS) + ONE_DAY_MS) % ONE_DAY_MS
        val remaining = ONE_DAY_MS - mod
        return if (remaining <= 0L) ONE_DAY_MS else remaining
    }
}

data class TowerMainSkill(
    val id: String,
    val sectKey: String,
    val sectLabel: String,
    val displayName: String,
    val combatStyle: String
)

data class AutoConfig(
    val on: Boolean = false,
    val theGiaoDich: Boolean = false,
    val dt: Boolean = true,
    val ldtMua: Boolean = true,
    val k2k: Int = 0,
    val pd: Boolean = true,
    val st: Boolean = true,
    val lbMua: Int = 0,
    val knbMua: Int = 0,
    val knbGiu: Int = 0,
    val qua: Boolean = true,
    val khan: Boolean = true,
    val ttl: Boolean = true,
    val ttlMua: Boolean = false,
    val tt2: Boolean = true,
    val tt2Mua: Boolean = false,
    val pb: Boolean = true,
    val thap: Boolean = false,
    val thapNeDon: Boolean = false,
    val thapNeDonStyle: String = ORBIT_AUTO,
    val thapMainSkill: String = "",
    val qht: Boolean = false,
    val qhtMua: Boolean = false,
    val doiNguyenLieu: Set<String> = emptySet()
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("on", on)
        o.put("theGiaoDich", theGiaoDich)
        o.put("dt", dt)
        o.put("ldtMua", ldtMua)
        o.put("k2k", k2k)
        o.put("pd", pd)
        o.put("st", st)
        o.put("lbMua", lbMua)
        o.put("knbMua", knbMua)
        o.put("knbGiu", knbGiu)
        o.put("qua", qua)
        o.put("khan", khan)
        o.put("ttl", ttl)
        o.put("ttlMua", ttlMua)
        o.put("tt2", tt2)
        o.put("tt2Mua", tt2Mua)
        o.put("pb", pb)
        o.put("thap", thap)
        o.put("thapNeDon", thapNeDon)
        o.put("thapNeDonStyle", normalizeOrbitStyle(thapNeDonStyle))
        o.put("thapMainSkill", normalizeMainSkillId(thapMainSkill))
        o.put("qht", qht)
        o.put("qhtMua", qhtMua)

        val arr = org.json.JSONArray()
        doiNguyenLieu.forEach { arr.put(it) }
        o.put("doiNguyenLieu", arr)

        return o
    }

    fun resolvedOrbitCombatStyle(): String? {
        return when (normalizeOrbitStyle(thapNeDonStyle)) {
            ORBIT_MELEE -> ORBIT_MELEE
            ORBIT_RANGED -> ORBIT_RANGED
            else -> findMainSkill(thapMainSkill)?.combatStyle
        }
    }

    companion object {
        const val K2K_MAX = 100_000
        const val LB_MUA_MAX = 100
        const val KNB_MUA_MAX = 1000
        const val KNB_GIU_MAX = 10_000_000

        const val ORBIT_AUTO = "AUTO"
        const val ORBIT_MELEE = "MELEE"
        const val ORBIT_RANGED = "RANGED"

        val ALL_9X_SKILLS: List<TowerMainSkill> = listOf(
            // Thiếu Lâm
            TowerMainSkill("dat ma do giang", "thieu lam", "Thiếu Lâm", "Đạt Ma Độ Giang", ORBIT_MELEE),
            TowerMainSkill("hoanh tao thien quan", "thieu lam", "Thiếu Lâm", "Hoành Tảo Thiên Quân", ORBIT_MELEE),
            TowerMainSkill("vo tuong tram", "thieu lam", "Thiếu Lâm", "Vô Tướng Trảm", ORBIT_RANGED),
            // Thiên Nhẫn
            TowerMainSkill("van long kich", "thien nhan", "Thiên Nhẫn", "Vân Long Kích", ORBIT_MELEE),
            TowerMainSkill("thien ngoai luu tinh", "thien nhan", "Thiên Nhẫn", "Thiên Ngoại Lưu Tinh", ORBIT_RANGED),
            // Côn Lôn
            TowerMainSkill("ngao tuyet tieu phong", "con lon", "Côn Lôn", "Ngạo Tuyết Tiêu Phong", ORBIT_RANGED),
            TowerMainSkill("loi dong cuu thien", "con lon", "Côn Lôn", "Lôi Động Cửu Thiên", ORBIT_RANGED),
            // Nga Mi
            TowerMainSkill("tam nga te tuyet", "nga mi", "Nga Mi", "Tam Nga Tề Tuyết", ORBIT_RANGED),
            TowerMainSkill("phong suong toai anh", "nga mi", "Nga Mi", "Phong Sương Toái Ảnh", ORBIT_RANGED),
            // Đường Môn
            TowerMainSkill("bao vu le hoa", "duong mon", "Đường Môn", "Bạo Vũ Lê Hoa", ORBIT_RANGED),
            TowerMainSkill("nhiep hon nguyet anh", "duong mon", "Đường Môn", "Nhiếp Hồn Nguyệt Ảnh", ORBIT_RANGED),
            TowerMainSkill("cuu cung phi tinh", "duong mon", "Đường Môn", "Cửu Cung Phi Tinh", ORBIT_RANGED),
            // Cái Bang
            TowerMainSkill("phi long tai thien", "cai bang", "Cái Bang", "Phi Long Tại Thiên", ORBIT_RANGED),
            TowerMainSkill("thien ha vo cau", "cai bang", "Cái Bang", "Thiên Hạ Vô Cẩu", ORBIT_RANGED),
            // Võ Đang
            TowerMainSkill("thien dia vo cuc", "vo dang", "Võ Đang", "Thiên Địa Vô Cực", ORBIT_RANGED),
            TowerMainSkill("nhan kiem hop nhat", "vo dang", "Võ Đang", "Nhân Kiếm Hợp Nhất", ORBIT_MELEE),
            // Thuý Yên
            TowerMainSkill("bang tung vo anh", "thuy yen", "Thuý Yên", "Băng Tung Vô Ảnh", ORBIT_RANGED),
            TowerMainSkill("bang tam tien tu", "thuy yen", "Thuý Yên", "Băng Tâm Tiên Tử", ORBIT_RANGED),
            // Thiên Vương Bang
            TowerMainSkill("truy tinh truc nguyet", "thien vuong", "Thiên Vương Bang", "Truy Tinh Trục Nguyệt", ORBIT_MELEE),
            TowerMainSkill("truy phong quyet", "thien vuong", "Thiên Vương Bang", "Truy Phong Quyết", ORBIT_MELEE),
            TowerMainSkill("pha thien tram", "thien vuong", "Thiên Vương Bang", "Phá Thiên Trảm", ORBIT_MELEE),
            // Ngũ Độc
            TowerMainSkill("am phong thuc cot", "ngu doc", "Ngũ Độc", "Âm Phong Thực Cốt", ORBIT_RANGED),
            TowerMainSkill("huyen am tram", "ngu doc", "Ngũ Độc", "Huyền Âm Trảm", ORBIT_RANGED)
        )

        fun normalizeTextKey(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            val nfd = java.text.Normalizer.normalize(raw.lowercase().trim(), java.text.Normalizer.Form.NFD)
            return nfd.replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
                .replace('đ', 'd')
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        fun normalizeSectKey(sectRaw: String?): String? {
            val n = normalizeTextKey(sectRaw)
            if (n.isEmpty() || n == "chua ro") return null
            return when {
                n.contains("thieu lam") -> "thieu lam"
                n.contains("thien vuong") -> "thien vuong"
                n.contains("thien nhan") -> "thien nhan"
                n.contains("con lon") -> "con lon"
                n.contains("nga mi") || n.contains("nga my") -> "nga mi"
                n.contains("duong mon") -> "duong mon"
                n.contains("cai bang") -> "cai bang"
                n.contains("vo dang") -> "vo dang"
                n.contains("thuy yen") -> "thuy yen"
                n.contains("ngu doc") -> "ngu doc"
                else -> null
            }
        }

        fun sectDisplayLabel(sectRaw: String?): String? {
            val key = normalizeSectKey(sectRaw) ?: return null
            return ALL_9X_SKILLS.firstOrNull { it.sectKey == key }?.sectLabel
        }

        fun skills9xForSect(sectRaw: String?): List<TowerMainSkill> {
            val key = normalizeSectKey(sectRaw) ?: return emptyList()
            return ALL_9X_SKILLS.filter { it.sectKey == key }
        }

        fun findMainSkill(rawSkill: String?): TowerMainSkill? {
            val cleaned = rawSkill?.replace(Regex("\\s*\\([^)]*\\)"), "")
            val n = normalizeTextKey(cleaned)
            if (n.isEmpty()) return null
            // Xác định hoàn toàn theo tên chiêu (không xác định theo vũ khí)
            return ALL_9X_SKILLS.firstOrNull { skill ->
                n == skill.id || normalizeTextKey(skill.displayName) == n
            } ?: ALL_9X_SKILLS.firstOrNull { skill ->
                n.contains(skill.id)
            }
        }

        fun normalizeMainSkillId(rawSkill: String?): String {
            return findMainSkill(rawSkill)?.id ?: ""
        }

        fun normalizeOrbitStyle(raw: String?): String {
            return when (raw?.trim()?.uppercase()) {
                ORBIT_MELEE -> ORBIT_MELEE
                ORBIT_RANGED -> ORBIT_RANGED
                else -> ORBIT_AUTO
            }
        }

        private val DEF = AutoConfig()

        fun fromJson(raw: String?): AutoConfig {
            if (raw.isNullOrBlank()) return DEF
            val o = try {
                JSONObject(raw)
            } catch (_: Exception) {
                return DEF
            }
            val arr = o.optJSONArray("doiNguyenLieu")
            val nguyenLieuSet = mutableSetOf<String>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    arr.optString(i)?.let { if (it.isNotBlank()) nguyenLieuSet.add(it) }
                }
            }

            return AutoConfig(
                on = o.optBoolean("on", DEF.on),
                theGiaoDich = o.optBoolean("theGiaoDich", DEF.theGiaoDich),
                dt = o.optBoolean("dt", DEF.dt),
                ldtMua = o.optBoolean("ldtMua", DEF.ldtMua),
                k2k = clamp(o.optInt("k2k", DEF.k2k), K2K_MAX),
                pd = o.optBoolean("pd", DEF.pd),
                st = o.optBoolean("st", DEF.st),
                lbMua = clamp(o.optInt("lbMua", DEF.lbMua), LB_MUA_MAX),
                knbMua = clamp(o.optInt("knbMua", DEF.knbMua), KNB_MUA_MAX),
                knbGiu = clamp(o.optInt("knbGiu", DEF.knbGiu), KNB_GIU_MAX),
                qua = o.optBoolean("qua", DEF.qua),
                khan = o.optBoolean("khan", DEF.khan),
                ttl = o.optBoolean("ttl", DEF.ttl),
                ttlMua = o.optBoolean("ttlMua", DEF.ttlMua),
                tt2 = o.optBoolean("tt2", DEF.tt2),
                tt2Mua = o.optBoolean("tt2Mua", DEF.tt2Mua),
                pb = o.optBoolean("pb", DEF.pb),
                thap = o.optBoolean("thap", DEF.thap),
                thapNeDon = o.optBoolean("thapNeDon", DEF.thapNeDon),
                thapNeDonStyle = normalizeOrbitStyle(o.optString("thapNeDonStyle", DEF.thapNeDonStyle)),
                thapMainSkill = normalizeMainSkillId(o.optString("thapMainSkill", DEF.thapMainSkill)),
                qht = o.optBoolean("qht", DEF.qht),
                qhtMua = o.optBoolean("qhtMua", DEF.qhtMua),
                doiNguyenLieu = nguyenLieuSet
            )
        }

        private fun clamp(v: Int, max: Int): Int = if (v < 0) 0 else if (v > max) max else v
    }
}

class GameAutoPilot(
    private val scope: CoroutineScope,
    private val tabCount: () -> Int,
    initialConfigJsonForTab: (Int) -> String?,
    private val webViewAt: (Int) -> KeepAliveWebView?,
    private val isTabActive: (Int) -> Boolean,
    private val isSystemRunning: () -> Boolean
) {
    companion object {
        private const val TICK_MS = 25_000L
        private const val FULL_EVERY = 5L
        const val SCRIPT_VER = "vlmauto-30"
        const val KNB_NGAY_MAX = 1000
        const val EV_DHB_MAX = 1
        const val EV_HB_MAX = 3
    }

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private val cfgs: Array<AutoConfig> = Array(com.example.model.TabLimits.SLOT_COUNT) { i ->
        AutoConfig.fromJson(initialConfigJsonForTab(i))
    }

    private val installedUrl = arrayOfNulls<String>(com.example.model.TabLimits.SLOT_COUNT)

    @Volatile
    private var lastVnGameDay: String = VnResetSchedule.vnGameDayId(System.currentTimeMillis())

    @Volatile
    private var lastVnCalendarDay: String = VnResetSchedule.vnCalendarDayId(System.currentTimeMillis())

    private var ticks = 0L
    private var job: Job? = null
    private var midnightResetJob: Job? = null
    private var midnightTradeCardJob: Job? = null

    fun isEnabled(tabIndex: Int): Boolean = cfgs.getOrNull(tabIndex)?.on == true

    fun isAnyEnabled(): Boolean = cfgs.any { it.on }

    fun currentConfig(tabIndex: Int): AutoConfig = cfgs.getOrNull(tabIndex) ?: AutoConfig()

    fun start() {
        if (job?.isActive != true) {
            job = scope.launch {
                while (true) {
                    try {
                        val now = System.currentTimeMillis()
                        val currentCalendarDay = VnResetSchedule.vnCalendarDayId(now)
                        if (currentCalendarDay != lastVnCalendarDay) {
                            lastVnCalendarDay = currentCalendarDay
                            triggerMidnight000000OnTabs()
                        }
                        val currentVnDay = VnResetSchedule.vnGameDayId(now)
                        if (currentVnDay != lastVnGameDay) {
                            lastVnGameDay = currentVnDay
                            triggerNewVnDayOnTabs()
                        } else {
                            tick()
                        }
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (_: Throwable) {
                    }
                    delay(TICK_MS)
                }
            }
        }
        startMidnightTradeCardScheduler()
        startMidnightResetScheduler()
    }

    private fun startMidnightTradeCardScheduler() {
        if (midnightTradeCardJob?.isActive == true) return
        midnightTradeCardJob = scope.launch {
            while (true) {
                try {
                    val waitMs = VnResetSchedule.millisUntilMidnight000000(System.currentTimeMillis()) + 80L
                    delay(waitMs)
                    val newCalDay = VnResetSchedule.vnCalendarDayId(System.currentTimeMillis())
                    if (newCalDay != lastVnCalendarDay) {
                        lastVnCalendarDay = newCalDay
                        triggerMidnight000000OnTabs()
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Throwable) {
                    delay(5_000L)
                }
            }
        }
    }

    private fun startMidnightResetScheduler() {
        if (midnightResetJob?.isActive == true) return
        midnightResetJob = scope.launch {
            while (true) {
                try {
                    val waitMs = VnResetSchedule.millisUntilNextReset(System.currentTimeMillis()) + 150L
                    delay(waitMs)
                    val newDay = VnResetSchedule.vnGameDayId(System.currentTimeMillis())
                    if (newDay != lastVnGameDay) {
                        lastVnGameDay = newDay
                        triggerNewVnDayOnTabs()
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Throwable) {
                    delay(5_000L)
                }
            }
        }
    }

    private fun triggerMidnight000000OnTabs() {
        mainHandler.post {
            for (i in 0 until tabCount().coerceAtMost(installedUrl.size)) {
                if (!isTabActive(i)) continue
                val tabCfg = cfgs[i]
                val wv = webViewAt(i) ?: continue
                val url = wv.url ?: continue
                if (url == "about:blank") continue
                val cfgJson = tabCfg.toJson().toString()
                if (installedUrl[i] != url) {
                    installedUrl[i] = url
                    runCatching {
                        wv.evaluateJavascript(
                            buildAutoScript(i, cfgJson, wv.lastTouchTimeMs, immediate = tabCfg.on),
                            null
                        )
                    }
                }
                if (tabCfg.on && tabCfg.theGiaoDich && isSystemRunning()) {
                    runCatching { wv.evaluateJavascript(buildMidnight000000Script(cfgJson), null) }
                }
            }
        }
    }

    private fun triggerNewVnDayOnTabs() {
        mainHandler.post {
            for (i in 0 until tabCount().coerceAtMost(installedUrl.size)) {
                if (!isTabActive(i)) continue
                val tabCfg = cfgs[i]
                val wv = webViewAt(i) ?: continue
                val url = wv.url ?: continue
                if (url == "about:blank") continue
                val cfgJson = tabCfg.toJson().toString()
                if (installedUrl[i] != url) {
                    installedUrl[i] = url
                    runCatching {
                        wv.evaluateJavascript(
                            buildAutoScript(i, cfgJson, wv.lastTouchTimeMs, immediate = tabCfg.on),
                            null
                        )
                    }
                }
                if (tabCfg.on && isSystemRunning()) {
                    runCatching { wv.evaluateJavascript(buildNewVnDayScript(cfgJson), null) }
                }
            }
        }
    }

    fun shutdown() {
        job?.cancel()
        job = null
        midnightResetJob?.cancel()
        midnightResetJob = null
        midnightTradeCardJob?.cancel()
        midnightTradeCardJob = null
    }

    fun saveConfigOnly(tabIndex: Int, json: String?) {
        if (tabIndex !in cfgs.indices) return
        val wasOn = cfgs[tabIndex].on
        val updated = AutoConfig.fromJson(json)
        cfgs[tabIndex] = updated
        // Chỉ đồng bộ xuống WebView nếu tab đang bật tự động sẵn (hoặc vừa bị tắt);
        // khi tab đang tắt thì chỉ lưu cấu hình, tuyệt đối không kích hoạt chạy auto app.
        if (!wasOn && !updated.on) return
        val cfgJson = updated.toJson().toString()
        mainHandler.post {
            runCatching {
                injectTab(
                    i = tabIndex,
                    forceFull = false,
                    cfgJson = cfgJson,
                    immediate = false,
                    pushEvenIfOff = true
                )
            }
        }
    }

    fun updateConfig(tabIndex: Int, json: String?, immediate: Boolean = true) {
        if (tabIndex !in cfgs.indices) return
        val updated = AutoConfig.fromJson(json)
        cfgs[tabIndex] = updated
        installedUrl[tabIndex] = null
        if (job == null || job?.isActive != true || midnightResetJob?.isActive != true || midnightTradeCardJob?.isActive != true) start()
        val cfgJson = updated.toJson().toString()
        mainHandler.post {
            runCatching {
                injectTab(
                    i = tabIndex,
                    forceFull = true,
                    cfgJson = cfgJson,
                    immediate = immediate && updated.on,
                    pushEvenIfOff = true
                )
            }
        }
    }

    fun resetDailyCounters(tabIndex: Int? = null) {
        mainHandler.post {
            val range = if (tabIndex != null && tabIndex in 0 until tabCount().coerceAtMost(installedUrl.size)) {
                tabIndex..tabIndex
            } else {
                0 until tabCount().coerceAtMost(installedUrl.size)
            }
            for (i in range) {
                val wv = webViewAt(i) ?: continue
                if (!isTabActive(i)) continue
                if (wv.url == null || wv.url == "about:blank") continue
                runCatching { wv.evaluateJavascript(buildResetScript(), null) }
            }
        }
    }

    fun onTabRestarted(tabIndex: Int) {
        if (tabIndex in installedUrl.indices) installedUrl[tabIndex] = null
    }

    fun onTabRemoved(tabIndex: Int) {
        if (tabIndex in installedUrl.indices) installedUrl[tabIndex] = null
    }

    private fun tick() {
        if (!isSystemRunning()) return
        if (!isAnyEnabled()) return

        ticks++
        val forceFull = ticks % FULL_EVERY == 0L

        mainHandler.post { injectAll(forceFull, immediate = false) }
    }

    private fun injectAll(forceFull: Boolean, immediate: Boolean) {
        for (i in 0 until tabCount().coerceAtMost(installedUrl.size)) {
            val tabCfg = cfgs[i]
            if (!tabCfg.on) continue
            injectTab(
                i = i,
                forceFull = forceFull,
                cfgJson = tabCfg.toJson().toString(),
                immediate = immediate,
                pushEvenIfOff = false
            )
        }
    }

    private fun injectTab(
        i: Int,
        forceFull: Boolean,
        cfgJson: String,
        immediate: Boolean,
        pushEvenIfOff: Boolean
    ) {
        if (i !in installedUrl.indices || i >= tabCount()) return
        if (!isTabActive(i)) {
            installedUrl[i] = null
            return
        }
        val tabCfg = cfgs[i]
        if (!tabCfg.on && !pushEvenIfOff) return

        val wv = webViewAt(i) ?: return
        val url = wv.url ?: return
        if (url == "about:blank") return

        val needFull = forceFull || installedUrl[i] != url
        val script = if (needFull) {
            installedUrl[i] = url
            buildAutoScript(i, cfgJson, wv.lastTouchTimeMs, immediate)
        } else {
            buildPushScript(cfgJson, wv.lastTouchTimeMs, immediate)
        }
        runCatching { wv.evaluateJavascript(script, null) }
    }

    private fun buildPushScript(cfgJson: String, touchMs: Long, immediate: Boolean): String {
        return "(function(){try{var a=window.__vlmAuto;if(a&&a.ver==='" + SCRIPT_VER +
            "'){a.setCfg(" + cfgJson + "," + touchMs + "," + immediate + ");" +
            (if (immediate) "if(a.tick)a.tick();" else "") + "}}catch(e){}})();"
    }

    private fun buildMidnight000000Script(cfgJson: String): String {
        return "(function(){try{var a=window.__vlmAuto;if(a&&a.onMidnight000000)a.onMidnight000000(" + cfgJson + ");}catch(e){}})();"
    }

    private fun buildNewVnDayScript(cfgJson: String): String {
        return "(function(){try{var a=window.__vlmAuto;if(a&&a.onNewVnDay)a.onNewVnDay(" + cfgJson + ");}catch(e){}})();"
    }

    private fun buildResetScript(): String {
        return "(function(){try{var a=window.__vlmAuto;if(a&&a.resetDay)a.resetDay();}catch(e){}})();"
    }

    internal fun buildAutoScript(tabIndex: Int, cfgJson: String, touchMs: Long, immediate: Boolean): String {
        return BOOTSTRAP_JS
            .replace("__TAB__", tabIndex.toString())
            .replace("__CFG__", cfgJson)
            .replace("__TOUCH__", touchMs.toString())
            .replace("__IMMEDIATE__", if (immediate) "true" else "false")
            .replace("__VER__", SCRIPT_VER)
    }

    private val BOOTSTRAP_JS: String = """
(function () {
  'use strict';
  var VER = '__VER__';
  var TAB = __TAB__;
  var CFG0 = __CFG__;
  var TOUCH0 = __TOUCH__;
  var IMM0 = __IMMEDIATE__;

  try {
    if (window.__vlmAuto && window.__vlmAuto.ver === VER) {
      window.__vlmAuto.setCfg(CFG0, TOUCH0, IMM0);
      if (IMM0 && window.__vlmAuto.tick) window.__vlmAuto.tick();
      return 'ok';
    }
    if (window.__vlmTickTimer) { clearInterval(window.__vlmTickTimer); window.__vlmTickTimer = 0; }
    if (window.__vlmOrbitTimer) { clearTimeout(window.__vlmOrbitTimer); clearInterval(window.__vlmOrbitTimer); window.__vlmOrbitTimer = 0; }
    if (window.__vlmAutoBtnTimer) { clearInterval(window.__vlmAutoBtnTimer); window.__vlmAutoBtnTimer = 0; }
    if (window.__vlmOrbitRelease) window.__vlmOrbitRelease();
  } catch (e0) {}

  var sleep = function (ms) { return new Promise(function (r) { setTimeout(r, ms); }); };

  // Giờ Việt Nam GMT+7: làm mới hoạt động lúc 00:00:01; Thẻ Giao Dịch dùng mốc 00:00:00.
  var vnDay = function () {
    var v = new Date(Date.now() + 7 * 3600e3 - 1000);
    return v.getUTCFullYear() + '-' + (v.getUTCMonth() + 1) + '-' + v.getUTCDate();
  };
  var vnTradeDay = function () {
    var v = new Date(Date.now() + 7 * 3600e3);
    return v.getUTCFullYear() + '-' + (v.getUTCMonth() + 1) + '-' + v.getUTCDate();
  };
  var dayIdForKey = function (k) {
    return k === 'theGd' ? vnTradeDay() : vnDay();
  };

  var norm = function (s) {
    return String(s || '').toLowerCase().normalize('NFC').replace(/\s+/g, ' ').trim();
  };
  var noAccent = function (s) {
    return norm(s).normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd');
  };

  var DEF = { on: false, theGiaoDich: false, dt: true, ldtMua: true, k2k: 0, pd: true, st: true, lbMua: 0,
    knbMua: 0, knbGiu: 0, qua: true, evHb: false, evDhb: false, khan: true, ttl: true,
    ttlMua: false, tt2: true, tt2Mua: false, pb: true, thap: false, thapNeDon: false, thapNeDonStyle: 'AUTO', thapMainSkill: '', qht: false, qhtMua: false };

  var A = {
    cfg: Object.assign({}, DEF, CFG0 || {}),
    busy: false,
    userAt: 0,
    touchK: (typeof TOUCH0 === 'number' ? TOUCH0 : 0),
    cool: {},
    st: null,
    stT: 0,
    started: 0,
    immediate: !!IMM0,
    immUntil: IMM0 ? (Date.now() + 120000) : 0,
    lastDay: vnDay(),
    lastTradeDay: vnTradeDay(),
    last: '',
    lastAt: 0,
    pbOut: 0,
    pbSeen: 0,
    info: null,
    gate: 0
  };

  function note(m) {
    A.last = String(m || '');
    A.lastAt = Date.now();
    try {
      if (window.AndroidBridge && window.AndroidBridge.onGameLog) {
        window.AndroidBridge.onGameLog(TAB, '🤖 ' + A.last);
      }
    } catch (e) {}
  }

  var evList = ['mousedown', 'keydown', 'wheel', 'touchstart', 'pointerdown'];
  for (var ei = 0; ei < evList.length; ei++) {
    (function (ev) {
      document.addEventListener(ev, function (e) {
        if (e && e.isTrusted) {
          A.userAt = Date.now();
          try { if (typeof pauseOrbitMovement === 'function') pauseOrbitMovement(); } catch (eP) {}
        }
      }, true);
    })(evList[ei]);
  }

  window.addEventListener('message', function (e) {
    try {
      var d = e.data;
      if (!d || typeof d !== 'object' || e.source !== window) return;
      if (d.vlm === 'st' && d.st && typeof d.st === 'object') {
        try { if (JSON.stringify(d.st).length > 64 * 1024) return; } catch (e2) { return; }
        A.st = d.st;
        A.stT = Date.now();
      }
    } catch (e3) {}
  });

  var dayWho = function () {
    try {
      if (A.st && A.st.ten) return String(A.st.ten);
    } catch (e) {}
    return '';
  };

  var dayKey = function (k) {
    var who = dayWho();
    return 'vlm_auto_' + (who ? who + '_' : '') + k;
  };

  var dayGet = function (k) {
    try {
      var o = JSON.parse(localStorage.getItem(dayKey(k)) || 'null');
      return o && o.d === dayIdForKey(k) && isFinite(o.n) && o.n >= 0 && o.n < 1e12 ? Math.floor(o.n) : 0;
    } catch (e) { return 0; }
  };

  var daySet = function (k, val) {
    try {
      if (!isFinite(val) || val < 0) return false;
      var max = k === 'knb' ? 1000 : k === 'evDhb' ? 1 : k === 'evHb' ? 3 : k === 'theGd' ? 1 : 9999999999;
      localStorage.setItem(dayKey(k),
        JSON.stringify({ d: dayIdForKey(k), n: Math.min(max, Math.floor(Math.max(0, val))) }));
      return true;
    } catch (e) { return false; }
  };

  var dayAdd = function (k, n) {
    try {
      if (!isFinite(n) || n < 0) return false;
      var max = k === 'knb' ? 1000 : k === 'evDhb' ? 1 : k === 'evHb' ? 3 : k === 'theGd' ? 1 : 9999999999;
      localStorage.setItem(dayKey(k),
        JSON.stringify({ d: dayIdForKey(k), n: Math.min(max, dayGet(k) + Math.floor(Math.min(n, max))) }));
      return true;
    } catch (e) { return false; }
  };

  function normalizeOrbitStyleJs(raw) {
    var s = String(raw || '').toUpperCase().trim();
    if (s === 'MELEE' || s === 'RANGED') return s;
    return 'AUTO';
  }

  function setCfg(j, t, imm) {
    var prevOn = !!A.cfg.on;
    var prevTtl = !!A.cfg.ttl;
    var prevTt2 = !!A.cfg.tt2;
    var prevQht = !!A.cfg.qht;
    var prevTgd = !!A.cfg.theGiaoDich;
    try {
      var c = (typeof j === 'string') ? JSON.parse(j) : (j || {});
      A.cfg = Object.assign({}, DEF, c || {});
      A.cfg.thapNeDon = !!A.cfg.thapNeDon;
      A.cfg.thapNeDonStyle = normalizeOrbitStyleJs(A.cfg.thapNeDonStyle);
      A.cfg.thapMainSkill = String(A.cfg.thapMainSkill || '').trim();
    } catch (e) {}
    if (typeof t === 'number' && t > A.touchK) A.touchK = t;
    if (!A.cfg.on || (!A.cfg.thap && !A.cfg.thapNeDon)) {
      try { if (typeof releaseOrbitKeys === 'function') releaseOrbitKeys(); } catch (eRel) {}
      try { if (typeof towerLockSet === 'function') towerLockSet(false); } catch (eTl) {}
    }
    if (!A.cfg.on) {
      A.started = 0;
      A.immediate = false;
      A.immUntil = 0;
    } else if (imm || !prevOn) {
      A.immediate = true;
      A.immUntil = Date.now() + 120000;
      A.started = Date.now() - 60000;
      A.cool = {};
    } else {
      if ((!prevTtl && A.cfg.ttl) || (!prevTt2 && A.cfg.tt2) || (!prevQht && A.cfg.qht)) {
        delete A.cool.buff_scan_15m;
      }
      if (!prevTgd && A.cfg.theGiaoDich) {
        delete A.cool.theGd;
      }
    }
  }

  function resetDay(silent) {
    try {
      var td = vnTradeDay();
      var ks = [];
      for (var i = 0; i < localStorage.length; i++) {
        var k = localStorage.key(i);
        if (k && k.indexOf('vlm_auto_') === 0 && k.indexOf('vlm_auto_prev') !== 0) ks.push(k);
      }
      for (var j = 0; j < ks.length; j++) {
        var keyName = ks[j];
        if (silent && /_theGd$/.test(keyName)) {
          try {
            var cur = JSON.parse(localStorage.getItem(keyName) || 'null');
            if (cur && cur.d === td) continue;
          } catch (eCur) {}
        }
        localStorage.removeItem(keyName);
      }
      A.pbOut = 0; A.pbSeen = 0; A.gate = 0; A.cool = {};
      try { if (typeof towerLockSet === 'function') towerLockSet(false); } catch (eTl0) {}
      try { if (typeof bossHkPrevSet === 'function') bossHkPrevSet(null); } catch (eBh0) {}
      try { if (typeof prevSet === 'function') prevSet(null); } catch (ePr0) {}
      if (!silent) note('đã đặt lại bộ đếm ngày');
    } catch (e) {}
  }

  function onMidnight000000(c) {
    if (c) setCfg(c, A.touchK, true);
    A.lastTradeDay = vnTradeDay();
    delete A.cool.theGd;
    A.immediate = true;
    A.immUntil = Date.now() + 120000;
    A.started = Date.now() - 60000;
    note('00:00:00 (GMT+7): ưu tiên mua ẩn Thẻ Giao Dịch');
    setTimeout(function () { tick().catch(function () {}); }, 80);
  }

  function onNewVnDay(c) {
    if (c) setCfg(c, A.touchK, true);
    A.lastDay = vnDay();
    resetDay(true);
    A.immediate = true;
    A.immUntil = Date.now() + 120000;
    A.started = Date.now() - 60000;
    note('00:00:01 (GMT+7): làm mới hoạt động ngày mới');
    setTimeout(function () { tick().catch(function () {}); }, 400);
  }

  var ready = function (k, ms) {
    if (A.cool[k] > Date.now()) return false;
    A.cool[k] = Date.now() + ms;
    return true;
  };

  var isUserControlling = function (graceMs) {
    var g = graceMs || 5000;
    if (window.__isUserTouching) return true;
    var now = Date.now();
    if (now - A.userAt <= g) return true;
    if (now - (A.touchK || 0) <= g) return true;
    return false;
  };

  var ranh = function () {
    if (isUserControlling(6000)) return false;
    if (A.immediate || A.immUntil > Date.now()) {
      return !isUserControlling(6000);
    }
    if (Date.now() - A.userAt <= 45000) return false;
    return Date.now() - (A.touchK || 0) > 15000;
  };

  // 4 mốc Boss Hoàng Kim theo giờ Việt Nam (GMT+7): 12:00, 15:30, 19:30, 22:00
  // Cửa sổ ưu tiên: trước 1 phút đến sau 20 phút (ví dụ 11:59 -> 12:20)
  var BOSS_HK_SLOTS_MIN = [12 * 60, 15 * 60 + 30, 19 * 60 + 30, 22 * 60];
  function getActiveBossHkSlot() {
    var v = new Date(Date.now() + 7 * 3600e3);
    var curMin = v.getUTCHours() * 60 + v.getUTCMinutes();
    for (var i = 0; i < BOSS_HK_SLOTS_MIN.length; i++) {
      var start = BOSS_HK_SLOTS_MIN[i] - 1;
      var end = BOSS_HK_SLOTS_MIN[i] + 20;
      if (curMin >= start && curMin < end) {
        return vnTradeDay() + '_' + BOSS_HK_SLOTS_MIN[i];
      }
    }
    return null;
  }

  var bossHkPrevKey = function () {
    var who = dayWho();
    return 'vlm_auto_bosshk_prev' + (who ? '_' + who : '');
  };
  var bossHkPrevGet = function () {
    try { return localStorage.getItem(bossHkPrevKey()); } catch (e) { return null; }
  };
  var bossHkPrevSet = function (m) {
    try {
      var k = bossHkPrevKey();
      if (m) localStorage.setItem(k, m);
      else localStorage.removeItem(k);
    } catch (e) {}
  };

  var isAuthOrProtectedModalOpen = function () {
    try {
      var authIds = ['ngU', 'ngP', 'ngStart', 'ngIn', 'csel', 'csGo', 'sfRe', 'ngRetry'];
      for (var ai = 0; ai < authIds.length; ai++) {
        var aEl = document.getElementById(authIds[ai]);
        if (aEl && isVis(aEl)) return true;
      }
      var md = document.getElementById('modal');
      if (md && !md.classList.contains('hidden')) {
        var mt = noAccent(md.textContent || '');
        if (mt.indexOf('choi o day') >= 0 || mt.indexOf('choi o tab nay') >= 0 ||
            mt.indexOf('dang ky tai khoan') >= 0 || mt.indexOf('chon nhan vat') >= 0) {
          return true;
        }
      }
    } catch (e) {}
    return false;
  };

  var canRunHiddenConsumable = function () {
    if (!A.cfg.on) return false;
    if (Date.now() - A.userAt <= 2500) return false;
    if (isModalLocked()) return false;
    if (isAuthOrProtectedModalOpen()) return false;
    var md = document.getElementById('modal');
    if (md && !md.classList.contains('hidden')) return false;
    return true;
  };

  function ensureStealthCss() {
    try {
      var id = '__vlm_stealth_css';
      var st = document.getElementById(id);
      if (st && st.dataset && st.dataset.ver === '3') return;
      if (!st) {
        st = document.createElement('style');
        st.id = id;
        (document.head || document.documentElement).appendChild(st);
      }
      if (st.dataset) st.dataset.ver = '3';
      st.textContent =
        'html.__vlm-stealth #modal, html.__vlm-stealth #modal *, ' +
        'html.__vlm-stealth #cModal, html.__vlm-stealth #cModal *, ' +
        'html.__vlm-stealth #cfModal, html.__vlm-stealth #cfModal *, ' +
        'html.__vlm-stealth #confirmModal, html.__vlm-stealth #confirmModal *, ' +
        'html.__vlm-stealth #itemModal, html.__vlm-stealth #itemModal *, ' +
        'html.__vlm-stealth #detailModal, html.__vlm-stealth #detailModal *, ' +
        'html.__vlm-stealth #popup, html.__vlm-stealth #popup *, ' +
        'html.__vlm-stealth .popup, html.__vlm-stealth .popup *, ' +
        'html.__vlm-stealth .modal, html.__vlm-stealth .modal *, ' +
        'html.__vlm-stealth [role="dialog"], html.__vlm-stealth [role="dialog"] *, ' +
        'html.__vlm-stealth [aria-modal="true"], html.__vlm-stealth [aria-modal="true"] *, ' +
        'html.__vlm-stealth .confirm, html.__vlm-stealth .confirm *, ' +
        'html.__vlm-stealth [data-confirm], html.__vlm-stealth [data-confirm] *, ' +
        'html.__vlm-stealth .modal-backdrop, html.__vlm-stealth .modal-overlay, html.__vlm-stealth .backdrop, html.__vlm-stealth .overlay, ' +
        'html.__vlm-stealth #u20pop, html.__vlm-stealth #u20pop *, ' +
        'html.__vlm-stealth .u20-pop, html.__vlm-stealth .u20-pop *, ' +
        'html.__vlm-stealth .u20-more, html.__vlm-stealth .u20-more *, ' +
        'html.__vlm-stealth .more-menu, html.__vlm-stealth .more-menu *, ' +
        'html.__vlm-stealth [data-more-pop], html.__vlm-stealth [data-more-pop] *, ' +
        'html.__vlm-stealth #t-inv, html.__vlm-stealth #t-inv *, ' +
        'html.__vlm-stealth #t-bag, html.__vlm-stealth #t-bag *, ' +
        'html.__vlm-stealth #p-inv, html.__vlm-stealth #p-inv *, ' +
        'html.__vlm-stealth #p-bag, html.__vlm-stealth #p-bag *, ' +
        'html.__vlm-stealth #invTab, html.__vlm-stealth #invTab *, ' +
        'html.__vlm-stealth #bagTab, html.__vlm-stealth #bagTab *, ' +
        'html.__vlm-stealth #invPane, html.__vlm-stealth #invPane *, ' +
        'html.__vlm-stealth #bagPane, html.__vlm-stealth #bagPane *, ' +
        'html.__vlm-stealth #invPanel, html.__vlm-stealth #invPanel *, ' +
        'html.__vlm-stealth #bagPanel, html.__vlm-stealth #bagPanel * { ' +
        '  pointer-events: none !important; ' +
        '  transition: none !important; ' +
        '  animation: none !important; ' +
        '} ' +
        'html.__vlm-stealth #modal, html.__vlm-stealth #cModal, html.__vlm-stealth #cfModal, ' +
        'html.__vlm-stealth #confirmModal, html.__vlm-stealth #itemModal, html.__vlm-stealth #detailModal, ' +
        'html.__vlm-stealth #popup, html.__vlm-stealth .popup, html.__vlm-stealth .modal, ' +
        'html.__vlm-stealth [role="dialog"], html.__vlm-stealth [aria-modal="true"], ' +
        'html.__vlm-stealth .confirm, html.__vlm-stealth [data-confirm], ' +
        'html.__vlm-stealth .modal-backdrop, html.__vlm-stealth .modal-overlay, html.__vlm-stealth .backdrop, html.__vlm-stealth .overlay, ' +
        'html.__vlm-stealth #u20pop, html.__vlm-stealth .u20-pop, html.__vlm-stealth .u20-more, ' +
        'html.__vlm-stealth .more-menu, html.__vlm-stealth [data-more-pop], ' +
        'html.__vlm-stealth #t-inv, html.__vlm-stealth #t-bag, html.__vlm-stealth #p-inv, html.__vlm-stealth #p-bag, ' +
        'html.__vlm-stealth #invTab, html.__vlm-stealth #bagTab, ' +
        'html.__vlm-stealth #invPane, html.__vlm-stealth #bagPane, html.__vlm-stealth #invPanel, html.__vlm-stealth #bagPanel { ' +
        '  opacity: 0.001 !important; ' +
        '  z-index: -9999 !important; ' +
        '  position: fixed !important; ' +
        '  left: -99999px !important; ' +
        '  top: -99999px !important; ' +
        '}';
    } catch (e) {}
  }

  function stealthObj() {
    if (!window.__vlmStealth) {
      window.__vlmStealth = {
        count: 0,
        enter: function () {
          ensureStealthCss();
          this.count = (this.count | 0) + 1;
          try { document.documentElement.classList.add('__vlm-stealth'); } catch (e) {}
        },
        leave: function () {
          this.count = Math.max(0, (this.count | 0) - 1);
          if (this.count === 0) {
            setTimeout(function () {
              if (window.__vlmStealth && window.__vlmStealth.count === 0) {
                try { document.documentElement.classList.remove('__vlm-stealth'); } catch (e) {}
              }
            }, 120);
          }
        }
      };
    }
    return window.__vlmStealth;
  }

  function isModalLocked() {
    return (window.__vlmModalLockUntil || 0) > Date.now();
  }

  function lockModal(ms) {
    window.__vlmModalLockUntil = Date.now() + (ms || 15000);
  }

  function unlockModal() {
    window.__vlmModalLockUntil = 0;
  }

  async function closeModalCleanly(md) {
    try {
      if (md && !md.classList.contains('hidden')) {
        var x = document.getElementById('mClose');
        if (x) { try { x.click(); } catch (e) {} }
        var end1 = Date.now() + 350;
        while (!md.classList.contains('hidden') && Date.now() < end1) await sleep(60);
      }
      if (md && !md.classList.contains('hidden')) {
        try { md.click(); } catch (e) {}
        var end2 = Date.now() + 250;
        while (!md.classList.contains('hidden') && Date.now() < end2) await sleep(60);
      }
      var extras = document.querySelectorAll('#cModal, #cfModal, #confirmModal, #itemModal, #detailModal, .confirm, [data-confirm]');
      for (var ei = 0; ei < extras.length; ei++) {
        var ov = extras[ei];
        if (!ov || (ov.classList && ov.classList.contains('hidden'))) continue;
        var closeBtn = ov.querySelector('#mClose, .close, [data-close], button, [role="button"]');
        if (closeBtn && isVis(closeBtn)) {
          var ct = noAccent(closeBtn.textContent || '');
          if (ct === 'x' || ct === 'dong' || ct === 'dong lai' || ct === 'huy' || ct === 'khong' || (closeBtn.classList && closeBtn.classList.contains('close'))) {
            try { closeBtn.click(); } catch (e2) {}
          }
        }
        try { ov.classList.add('hidden'); } catch (e3) {}
      }
      var strayPops = document.querySelectorAll('#mswMenu, #u20pop, .u20-pop, .u20-more, .more-menu, [data-more-pop]');
      for (var sp = 0; sp < strayPops.length; sp++) {
        try { strayPops[sp].classList.add('hidden'); } catch (e4) {}
      }
    } catch (e) {}
  }

  var modalCuaTa = function (md, moAt, dungBang) {
    return !!md && (A.userAt === moAt || document.documentElement.classList.contains('__vlm-stealth')) &&
      !md.classList.contains('hidden') && dungBang();
  };

  var isVis = function (el) {
    if (!el || !el.isConnected) return false;
    try {
      if (el.getClientRects && el.getClientRects().length === 0) return false;
      var cur = el;
      while (cur && cur !== document.body && cur !== document.documentElement) {
        if (cur.hidden) return false;
        if (cur.classList && cur.classList.contains('hidden')) return false;
        var cs = window.getComputedStyle(cur);
        if (cs && (cs.display === 'none' || cs.visibility === 'hidden' || parseFloat(cs.opacity) === 0)) return false;
        cur = cur.parentElement;
      }
    } catch (e) {}
    return true;
  };

  var fillInp = function (inp, valStr) {
    if (!inp) return;
    try { inp.focus(); } catch (e) {}
    try {
      if (inp._valueTracker && typeof inp._valueTracker.setValue === 'function') {
        inp._valueTracker.setValue('');
      }
    } catch (e) {}
    try {
      var desc = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value');
      if (desc && desc.set) desc.set.call(inp, valStr);
      else inp.value = valStr;
    } catch (e) {
      inp.value = valStr;
    }
    try { inp.dispatchEvent(new Event('input', { bubbles: true })); } catch (e) {}
    try { inp.dispatchEvent(new Event('change', { bubbles: true })); } catch (e) {}
    try { inp.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true, key: '0' })); } catch (e) {}
  };

  var prevKey = function () {
    var who = dayWho();
    return 'vlm_auto_prev' + (who ? '_' + who : '');
  };
  var prevGet = function () {
    try { return localStorage.getItem(prevKey()) || localStorage.getItem('vlm_auto_prev'); } catch (e) { return null; }
  };
  var prevSet = function (m) {
    try {
      var k = prevKey();
      if (m) localStorage.setItem(k, m);
      else { localStorage.removeItem(k); localStorage.removeItem('vlm_auto_prev'); }
    } catch (e) {}
  };

  var towerLockKey = function () {
    var who = dayWho();
    return 'vlm_auto_tower_lock' + (who ? '_' + who : '');
  };
  var towerLockGet = function () {
    try {
      return localStorage.getItem(towerLockKey()) === '1' || localStorage.getItem('vlm_auto_tower_lock') === '1';
    } catch (e) { return false; }
  };
  var towerLockSet = function (on) {
    try {
      var k = towerLockKey();
      if (on) {
        localStorage.setItem(k, '1');
        localStorage.setItem('vlm_auto_tower_lock', '1');
      } else {
        localStorage.removeItem(k);
        localStorage.removeItem('vlm_auto_tower_lock');
      }
    } catch (e) {}
  };

  var MODE_VI = { farm: 'Luyện Công', dg: 'Phó Bản', tower: 'Leo Tháp', quest: 'Nhiệm Vụ' };
  var pbXong = function () { return dayGet('pbx') > 0; };

  async function api(act, arg, ms) {
    arg = arg || {};
    ms = ms || 45000;
    var md = document.getElementById('modal');
    var guardStealth = (act !== 'info' && act !== 'cheDo' && !isModalLocked() && (!md || md.classList.contains('hidden')));
    if (guardStealth) stealthObj().enter();
    var b = document.createElement('button');
    b.id = 'vlmAuto';
    b.type = 'button';
    b.style.display = 'none';
    b.dataset.act = act;
    b.dataset.arg = JSON.stringify(arg);
    (document.body || document.documentElement).appendChild(b);
    try {
      b.click();
      var end = Date.now() + ms;
      while (!b.dataset.xong && Date.now() < end) await sleep(120);
      if (!b.dataset.xong) return { ok: false, why: 'hết giờ' };
      try { return JSON.parse(b.dataset.kq || '{}'); } catch (e) { return { ok: false, why: 'kết quả lạ' }; }
    } finally {
      try { b.remove(); } catch (e) {}
      if (guardStealth) {
        await closeModalCleanly(md);
        stealthObj().leave();
      }
    }
  }

  async function info() {
    var i = await api('info', {}, 8000);
    if (i && i.ok) { A.info = i; return i; }
    return null;
  }

  async function troThu(I) {
    var dt = I.dt || {};
    var can = (A.cfg.dt && (!dt.auto || !I.autoLdt)) || (A.cfg.st && !I.autoSt);
    if (!can || !ready('tt', 60000)) return;
    var r = await api('troThu', { dt: !!A.cfg.dt, ldt: !!A.cfg.dt, st: !!A.cfg.st });
    if (r && r.ok && r.bat && r.bat.length) {
      var ten = { dt: 'Dã Tẩu tự trả / nhận', ldt: 'tự dùng Lệnh Bài Dã Tẩu', st: 'tự gọi Sát Thủ' };
      var ds = [];
      for (var i = 0; i < r.bat.length; i++) if (ten[r.bat[i]]) ds.push(ten[r.bat[i]]);
      note('bật Trợ thủ: ' + ds.join(', '));
    }
  }

  async function khan(I) {
    if (!A.cfg.khan || I.khan || I.dg || I.tower || I.sat || I.dead || !ranh() || !ready('khan', 5 * 60000)) return;
    var r = await api('khan', {}, 60000);
    if (r && r.ok && r.xong) {
      var I2 = await info();
      if (I2 && I2.khan) note('khấn tượng: +20% kinh nghiệm 8 giờ');
    }
  }

  var BUFF_SCAN_MAX_MS = 15 * 60000;
  var BUFF_RENEW_LEAD_MS = 15000; // Căn trước 15 giây khi vật phẩm tiêu hao sắp hết

  function classifyBuffKind(rawMeta, allowShortApiTokens) {
    var n = noAccent(rawMeta || '');
    if (!n) return null;
    if (n.indexOf('tien thao lo dac biet') >= 0 ||
        n.indexOf('tien_thao_lo_dac_biet') >= 0 ||
        (n.indexOf('tien thao') >= 0 && n.indexOf('dac biet') >= 0) ||
        /(?:^|[^a-z0-9])(ttsp|tt2|ttdb|tt_db)(?:$|[^a-z0-9])/.test(n)) {
      return 'tt2';
    }
    if (n.indexOf('tien thao lo') >= 0 ||
        n.indexOf('tien_thao') >= 0 ||
        /(?:^|[^a-z0-9])ttl(?:$|[^a-z0-9])/.test(n) ||
        (allowShortApiTokens && /(?:^|[^a-z0-9])tt(?:$|[^a-z0-9])/.test(n))) {
      return 'ttl';
    }
    if (n.indexOf('que hoa tuu') >= 0 ||
        n.indexOf('que hoa') >= 0 ||
        n.indexOf('quehoa') >= 0 ||
        n.indexOf('que_hoa') >= 0 ||
        /(?:^|[^a-z0-9])(qht|qhtuu)(?:$|[^a-z0-9])/.test(n) ||
        (allowShortApiTokens && /(?:^|[^a-z0-9])tuu(?:$|[^a-z0-9])/.test(n))) {
      return 'qht';
    }
    return null;
  }

  function isBuffTimerExpired(txt) {
    var t = noAccent(txt || '');
    if (!t) return false;
    return /^(0+(:0+)+|0+\s*[smhdpg]|het|expired|n\/a)$/.test(t);
  }

  // Phân tích thời gian còn lại của buff trên HUD (hỗ trợ cả màn hình ngang/dọc: 4m, 7h, 18h, 1h30m, 04:32, 45s, 12p, 1g20p)
  function parseBuffDurationMs(txt) {
    var t = noAccent(txt || '');
    if (!t || isBuffTimerExpired(t)) return 0;

    // Dạng HH:MM:SS hoặc MM:SS
    var col = /(\d{1,2}):(\d{2})(?::(\d{2}))?/.exec(t);
    if (col) {
      if (col[3] !== undefined) {
        return ((Number(col[1]) * 3600) + (Number(col[2]) * 60) + Number(col[3])) * 1000;
      }
      return ((Number(col[1]) * 60) + Number(col[2])) * 1000;
    }

    // Dạng đơn vị: d/ngay, h/g/gio, m/p/phut, s/giay (ví dụ: 4m, 7h, 18h, 1h30m, 45s)
    var totalMs = 0;
    var matched = false;
    var re = /(\d+)\s*(ngay|d|gio|h|g|phut|m|p|giay|s)/g;
    var m;
    while ((m = re.exec(t)) !== null) {
      var val = Number(m[1]);
      var unit = m[2];
      if (!isFinite(val)) continue;
      matched = true;
      if (unit === 'd' || unit === 'ngay') totalMs += val * 86400000;
      else if (unit === 'h' || unit === 'g' || unit === 'gio') totalMs += val * 3600000;
      else if (unit === 'm' || unit === 'p' || unit === 'phut') totalMs += val * 60000;
      else if (unit === 's' || unit === 'giay') totalMs += val * 1000;
    }
    if (matched) return totalMs;
    return -1; // Đang bật nhưng không ghi rõ thời gian
  }

  function apiValToMs(v, isMsField) {
    var n = Number(v);
    if (!isFinite(n) || n <= 0) return 0;
    if (isMsField) return n;
    // Nếu lớn hơn 7 ngày tính theo giây (> 604800) thì coi là milliseconds
    return n > 604800 ? n : (n * 1000);
  }

  function extractNodeMeta(el) {
    if (!el) return '';
    var parts = [];
    try {
      if (el.id) parts.push(el.id);
      if (typeof el.className === 'string') parts.push(el.className);
      var attrs = [
        'title', 'aria-label', 'alt', 'src', 'data-k', 'data-key', 'data-id',
        'data-buff', 'data-effect', 'data-name', 'data-type', 'data-item',
        'data-tip', 'data-tooltip', 'data-title', 'data-label', 'data-desc'
      ];
      for (var i = 0; i < attrs.length; i++) {
        var v = el.getAttribute ? el.getAttribute(attrs[i]) : null;
        if (v) parts.push(v);
      }
      var imgs = el.querySelectorAll ? el.querySelectorAll('img') : [];
      for (var j = 0; j < imgs.length; j++) {
        var img = imgs[j];
        parts.push(img.getAttribute('src') || '');
        parts.push(img.getAttribute('alt') || '');
        parts.push(img.getAttribute('title') || '');
        parts.push(img.getAttribute('data-k') || '');
        parts.push(img.getAttribute('data-name') || '');
        parts.push(img.getAttribute('data-tip') || '');
        parts.push(img.getAttribute('data-tooltip') || '');
      }
      parts.push(el.textContent || '');
    } catch (e) {}
    return parts.join(' ');
  }

  // Trả về cả trạng thái bật/tắt lẫn thời gian còn lại (ms) của từng vật phẩm tiêu hao
  function scanHudBuffs(I) {
    var found = { ttl: false, tt2: false, qht: false, rem: { ttl: 0, tt2: 0, qht: 0 } };
    var recordBuff = function (kind, ms) {
      if (!kind) return;
      if (ms > 0) {
        found[kind] = true;
        if (ms > found.rem[kind]) found.rem[kind] = ms;
      } else if (ms < 0) {
        found[kind] = true;
        if (found.rem[kind] === 0) found.rem[kind] = 5 * 60000;
      }
    };

    try {
      var checkApiBuffSource = function (src) {
        if (!src || typeof src !== 'object') return;
        if (src.tt > 0) recordBuff('ttl', apiValToMs(src.tt, false));
        if (src.ttl > 0) recordBuff('ttl', apiValToMs(src.ttl, false));
        if (src.tt2 > 0) recordBuff('tt2', apiValToMs(src.tt2, false));
        if (src.ttsp > 0) recordBuff('tt2', apiValToMs(src.ttsp, false));
        if (src.qhtMs > 0) recordBuff('qht', apiValToMs(src.qhtMs, true));
        if (src.qht > 0) recordBuff('qht', apiValToMs(src.qht, false));
        if (src.queHoaTuu > 0) recordBuff('qht', apiValToMs(src.queHoaTuu, false));
        if (src.tuu > 0) recordBuff('qht', apiValToMs(src.tuu, false));
        if (src.buffs && typeof src.buffs === 'object') {
          var entries = Array.isArray(src.buffs) ? src.buffs : Object.keys(src.buffs);
          for (var ei = 0; ei < entries.length; ei++) {
            var entry = Array.isArray(src.buffs) ? entries[ei] : { k: entries[ei], v: src.buffs[entries[ei]] };
            var label = typeof entry === 'string'
              ? entry
              : ((entry && (entry.name || entry.ten || entry.k || entry.key || entry.id || entry.type)) || '');
            var isMs = !!(entry && typeof entry === 'object' && entry.ms !== undefined);
            var rem = (entry && typeof entry === 'object')
              ? (entry.ms !== undefined ? entry.ms : (entry.v !== undefined ? entry.v : (entry.rem !== undefined ? entry.rem : (entry.left !== undefined ? entry.left : (entry.sec !== undefined ? entry.sec : 1)))))
              : 1;
            var ek = classifyBuffKind(label, true);
            if (ek && Number(rem) > 0) recordBuff(ek, apiValToMs(rem, isMs));
          }
        }
      };

      checkApiBuffSource(I);
      if (A.st && Date.now() - A.stT < 90000) checkApiBuffSource(A.st);

      // Quét vùng hiển thị buff phía trên bên trái màn hình (tối ưu cho cả màn hình ngang & dọc, ngay trên Tổ đội)
      var anchorBottom = 220;
      var anchorRight = Math.max(480, Math.floor((window.innerWidth || 800) * 0.55));
      try {
        var anchorCands = [document.getElementById('mswBtn'), document.getElementById('modeSw')];
        var smallBtns = document.querySelectorAll('button, div, span, a');
        for (var ai = 0; ai < smallBtns.length; ai++) {
          var ancEl = smallBtns[ai];
          if (!isVis(ancEl)) continue;
          var ancTxt = noAccent(ancEl.textContent || '');
          if (ancTxt.indexOf('che do') === 0 || ancTxt.indexOf('to doi') >= 0) {
            if (ancTxt.length <= 40) anchorCands.push(ancEl);
          }
        }
        for (var ac = 0; ac < anchorCands.length; ac++) {
          var aEl = anchorCands[ac];
          if (!aEl || !isVis(aEl) || !aEl.getBoundingClientRect) continue;
          var ar = aEl.getBoundingClientRect();
          if (ar && ar.top > 0 && ar.top < window.innerHeight * 0.6 && ar.left < anchorRight) {
            anchorBottom = Math.max(anchorBottom, Math.ceil(ar.top) + 40);
          }
        }
      } catch (eAnc) {}

      var seen = [];
      var pushCand = function (el) {
        if (!el || seen.indexOf(el) >= 0) return;
        if (el.closest && el.closest('#modal, #chatLines, #u20lines, #logBox, #u20log, #u20bar')) return;
        if (el.id === 'buffs' || el.id === 'buffBar' || el.id === 'topBuffs') return;
        if (!isVis(el)) return;
        var childImgs = el.querySelectorAll ? el.querySelectorAll('img').length : 0;
        if (childImgs > 1) return;
        var nestedSlots = el.querySelectorAll ? el.querySelectorAll('.buff, .buff-item, .buff-slot, [data-buff], [data-effect]').length : 0;
        if (nestedSlots > 0) return;
        seen.push(el);
      };

      var buffDom = document.querySelectorAll(
        '#buffs > *, #buffBar > *, #topBuffs > *, ' +
        '.buff, .buff-item, .buff-slot, [data-buff], [data-effect]'
      );
      for (var bi = 0; bi < buffDom.length; bi++) pushCand(buffDom[bi]);

      if (seen.length === 0) {
        var topNodes = document.querySelectorAll('div, span, a, img');
        var scanCap = Math.min(topNodes.length, 480);
        for (var ti = 0; ti < scanCap; ti++) {
          var tn = topNodes[ti];
          if (seen.indexOf(tn) >= 0) continue;
          if (!isVis(tn) || !tn.getBoundingClientRect) continue;
          if (tn.closest && tn.closest('#modal, #chatLines, #u20lines, #logBox, #u20log, #u20bar')) continue;
          var rect = tn.getBoundingClientRect();
          if (!rect || rect.bottom <= 0 || rect.top > anchorBottom || rect.left > anchorRight) continue;
          if (rect.width > 160 || rect.height > 160) continue;
          var hasImgOrTitle = !!(tn.tagName === 'IMG' || tn.getAttribute('title') || tn.getAttribute('aria-label') ||
            tn.getAttribute('data-buff') || tn.getAttribute('data-tip') || tn.getAttribute('data-tooltip') ||
            (tn.querySelector && tn.querySelector('img')));
          if (hasImgOrTitle) pushCand(tn);
        }
      }

      for (var si = 0; si < seen.length; si++) {
        var bEl = seen[si];
        var bTxt = (bEl.textContent || '').trim();
        if (isBuffTimerExpired(bTxt)) continue;
        var allowShort = !!(bEl.getAttribute && (bEl.getAttribute('data-buff') || bEl.getAttribute('data-k') || bEl.getAttribute('data-key')));
        var kind = classifyBuffKind(extractNodeMeta(bEl), allowShort);
        if (kind) {
          var parsedMs = parseBuffDurationMs(bTxt);
          if (parsedMs === 0) continue;
          recordBuff(kind, parsedMs);
        }
      }
    } catch (eScan) {}
    return found;
  }

  function matchConsumableRowText(rtNA, kind) {
    if (!rtNA || rtNA.length > 220) return false;
    if (kind === 'ttl') return rtNA.indexOf('tien thao lo') >= 0 && rtNA.indexOf('dac biet') < 0;
    if (kind === 'tt2') return rtNA.indexOf('tien thao lo dac biet') >= 0 || (rtNA.indexOf('tien thao') >= 0 && rtNA.indexOf('dac biet') >= 0);
    if (kind === 'qht') return rtNA.indexOf('que hoa tuu') >= 0 || rtNA.indexOf('que hoa') >= 0;
    return false;
  }

  function stripCountText(raw) {
    return noAccent(raw || '')
      .replace(/\(\s*\d+\s*(?:\/\s*\d+)?\s*\)/g, '')
      .replace(/\[\s*\d+\s*\]/g, '')
      .replace(/\s+\d+(?:\/\d+)?$/, '')
      .trim();
  }

  function matchConsumableTabBtn(root, subTabNames) {
    if (!root) return null;
    var tabs = root.querySelectorAll('#invTabs button, #bagTabs button, .bag-tabs button, .subtabs button, [role="tab"], button, a');
    for (var i = 0; i < tabs.length; i++) {
      if (!isVis(tabs[i]) || tabs[i].disabled) continue;
      var t = stripCountText(tabs[i].textContent || '');
      if (subTabNames.indexOf(t) >= 0) return tabs[i];
    }
    return null;
  }

  function collectHiddenBagTriggers() {
    var md = document.getElementById('modal');
    var out = [];
    var push = function (el) {
      if (!el || out.indexOf(el) >= 0) return;
      if (md && md.contains(el)) return;
      if (el.closest && el.closest('#chatLines, #u20lines, #logBox, #u20log, #chatBox')) return;
      if (!isVis(el) || el.disabled) return;
      out.push(el);
    };
    var bagIds = ['u20bag', 'bagBtn', 'tuiBtn', 'invBtn', 'bBag', 'btnBag'];
    for (var i = 0; i < bagIds.length; i++) push(document.getElementById(bagIds[i]));
    var attrEls = document.querySelectorAll(
      '[data-open="bag"], [data-tab="bag"], [data-tab="inv"], [data-tab="t-inv"], ' +
      '[data-tab*="inv" i], [data-tab*="bag" i], [data-t="inv"], [data-t="bag"], ' +
      '[data-nav="bag"], [data-act="bag"], [onclick*="inv" i], [onclick*="bag" i], ' +
      '[onclick*="tui" i], [title*="Hành trang" i], [aria-label*="Hành trang" i]'
    );
    for (var a = 0; a < attrEls.length; a++) push(attrEls[a]);
    var allBtns = document.querySelectorAll('button, [role="button"], [role="tab"], a, .btn, [onclick], #u20bar > *, #tabs > *');
    for (var ai = 0; ai < allBtns.length; ai++) {
      var bt = stripCountText(allBtns[ai].textContent || allBtns[ai].getAttribute('title') || allBtns[ai].getAttribute('aria-label') || '');
      if (bt === 'hanh trang' || bt === 'tui do' || bt === 'tui') push(allBtns[ai]);
    }
    var leafEls = document.querySelectorAll('div, span');
    for (var li = 0; li < leafEls.length; li++) {
      if (leafEls[li].children && leafEls[li].children.length > 3) continue;
      var lt = stripCountText(leafEls[li].textContent || '');
      if (lt === 'hanh trang' || lt === 'tui do') push(leafEls[li]);
    }
    return out;
  }

  async function openHiddenBagModal() {
    var md = document.getElementById('modal');
    var bagCands = collectHiddenBagTriggers();
    var bagBtn = bagCands.length > 0 ? bagCands[0] : null;
    var openedMoreBtn = null;
    if (!bagBtn) {
      var moreBtn = document.getElementById('u20more') || document.getElementById('moreBtn') || document.getElementById('btnMore');
      if (moreBtn && isVis(moreBtn) && !moreBtn.disabled) {
        openedMoreBtn = moreBtn;
        moreBtn.click();
        await sleep(200);
        bagCands = collectHiddenBagTriggers();
        bagBtn = bagCands.length > 0 ? bagCands[0] : null;
      }
    }
    if (!bagBtn) return null;

    var prevTabBtn = document.querySelector('#tabs .on, #tabs .active, #tabs [aria-selected="true"]');
    try { bagBtn.click(); } catch (eClick) { return null; }
    var waitEnd = Date.now() + 2200;
    while (Date.now() < waitEnd) {
      if (md && !md.classList.contains('hidden')) {
        await sleep(160);
        return { root: document.getElementById('mBody') || md, md: md, mode: 'modal', prevTabBtn: null, moreBtn: openedMoreBtn };
      }
      var paneIds = ['t-inv', 't-bag', 'p-inv', 'p-bag', 'invTab', 'bagTab', 'invPane', 'bagPane', 'invPanel', 'bagPanel'];
      for (var pi = 0; pi < paneIds.length; pi++) {
        var pe = document.getElementById(paneIds[pi]);
        if (pe && isVis(pe)) {
          await sleep(160);
          return { root: pe, md: md, mode: 'tab', prevTabBtn: prevTabBtn, moreBtn: openedMoreBtn };
        }
      }
      await sleep(80);
    }
    return null;
  }

  async function closeHiddenBagModal(ctx) {
    await closeModalCleanly(ctx && ctx.md ? ctx.md : document.getElementById('modal'));
    if (ctx && ctx.mode === 'tab' && ctx.prevTabBtn && ctx.prevTabBtn.isConnected && isVis(ctx.prevTabBtn)) {
      try { ctx.prevTabBtn.click(); } catch (e) {}
      await sleep(80);
    }
    if (ctx && ctx.moreBtn && ctx.moreBtn.isConnected) {
      try {
        var pop = document.querySelector('#u20pop, .u20-pop, .u20-more, .more-menu, [data-more-pop]');
        if (pop && isVis(pop)) ctx.moreBtn.click();
      } catch (e) {}
    }
  }

  function isBagSlotOrContentRendered(root) {
    if (!root) return false;
    var hasItemNodes = !!root.querySelector(
      '.qrow, .item, .bag-item, .inv-item, .item-slot, .eq-item, ' +
      '[data-bag], [data-inv], [data-iid], [data-slot], [data-item], #invTabs, #bagTabs, .bag-tabs'
    );
    if (hasItemNodes) return true;
    var t = noAccent(root.textContent || '');
    return t.indexOf('hanh trang trong') >= 0 ||
      t.indexOf('tui trong') >= 0 ||
      t.indexOf('khong co vat pham') >= 0 ||
      /hanh trang\s*\(\s*0\s*\//.test(t);
  }

  async function scanAndUseOneFromHiddenBagDom(kind) {
    // Tuyệt đối KHÔNG tự bấm mở tab Hành trang / Nhân vật / Võ công trên giao diện game.
    // Chỉ sử dụng kênh API ẩn (dungTui) ở ensureOneConsumable để tránh bật tab chức năng.
    return { scanned: true, used: false };
    if (!canRunHiddenConsumable()) return { scanned: false, used: false };

    lockModal(18000);
    stealthObj().enter();
    var ctx = null;
    try {
      ctx = await openHiddenBagModal();
      if (!ctx || !ctx.root) return { scanned: false, used: false };

      var readyEnd = Date.now() + 1400;
      var bagRendered = false;
      while (Date.now() < readyEnd) {
        if (isBagSlotOrContentRendered(ctx.root)) {
          bagRendered = true;
          break;
        }
        await sleep(80);
      }
      if (!bagRendered) {
        // Thử chờ thêm một nhịp ngắn nếu danh sách item vừa mới render text nhưng chưa có class
        var txtLen = (ctx.root.textContent || '').trim().length;
        if (txtLen > 24 && (ctx.root.querySelectorAll('button, tr, li').length > 0)) {
          bagRendered = true;
        } else {
          return { scanned: false, used: false };
        }
      }

      var isUseBtnLabel = function (bTxt) {
        if (!bTxt || bTxt.indexOf('het') >= 0 || bTxt.indexOf('tat ca') >= 0 || bTxt.indexOf('ban') >= 0 || bTxt.indexOf('bo') >= 0) return false;
        return bTxt === 'dung' || bTxt === 'dung 1' || bTxt === 'dung ngay' ||
          bTxt === 'su dung' || bTxt === 'su dung 1' || bTxt === 'an' || bTxt === 'uong' || bTxt === 'mo';
      };

      var findConfirmUseBtn = function (scopeEl) {
        var scopes = [
          scopeEl,
          document.getElementById('itemModal'),
          document.getElementById('detailModal'),
          document.getElementById('cfModal'),
          document.getElementById('confirmModal'),
          document.getElementById('cModal'),
          document.querySelector('[role="dialog"]'),
          document.getElementById('modal')
        ];
        for (var si = 0; si < scopes.length; si++) {
          var sc = scopes[si];
          if (!sc || !isVis(sc)) continue;
          var btns = sc.querySelectorAll('button, [role="button"], a');
          for (var j = 0; j < btns.length; j++) {
            if (!isVis(btns[j]) || btns[j].disabled) continue;
            var bTxt = noAccent(btns[j].textContent || '');
            if (isUseBtnLabel(bTxt)) return btns[j];
          }
        }
        return null;
      };

      var tabCands = [null, 'tat ca', 'vat pham', 'tieu hao', 'duoc pham', 'dao cu', 'khac'];
      var anyTabScanned = false;
      for (var tIdx = 0; tIdx < tabCands.length; tIdx++) {
        if (tabCands[tIdx]) {
          var tb = matchConsumableTabBtn(ctx.root, [tabCands[tIdx]]);
          if (!tb) continue;
          if (!tb.classList.contains('active') && !tb.classList.contains('on') && tb.getAttribute('aria-selected') !== 'true') {
            tb.click();
            await sleep(260);
          }
        }
        anyTabScanned = true;
        var rows = ctx.root.querySelectorAll('.qrow, .item, .bag-item, .inv-item, .item-slot, [data-bag], [data-inv], [data-iid], [data-slot], [data-item], tr, li, div');
        var matchedRow = null;
        var useBtn = null;
        for (var ri = 0; ri < rows.length; ri++) {
          var row = rows[ri];
          if (!isVis(row)) continue;
          var nested = row.querySelectorAll
            ? row.querySelectorAll('.qrow, .item, .bag-item, .inv-item, .item-slot, [data-bag], [data-inv], [data-iid], [data-slot], [data-item]').length
            : 0;
          if (nested > 0) continue;
          var rt = noAccent(extractNodeMeta(row));
          if (!matchConsumableRowText(rt, kind)) continue;
          matchedRow = row;
          var btns = row.querySelectorAll('button, [role="button"], a');
          for (var j = 0; j < btns.length; j++) {
            if (!isVis(btns[j]) || btns[j].disabled) continue;
            var bTxt = noAccent(btns[j].textContent || '');
            if (isUseBtnLabel(bTxt)) {
              useBtn = btns[j];
              break;
            }
          }
          if (useBtn) break;
        }

        if (!useBtn && matchedRow) {
          try { matchedRow.click(); } catch (eClick) {}
          var detailWaitEnd = Date.now() + 1200;
          while (Date.now() < detailWaitEnd) {
            await sleep(90);
            useBtn = findConfirmUseBtn(null);
            if (useBtn) break;
          }
        }

        if (useBtn && !useBtn.disabled) {
          useBtn.click();
          var postClickEnd = Date.now() + 900;
          var handledQtyPopup = false;
          while (Date.now() < postClickEnd) {
            await sleep(100);
            var inps = document.querySelectorAll(
              '#modal input:not([type="hidden"]):not([type="checkbox"]):not([type="radio"]), ' +
              '#cfModal input, #confirmModal input, #cModal input, .confirm input, [role="dialog"] input:not([type="hidden"])'
            );
            for (var p = 0; p < inps.length; p++) {
              if (isVis(inps[p]) && !inps[p].disabled && !inps[p].readOnly) {
                fillInp(inps[p], '1');
                await sleep(100);
                var okBtns = document.querySelectorAll(
                  '#modal button, #modal [role="button"], #cfModal button, #confirmModal button, ' +
                  '#cModal button, .confirm button, [role="dialog"] button'
                );
                for (var ob = 0; ob < okBtns.length; ob++) {
                  if (!isVis(okBtns[ob]) || okBtns[ob].disabled) continue;
                  var obt = noAccent(okBtns[ob].textContent || '');
                  if (isUseBtnLabel(obt) || obt === 'xac nhan' || obt === 'dong y' || obt === 'doi' || obt === 'ok') {
                    okBtns[ob].click();
                    handledQtyPopup = true;
                    await sleep(320);
                    break;
                  }
                }
                break;
              }
            }
            if (handledQtyPopup) break;
          }
          await sleep(220);
          return { scanned: true, used: true };
        }
      }
      return { scanned: anyTabScanned && bagRendered, used: false };
    } finally {
      await closeHiddenBagModal(ctx);
      stealthObj().leave();
      unlockModal();
    }
  }

  async function useOneBottleFromHiddenBagDom(kind) {
    var r = await scanAndUseOneFromHiddenBagDom(kind);
    return !!(r && r.used);
  }

  function findKtcBuyQtyInput(buyBtn) {
    var roots = [
      document.getElementById('cfModal'),
      document.getElementById('confirmModal'),
      document.getElementById('cModal'),
      document.querySelector('.confirm'),
      document.querySelector('[data-confirm]'),
      document.getElementById('mBody'),
      document.getElementById('modal'),
      document.body
    ];
    for (var r = 0; r < roots.length; r++) {
      var root = roots[r];
      if (!root || !isVis(root)) continue;
      var inps = root.querySelectorAll('input[type="number"], input[type="tel"], input[type="text"], input:not([type])');
      for (var i = inps.length - 1; i >= 0; i--) {
        var inp = inps[i];
        if (inp === buyBtn || inp.disabled || inp.readOnly || !isVis(inp)) continue;
        var id = (inp.id || '').toLowerCase();
        if (id === 'nau' || id === 'nap' || id === 'ngu' || id === 'ngp') continue;
        return inp;
      }
    }
    return null;
  }

  function findKtcBuyConfirmButton(qtyInp, buyBtn) {
    var scope = (qtyInp && qtyInp.closest ? qtyInp.closest('#cfModal, #confirmModal, #cModal, .confirm, [data-confirm], #mBody, #modal, .modal, .dialog, [role="dialog"], form, div') : null);
    var searchRoots = [
      scope,
      document.getElementById('cfModal'),
      document.getElementById('confirmModal'),
      document.getElementById('cModal'),
      document.querySelector('.confirm'),
      document.querySelector('[data-confirm]'),
      document.getElementById('modal'),
      document.body
    ];
    var candidates = [];
    for (var s = 0; s < searchRoots.length; s++) {
      var sr = searchRoots[s];
      if (!sr || !isVis(sr)) continue;
      var btns = sr.querySelectorAll('button, [role="button"], input[type="submit"], input[type="button"]');
      for (var i = 0; i < btns.length; i++) {
        var el = btns[i];
        if (el === buyBtn || el.disabled || !isVis(el)) continue;
        var t = noAccent(el.textContent || el.value || '');
        if (!t || t.length > 28) continue;
        if (t === 'huy' || t === 'dong' || t === 'dong lai' || t === 'bo qua' || t === 'khong') continue;
        var score = 0;
        if (t === 'mua') score = 220;
        else if (t.indexOf('mua') === 0) score = 160;
        else if (t === 'xac nhan' || t === 'dong y' || t === 'ok' || t === 'doi') score = 120;
        if (score > 0) candidates.push({ el: el, score: score });
      }
      if (candidates.length > 0) break;
    }
    candidates.sort(function (a, b) { return b.score - a.score; });
    return candidates.length > 0 ? candidates[0].el : null;
  }

  async function moBangKnbKhoa(md) {
    var mb = document.getElementById('mBody') || md;
    if (!mb) return false;
    var cands = mb.querySelectorAll('[data-kb], [role="tab"], button, a, .tab, div, span');
    var lockedTab = null;
    for (var i = 0; i < cands.length; i++) {
      var el = cands[i];
      if (!isVis(el) || el.disabled) continue;
      var t = stripCountText(el.textContent || '');
      if (!t || t.length > 36) continue;
      if (t === 'bang knb khoa' || t === 'knb khoa' || (t.indexOf('knb khoa') >= 0 && t.indexOf('doi') < 0 && t.indexOf('dung trong') < 0)) {
        lockedTab = el;
        break;
      }
    }
    if (lockedTab) {
      try { if (lockedTab.scrollIntoView) lockedTab.scrollIntoView({ inline: 'center', block: 'nearest' }); } catch (eSc) {}
      try { lockedTab.click(); } catch (e) {}
      await sleep(320);
      return true;
    }
    return false;
  }

  async function clickKtcSubTab(md, targetNames) {
    var mb = document.getElementById('mBody') || md;
    if (!mb) return false;
    var cands = mb.querySelectorAll('[data-kb], [data-ktc-sub], [data-sub], [role="tab"], button, a, .tab, .subtab');
    for (var i = 0; i < cands.length; i++) {
      var el = cands[i];
      if (!isVis(el) || el.disabled) continue;
      var t = stripCountText(el.textContent || '');
      if (!t || t.length > 32) continue;
      for (var k = 0; k < targetNames.length; k++) {
        if (t === targetNames[k] || t.indexOf(targetNames[k]) >= 0) {
          try { if (el.scrollIntoView) el.scrollIntoView({ inline: 'center', block: 'nearest' }); } catch (eSc) {}
          try { el.click(); } catch (eClk) {}
          await sleep(320);
          return true;
        }
      }
    }
    return false;
  }

  async function buyOneBottleFromHiddenKtcDom(kind) {
    if (!canRunHiddenConsumable()) return false;

    lockModal(20000);
    stealthObj().enter();
    try {
      var md = await moKtc();
      if (!md) return false;
      await sleep(220);

      // Đường dẫn chuẩn: Kỳ Trân Các -> Bảng KNB khóa -> vật phẩm -> Mua -> nhập số 1 -> ấn Mua
      await moBangKnbKhoa(md);

      var findRowBuyBtn = function () {
        var mb = document.getElementById('mBody') || md;
        var rows = mb.querySelectorAll('.kc, .shoprow, .qrow, .item, tr, li, div');
        for (var i = 0; i < rows.length; i++) {
          var row = rows[i];
          if (!isVis(row)) continue;
          var nested = row.querySelectorAll ? row.querySelectorAll('.kc, .shoprow, .qrow, .item').length : 0;
          if (nested > 0) continue;
          var rt = noAccent(extractNodeMeta(row));
          if (!matchConsumableRowText(rt, kind)) continue;
          var btns = row.querySelectorAll('button, [role="button"], a');
          for (var j = 0; j < btns.length; j++) {
            if (!isVis(btns[j]) || btns[j].disabled) continue;
            var bt = noAccent(btns[j].textContent || '');
            if (bt === 'mua' || bt.indexOf('mua') === 0 || bt === 'doi') return btns[j];
          }
        }
        return null;
      };

      var buyBtn = findRowBuyBtn();
      if (!buyBtn) {
        await clickKtcSubTab(md, ['bua & thuoc', 'bua va thuoc', 'thuoc', 'duoc pham', 'dac biet']);
        buyBtn = findRowBuyBtn();
      }
      if (!buyBtn) {
        var mbTabs = (document.getElementById('mBody') || md).querySelectorAll('[data-kb], [data-ktc-sub], [role="tab"], button, a');
        for (var ti = 0; ti < mbTabs.length; ti++) {
          var tb = mbTabs[ti];
          if (!isVis(tb) || tb.disabled) continue;
          var tTxt = noAccent(tb.textContent || '');
          if (tTxt.indexOf('bang kim nguyen bao') >= 0 || tTxt === 'mua' || tTxt === 'doi' || tTxt === 'huy') continue;
          try { if (tb.scrollIntoView) tb.scrollIntoView({ inline: 'center', block: 'nearest' }); } catch (eSc) {}
          try { tb.click(); } catch (e) {}
          await sleep(260);
          buyBtn = findRowBuyBtn();
          if (buyBtn) break;
        }
      }
      if (!buyBtn) return false;

      buyBtn.click();
      var qtyInp = null;
      var waitInp = Date.now() + 2000;
      while (Date.now() < waitInp) {
        qtyInp = findKtcBuyQtyInput(buyBtn);
        if (qtyInp) break;
        await sleep(90);
      }

      if (qtyInp) {
        fillInp(qtyInp, '1');
        await sleep(100);
        var confirmBuyBtn = findKtcBuyConfirmButton(qtyInp, buyBtn);
        if (!confirmBuyBtn) return false;
        confirmBuyBtn.click();
        var postEnd = Date.now() + 4500;
        var seenProcessing = false;
        while (Date.now() < postEnd) {
          await sleep(100);
          var dlgTxt = '';
          try {
            var dlg = (confirmBuyBtn.closest && confirmBuyBtn.closest('#cfModal, #confirmModal, #cModal, .confirm, [data-confirm], [role="dialog"], #modal')) || document.getElementById('modal');
            dlgTxt = noAccent(dlg ? dlg.textContent : '');
          } catch (eDlg) {}
          if (dlgTxt.indexOf('dang xu ly') >= 0) {
            seenProcessing = true;
            continue;
          }
          if (dlgTxt.indexOf('khong du') >= 0 || dlgTxt.indexOf('that bai') >= 0) {
            return false;
          }
          if (!findKtcBuyQtyInput(buyBtn)) {
            if (seenProcessing || Date.now() > postEnd - 3900) break;
          }
        }
      } else {
        await sleep(450);
      }
      return true;
    } finally {
      await closeModalCleanly(document.getElementById('modal'));
      stealthObj().leave();
      unlockModal();
    }
  }

  async function ensureOneConsumable(I, itemSpec) {
    var tui = (I && I.tui) ? I.tui : {};
    var used = false;
    var bagConfirmedEmpty = false;

    // 1. Quét / dùng trong Hành trang trước tiên
    for (var ki = 0; ki < itemSpec.tuiKeys.length; ki++) {
      var tk = itemSpec.tuiKeys[ki];
      if ((tui[tk] | 0) > 0) {
        var rTui = await api('dungTui', { k: tk, n: 1 });
        if (rTui && rTui.ok && rTui.n) {
          used = true;
          note('dùng 1 bình ' + itemSpec.label + ' (trong túi)');
          break;
        }
      }
    }

    if (!used) {
      if (!I.dead && canRunHiddenConsumable()) {
        var bagScanRes = await scanAndUseOneFromHiddenBagDom(itemSpec.kind);
        if (bagScanRes && bagScanRes.used) {
          used = true;
          note('dùng 1 bình ' + itemSpec.label + ' (ẩn trong túi)');
        } else if (bagScanRes && bagScanRes.scanned) {
          bagConfirmedEmpty = true;
        }
      }
    }

    // 2. Chỉ khi đã quét Hành trang xác nhận hết VÀ người dùng tích "túi hết thì mua" mới mua ẩn
    if (!used && bagConfirmedEmpty && itemSpec.allowBuy && canRunHiddenConsumable()) {
      var bought = await buyOneBottleFromHiddenKtcDom(itemSpec.kind);
      if (!bought && (itemSpec.ktcKey === 'tt' || itemSpec.ktcKey === 'ttsp')) {
        var ktcRes = await api('muaKtc', { k: itemSpec.ktcKey, n: 1 });
        if (ktcRes && ktcRes.ok && ktcRes.n) {
          bought = true;
          used = true;
        }
      }
      if (bought && !used) {
        await sleep(360);
        var I2 = await info();
        var hud2 = scanHudBuffs(I2 || I);
        if (hud2[itemSpec.kind]) {
          used = true;
        } else {
          for (var ki2 = 0; ki2 < itemSpec.tuiKeys.length; ki2++) {
            var tk2 = itemSpec.tuiKeys[ki2];
            var r2 = await api('dungTui', { k: tk2, n: 1 });
            if (r2 && r2.ok && r2.n) { used = true; break; }
          }
          if (!used) {
            var useAfterBuy = await scanAndUseOneFromHiddenBagDom(itemSpec.kind);
            used = !!(useAfterBuy && useAfterBuy.used);
          }
        }
      }
      if (bought && used) {
        note('mua ẩn (Bảng KNB khóa) và dùng 1 bình ' + itemSpec.label);
      } else if (bought && !used) {
        note('đã mua ẩn ' + itemSpec.label + ' — sẽ dùng ở nhịp kế tiếp');
      }
    }

    if (used && itemSpec.kind === 'qht') {
      dayAdd('qht', 1);
    }
    return { done: used || (bagConfirmedEmpty && !itemSpec.allowBuy), used: used };
  }

  async function tienThao(I, hudOpt) {
    var hud = hudOpt || scanHudBuffs(I);
    var allDone = true;
    var curI = I;
    if (A.cfg.ttl && !hud.ttl) {
      var r1 = await ensureOneConsumable(curI, {
        kind: 'ttl',
        label: 'Tiên Thảo Lộ',
        tuiKeys: ['tt', 'ttl'],
        ktcKey: 'tt',
        allowBuy: !!A.cfg.ttlMua
      });
      if (!r1 || !r1.done) allDone = false;
      curI = (await info()) || curI;
    }
    if (A.cfg.tt2 && !hud.tt2) {
      var r2 = await ensureOneConsumable(curI, {
        kind: 'tt2',
        label: 'Tiên Thảo Lộ Đặc Biệt',
        tuiKeys: ['ttsp', 'tt2'],
        ktcKey: 'ttsp',
        allowBuy: !!A.cfg.tt2Mua
      });
      if (!r2 || !r2.done) allDone = false;
    }
    return allDone;
  }

  async function queHoaTuu(I, hudOpt) {
    if (!A.cfg.qht) return true;
    var hud = hudOpt || scanHudBuffs(I);
    if (hud.qht) return true;
    var r = await ensureOneConsumable(I, {
      kind: 'qht',
      label: 'Quế Hoa Tửu',
      tuiKeys: ['qht', 'queHoaTuu', 'tuu', 'que_hoa_tuu', 'qhtuu'],
      ktcKey: 'qht',
      allowBuy: !!A.cfg.qhtMua
    });
    return !!(r && r.done);
  }

  function computeNextBuffCheckDelayMs(hud) {
    var minRem = BUFF_SCAN_MAX_MS;
    var checks = [
      { on: !!A.cfg.ttl, rem: (hud.rem && hud.rem.ttl) || 0 },
      { on: !!A.cfg.tt2, rem: (hud.rem && hud.rem.tt2) || 0 },
      { on: !!A.cfg.qht, rem: (hud.rem && hud.rem.qht) || 0 }
    ];
    for (var i = 0; i < checks.length; i++) {
      if (!checks[i].on) continue;
      if (checks[i].rem <= BUFF_RENEW_LEAD_MS) return 20000;
      var waitForExpiry = checks[i].rem - 10000;
      if (waitForExpiry < minRem) minRem = waitForExpiry;
    }
    return Math.max(20000, Math.min(BUFF_SCAN_MAX_MS, minRem));
  }

  async function suDungVatPhamTieuHao(I) {
    if (!A.cfg.ttl && !A.cfg.tt2 && !A.cfg.qht) return;
    if ((A.cool.buff_scan_15m || 0) > Date.now()) return;
    var hud = scanHudBuffs(I);
    var ttlExpiring = A.cfg.ttl && (!hud.ttl || ((hud.rem && hud.rem.ttl) || 0) <= BUFF_RENEW_LEAD_MS);
    var tt2Expiring = A.cfg.tt2 && (!hud.tt2 || ((hud.rem && hud.rem.tt2) || 0) <= BUFF_RENEW_LEAD_MS);
    var qhtExpiring = A.cfg.qht && (!hud.qht || ((hud.rem && hud.rem.qht) || 0) <= BUFF_RENEW_LEAD_MS);

    if (!ttlExpiring && !tt2Expiring && !qhtExpiring) {
      // Căn chính xác theo thời gian còn lại của vật phẩm sắp hết nhất
      A.cool.buff_scan_15m = Date.now() + computeNextBuffCheckDelayMs(hud);
      return;
    }
    if (!canRunHiddenConsumable()) return;
    // Đánh dấu hết hạn nếu còn <= 15s để dùng bù ngay lập tức
    var hudForUse = {
      ttl: !ttlExpiring && hud.ttl,
      tt2: !tt2Expiring && hud.tt2,
      qht: !qhtExpiring && hud.qht
    };
    var d1 = await tienThao(I, hudForUse);
    if (!A.cfg.on) return;
    var Icur = (await info()) || I;
    var hudCur = scanHudBuffs(Icur);
    var hudCurForUse = {
      ttl: hudCur.ttl,
      tt2: hudCur.tt2,
      qht: !qhtExpiring && hudCur.qht
    };
    var d2 = await queHoaTuu(Icur, hudCurForUse);
    if (d1 && d2) {
      var Ifinal = (await info()) || Icur;
      var hudFinal = scanHudBuffs(Ifinal);
      A.cool.buff_scan_15m = Date.now() + computeNextBuffCheckDelayMs(hudFinal);
    }
  }

  async function qua(I) {
    if (!A.cfg.qua) return;
    var q = I.qua || {};
    if (!(q.login || q.lvms)) return;
    if (!ready('qua', 20000)) return;

    var gotLogin = false;
    var gotLv = 0;
    for (var attempt = 0; attempt < 2; attempt++) {
      var r = await api('qua', {}, 15000);
      if (r && r.ok) {
        if (r.login) gotLogin = true;
        if (r.lv) gotLv += (r.lv | 0);
      }
      var I2 = await info();
      if (!I2 || !I2.qua || !(I2.qua.login || I2.qua.lvms)) break;
      await sleep(350);
    }
    if (gotLogin || gotLv > 0) {
      var ds = [];
      if (gotLogin) ds.push('điểm danh');
      if (gotLv > 0) ds.push(gotLv + ' mốc cấp');
      note('nhận quà trọn vẹn: ' + ds.join(', '));
    }
  }

  function need(I) {
    var dt = I.dt || {};
    if (dt.du) return 0;
    return Math.max(0, (dt.con | 0) - (I.ldt | 0));
  }

  async function k2k(knbk) {
    var con = Math.max(0, (A.cfg.k2k | 0) - dayGet('k2k'));
    if (!con || knbk <= 0) return 0;
    var n = Math.min(con, Math.max(10, knbk));
    var r = await api('k2k', { n: n });
    if (r && r.ok && r.n > 0) {
      dayAdd('k2k', r.n);
      note('đổi ' + r.n + ' KNB → KNB khóa');
      return r.n;
    }
    return 0;
  }

  async function daTau(I) {
    if (!A.cfg.dt || (I.lvl | 0) < 20 || need(I) <= 0 || !ready('ldt', 45000)) return;

    if (A.cfg.ldtMua) {
      var got = 0, doi = false;
      for (var i = 0; i < 3; i++) {
        var n = Math.min(need(I), Math.max(0, (I.ldtMax | 0) - (I.ldtMua | 0)));
        if (n <= 0) break;
        var r = await api('muaKtc', { k: 'ldt', n: n });
        var I2 = await info();
        if (I2) I = I2;
        if (r && r.ok && r.n > 0) got += r.n;
        if (r && r.ok && r.n >= n) break;
        if (doi || !(A.cfg.k2k > 0)) break;
        doi = true;
        var gia = (I.gia && I.gia.ldt) || 20;
        var can = (n - (r.n | 0)) * gia - (I.knbk | 0);
        if (!await k2k(can)) break;
        var I3 = await info();
        if (I3) I = I3;
      }
      if (got) note('mua ' + got + ' Lệnh Bài Dã Tẩu (KNB khóa)');
    }

    if (A.cfg.pd && need(I) > 0) {
      var p = I.pdl || {};
      if (p && ((p.cho | 0) >= 10 || (p.l && p.l.some(function (n) { return n > 0; })))) {
        var r4 = await api('pdl');
        if (r4 && r4.ok && (r4.dong || r4.fd)) {
          note('bình Phúc Duyên Lộ: ' + (r4.dong ? 'đóng hết, ' : '') + 'dùng hết (+' + (r4.fd | 0) + ' Phúc Duyên)');
        }
        var I4 = await info();
        if (I4) I = I4;
      }
      var mo = 0;
      for (var j = 0; j < 10 && need(I) > 0 && (I.fd | 0) >= 10; j++) {
        var r5 = await api('ruong', { n: Math.min(10, Math.floor((I.fd | 0) / 10)) });
        if (!r5 || !r5.ok || !r5.n) break;
        mo += r5.n;
        var I5 = await info();
        if (!I5) break;
        I = I5;
      }
      if (mo) note('mở ' + mo + ' Rương Phúc Duyên (lấy Lệnh Bài Dã Tẩu)');
    }
  }

  async function muaLb(I) {
    var cap = A.cfg.lbMua | 0;
    if (!A.cfg.st || cap <= 0 || (I.lb | 0) > 0 || (I.lvl | 0) < 20 || !ready('lb', 3 * 60000)) return;
    var n = Math.min(10, cap - dayGet('lb'), Math.max(0, (I.lbMax | 0) - (I.lbMua | 0)));
    if (n <= 0) return;
    var r = await api('muaLb', { n: n });
    if (r && r.ok && r.n > 0) {
      dayAdd('lb', r.n);
      note('mua ' + r.n + ' Lệnh Bài Sát Thủ (' + (r.n * 100) + ' KNB)');
    }
  }

  async function doiNguyenLieuThua(I) {
    var items = A.cfg.doiNguyenLieu || [];
    if (items.length === 0) return;

    var canChay = function () {
      if (!A.cfg.on || !ranh()) return false;
      if (A.st && (A.st.o !== 'game' || Date.now() - A.stT >= 90000)) return false;
      if (A.st && ['phoban', 'bosstuan', 'thap'].indexOf(A.st.noi) >= 0) return false;
      if (I.dg || I.tower || I.sat || I.dead) return false;
      return true;
    };
    if (!canChay() || (A.cool.evHongBao > Date.now()) || isModalLocked()) return;

    var md = document.getElementById('modal');
    var gift = document.getElementById('giftBtn');
    if (!gift || !isVis(gift)) {
      var gCands = document.querySelectorAll('[data-open="gift"], [data-act="gift"], [title*="Phần thưởng" i], [aria-label*="Phần thưởng" i], button, [role="button"], .btn');
      for (var gi = 0; gi < gCands.length; gi++) {
        var gc = gCands[gi];
        if (!isVis(gc) || gc.disabled || (md && md.contains(gc))) continue;
        var gt = noAccent(gc.textContent || gc.getAttribute('title') || gc.getAttribute('aria-label') || '');
        if (gt === 'phan thuong' || (gt.indexOf('phan thuong') >= 0 && gt.length <= 22)) {
          gift = gc;
          break;
        }
      }
    }
    if (!md || !md.classList.contains('hidden') || !gift || gift.disabled) return;
    if (!ready('evHongBao', 45000)) return;

    var giftMo = function () {
      return !md.classList.contains('hidden') && document.querySelector('#mBody #giftTabs');
    };

    var tenCuaHang = function (row) {
      var span = row ? row.querySelector('span') : null;
      if (!span) return '';
      var txt = span.textContent || '';
      var small = span.querySelector('small');
      if (small && small.textContent) txt = txt.split(small.textContent).join(' ');
      return txt.replace(/^\s*\d+\s+/, '').replace(/\s+/g, ' ').trim();
    };

    var nutTheoTen = function (ten) {
      if (!giftMo()) return null;
      var want = noAccent(ten);
      var rows = document.querySelectorAll('#mBody .qrow');
      for (var i = 0; i < rows.length; i++) {
        var b = rows[i].querySelector('button[data-ng^="doi:"]');
        if (!b) continue;
        if (noAccent(tenCuaHang(rows[i])) === want) return b;
      }
      return null;
    };

    var dem = function (b) {
      var row = b && b.closest ? b.closest('.qrow') : null;
      var s = row ? row.querySelector('small') : null;
      if (!s) return null;
      var m = /(\d+)\s*\/\s*(\d+)\s+hôm nay/i.exec(s.textContent || '');
      if (!m) return null;
      var n = Number(m[1]), mx = Number(m[2]);
      if (!isFinite(n) || !isFinite(mx)) return null;
      if (n !== Math.floor(n) || mx !== Math.floor(mx)) return null;
      if (n < 0 || n > mx || mx > 1000) return null;
      return { n: n, mx: mx };
    };

    var anyNut = function () {
      for (var i = 0; i < items.length; i++) {
        var b = nutTheoTen(items[i]);
        if (b) return true;
      }
      return false;
    };

    var cho = async function (tim) {
      var end = Date.now() + 3000;
      while (canChay() && !tim() && Date.now() < end) await sleep(100);
      return canChay() && tim();
    };

    if (!canChay()) return;
    lockModal(25000);
    stealthObj().enter();
    try {
      gift.click();
      if (!await cho(giftMo)) { A.cool.evHongBao = Date.now() + 2 * 60000; return; }
      var tab = document.querySelector('#mBody #giftTabs button[data-g="ng"]');
      if (!tab || tab.disabled) { A.cool.evHongBao = Date.now() + 5 * 60000; return; }
      if (!canChay()) return;
      tab.click();
      if (!await cho(anyNut)) {
        A.cool.evHongBao = Date.now() + 3 * 60000;
        return;
      }

      for (var q = 0; q < items.length; q++) {
        var ten = items[q];
        if (!canChay()) break;

        var b = nutTheoTen(ten);
        var info0 = dem(b);
        if (!b || info0 === null) continue;

        var count = 0;
        while (b && !b.disabled && canChay() && count < 500) {
          var d0 = dem(b);
          if (d0 === null || d0.n >= d0.mx) break;
          var t0 = d0.n;
          b.click();

          var endWait = Date.now() + 4500;
          var confirmed = false;
          while (Date.now() < endWait && canChay()) {
            await sleep(100);
            b = nutTheoTen(ten);
            var d1 = dem(b);
            if (d1 !== null && d1.n > t0) {
              confirmed = true;
              count += (d1.n - t0);
              break;
            }
            if (!b || b.disabled) {
              await sleep(250);
              b = nutTheoTen(ten);
              var d2 = dem(b);
              if (d2 !== null && d2.n > t0) {
                confirmed = true;
                count += (d2.n - t0);
              }
              break;
            }
          }
          if (!confirmed) {
            if (!b || b.disabled) break;
            A.cool.evHongBao = Date.now() + 2 * 60000;
            note('sự kiện: chưa xác nhận đổi ' + ten + ', thử lại sau');
            break;
          }
          await sleep(120);
        }
        if (count > 0) {
          note('đã đổi ẩn trọn vẹn ' + count + ' ' + ten + ' ở Phần thưởng');
        }
      }
    } finally {
      await closeModalCleanly(md);
      stealthObj().leave();
      unlockModal();
    }
  }

  var KNB_GIA = 1e4, KNB_NGAY = 1000;
  var knbGhiLoi = false;

  function dongBoKnb(totalToday) {
    if (knbGhiLoi) return false;
    if (daySet('knb', Math.min(KNB_NGAY, totalToday))) return true;
    knbGhiLoi = true;
    note('không ghi được bộ đếm ngày — dừng mua KNB');
    return false;
  }

  var demKnb = function () { return Math.min(KNB_NGAY, dayGet('knb')); };
  var sanKnb = function () { return Math.max(0, A.cfg.knbGiu | 0) + (A.cfg.knbGiu > 0 ? KNB_GIA : 0); };

  async function choViMoi(t) {
    var end = Date.now() + 3200;
    while (A.stT <= t && Date.now() < end) await sleep(100);
    if (A.stT > t && Date.now() - A.stT <= 3000) return true;
    return false;
  }

  function viKnb(I) {
    var l = (A.st && typeof A.st.luong === 'number') ? A.st.luong :
      (I && typeof I.luong === 'number' ? I.luong : null);
    return (typeof l === 'number' && isFinite(l) && l >= 0) ? Math.floor(l) * KNB_GIA : null;
  }

  async function moKtc() {
    var md = document.getElementById('modal');
    if (!md) return null;
    if (!md.classList.contains('hidden')) {
      if (document.querySelector('#mBody [data-ktc="knb"], #mBody [data-kb]')) return md;
      var curTxt = noAccent(md.textContent || '');
      if (curTxt.indexOf('ky tran cac') >= 0 || curTxt.indexOf('knb khoa') >= 0) return md;
      return null;
    }
    var ids = ['ktcBtn', 'bKtcT', 'u20ktc'];
    var nut = null;
    for (var i = 0; i < ids.length; i++) {
      var el = document.getElementById(ids[i]);
      if (el && isVis(el) && !el.disabled) { nut = el; break; }
    }
    if (!nut) {
      var attrKtc = document.querySelectorAll(
        '[data-act="ktc"], [data-open="ktc"], [onclick*="ktc" i], ' +
        '[title*="Kỳ Trân Các" i], [aria-label*="Kỳ Trân Các" i]'
      );
      for (var ak = 0; ak < attrKtc.length; ak++) {
        if (isVis(attrKtc[ak]) && !attrKtc[ak].disabled && (!md || !md.contains(attrKtc[ak]))) {
          nut = attrKtc[ak];
          break;
        }
      }
    }
    if (!nut) {
      var btns = document.querySelectorAll('button, [role="button"], a, .btn');
      for (var j = 0; j < btns.length; j++) {
        if (!isVis(btns[j]) || btns[j].disabled) continue;
        if (md && md.contains(btns[j])) continue;
        var bt = noAccent(btns[j].textContent || btns[j].getAttribute('title') || btns[j].getAttribute('aria-label') || '');
        if (bt === 'ky tran cac' || (bt.indexOf('ky tran cac') >= 0 && bt.length <= 24)) {
          nut = btns[j];
          break;
        }
      }
    }
    if (!nut) return null;
    try { nut.click(); } catch (e) { return null; }
    var end = Date.now() + 2000;
    while (md.classList.contains('hidden') && Date.now() < end) await sleep(100);
    if (!md.classList.contains('hidden')) {
      var mbEnd = Date.now() + 1200;
      while (Date.now() < mbEnd) {
        var mb = document.getElementById('mBody') || md;
        if ((mb.textContent || '').trim().length > 0 || mb.children.length > 0) break;
        await sleep(80);
      }
      return md;
    }
    if (ready('knbMo', 10 * 60000)) note('Kỳ Trân Các: không mở được bảng');
    return null;
  }

  function dongKtc(md, moAt) {
    var dungBang = function () {
      return document.querySelector('#mBody [data-ktc="knb"], #mBody [data-kb], #mBody');
    };
    if (!modalCuaTa(md, moAt, dungBang)) return;
    var x = document.getElementById('mClose');
    if (x) { try { x.click(); } catch (e) {} }
    if (!modalCuaTa(md, moAt, dungBang)) return;
    try { md.click(); } catch (e) {}
  }

  function timNutMuaKnbDom() {
    var mb = document.getElementById('mBody');
    if (!mb) return null;
    var direct = mb.querySelector('[data-ktc="knb"]');
    if (direct && isVis(direct)) return direct;
    var rows = mb.querySelectorAll('.kc, .shoprow, .qrow, .item, tr, div');
    for (var i = 0; i < rows.length; i++) {
      var row = rows[i];
      if (!isVis(row)) continue;
      var rt = noAccent(row.textContent || '');
      if (rt.indexOf('kim nguyen bao') < 0 || rt.length > 220) continue;
      if (rt.indexOf('knb khoa') >= 0 && rt.indexOf('doi knb') >= 0) continue;
      var btns = row.querySelectorAll('button, [role="button"]');
      for (var j = 0; j < btns.length; j++) {
        if (!isVis(btns[j])) continue;
        var bt = noAccent(btns[j].textContent || '');
        if (bt === 'mua' || bt.indexOf('mua') === 0) return btns[j];
      }
    }
    return direct;
  }

  function nutKnb() {
    var mb = document.getElementById('mBody');
    if (!mb) return null;
    var found = timNutMuaKnbDom();
    if (found) return found;
    var ts = mb.querySelectorAll('[data-kb]');
    for (var i = 0; i < ts.length; i++) {
      if (/Kim Nguyên Bảo/i.test(ts[i].textContent || '')) {
        try { ts[i].click(); } catch (e) {}
        break;
      }
    }
    return timNutMuaKnbDom();
  }

  async function moBangKnb() {
    var existing = timNutMuaKnbDom();
    if (existing) return existing;
    var mb = document.getElementById('mBody');
    if (!mb) return null;

    var tab = null;
    var kbTabs = mb.querySelectorAll('[data-kb]');
    for (var i = 0; i < kbTabs.length; i++) {
      var t = noAccent(kbTabs[i].textContent || '');
      if (t.indexOf('kim nguyen bao') >= 0) { tab = kbTabs[i]; break; }
    }
    if (!tab) {
      var allTabs = mb.querySelectorAll('button, [role="tab"], [role="button"], a, div, span');
      for (var j = 0; j < allTabs.length; j++) {
        if (!isVis(allTabs[j])) continue;
        var t2 = noAccent(allTabs[j].textContent || '');
        if (t2 === 'bang kim nguyen bao' || (t2.indexOf('kim nguyen bao') >= 0 && t2.length <= 28)) {
          tab = allTabs[j];
          break;
        }
      }
    }
    if (tab) {
      try { tab.click(); } catch (e) {}
      var end = Date.now() + 1600;
      while (Date.now() < end) {
        await sleep(100);
        var btn = timNutMuaKnbDom();
        if (btn) return btn;
      }
    }
    return timNutMuaKnbDom();
  }

  function ngayKnb(b) {
    var row = b && b.closest ? b.closest('.kc, .shoprow, .qrow, .item, tr') : null;
    var s = row && (row.querySelector('.kc-s') || row.querySelector('small'));
    var text = (s && s.textContent) || (row && row.textContent) || '';
    var m = /Hôm nay đã đổi\s+(\d+)(?![\d.])/i.exec(text) || /(?:^|[^\d.\-+])(\d+)\s*\/\s*1000\s+hôm nay/i.exec(text) || /(\d+)\s*\/\s*1000/i.exec(text);
    var n = m ? Number(m[1]) : NaN;
    return (isFinite(n) && n === Math.floor(n) && n >= 0 && n <= KNB_NGAY) ? n : null;
  }

  function khopNgayKnb(b) {
    var n = ngayKnb(b);
    if (n === null) return true;
    return dongBoKnb(n);
  }

  function findKnbQtyInput(buyBtn) {
    var roots = [document.getElementById('mBody'), document.getElementById('modal'), document.body];
    for (var r = 0; r < roots.length; r++) {
      var root = roots[r];
      if (!root) continue;
      var inps = root.querySelectorAll('input[type="number"], input[type="tel"], input[type="text"], input:not([type])');
      for (var i = inps.length - 1; i >= 0; i--) {
        var inp = inps[i];
        if (inp === buyBtn || inp.disabled || inp.readOnly || !isVis(inp)) continue;
        var id = (inp.id || '').toLowerCase();
        if (id === 'nau' || id === 'nap' || id === 'ngu' || id === 'ngp') continue;
        return inp;
      }
    }
    return null;
  }

  function findKnbConfirmButton(qtyInp, buyBtn) {
    var scope = (qtyInp && qtyInp.closest ? qtyInp.closest('#mBody, #modal, .modal, .dialog, [role="dialog"], form, div') : null) ||
      document.getElementById('modal') || document.body;
    var candidates = [];
    var searchRoots = [scope, document.getElementById('modal'), document.body];
    for (var s = 0; s < searchRoots.length; s++) {
      var sr = searchRoots[s];
      if (!sr) continue;
      var btns = sr.querySelectorAll('button, [role="button"], input[type="submit"], input[type="button"]');
      for (var i = 0; i < btns.length; i++) {
        var el = btns[i];
        if (el === buyBtn || el.disabled || !isVis(el)) continue;
        var t = noAccent(el.textContent || el.value || '');
        if (!t || t.length > 25) continue;
        if (t === 'huy' || t === 'dong' || t === 'bo qua' || t === 'mua') continue;
        var score = 0;
        if (t === 'doi') score = 200;
        else if (t === 'xac nhan' || t === 'dong y' || t === 'ok') score = 140;
        else if (t.indexOf('doi') === 0) score = 100;
        if (score > 0) candidates.push({ el: el, score: score });
      }
      if (candidates.length > 0) break;
    }
    candidates.sort(function (a, b) { return b.score - a.score; });
    return candidates.length > 0 ? candidates[0].el : null;
  }

  async function bamKnb(n, k0) {
    if (Date.now() - A.stT > 3000 && !await choViMoi(A.stT)) {
      await info();
    }
    var md = document.getElementById('modal');
    if (!md || md.classList.contains('hidden')) return 0;

    var mot = await moBangKnb();
    if (!mot || mot.disabled) return 0;
    if (!khopNgayKnb(mot)) return 0;

    var truocRaw = ngayKnb(mot);
    var truoc = truocRaw !== null ? truocRaw : demKnb();
    var vi = viKnb(A.info);
    if (vi === null) {
      var Iinf = await info();
      vi = viKnb(Iinf);
    }
    if (vi === null) return 0;

    var targetBuy = Math.min(n, (A.cfg.knbMua | 0) - truoc, KNB_NGAY - truoc,
      Math.floor((vi - sanKnb()) / KNB_GIA));
    if (targetBuy <= 0) return 0;

    // 1. Ấn nút Mua -> chờ bảng điền số KNB (mặc định tối đa 1000/ngày) rồi ấn Đổi
    var stAt = A.stT;
    try { mot.click(); } catch (e) { return 0; }

    var qtyInp = null;
    var waitInpEnd = Date.now() + 1800;
    while (Date.now() < waitInpEnd) {
      qtyInp = findKnbQtyInput(mot);
      if (qtyInp) break;
      await sleep(100);
    }

    if (qtyInp) {
      if (!A.cfg.on || !ranh()) return 0;
      fillInp(qtyInp, String(targetBuy));
      await sleep(120);
      var doiBtn = findKnbConfirmButton(qtyInp, mot);
      if (doiBtn) {
        try { doiBtn.click(); } catch (e) {}
        var endWait = Date.now() + 5000;
        var confirmed = false, sau = null, got = 0;
        while (Date.now() < endWait) {
          if (!A.cfg.on || !ranh()) break;
          await sleep(140);
          var nBtn = timNutMuaKnbDom();
          sau = ngayKnb(nBtn);
          if (sau !== null && sau > truoc) {
            got = sau - truoc;
            confirmed = true;
            break;
          }
          if (!findKnbQtyInput(mot)) {
            var Ichk = await info();
            var kNow = (Ichk && typeof Ichk.knb === 'number') ? (Ichk.knb | 0) : k0;
            if (kNow > k0) {
              got = Math.min(targetBuy, kNow - k0);
              confirmed = true;
              break;
            }
          }
        }
        if (confirmed && got > 0) {
          var nextDayCount = (sau !== null) ? sau : (truoc + got);
          dongBoKnb(nextDayCount);
          return got;
        }
        note('KNB: đã điền ' + targetBuy + ' và bấm Đổi nhưng chưa xác nhận — thử lại sau');
        return 0;
      }
    }

    // 2. Dự phòng: Nếu không mở ô nhập số lượng mà giao dịch trực tiếp hoặc nút 100/10/1
    var afterClickCount = ngayKnb(nutKnb());
    if (afterClickCount !== null && afterClickCount > truoc) {
      dongBoKnb(afterClickCount);
      return afterClickCount - truoc;
    }

    var du = n, gotLegacy = 0;
    for (var i = 0; i < 300 && du > 0; i++) {
      if (!A.cfg.on || !(A.cfg.knbMua > 0) || !ranh()) break;
      if (A.st && (A.st.o !== 'game' || Date.now() - A.stT >= 90000)) break;
      if (A.st && ['phoban', 'bosstuan', 'thap'].indexOf(A.st.noi) >= 0) break;
      md = document.getElementById('modal');
      if (!md || md.classList.contains('hidden')) break;
      mot = nutKnb();
      if (!mot || !khopNgayKnb(mot)) break;
      var tCur = ngayKnb(mot);
      if (tCur === null) break;
      var viCur = viKnb(A.info);
      if (viCur === null) break;
      du = Math.min(n - gotLegacy, (A.cfg.knbMua | 0) - tCur, KNB_NGAY - tCur,
        Math.floor((viCur - sanKnb()) / KNB_GIA));
      if (du <= 0 || mot.disabled) break;
      var b100 = document.querySelector('#mBody [data-ktc100="knb"]');
      var b10 = document.querySelector('#mBody [data-ktc10="knb"]');
      var nut = null;
      if (du >= 100 && b100 && !b100.disabled) nut = b100;
      else if (du >= 10 && b10 && !b10.disabled) nut = b10;
      else if (!mot.disabled) nut = mot;
      if (!nut) break;

      stAt = A.stT;
      try { nut.click(); } catch (e) { break; }
      var endL = Date.now() + 5500;
      var xongL = false, sauL = null;
      while (Date.now() < endL) {
        if (!A.cfg.on || !ranh()) break;
        sauL = ngayKnb(nutKnb());
        if (sauL !== null && sauL > tCur) { xongL = true; break; }
        await sleep(100);
      }
      if (!xongL) {
        note('KNB: chưa xác nhận giao dịch — thử lại sau');
        break;
      }
      if (!dongBoKnb(sauL)) break;
      var delta = Math.max(0, sauL - tCur);
      gotLegacy += delta;
      du -= delta;
      if (A.stT <= stAt) await choViMoi(stAt);
    }
    return gotLegacy;
  }

  function findTheGiaoDichBuyButton(root) {
    if (!root) return null;
    var rows = root.querySelectorAll('.kc, .shoprow, .qrow, .item, tr, li, div');
    for (var i = 0; i < rows.length; i++) {
      var row = rows[i];
      if (!isVis(row)) continue;
      var nested = row.querySelectorAll ? row.querySelectorAll('.kc, .shoprow, .qrow, .item').length : 0;
      if (nested > 0) continue;
      var rt = noAccent(extractNodeMeta(row));
      if (!rt || rt.length > 220) continue;
      if (rt.indexOf('the giao dich') < 0) continue;
      // Bỏ qua thanh trạng thái buff ở phía trên (không có nút Mua)
      var btns = row.querySelectorAll('button, [role="button"], a');
      for (var j = 0; j < btns.length; j++) {
        if (!isVis(btns[j]) || btns[j].disabled) continue;
        var bt = noAccent(btns[j].textContent || '');
        if (bt === 'mua' || bt.indexOf('mua') === 0) return btns[j];
      }
    }
    return null;
  }

  async function muaTheGiaoDich(I) {
    if (!A.cfg.on || !A.cfg.theGiaoDich) return;
    if (dayGet('theGd') >= 1) return;
    if (I && I.dead) return;
    if (!canRunHiddenConsumable()) return;
    if (!ready('theGd', 35000)) return;

    var knbkBefore = (I && typeof I.knbk === 'number') ? (I.knbk | 0) : null;
    lockModal(28000);
    stealthObj().enter();
    var bought = false;
    var alreadyBoughtToday = false;
    var errMsg = '';
    try {
      var md = await moKtc();
      if (!md) return;
      await sleep(220);

      // 1. Kỳ Trân Các -> Bảng KNB khóa -> tab Giao dịch -> Thẻ Giao Dịch -> Mua -> nhập 1 -> Mua
      await moBangKnbKhoa(md);
      var buyBtn = findTheGiaoDichBuyButton(document.getElementById('mBody') || md);
      if (!buyBtn) {
        await clickKtcSubTab(md, ['giao dich']);
        var waitGdEnd = Date.now() + 1200;
        while (Date.now() < waitGdEnd) {
          buyBtn = findTheGiaoDichBuyButton(document.getElementById('mBody') || md);
          if (buyBtn) break;
          await sleep(90);
        }
      }
      if (!buyBtn) {
        var mbTabs = (document.getElementById('mBody') || md).querySelectorAll('[data-kb], [data-ktc-sub], [role="tab"], button, a');
        for (var ti = 0; ti < mbTabs.length; ti++) {
          var tb = mbTabs[ti];
          if (!isVis(tb) || tb.disabled) continue;
          var tTxt = stripCountText(tb.textContent || '');
          if (tTxt.indexOf('giao dich') >= 0) {
            try { if (tb.scrollIntoView) tb.scrollIntoView({ inline: 'center', block: 'nearest' }); } catch (eSc) {}
            try { tb.click(); } catch (eClickTab) {}
            await sleep(320);
            buyBtn = findTheGiaoDichBuyButton(document.getElementById('mBody') || md);
            if (buyBtn) break;
          }
        }
      }
      if (!buyBtn) return;

      buyBtn.click();
      var qtyInp = null;
      var waitInp = Date.now() + 2200;
      while (Date.now() < waitInp) {
        qtyInp = findKtcBuyQtyInput(buyBtn);
        if (qtyInp) break;
        await sleep(90);
      }

      if (qtyInp) {
        fillInp(qtyInp, '1');
        await sleep(100);
        var confirmBuyBtn = findKtcBuyConfirmButton(qtyInp, buyBtn);
        if (!confirmBuyBtn) return;
        confirmBuyBtn.click();

        var postEnd = Date.now() + 5000;
        var seenProcessing = false;
        while (Date.now() < postEnd) {
          await sleep(110);
          var dlgTxt = '';
          try {
            var dlg = (confirmBuyBtn.closest && confirmBuyBtn.closest('#cfModal, #confirmModal, #cModal, .confirm, [data-confirm], [role="dialog"], #modal')) || document.getElementById('modal');
            dlgTxt = noAccent(dlg ? dlg.textContent : '');
          } catch (eDlg) {}
          if (dlgTxt.indexOf('dang xu ly') >= 0) {
            seenProcessing = true;
            continue;
          }
          if (dlgTxt.indexOf('da mua') >= 0 || dlgTxt.indexOf('het luot') >= 0 || dlgTxt.indexOf('toi da') >= 0) {
            alreadyBoughtToday = true;
            break;
          }
          if (dlgTxt.indexOf('khong du') >= 0 || dlgTxt.indexOf('thieu') >= 0) {
            errMsg = 'chưa đủ 200 KNB khóa để mua Thẻ Giao Dịch';
            break;
          }
          if (!findKtcBuyQtyInput(buyBtn)) {
            if (seenProcessing || Date.now() > postEnd - 4400) {
              bought = true;
              break;
            }
          }
        }
      } else {
        await sleep(600);
        bought = true;
      }
    } finally {
      await closeModalCleanly(document.getElementById('modal'));
      stealthObj().leave();
      unlockModal();
    }

    if (alreadyBoughtToday) {
      daySet('theGd', 1);
      note('Thẻ Giao Dịch hôm nay đã mua trước đó — mở khóa mua KNB');
      return;
    }

    if (errMsg) {
      note(errMsg + ' (tạm hoãn mua KNB bằng tiền vạn)');
      return;
    }

    if (bought) {
      var I2 = await info();
      if (I2 && typeof I2.knbk === 'number' && knbkBefore !== null && (I2.knbk | 0) > knbkBefore - 200) {
        // Nếu server từ chối âm thầm khiến KNB khóa không giảm nhưng không báo lỗi, vẫn kiểm tra thêm nếu đã đổi thành công
        if ((I2.knbk | 0) < 200) {
          note('Thẻ Giao Dịch: chưa đủ 200 KNB khóa (tạm hoãn mua KNB)');
          return;
        }
      }
      daySet('theGd', 1);
      note('00:00:00: đã mua ẩn 1 Thẻ Giao Dịch (200 KNB khóa)');
    }
  }

  async function muaKnb(I) {
    var cap = A.cfg.knbMua | 0;
    if (knbGhiLoi || !A.cfg.on || cap <= 0 || (I.lvl | 0) < 20 || !ranh()) return;
    if (A.cfg.theGiaoDich && dayGet('theGd') < 1) {
      if (ready('knb_wait_thegd', 3 * 60000)) {
        note('chờ hoàn tất mua Thẻ Giao Dịch trước khi mua KNB bằng tiền vạn');
      }
      return;
    }
    if (I.dg || I.tower || I.sat || I.dead) return;

    var daMua = demKnb();
    var con = Math.min(cap - daMua, KNB_NGAY - daMua, KNB_NGAY);
    if (typeof I.knbCon === 'number' && isFinite(I.knbCon)) con = Math.min(con, Math.max(0, I.knbCon | 0));
    if (con <= 0) return;

    var vi = viKnb(I);
    if (vi === null) return;
    con = Math.min(con, Math.floor((vi - sanKnb()) / KNB_GIA));
    if (con <= 0) return;
    if (!ready('knb', 45000)) return;

    var n = con;
    var k0 = I.knb | 0;
    var got = 0;

    if (!ranh() || isModalLocked()) return;
    var mdCheck = document.getElementById('modal');
    if (!mdCheck || !mdCheck.classList.contains('hidden')) return;
    var I1 = await info();
    if (!I1 || I1.dg || I1.tower || I1.sat || I1.dead) return;
    var vi1 = viKnb(I1);
    if (vi1 === null) return;
    n = Math.min(n, (A.cfg.knbMua | 0) - demKnb(), KNB_NGAY - demKnb(),
      Math.floor((vi1 - sanKnb()) / KNB_GIA));
    if (typeof I1.knbCon === 'number' && isFinite(I1.knbCon)) n = Math.min(n, Math.max(0, I1.knbCon | 0));
    if (n <= 0 || !ranh() || isModalLocked()) return;
    if (A.st && (A.st.o !== 'game' || Date.now() - A.stT >= 90000)) return;
    if (!A.cfg.on || (A.cfg.knbMua | 0) <= 0) return;
    if (A.cfg.theGiaoDich && dayGet('theGd') < 1) return;

    lockModal(25000);
    stealthObj().enter();
    try {
      var md = await moKtc();
      if (!md) return;
      got = await bamKnb(n, k0);
    } finally {
      await closeModalCleanly(document.getElementById('modal'));
      stealthObj().leave();
      unlockModal();
    }

    if (knbGhiLoi || got <= 0) return;

    var I2 = await info();
    var lech = (I2 && typeof I2.knb === 'number') ? (I2.knb | 0) - k0 : 0;
    var that = Math.max(lech, got);
    if (that > 0) {
      var soLuong = (that * KNB_GIA).toLocaleString('vi-VN');
      note('mua ẩn trọn vẹn ' + that + ' KNB (' + soLuong + ' lượng)');
    } else if (ready('knbIm', 15 * 60000)) {
      note('Kỳ Trân Các: đã bấm mua nhưng chưa thấy KNB tăng — kiểm tra lại');
    }
  }

  async function cheDo(I) {
    var lv = I.lvl | 0, t = I.dt || {}, mode = I.mode || 'farm';

    if (A.cfg.pb && !pbXong()) {
      var pbHetApi = (typeof I.pbCon === 'number' && I.pbCon <= 0) ||
        (typeof I.dgCon === 'number' && I.dgCon <= 0) ||
        (typeof I.dgLeft === 'number' && I.dgLeft <= 0) ||
        (I.pb && typeof I.pb.con === 'number' && I.pb.con <= 0);
      if (pbHetApi) {
        dayAdd('pbx', 1);
        A.pbOut = 0;
        note('xong phó bản hôm nay → chuyển Dã Tẩu tối đa');
      } else {
        var trong = I.dg || (A.st && A.st.noi === 'phoban'), now = Date.now();
        var ltOk = !A.st || !A.st.lt || A.st.lt === 'noi' || A.st.lt === 'khong';
        var lien = A.pbSeen && now - A.pbSeen < 60000;
        A.pbSeen = now;
        if (mode !== 'dg' || trong || I.dead || I.sat || !ltOk || !lien) A.pbOut = 0;
        else if (!A.pbOut) A.pbOut = Date.now();
        else if (Date.now() - A.pbOut > 40000) {
          dayAdd('pbx', 1);
          A.pbOut = 0;
          note('xong phó bản hôm nay → chuyển tiếp Dã Tẩu tối đa');
        }
      }
    }

    if (isUserControlling(6000)) return;

    var forceOnlyTower = !!A.cfg.thapNeDon;
    var inTowerRightNow = !!(I.tower || (A.st && A.st.noi === 'thap') || isInTowerNow());

    // Khi tích "Chỉ Leo Tháp": dù đang làm gì cũng lập tức khóa & chuyển sang Chế độ: Leo Tháp
    if (forceOnlyTower) {
      towerLockSet(true);
    }

    // ƯU TIÊN KHUNG GIỜ: 4 mốc Boss Hoàng Kim (12:00, 15:30, 19:30, 22:00)
    // (Bỏ qua nếu người chơi đang bật "Chỉ Leo Tháp" để tuyệt đối giữ Chế độ Leo Tháp)
    var activeBossSlot = forceOnlyTower ? null : getActiveBossHkSlot();
    var savedBossPrev = bossHkPrevGet();
    if (activeBossSlot) {
      if (!savedBossPrev && mode && mode !== 'farm') {
        bossHkPrevSet(mode);
      }
      if (mode !== 'farm') {
        if (I.dead || isUserControlling(6000) || !ready('mode_bosshk', 8000)) return;
        if (await switchMode('farm')) {
          ensureGameAutoOn(false);
          note('👹 Đến giờ Boss Hoàng Kim → chuyển Luyện Công đánh Boss');
        }
      } else {
        ensureGameAutoOn(false);
      }
      return;
    } else if (savedBossPrev) {
      if (forceOnlyTower) {
        bossHkPrevSet(null);
      } else if (!I.dead && !isUserControlling(6000) && ready('mode_bosshk_restore', 8000)) {
        if (mode !== savedBossPrev) {
          if (await switchMode(savedBossPrev)) {
            bossHkPrevSet(null);
            note('✅ Xong khung giờ Boss Hoàng Kim → trở về ' + (MODE_VI[savedBossPrev] || savedBossPrev));
          }
        } else {
          bossHkPrevSet(null);
        }
        return;
      } else {
        return;
      }
    }

    // THỨ TỰ HOẠT ĐỘNG TRONG NGÀY:
    // 1. Dã Tẩu + Boss Sát Thủ + Đi thuyền (Phong Lăng Độ) -> 'quest'
    // 2. Phó Bản -> 'dg'
    // 3. Leo Tháp -> 'tower' (khi xong hết hoạt động & tích Ưu tiên Leo Tháp, sẽ tự bật các tính năng của Chỉ Leo Tháp)
    var dtCon = !forceOnlyTower && A.cfg.dt && lv >= 20 && !t.du && ((t.today | 0) < (t.max | 0) || ((I.ldt | 0) > 0 && (t.con | 0) > 0));
    var stCon = !forceOnlyTower && A.cfg.st && lv >= 20 && (I.lb | 0) > 0;
    var pldCon = !forceOnlyTower && !!(I.pldCon > 0 || (A.st && A.st.noi === 'thuyen'));
    var pbCon = !forceOnlyTower && A.cfg.pb && !pbXong();
    var dailyDoneForTower = !dtCon && !stCon && !pldCon && !pbCon;

    if (A.cfg.thap && dailyDoneForTower && (inTowerRightNow || mode === 'tower')) {
      towerLockSet(true);
    }

    var towerLocked = forceOnlyTower || (!!A.cfg.thap && dailyDoneForTower && towerLockGet());
    var thapCon = !!(A.cfg.thap || forceOnlyTower);
    var want = towerLocked ? 'tower' : ((dtCon || stCon || pldCon) ? 'quest' : (pbCon ? 'dg' : (thapCon ? 'tower' : null)));
    var prev = prevGet();

    async function switchMode(targetMode) {
      if (targetMode !== 'tower') {
        try { if (typeof releaseOrbitKeys === 'function') releaseOrbitKeys(); } catch (eSwRel) {}
      }
      var r = await api('cheDo', { m: targetMode });
      if (r && r.ok && r.m === targetMode) return true;
      var item = document.querySelector('#mswMenu [data-msw="' + targetMode + '"]');
      if (item) {
        try {
          var mBtn = document.getElementById('mswBtn');
          var menu = document.getElementById('mswMenu');
          if (mBtn && menu && (menu.classList.contains('hidden') || !isVis(item))) {
            mBtn.click();
            await sleep(120);
          }
          item.click();
          await sleep(220);
          if (menu && !menu.classList.contains('hidden')) {
            try { menu.classList.add('hidden'); } catch (eM) {}
          }
          await sleep(120);
          var Icheck = await info();
          if (Icheck && Icheck.mode === targetMode) return true;
        } catch (eDom) {}
      }
      return false;
    }

    // Chế độ Chỉ Leo Tháp: khi đã vào Thông Thiên Tháp thì khóa cứng Chế độ Leo Tháp,
    // tự động vào lại Tháp sau khi Rớt Tháp (chết) hoặc mất kết nối / tải lại game.
    if (towerLocked) {
      if (I.dead) return;
      if (isUserControlling(6000)) return;
      if (mode !== 'tower') {
        if (!ready('mode_tower_lock', 7000)) return;
        if (await switchMode('tower')) {
          A.pbOut = 0;
          note('🗼 Chỉ Leo Tháp: khôi phục Chế độ Leo Tháp');
        }
        return;
      }
      if (!inTowerRightNow && ready('tower_reenter', 8000)) {
        // Đã ở chế độ Leo Tháp nhưng bị Rớt Tháp hoặc vừa kết nối lại ở ngoài tháp -> kích hoạt vào lại Tháp
        var reOk = await api('cheDo', { m: 'tower' });
        if (!reOk || !reOk.ok) {
          await switchMode('tower');
        }
        var enterBtns = document.querySelectorAll('button, [role="button"]');
        for (var eb = 0; eb < enterBtns.length; eb++) {
          var bEl = enterBtns[eb];
          if (!isVis(bEl) || bEl.disabled) continue;
          var bt = noAccent(bEl.textContent || '');
          if (bt === 'vao thap' || bt === 'leo thap' || bt === 'vao lai thap' || bt === 'thieu chien thap') {
            try { bEl.click(); } catch (eBt) {}
            break;
          }
        }
        note('🗼 Chỉ Leo Tháp: phát hiện Rớt Tháp / kết nối lại → tự vào lại Tháp');
      }
      return;
    }

    if (want && mode !== want) {
      if (I.dg || I.tower || I.sat || I.dead || I.st === true) return;
      if (!ranh() || !ready('mode_' + want, 12000)) return;
      var savedPrev = prev || mode;
      if (await switchMode(want)) {
        if (!prev) prevSet(savedPrev);
        A.pbOut = 0;
        if (want === 'tower' && (forceOnlyTower || A.cfg.thap)) towerLockSet(true);
        note(want === 'dg'
          ? 'chuyển Tự chạy Phó Bản (còn lượt)'
          : (want === 'quest' ? ('chuyển Dã Tẩu tối đa (' + (dtCon ? 'còn Dã Tẩu' : 'có Lệnh Bài Sát Thủ') + ')') : 'chuyển Leo Tháp'));
      }
    } else if (!want && prev && MODE_VI[prev] && mode !== prev && (mode === 'quest' || mode === 'dg' || mode === 'tower')) {
      if (I.dg || I.st === true || I.sat || !ranh() || !ready('mode_restore', 10000)) return;
      if (await switchMode(prev)) {
        prevSet(null);
        note('hoàn tất tự động: trở về trạng thái ban đầu (' + MODE_VI[prev] + ')');
      }
    } else if (!want && prev && mode === prev) {
      prevSet(null);
    }
  }

  async function tick() {
    if (A.busy || !A.cfg.on) return;
    var curTradeDay = vnTradeDay();
    if (curTradeDay !== A.lastTradeDay) {
      A.lastTradeDay = curTradeDay;
      delete A.cool.theGd;
      A.immediate = true;
      A.immUntil = Date.now() + 120000;
    }
    var curDay = vnDay();
    if (curDay !== A.lastDay) {
      A.lastDay = curDay;
      resetDay(true);
      A.immediate = true;
      A.immUntil = Date.now() + 120000;
      A.started = Date.now() - 60000;
    }

    var hasRecentSt = !!(A.st && A.st.o === 'game' && Date.now() - A.stT < 90000);
    var hasHudDom = false;
    try {
      var mswEl = document.getElementById('modeSw');
      hasHudDom = !!(mswEl && !mswEl.classList.contains('hidden')) || !!document.getElementById('ktcBtn') || !!document.getElementById('mswBtn');
    } catch (eHud) {}

    var ingame = hasRecentSt || hasHudDom;

    if (hasRecentSt && (A.st.v | 0) > 0 && (A.st.v | 0) < 990) {
      if (!A.gate) {
        A.gate = 1;
        note('bản game ' + (A.st.v | 0) + ' < 990 — không chạy tự động');
      }
      return;
    }
    if (!ingame) { A.started = 0; return; }

    if (!A.started) A.started = Date.now();
    if (!A.immediate && A.immUntil <= Date.now() && Date.now() - A.started < 8000) return;

    A.busy = true;
    try {
      // Ưu tiên Mua Thẻ Giao Dịch đầu tiên (00:00:00), sau đó Điểm danh + Đổi nguyên liệu + Mua KNB (bị chặn nếu chưa xong Thẻ GD) + Vật phẩm tiêu hao
      var fns = [muaTheGiaoDich, qua, doiNguyenLieuThua, muaKnb, troThu, khan, suDungVatPhamTieuHao, daTau, muaLb, cheDo];
      for (var i = 0; i < fns.length; i++) {
        if (!A.cfg.on) break;
        var I = await info();
        if (!I) break;
        await fns[i](I);
      }
    } catch (e) {
    } finally {
      A.busy = false;
      if (A.immUntil <= Date.now()) A.immediate = false;
    }
  }

  // =========================================================================
  // LEO THÁP: LẤY TỌA ĐỘ TRUNG TÂM MAP LÀM ĐIỂM TẬP TRUNG, GIỮ AUTO GAME BẬT
  // =========================================================================
  // Chỉ dùng phím mũi tên điều hướng hoặc joystick (KHÔNG gửi W/A/S/D hay click canvas để tránh đổi chiêu)
  var ORBIT_KEY_GROUPS = {
    up: [{ key: 'ArrowUp', code: 'ArrowUp', keyCode: 38 }],
    down: [{ key: 'ArrowDown', code: 'ArrowDown', keyCode: 40 }],
    left: [{ key: 'ArrowLeft', code: 'ArrowLeft', keyCode: 37 }],
    right: [{ key: 'ArrowRight', code: 'ArrowRight', keyCode: 39 }]
  };

  var O = {
    active: false,
    moving: false,
    style: '',
    skillName: '',
    theta: 0,
    orbitDir: 1, // +1 thuận chiều kim đồng hồ, -1 ngược chiều (tự đảo chiều tránh 4 góc & 4 cạnh)
    lastAt: 0,
    moveStartedAt: 0,
    lastClickAt: 0,
    towerEnteredAt: 0,
    wasInTower: false,
    lastFloorNum: -1,
    detectedFloorFromCanvas: -1,
    lastStageText: '',
    // Trạng thái chạy về giữa bản đồ TRƯỚC KHI ĐÁNH (khi hết quái / vượt tầng / mất >= 50% máu / chạm cạnh-góc)
    returningToCenter: false,
    returnReason: '',       // 'floor' | 'clear' | 'low_hp' | 'wall' | 'enter' | 'stuck'
    returnStartedAt: 0,
    returnLockUntil: 0,
    healWaitUntil: 0,
    lowHpCooldownUntil: 0,
    peakHpRatio: 1.0,
    lastHpRatio: -1,
    obsCanvasHp: null,      // { ratio, at } đọc dự phòng từ chữ HP trên HUD/canvas
    noTargetSince: 0,
    hadTargetInFloor: false,
    prevMapX: 0,
    prevMapY: 0,
    stuckBeats: 0,
    lastTargetScreenX: 0,
    lastTargetScreenY: 0,
    lastTargetAt: 0,
    lastRetClickAt: 0,
    lastMiniScanAt: 0,
    lastStageScanAt: 0,
    lastAutoBtnScanAt: 0,
    lastHpElScanAt: 0,
    // Ước lượng độ lệch so với tâm bản đồ dự phòng (khi người chơi bật "Xoá bản đồ" hoặc minimap chưa đọc kịp)
    estNdx: 0,
    estNdy: 0,
    // Cache DOM & Offscreen scratch canvas 56x56 (tối ưu tối đa cho Xiaomi & máy cấu hình thấp)
    cachedStage: null,      // { canvas, rect }
    cachedAutoBtn: null,
    cachedHpEl: null,
    cachedStageLbl: null,
    miniCanvas: null,       // Canvas minimap riêng nếu có
    miniInMainCanvas: false,// True nếu game vẽ minimap ngay trên canvas chính
    miniScratch: null,      // Canvas 56x56 offscreen { willReadFrequently: true }
    miniScratchCtx: null,
    miniDiamond: null,      // { cx, cy, rx, ry } tự hiệu chuẩn tâm & bán trục thật của hình thoi
    obsPlayer: null,        // { nx, ny, at } tên nhân vật trên canvas chính
    obsTargetRing: null,    // { nx, ny, at } vòng tròn đỏ dưới chân mục tiêu ĐANG CHỌN
    obsEnemies: [],         // [{ nx, ny, name, at }] tất cả quái đang thấy trên canvas chính
    obsMiniPlayer: null,    // { nx, ny, ndx, ndy, at } vị trí nhân vật chuẩn hóa theo tâm thật hình thoi
    obsMiniEnemies: [],     // [{ nx, ny, at }] chấm đỏ quái trên Minimap
    autoSawOn: false,
    autoPreMoveSig: '',
    pausedAutoForReturn: false,
    lastAutoRestoreAt: 0,
    autoPauseUntil: 0,
    moveEndTimer: 0,
    lastNoteAt: 0,
    targetEl: null
  };

  // Bảng tham chiếu theo TÊN CHIÊU 9x (chỉ đọc lựa chọn của người chơi, không bao giờ tự đổi skill trong game)
  var ORBIT_MELEE_SKILLS = [
    { key: 'dat ma do giang', label: 'Đạt Ma Độ Giang' },
    { key: 'hoanh tao thien quan', label: 'Hoành Tảo Thiên Quân' },
    { key: 'van long kich', label: 'Vân Long Kích' },
    { key: 'nhan kiem hop nhat', label: 'Nhân Kiếm Hợp Nhất' },
    { key: 'truy tinh truc nguyet', label: 'Truy Tinh Trục Nguyệt' },
    { key: 'truy phong quyet', label: 'Truy Phong Quyết' },
    { key: 'pha thien tram', label: 'Phá Thiên Trảm' }
  ];

  var ORBIT_RANGED_SKILLS = [
    { key: 'vo tuong tram', label: 'Vô Tướng Trảm' },
    { key: 'thien ngoai luu tinh', label: 'Thiên Ngoại Lưu Tinh' },
    { key: 'ngao tuyet tieu phong', label: 'Ngạo Tuyết Tiêu Phong' },
    { key: 'loi dong cuu thien', label: 'Lôi Động Cửu Thiên' },
    { key: 'tam nga te tuyet', label: 'Tam Nga Tề Tuyết' },
    { key: 'phong suong toai anh', label: 'Phong Sương Toái Ảnh' },
    { key: 'bao vu le hoa', label: 'Bạo Vũ Lê Hoa' },
    { key: 'nhiep hon nguyet anh', label: 'Nhiếp Hồn Nguyệt Ảnh' },
    { key: 'cuu cung phi tinh', label: 'Cửu Cung Phi Tinh' },
    { key: 'phi long tai thien', label: 'Phi Long Tại Thiên' },
    { key: 'thien ha vo cau', label: 'Thiên Hạ Vô Cẩu' },
    { key: 'thien dia vo cuc', label: 'Thiên Địa Vô Cực' },
    { key: 'bang tung vo anh', label: 'Băng Tung Vô Ảnh' },
    { key: 'bang tam tien tu', label: 'Băng Tâm Tiên Tử' },
    { key: 'am phong thuc cot', label: 'Âm Phong Thực Cốt' },
    { key: 'huyen am tram', label: 'Huyền Âm Trảm' }
  ];

  function parseCssRgb(fs) {
    if (!fs || typeof fs !== 'string') return null;
    var s = fs.toLowerCase().trim();
    if (s === 'white') return { r: 255, g: 255, b: 255 };
    if (s === 'red') return { r: 255, g: 0, b: 0 };
    if (s === 'lime' || s === 'green') return { r: 0, g: 255, b: 0 };
    if (s === 'yellow') return { r: 255, g: 255, b: 0 };
    if (s === 'cyan') return { r: 0, g: 255, b: 255 };
    if (s === 'orange') return { r: 255, g: 165, b: 0 };
    if (s.charAt(0) === '#') {
      var hex = s.slice(1);
      if (hex.length === 3) {
        return {
          r: parseInt(hex.charAt(0) + hex.charAt(0), 16),
          g: parseInt(hex.charAt(1) + hex.charAt(1), 16),
          b: parseInt(hex.charAt(2) + hex.charAt(2), 16)
        };
      }
      if (hex.length >= 6) {
        return {
          r: parseInt(hex.slice(0, 2), 16),
          g: parseInt(hex.slice(2, 4), 16),
          b: parseInt(hex.slice(4, 6), 16)
        };
      }
    }
    var m = /rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/.exec(s);
    if (m) return { r: Number(m[1]), g: Number(m[2]), b: Number(m[3]) };
    return null;
  }

  // Quét & cache canvas chính + canvas minimap (chỉ chạy tối đa 1 lần / 1500ms, KHÔNG BAO GIỜ chạy trong Canvas2D hook)
  function refreshStageAndMinimapCache(now, force) {
    if (!force && O.cachedStage && O.cachedStage.canvas && O.cachedStage.canvas.isConnected &&
        (now - O.lastStageScanAt) < 1500) {
      return O.cachedStage;
    }
    O.lastStageScanAt = now;
    var best = null;
    var bestArea = 0;
    var candidates = [];
    try {
      var canvases = document.querySelectorAll('canvas');
      for (var i = 0; i < canvases.length; i++) {
        var c = canvases[i];
        if (!c || !c.isConnected || c.offsetParent === null) continue;
        if (c.closest && c.closest('#modal')) continue;
        var rect = c.getBoundingClientRect ? c.getBoundingClientRect() : null;
        if (!rect || rect.width < 16 || rect.height < 16) continue;
        var area = rect.width * rect.height;
        candidates.push({ canvas: c, rect: rect, area: area });
        var idCls = ((c.id || '') + ' ' + (typeof c.className === 'string' ? c.className : '')).toLowerCase();
        var looksMini = (idCls.indexOf('minimap') >= 0 || idCls.indexOf('radar') >= 0 || idCls === 'mm' || idCls.indexOf('mm-') >= 0);
        if (!looksMini && rect.width >= 100 && rect.height >= 100 && area > bestArea) {
          bestArea = area;
          best = { canvas: c, rect: rect };
        }
      }
    } catch (e) {}

    O.cachedStage = best;
    O.targetEl = best ? best.canvas : null;

    // Xác định Minimap canvas (hỗ trợ cả Chế độ PC zoom 70%, lưới 2x2 lẫn trường hợp vẽ chung trên canvas chính)
    var foundMini = null;
    if (best) {
      for (var j = 0; j < candidates.length; j++) {
        var item = candidates[j];
        if (item.canvas === best.canvas) continue;
        var ic = ((item.canvas.id || '') + ' ' + (typeof item.canvas.className === 'string' ? item.canvas.className : '')).toLowerCase();
        if (ic.indexOf('minimap') >= 0 || ic.indexOf('radar') >= 0 || ic === 'mm' || ic.indexOf('mm-') >= 0 || ic.indexOf('map') >= 0) {
          foundMini = item.canvas;
          break;
        }
        // So sánh tương đối với khung canvas chính (không bị sai lệch khi bật PC zoom 70%)
        if (item.rect.width >= 18 && item.rect.width <= best.rect.width * 0.48 &&
            item.rect.height >= 18 && item.rect.height <= best.rect.height * 0.45 &&
            item.rect.right >= best.rect.left + best.rect.width * 0.50 &&
            item.rect.top <= best.rect.top + best.rect.height * 0.48) {
          foundMini = item.canvas;
        }
      }
    }
    O.miniCanvas = foundMini;
    O.miniInMainCanvas = (!foundMini && !!best);
    return best;
  }

  // VÙNG CẤM HÌNH THOI CHUẨN HÓA THEO TÂM THẬT (ndx, ndy trong -1..+1):
  // - (0, 0) = chính giữa bản đồ; |ndx| + |ndy| = 1.0 là tường ngoài cùng.
  // - Thu gọn vùng chơi an toàn sâu vào trung tâm: cách 4 cạnh >= 38% (edgeDist < 0.62) & cắt cụt 4 góc (cornerDist < 0.50).
  function arenaDangerScore(ndx, ndy) {
    var edgeDist = Math.abs(ndx) + Math.abs(ndy);
    var cornerDist = Math.max(Math.abs(ndx), Math.abs(ndy));
    return Math.max(edgeDist / 0.62, cornerDist / 0.50);
  }

  function classifyMinimapRgb(rgb) {
    if (!rgb) return '';
    var r = rgb.r, g = rgb.g, b = rgb.b;
    if (r >= 120 && (r - g) >= 48 && (r - b) >= 48) return 'enemy';
    var avg = (r + g + b) / 3;
    if (avg >= 128 && g >= r - 35) return 'player';
    if (g >= 170 && r <= 145) return 'player';
    return '';
  }

  function ensureMiniScratch() {
    if (O.miniScratch && O.miniScratchCtx) return true;
    try {
      var sc = document.createElement('canvas');
      sc.width = 56;
      sc.height = 56;
      var sctx = sc.getContext('2d', { willReadFrequently: true });
      if (!sctx) return false;
      O.miniScratch = sc;
      O.miniScratchCtx = sctx;
      return true;
    } catch (e) { return false; }
  }

  // Quét Minimap siêu nhẹ trên canvas offscreen 56x56 (tránh hoàn toàn GPU readback stall trên Xiaomi / máy yếu):
  // - Nếu vector hook trên O.miniCanvas đã cập nhật vị trí nhân vật trong < 320ms VÀ đã hiệu chuẩn hình thoi -> BỎ QUA đọc pixel!
  // - Ngược lại chỉ blit 1 lần xuống 56x56 mỗi 260ms để tự hiệu chuẩn tâm thật (dcx, dcy) & đọc chấm nhân vật.
  function scanMinimapLightweight(now) {
    var hasFreshVec = !!(O.miniCanvas && O.miniDiamond && O.obsMiniPlayer && (now - O.obsMiniPlayer.at < 320));
    if (hasFreshVec) return;
    if (now - O.lastMiniScanAt < 260) return;
    O.lastMiniScanAt = now;

    var srcCv = O.miniCanvas || (O.miniInMainCanvas ? O.targetEl : null);
    if (!srcCv || srcCv.width < 20 || srcCv.height < 20) return;
    if (!ensureMiniScratch()) return;

    try {
      var S = 56;
      var sctx = O.miniScratchCtx;
      sctx.clearRect(0, 0, S, S);
      if (O.miniCanvas) {
        sctx.drawImage(srcCv, 0, 0, srcCv.width, srcCv.height, 0, 0, S, S);
      } else {
        // Trường hợp game vẽ minimap trực tiếp lên góc trên phải của canvas chính
        var sx0 = Math.floor(srcCv.width * 0.72);
        var sy0 = Math.floor(srcCv.height * 0.14);
        var sw0 = Math.max(20, srcCv.width - sx0);
        var sh0 = Math.max(20, Math.floor(srcCv.height * 0.22));
        sctx.drawImage(srcCv, sx0, sy0, sw0, sh0, 0, 0, S, S);
      }

      var data = sctx.getImageData(0, 0, S, S).data;
      var bx1 = 3, bx2 = S - 3, by1 = 3, by2 = S - 3;

      // Bước 1: Tự đo hình thoi nền xám (gray 24..125) trên 56x56 để tìm tâm thật & bán trục thật
      var dMinX = S, dMaxX = 0, dMinY = S, dMaxY = 0, dCount = 0, floorSum = 0;
      for (var y = by1; y < by2; y++) {
        for (var x = bx1; x < bx2; x++) {
          var idx = (y * S + x) * 4;
          if (data[idx + 3] < 140) continue;
          var gAvg = (data[idx] + data[idx + 1] + data[idx + 2]) / 3;
          if (gAvg > 24 && gAvg < 125) {
            if (x < dMinX) dMinX = x;
            if (x > dMaxX) dMaxX = x;
            if (y < dMinY) dMinY = y;
            if (y > dMaxY) dMaxY = y;
            floorSum += gAvg;
            dCount++;
          }
        }
      }
      if (dCount < 16) return;

      var dcxN = ((dMinX + dMaxX) * 0.5) / S;
      var dcyN = ((dMinY + dMaxY) * 0.5) / S;
      var drxN = Math.max(0.12, ((dMaxX - dMinX) * 0.5) / S);
      var dryN = Math.max(0.10, ((dMaxY - dMinY) * 0.5) / S);
      var floorMed = floorSum / dCount;

      if (!O.miniDiamond) {
        O.miniDiamond = { cxN: dcxN, cyN: dcyN, rxN: drxN, ryN: dryN };
      } else {
        O.miniDiamond.cxN = O.miniDiamond.cxN * 0.72 + dcxN * 0.28;
        O.miniDiamond.cyN = O.miniDiamond.cyN * 0.72 + dcyN * 0.28;
        O.miniDiamond.rxN = O.miniDiamond.rxN * 0.72 + drxN * 0.28;
        O.miniDiamond.ryN = O.miniDiamond.ryN * 0.72 + dryN * 0.28;
      }
      var md = O.miniDiamond;

      // Bước 2: Tìm chấm sáng nhất (nhân vật) và chấm đỏ (quái) trong hình thoi 56x56
      var maxBright = 0;
      var pList = [];
      var enemies = [];
      var py1 = Math.max(by1, Math.floor((md.cyN - md.ryN * 1.06) * S));
      var py2 = Math.min(by2, Math.ceil((md.cyN + md.ryN * 1.06) * S));
      var px1 = Math.max(bx1, Math.floor((md.cxN - md.rxN * 1.06) * S));
      var px2 = Math.min(bx2, Math.ceil((md.cxN + md.rxN * 1.06) * S));

      for (var py = py1; py <= py2; py++) {
        var ny = py / S;
        for (var px = px1; px <= px2; px++) {
          var nx = px / S;
          if ((Math.abs(nx - md.cxN) / (md.rxN * 1.08) + Math.abs(ny - md.cyN) / (md.ryN * 1.08)) > 1.08) continue;
          var pIdx = (py * S + px) * 4;
          if (data[pIdx + 3] < 140) continue;
          var r = data[pIdx], g = data[pIdx + 1], b = data[pIdx + 2];
          var lum = (r + g + b) / 3;
          if (lum >= floorMed + 38 && g >= r - 35) {
            if (lum > maxBright) maxBright = lum;
            pList.push({ nx: nx, ny: ny, w: lum - floorMed, lum: lum });
          } else if (r >= 120 && (r - g) >= 48 && (r - b) >= 48) {
            if (enemies.length < 20) enemies.push({ nx: nx, ny: ny, at: now });
          }
        }
      }

      if (pList.length > 0) {
        var cut = Math.max(floorMed + 38, maxBright - 24);
        var sx = 0, sy = 0, sw = 0;
        for (var k = 0; k < pList.length; k++) {
          if (pList[k].lum >= cut) {
            sx += pList[k].nx * pList[k].w;
            sy += pList[k].ny * pList[k].w;
            sw += pList[k].w;
          }
        }
        if (sw > 0) {
          var fxN = sx / sw, fyN = sy / sw;
          var ndx = Math.max(-1.05, Math.min(1.05, (fxN - md.cxN) / md.rxN));
          var ndy = Math.max(-1.05, Math.min(1.05, (fyN - md.cyN) / md.ryN));
          O.obsMiniPlayer = { nx: fxN, ny: fyN, ndx: ndx, ndy: ndy, at: now };
          O.estNdx = ndx;
          O.estNdy = ndy;
        }
      }
      O.obsMiniEnemies = enemies;
    } catch (ePix) {}
  }

  // =========================================================================
  // CANVAS2D SPY SIÊU NHẸ — CHỈ CÀI KHI ĐANG Ở TRONG THÁP, GỠ SẠCH KHI RA NGOÀI
  // TUYỆT ĐỐI KHÔNG GỌI getBoundingClientRect() HAY getComputedStyle() BÊN TRONG!
  // =========================================================================
  function handleCanvasSpyText(ctx, text, rawX, rawY) {
    try {
      if (!ctx || !ctx.canvas) return;
      var cv = ctx.canvas;
      // Kiểm tra O(1) theo tham chiếu, không gọi getBoundingClientRect() gây giật lag!
      if (cv !== O.targetEl) return;
      var txt = String(text || '').trim();
      if (!txt || txt.length < 3 || txt.length > 42) return;

      // Bắt tỷ lệ HP nếu game vẽ dạng cur/max lên vùng HUD trên trái của canvas
      if (txt.indexOf('/') > 0 && /^[\d.,\s]+\/[\d.,\s]+$/.test(txt)) {
        var parts = txt.split('/');
        var cVal = Number(parts[0].replace(/[^\d]/g, ''));
        var mVal = Number(parts[1].replace(/[^\d]/g, ''));
        if (mVal >= 50 && cVal >= 0 && cVal <= mVal) {
          var hsy = Number(rawY) || 0;
          if (typeof ctx.getTransform === 'function') {
            try { var hm = ctx.getTransform(); if (hm) hsy = hm.b * (Number(rawX) || 0) + hm.d * hsy + hm.f; } catch (eH) {}
          }
          if (hsy / cv.height < 0.16) {
            O.obsCanvasHp = { ratio: cVal / mVal, at: Date.now() };
          }
        }
        return;
      }

      if (/^[\d.,+\-%/\s]+$/.test(txt)) return;
      var tNA = noAccent(txt);

      // Bắt số tầng trực tiếp từ tên quái hoặc thông báo trên canvas (vd: "Hoa Ho Tang 2")
      var flMatch = /\btang\s*(\d{1,4})\b/.exec(tNA);
      if (flMatch) {
        var flNum = Number(flMatch[1]);
        if (isFinite(flNum) && flNum > 0) O.detectedFloorFromCanvas = flNum;
      }

      if (tNA.indexOf('ban ') === 0 || tNA.indexOf('luong') >= 0 || tNA.indexOf('to doi') >= 0 ||
          tNA.indexOf('thong thien') >= 0 || tNA.indexOf('che do') >= 0 || tNA.indexOf('len ngua') >= 0 ||
          tNA.indexOf('xoay chieu') >= 0 || tNA.indexOf('auto') === 0 || tNA.indexOf('knb') >= 0) return;

      var sx = Number(rawX) || 0, sy = Number(rawY) || 0;
      if (typeof ctx.getTransform === 'function') {
        try {
          var m = ctx.getTransform();
          if (m) {
            sx = m.a * sx + m.c * sy + m.e;
            sy = m.b * (Number(rawX) || 0) + m.d * sy + m.f;
          }
        } catch (eTr) {}
      }
      var nx = sx / cv.width;
      var ny = sy / cv.height;
      if (!isFinite(nx) || !isFinite(ny) || nx < 0.06 || nx > 0.94 || ny < 0.14 || ny > 0.82) return;

      var now = Date.now();
      var charName = (A.st && A.st.ten) ? noAccent(String(A.st.ten)) : ((A.info && (A.info.ten || A.info.name)) ? noAccent(String(A.info.ten || A.info.name)) : '');
      if (charName && (tNA === charName || (charName.length >= 3 && tNA.indexOf(charName) >= 0))) {
        O.obsPlayer = { nx: nx, ny: ny, at: now };
        return;
      }

      var inPlayerOverhead = (Math.abs(nx - 0.50) < 0.10 && ny >= 0.34 && ny <= 0.49);
      var isEnemyKeyword = (
        tNA.indexOf('tang ') >= 0 || tNA.indexOf('boss') >= 0 || tNA.indexOf('thu linh') >= 0 ||
        tNA.indexOf('cao thu') >= 0 || tNA.indexOf('sat thu') >= 0 || tNA.indexOf('de tu') >= 0 ||
        tNA.indexOf('vu tang') >= 0 || tNA.indexOf('ho phap') >= 0 || tNA.indexOf('kim ho') >= 0 ||
        tNA.indexOf('tu la') >= 0 || txt.indexOf('☆') >= 0 || txt.indexOf('★') >= 0
      );
      if (inPlayerOverhead && !isEnemyKeyword) {
        if (!charName) O.obsPlayer = { nx: nx, ny: ny, at: now };
        return;
      }

      var rgb = parseCssRgb(ctx.fillStyle);
      var isEnemyColor = !!(rgb && (
        (rgb.r >= 195 && rgb.g <= 155 && rgb.b <= 130) ||
        (rgb.r >= 220 && rgb.g >= 180 && rgb.b <= 120) ||
        (rgb.r >= 180 && rgb.b >= 180 && rgb.g <= 140)
      ));

      if (isEnemyColor || isEnemyKeyword) {
        var foundIdx = -1;
        for (var i = 0; i < O.obsEnemies.length; i++) {
          if (O.obsEnemies[i].name === tNA && Math.hypot(O.obsEnemies[i].nx - nx, O.obsEnemies[i].ny - ny) < 0.14) {
            foundIdx = i;
            break;
          }
        }
        var entry = { nx: nx, ny: ny, name: tNA, at: now };
        if (foundIdx >= 0) O.obsEnemies[foundIdx] = entry;
        else {
          O.obsEnemies.push(entry);
          if (O.obsEnemies.length > 16) O.obsEnemies.shift();
        }
      }
    } catch (eSpy) {}
  }

  function handleCanvasSpyShape(ctx, rawX, rawY, radW, radH, isStroke) {
    try {
      if (!ctx || !ctx.canvas) return;
      var cv = ctx.canvas;
      var isMini = (cv === O.miniCanvas);
      var isMain = (cv === O.targetEl);
      if (!isMini && !isMain) return;

      var sx = Number(rawX) || 0, sy = Number(rawY) || 0;
      if (typeof ctx.getTransform === 'function') {
        try {
          var m = ctx.getTransform();
          if (m) {
            sx = m.a * sx + m.c * sy + m.e;
            sy = m.b * (Number(rawX) || 0) + m.d * sy + m.f;
          }
        } catch (eTr) {}
      }
      var nx = sx / cv.width;
      var ny = sy / cv.height;
      if (!isFinite(nx) || !isFinite(ny) || nx < 0.02 || nx > 0.98 || ny < 0.02 || ny > 0.98) return;
      var now = Date.now();

      // 1. Trên Minimap canvas (0 GPU readback): bắt chấm nhân vật & chấm đỏ quái
      if (isMini) {
        if (isStroke) return;
        if ((radW || 0) > 22 || (radH || 0) > 22) return;
        var cxN = O.miniDiamond ? O.miniDiamond.cxN : 0.50;
        var cyN = O.miniDiamond ? O.miniDiamond.cyN : 0.60;
        var rxN = O.miniDiamond ? O.miniDiamond.rxN : 0.31;
        var ryN = O.miniDiamond ? O.miniDiamond.ryN : 0.19;
        var ndx = (nx - cxN) / Math.max(0.10, rxN);
        var ndy = (ny - cyN) / Math.max(0.08, ryN);
        if (Math.abs(ndx) + Math.abs(ndy) > 1.15) return;
        var role = classifyMinimapRgb(parseCssRgb(ctx.fillStyle));
        if (role === 'player') {
          var cNdx = Math.max(-1.05, Math.min(1.05, ndx));
          var cNdy = Math.max(-1.05, Math.min(1.05, ndy));
          O.obsMiniPlayer = { nx: nx, ny: ny, ndx: cNdx, ndy: cNdy, at: now };
          O.estNdx = cNdx;
          O.estNdy = cNdy;
        } else if (role === 'enemy') {
          O.obsMiniEnemies.push({ nx: nx, ny: ny, at: now });
          if (O.obsMiniEnemies.length > 20) O.obsMiniEnemies.shift();
        }
        return;
      }

      // 2. Trên Canvas chính: bắt vòng tròn đỏ dưới chân mục tiêu ĐANG CHỌN
      if (isStroke && ny >= 0.16 && ny <= 0.84) {
        var rMax = Math.max(radW || 0, radH || 0);
        if (rMax >= 8 && rMax <= 75) {
          var sRgb = parseCssRgb(ctx.strokeStyle);
          if (sRgb && sRgb.r >= 190 && sRgb.g <= 95 && sRgb.b <= 95) {
            O.obsTargetRing = { nx: nx, ny: ny, at: now };
          }
        }
      }
    } catch (eShp) {}
  }

  function installArenaCanvasSpy() {
    window.__vlmOrbitSpyCb = {
      text: handleCanvasSpyText,
      shape: handleCanvasSpyShape
    };
    window.__vlmOrbitSpyActive = true;
    if (window.__vlmOrbitCanvasSpyInstalled) return;
    try {
      if (typeof CanvasRenderingContext2D === 'undefined' || !CanvasRenderingContext2D.prototype) return;
      var proto = CanvasRenderingContext2D.prototype;
      if (!window.__vlmOrigCtx2D) {
        window.__vlmOrigCtx2D = {
          fillText: proto.fillText,
          strokeText: proto.strokeText,
          fillRect: proto.fillRect,
          arc: proto.arc,
          ellipse: proto.ellipse,
          fill: proto.fill,
          stroke: proto.stroke,
          beginPath: proto.beginPath
        };
      }
      var orig = window.__vlmOrigCtx2D;
      window.__vlmOrbitCanvasSpyInstalled = true;

      if (orig.beginPath) {
        proto.beginPath = function () {
          this.__vlmShape = null;
          return orig.beginPath.apply(this, arguments);
        };
      }
      if (orig.fillText) {
        proto.fillText = function (text, x, y) {
          if (window.__vlmOrbitSpyActive && window.__vlmOrbitSpyCb && window.__vlmOrbitSpyCb.text) {
            window.__vlmOrbitSpyCb.text(this, text, x, y);
          }
          return orig.fillText.apply(this, arguments);
        };
      }
      if (orig.strokeText) {
        proto.strokeText = function (text, x, y) {
          if (window.__vlmOrbitSpyActive && window.__vlmOrbitSpyCb && window.__vlmOrbitSpyCb.text) {
            window.__vlmOrbitSpyCb.text(this, text, x, y);
          }
          return orig.strokeText.apply(this, arguments);
        };
      }
      if (orig.fillRect) {
        proto.fillRect = function (x, y, w, h) {
          if (window.__vlmOrbitSpyActive && window.__vlmOrbitSpyCb && window.__vlmOrbitSpyCb.shape) {
            window.__vlmOrbitSpyCb.shape(this, x + (w || 0) * 0.5, y + (h || 0) * 0.5, Math.abs(w || 0), Math.abs(h || 0), false);
          }
          return orig.fillRect.apply(this, arguments);
        };
      }
      if (orig.arc) {
        proto.arc = function (x, y, r) {
          if (window.__vlmOrbitSpyActive) {
            this.__vlmShape = { x: x, y: y, rw: r || 0, rh: r || 0 };
          }
          return orig.arc.apply(this, arguments);
        };
      }
      if (orig.ellipse) {
        proto.ellipse = function (x, y, rx, ry) {
          if (window.__vlmOrbitSpyActive) {
            this.__vlmShape = { x: x, y: y, rw: rx || 0, rh: ry || 0 };
          }
          return orig.ellipse.apply(this, arguments);
        };
      }
      if (orig.fill) {
        proto.fill = function () {
          if (window.__vlmOrbitSpyActive && this.__vlmShape && window.__vlmOrbitSpyCb && window.__vlmOrbitSpyCb.shape) {
            var sh = this.__vlmShape;
            window.__vlmOrbitSpyCb.shape(this, sh.x, sh.y, sh.rw * 2, sh.rh * 2, false);
          }
          return orig.fill.apply(this, arguments);
        };
      }
      if (orig.stroke) {
        proto.stroke = function () {
          if (window.__vlmOrbitSpyActive && this.__vlmShape && window.__vlmOrbitSpyCb && window.__vlmOrbitSpyCb.shape) {
            var sh = this.__vlmShape;
            window.__vlmOrbitSpyCb.shape(this, sh.x, sh.y, sh.rw, sh.rh, true);
          }
          return orig.stroke.apply(this, arguments);
        };
      }
    } catch (eHook) {}
  }

  // GỠ SẠCH Canvas2D hook về nguyên bản khi rời khỏi Tháp hoặc chuyển chế độ -> 0% lag trên Xiaomi & máy yếu!
  function uninstallArenaCanvasSpy() {
    window.__vlmOrbitSpyActive = false;
    if (!window.__vlmOrbitCanvasSpyInstalled) return;
    window.__vlmOrbitCanvasSpyInstalled = false;
    try {
      var orig = window.__vlmOrigCtx2D;
      if (!orig || typeof CanvasRenderingContext2D === 'undefined' || !CanvasRenderingContext2D.prototype) return;
      var proto = CanvasRenderingContext2D.prototype;
      if (orig.fillText) proto.fillText = orig.fillText;
      if (orig.strokeText) proto.strokeText = orig.strokeText;
      if (orig.fillRect) proto.fillRect = orig.fillRect;
      if (orig.arc) proto.arc = orig.arc;
      if (orig.ellipse) proto.ellipse = orig.ellipse;
      if (orig.fill) proto.fill = orig.fill;
      if (orig.stroke) proto.stroke = orig.stroke;
      if (orig.beginPath) proto.beginPath = orig.beginPath;
    } catch (eUnhook) {}
  }

  // Tìm & cache nút Auto trong game (tránh querySelectorAll & getComputedStyle liên tục mỗi nhịp)
  function findGameAutoButton() {
    try {
      var now = Date.now();
      if (O.cachedAutoBtn && O.cachedAutoBtn.isConnected && !O.cachedAutoBtn.disabled && O.cachedAutoBtn.offsetParent !== null) {
        return O.cachedAutoBtn;
      }
      if (now - O.lastAutoBtnScanAt < 2500) return O.cachedAutoBtn && O.cachedAutoBtn.isConnected ? O.cachedAutoBtn : null;
      O.lastAutoBtnScanAt = now;

      var md = document.getElementById('modal');
      var directIds = ['autoBtn', 'bAuto', 'btnAuto', 'u20auto', 'autoAtk', 'atkAuto', 'tAuto', 'autoPlay', 'btnTuDanh', 'tuDanhBtn', 'u20Auto'];
      for (var i = 0; i < directIds.length; i++) {
        var el = document.getElementById(directIds[i]);
        if (el && el.offsetParent !== null && !el.disabled && (!md || !md.contains(el))) {
          O.cachedAutoBtn = el;
          return el;
        }
      }
      var attrEls = document.querySelectorAll(
        '[data-act="auto"], [data-cmd="auto"], [data-btn="auto"], [data-k="auto"], [data-auto], ' +
        '[onclick*="toggleAuto" i], [onclick*="autoAtk" i], [onclick*="tuDanh" i], [onclick*="setAuto" i]'
      );
      for (var ai = 0; ai < attrEls.length; ai++) {
        var ae = attrEls[ai];
        if (ae && ae.offsetParent !== null && !ae.disabled && (!md || !md.contains(ae)) && ae.id !== 'vlmAuto') {
          O.cachedAutoBtn = ae;
          return ae;
        }
      }
      var btns = document.querySelectorAll('button, [role="button"], .btn, [onclick]');
      for (var bi = 0; bi < btns.length; bi++) {
        var b = btns[bi];
        if (!b || b.id === 'vlmAuto' || b.id === 'mswBtn' || b.offsetParent === null || b.disabled) continue;
        if (md && md.contains(b)) continue;
        if (b.closest && b.closest('#modeSw, #mswMenu, #tabs, #t-inv, #t-char, #chatLines, #u20lines, #logBox, #u20log')) continue;
        var imgEl = b.querySelector ? b.querySelector('img') : null;
        var idCls = ((b.id || '') + ' ' + (typeof b.className === 'string' ? b.className : '') + ' ' + (imgEl ? (imgEl.getAttribute('src') || '') : '')).toLowerCase();
        var raw = (b.textContent || '') + ' ' + (b.getAttribute('title') || '') + ' ' +
          (b.getAttribute('aria-label') || '') + ' ' + (b.getAttribute('data-tip') || '') + ' ' +
          (b.getAttribute('data-tooltip') || '') + ' ' + (imgEl ? ((imgEl.getAttribute('alt') || '') + ' ' + (imgEl.getAttribute('title') || '')) : '');
        var t = noAccent(raw);
        if (t.indexOf('che do') >= 0 || t.indexOf('da tau') >= 0 || t.indexOf('sat thu') >= 0 ||
            t.indexOf('hanh trang') >= 0 || t.indexOf('pho ban') >= 0 || t.indexOf('nhiem vu') >= 0 ||
            t.indexOf('leo thap') >= 0 || t.indexOf('luyen cong') >= 0 || t.indexOf('ky tran') >= 0 ||
            t.indexOf('vlm') >= 0) continue;
        if (/(?:^|[^a-z])(auto|tudanh|tuchien)(?:$|[^a-z])/.test(idCls) && idCls.indexOf('vlm') < 0) {
          O.cachedAutoBtn = b;
          return b;
        }
        if (!t || t.length > 28) continue;
        if (t === 'auto' || t.indexOf('auto ') === 0 || t.indexOf('auto:') === 0 ||
            t === 'tu danh' || t.indexOf('tu danh') === 0 || t === 'tu chien' ||
            t.indexOf('tu chien') === 0 || t.indexOf('auto danh') >= 0 ||
            t === 'bat auto' || t === 'tat auto' || t === 'dang auto') {
          O.cachedAutoBtn = b;
          return b;
        }
      }
    } catch (eAutoBtn) {}
    return null;
  }

  // Chữ ký nhẹ không gọi getComputedStyle() gây ép layout reflow
  function autoBtnLightSignature(btn) {
    if (!btn) return '';
    var inlineStyle = (btn.getAttribute && btn.getAttribute('style')) || '';
    var ariaP = (btn.getAttribute && btn.getAttribute('aria-pressed')) || '';
    var dOn = (btn.getAttribute && (btn.getAttribute('data-on') || btn.getAttribute('data-active') || btn.getAttribute('data-state'))) || '';
    return String(btn.className || '') + '|' + String(inlineStyle) + '|' + ariaP + '|' + dOn + '|' + noAccent(btn.textContent || '');
  }

  function detectGameAutoState(btn) {
    try {
      if (A.st && Date.now() - A.stT < 15000) {
        if (typeof A.st.auto === 'boolean') return A.st.auto ? 'on' : 'off';
        if (typeof A.st.tuDanh === 'boolean') return A.st.tuDanh ? 'on' : 'off';
        if (typeof A.st.autoAtk === 'boolean') return A.st.autoAtk ? 'on' : 'off';
      }
      if (A.info) {
        if (typeof A.info.auto === 'boolean') return A.info.auto ? 'on' : 'off';
        if (typeof A.info.tuDanh === 'boolean') return A.info.tuDanh ? 'on' : 'off';
        if (typeof A.info.autoAtk === 'boolean') return A.info.autoAtk ? 'on' : 'off';
      }
      if (!btn) return 'unknown';

      var ap = btn.getAttribute && btn.getAttribute('aria-pressed');
      if (ap === 'true') return 'on';
      if (ap === 'false') return 'off';

      var ac = btn.getAttribute && btn.getAttribute('aria-checked');
      if (ac === 'true') return 'on';
      if (ac === 'false') return 'off';

      var dataFlags = ['data-on', 'data-active', 'data-auto', 'data-state'];
      for (var di = 0; di < dataFlags.length; di++) {
        var dv = btn.getAttribute && btn.getAttribute(dataFlags[di]);
        if (dv === '1' || dv === 'true' || dv === 'on') return 'on';
        if (dv === '0' || dv === 'false' || dv === 'off') return 'off';
      }

      var t = noAccent(btn.textContent || '');
      if (/(?:^|\b)(auto\s*:?\s*(bat|on)|dang\s*auto|tu\s*danh\s*:?\s*(bat|on)|tat\s*auto)(?:\b|$)/.test(t)) return 'on';
      if (/(?:^|\b)(auto\s*:?\s*(tat|off)|tu\s*danh\s*:?\s*(tat|off)|bat\s*auto)(?:\b|$)/.test(t)) return 'off';

      var cl = btn.classList;
      if (cl) {
        if (cl.contains('on') || cl.contains('active') || cl.contains('checked') || cl.contains('enabled') ||
            cl.contains('is-on') || cl.contains('btn-on') || cl.contains('lit') || cl.contains('glow') ||
            cl.contains('selected') || cl.contains('running') || cl.contains('green') || cl.contains('emerald')) {
          return 'on';
        }
        if (cl.contains('off') || cl.contains('inactive') || cl.contains('is-off') || cl.contains('btn-off') || cl.contains('disabled')) {
          return 'off';
        }
      }

      if (O.autoSawOn && O.autoPreMoveSig) {
        var curSig = autoBtnLightSignature(btn);
        if (curSig && curSig !== O.autoPreMoveSig) return 'off';
      }
    } catch (eDet) {}
    return 'unknown';
  }

  // Tạm tắt Auto đánh trong lúc nhân vật đang chạy thẳng về giữa bản đồ (chạy về tâm TRƯỚC, đánh SAU)
  function pauseGameAutoForReturn() {
    try {
      if (O.pausedAutoForReturn) return;
      var btn = findGameAutoButton();
      if (!btn) return;
      var st = detectGameAutoState(btn);
      if (st === 'on') {
        O.autoSawOn = true;
        O.autoPreMoveSig = autoBtnLightSignature(btn);
        O.pausedAutoForReturn = true;
        O.lastAutoRestoreAt = Date.now();
        try { btn.click(); } catch (eOff) {}
      }
    } catch (e) {}
  }

  // Bật lại Auto đánh ngay khi nhân vật đã về tới giữa bản đồ
  function resumeGameAutoAfterReturn() {
    try {
      var wasPaused = O.pausedAutoForReturn;
      O.pausedAutoForReturn = false;
      var btn = findGameAutoButton();
      if (!btn) return;
      var st = detectGameAutoState(btn);
      if (st === 'off' || (wasPaused && st !== 'on')) {
        O.lastAutoRestoreAt = Date.now();
        try { btn.click(); } catch (eOn) {}
      }
    } catch (e) {}
  }

  function ensureGameAutoOn(afterMovePulse) {
    try {
      if (O.returningToCenter || O.pausedAutoForReturn) return false;
      if (isUserControlling(2500)) return false;
      var now = Date.now();
      if (!afterMovePulse && now - O.lastAutoRestoreAt < 450) return false;
      if (afterMovePulse && now - O.lastAutoRestoreAt < 180) return false;

      var btn = findGameAutoButton();
      var state = detectGameAutoState(btn);
      if (state === 'on') {
        O.autoSawOn = true;
        if (btn) O.autoPreMoveSig = autoBtnLightSignature(btn);
        return false;
      }

      if (btn && state === 'off') {
        O.lastAutoRestoreAt = now;
        O.autoPauseUntil = now + 80;
        try { btn.click(); } catch (eClk) {}
        return true;
      }

      if (afterMovePulse && btn && O.autoSawOn && O.autoPreMoveSig) {
        var sigNow = autoBtnLightSignature(btn);
        if (sigNow && sigNow !== O.autoPreMoveSig) {
          O.lastAutoRestoreAt = now;
          O.autoPauseUntil = now + 80;
          try { btn.click(); } catch (eClk2) {}
          return true;
        }
      }
    } catch (eEns) {}
    return false;
  }

  function pauseOrbitMovement() {
    if (O.moveEndTimer) {
      clearTimeout(O.moveEndTimer);
      O.moveEndTimer = 0;
    }
    O.moving = false;
    O.moveStartedAt = 0;
  }

  function releaseOrbitKeys() {
    pauseOrbitMovement();
    if (O.pausedAutoForReturn) {
      resumeGameAutoAfterReturn();
    }
    uninstallArenaCanvasSpy();
    O.active = false;
    O.wasInTower = false;
    O.towerEnteredAt = 0;
    O.lastFloorNum = -1;
    O.detectedFloorFromCanvas = -1;
    O.lastStageText = '';
    O.returningToCenter = false;
    O.returnReason = '';
    O.returnStartedAt = 0;
    O.returnLockUntil = 0;
    O.healWaitUntil = 0;
    O.lowHpCooldownUntil = 0;
    O.peakHpRatio = 1.0;
    O.lastHpRatio = -1;
    O.obsCanvasHp = null;
    O.noTargetSince = 0;
    O.hadTargetInFloor = false;
    O.prevMapX = 0;
    O.prevMapY = 0;
    O.estNdx = 0;
    O.estNdy = 0;
    O.stuckBeats = 0;
    O.lastTargetScreenX = 0;
    O.lastTargetScreenY = 0;
    O.lastTargetAt = 0;
    O.lastRetClickAt = 0;
    O.lastClickAt = 0;
    O.lastAt = 0;
    O.orbitDir = 1;
    O.miniDiamond = null;
    O.miniCanvas = null;
    O.miniInMainCanvas = false;
    O.miniScratch = null;
    O.miniScratchCtx = null;
    O.cachedStage = null;
    O.targetEl = null;
    O.obsPlayer = null;
    O.obsEnemies = [];
    O.obsMiniPlayer = null;
    O.obsMiniEnemies = [];
    O.obsTargetRing = null;
    O.autoSawOn = false;
    O.autoPreMoveSig = '';
    O.autoPauseUntil = 0;
  }

  window.__vlmOrbitRelease = releaseOrbitKeys;

  function matchSkillInList(normTxt, list) {
    if (!normTxt) return null;
    for (var i = 0; i < list.length; i++) {
      if (normTxt === list[i].key) return list[i];
    }
    for (var j = 0; j < list.length; j++) {
      if (normTxt.indexOf(list[j].key) >= 0) return list[j];
    }
    return null;
  }

  function resolveOrbitStyle() {
    var cfgStyle = normalizeOrbitStyleJs(A.cfg.thapNeDonStyle);
    var cleanedSkill = String(A.cfg.thapMainSkill || '').replace(/\([^)]*\)/g, ' ');
    var chosenRaw = noAccent(cleanedSkill);
    var mChosen = chosenRaw ? matchSkillInList(chosenRaw, ORBIT_MELEE_SKILLS) : null;
    var rChosen = chosenRaw ? matchSkillInList(chosenRaw, ORBIT_RANGED_SKILLS) : null;

    if (cfgStyle === 'MELEE') {
      return { style: 'melee', skill: (mChosen || rChosen) ? (mChosen || rChosen).label : '' };
    }
    if (cfgStyle === 'RANGED') {
      return { style: 'ranged', skill: (mChosen || rChosen) ? (mChosen || rChosen).label : '' };
    }

    if (mChosen) return { style: 'melee', skill: mChosen.label };
    if (rChosen) return { style: 'ranged', skill: rChosen.label };
    return { style: '', skill: '' };
  }

  function isInTowerNow() {
    try {
      if (A.st && Date.now() - A.stT < 15000) {
        if (A.st.o && A.st.o !== 'game') return false;
        if (A.st.dead || (typeof A.st.hp === 'number' && A.st.hp <= 0)) return false;
        if (A.st.noi) return A.st.noi === 'thap';
      }
      if (A.info) {
        if (A.info.dead) return false;
        if (A.info.tower === true) return true;
      }
      var mBtn = document.getElementById('mswBtn');
      var mTxt = mBtn ? noAccent(mBtn.textContent || '') : '';
      if (mTxt.indexOf('leo thap') >= 0) {
        var stg = O.cachedStageLbl && O.cachedStageLbl.isConnected ? O.cachedStageLbl : (O.cachedStageLbl = document.getElementById('stageLbl'));
        var stTxt = stg ? noAccent(stg.textContent || '') : '';
        if (stTxt.indexOf('chua di') >= 0) return false;
        if (stTxt.indexOf('tang') >= 0 || stTxt.indexOf('thap') >= 0 || (A.info && A.info.mode === 'tower')) {
          return true;
        }
      }
    } catch (e) {}
    return false;
  }

  function isTowerOrbitEnabled() {
    return !!(A.cfg.on && (A.cfg.thapNeDon || A.cfg.thap));
  }

  function isOrbitEligible() {
    if (!isTowerOrbitEnabled()) return false;
    if (isUserControlling(3500)) return false;
    try {
      var ae = document.activeElement;
      if (ae) {
        var tg = (ae.tagName || '').toUpperCase();
        if (tg === 'INPUT' || tg === 'TEXTAREA' || tg === 'SELECT') return false;
      }
      if (isModalLocked() || document.documentElement.classList.contains('__vlm-stealth')) return false;
      if (isAuthOrProtectedModalOpen()) return false;
      var md = document.getElementById('modal');
      if (md && !md.classList.contains('hidden')) return false;
      var popIds = ['mswMenu', 'cfModal', 'confirmModal', 'cModal', 'itemModal', 'detailModal'];
      for (var pi = 0; pi < popIds.length; pi++) {
        var pe = document.getElementById(popIds[pi]);
        if (pe && !pe.classList.contains('hidden') && pe.offsetParent !== null) return false;
      }
    } catch (e) { return false; }
    return isInTowerNow();
  }

  // Công thức elip mặt đất isometric đồng nhất cho Đánh gần (R=90, 12 nhịp/vòng) và Đánh xa (R=400, 18 nhịp/vòng)
  function computeOrbitParams(style, stageRect) {
    var minDim = stageRect ? Math.min(stageRect.width, stageRect.height) : 360;
    var isMelee = (style === 'melee');
    var rx = isMelee
      ? Math.max(54, Math.min(76, minDim * 0.165))   // R=90: ôm sát ngoài hitbox thân quái
      : Math.max(125, Math.min(185, minDim * 0.38)); // R=400: giữ tầm xa rộng quanh mục tiêu
    return {
      isMelee: isMelee,
      worldRadius: isMelee ? 90 : 400,
      stepsPerCircle: isMelee ? 12 : 18,
      stepRad: isMelee ? (Math.PI / 6) : (Math.PI / 9), // 30° (12 nhịp) vs 20° (18 nhịp)
      rx: rx,
      ry: rx * (isMelee ? 0.74 : 0.78),
      minStridePx: isMelee ? 42 : 48,
      maxStridePx: isMelee ? 95 : Math.max(95, Math.min(155, minDim * 0.36)),
      cycleMs: isMelee ? 480 : 500
    };
  }

  function pushOutOfEnemyHitbox(gx, gy, tx, ty) {
    if (Math.abs(gx - tx) < 28 && (ty - gy) > 6 && (ty - gy) < 56) {
      var side = (gx >= tx) ? 1 : -1;
      return { x: tx + side * 36, y: gy };
    }
    return { x: gx, y: gy };
  }

  // Đọc tỷ lệ máu (HP) hiện tại (0..1) từ state, info, phần tử thanh máu đã cache hoặc canvas HUD
  function readCharacterHpRatio(now) {
    try {
      if (A.st && now - A.stT < 15000) {
        if (typeof A.st.hp === 'number' && typeof A.st.maxHp === 'number' && A.st.maxHp > 0) {
          return Math.max(0, Math.min(1, A.st.hp / A.st.maxHp));
        }
        if (typeof A.st.hpPct === 'number' && isFinite(A.st.hpPct)) {
          return Math.max(0, Math.min(1, A.st.hpPct > 1 ? A.st.hpPct / 100 : A.st.hpPct));
        }
      }
      if (A.info) {
        if (typeof A.info.hp === 'number' && typeof A.info.maxHp === 'number' && A.info.maxHp > 0) {
          return Math.max(0, Math.min(1, A.info.hp / A.info.maxHp));
        }
        if (typeof A.info.hpPct === 'number' && isFinite(A.info.hpPct)) {
          return Math.max(0, Math.min(1, A.info.hpPct > 1 ? A.info.hpPct / 100 : A.info.hpPct));
        }
      }
      if (!O.cachedHpEl || !O.cachedHpEl.isConnected) {
        if (now - O.lastHpElScanAt > 2000) {
          O.lastHpElScanAt = now;
          O.cachedHpEl = document.querySelector('#hpBar, #hpFill, #hpText, #hpVal, #pHp, .hp-bar, .hp-fill, [data-hp]');
        }
      }
      var el = O.cachedHpEl;
      if (el && el.isConnected && el.offsetParent !== null) {
        var m = /(\d+)\s*\/\s*(\d+)/.exec(el.textContent || '');
        if (m) {
          var cur = Number(m[1]), mx = Number(m[2]);
          if (mx > 0 && cur >= 0 && cur <= mx) return cur / mx;
        }
        var wStr = el.style && el.style.width;
        if (wStr && wStr.indexOf('%') > 0) {
          var wp = parseFloat(wStr);
          if (isFinite(wp) && wp >= 0 && wp <= 100) return wp / 100;
        }
      }
      if (O.obsCanvasHp && now - O.obsCanvasHp.at < 2000) {
        return O.obsCanvasHp.ratio;
      }
    } catch (eHp) {}
    return -1;
  }

  function readCurrentTowerFloor() {
    try {
      if (A.st && Date.now() - A.stT < 15000) {
        var sf = Number(A.st.thapFloor || A.st.floor || A.st.tang || -1);
        if (isFinite(sf) && sf > 0) return sf;
      }
      if (A.info) {
        var inf = Number(A.info.thapFloor || A.info.floor || A.info.tang || -1);
        if (isFinite(inf) && inf > 0) return inf;
      }
      var stg = O.cachedStageLbl && O.cachedStageLbl.isConnected ? O.cachedStageLbl : (O.cachedStageLbl = document.getElementById('stageLbl'));
      if (stg) {
        var m = /(\d+)/.exec(stg.textContent || '');
        if (m) {
          var nf = Number(m[1]);
          if (isFinite(nf) && nf > 0) return nf;
        }
      }
      if (O.detectedFloorFromCanvas > 0) return O.detectedFloorFromCanvas;
    } catch (eFl) {}
    return -1;
  }

  // Đọc vị trí thật của nhân vật chuẩn hóa theo tâm thật hình thoi (ndx, ndy trong -1..+1; (0,0) = đúng tâm map)
  function readTrueMapPosition(now) {
    var srcs = [];
    if (A.st && now - A.stT < 15000) srcs.push(A.st);
    if (A.info) srcs.push(A.info);
    for (var i = 0; i < srcs.length; i++) {
      var s = srcs[i];
      if (!s || typeof s !== 'object') continue;
      var px = typeof s.x === 'number' ? s.x : (typeof s.px === 'number' ? s.px : null);
      var py = typeof s.y === 'number' ? s.y : (typeof s.py === 'number' ? s.py : null);
      var mw = typeof s.mapW === 'number' ? s.mapW : (typeof s.w === 'number' ? s.w : null);
      var mh = typeof s.mapH === 'number' ? s.mapH : (typeof s.h === 'number' ? s.h : null);
      if (px !== null && py !== null && mw && mh && mw > 0 && mh > 0) {
        var mapCx = typeof s.mapCx === 'number' ? s.mapCx : (mw * 0.5);
        var mapCy = typeof s.mapCy === 'number' ? s.mapCy : (mh * 0.5);
        var sNdx = (px - mapCx) / (mw * 0.5);
        var sNdy = (py - mapCy) / (mh * 0.5);
        O.estNdx = sNdx;
        O.estNdy = sNdy;
        return { hasMapPos: true, ndx: sNdx, ndy: sNdy, source: 'state' };
      }
    }

    if (O.obsMiniPlayer && now - O.obsMiniPlayer.at < 2500 && typeof O.obsMiniPlayer.ndx === 'number') {
      return {
        hasMapPos: true,
        ndx: O.obsMiniPlayer.ndx,
        ndy: O.obsMiniPlayer.ndy,
        source: 'minimap'
      };
    }

    // Dự phòng Dead-Reckoning (kể cả khi bật Xoá bản đồ): vẫn giữ ước lượng lệch tâm để tự kéo về giữa bản đồ
    return {
      hasMapPos: true,
      ndx: O.estNdx,
      ndy: O.estNdy,
      source: 'estimated'
    };
  }

  // Tính tọa độ CHÂN nhân vật (px, py) và CHÂN mục tiêu đang chọn (tx, ty) theo CSS pixel màn hình
  function resolveScreenEntities(now, stage) {
    var r = stage.rect;
    var isPortrait = r.height > r.width;
    var nameToFeetPx = Math.min(56, Math.max(36, Math.min(r.width, r.height) * 0.135));

    // 1. Vị trí CHÂN nhân vật
    var px = r.left + r.width * 0.50;
    var py = r.top + r.height * (isPortrait ? 0.495 : 0.50);
    if (O.obsPlayer && now - O.obsPlayer.at < 900) {
      var estPx = r.left + O.obsPlayer.nx * r.width;
      var estPy = r.top + O.obsPlayer.ny * r.height + nameToFeetPx;
      if (estPx > r.left + r.width * 0.12 && estPx < r.left + r.width * 0.88 &&
          estPy > r.top + r.height * 0.22 && estPy < r.top + r.height * 0.82) {
        px = estPx;
        py = estPy;
      }
    }

    // 2. Ưu tiên #1: Vòng tròn đỏ dưới chân mục tiêu ĐANG CHỌN
    if (O.obsTargetRing && now - O.obsTargetRing.at < 360) {
      var rx = r.left + O.obsTargetRing.nx * r.width;
      var ry = r.top + O.obsTargetRing.ny * r.height;
      return {
        px: px, py: py,
        hasTarget: true,
        tx: rx, ty: ry,
        targetSource: 'red_ring'
      };
    }

    // 3. Ưu tiên #2: Tên quái hiển thị trên canvas chính (tươi 340ms để nhận biết hết quái tức thì)
    var freshEnemies = [];
    for (var i = 0; i < O.obsEnemies.length; i++) {
      var e = O.obsEnemies[i];
      if (now - e.at < 340) freshEnemies.push(e);
    }
    O.obsEnemies = freshEnemies;

    if (freshEnemies.length > 0) {
      var best = null;
      var bestScore = 1e9;
      var hasPrev = (O.lastTargetAt > 0 && now - O.lastTargetAt < 900);
      for (var j = 0; j < freshEnemies.length; j++) {
        var fe = freshEnemies[j];
        var ex = r.left + fe.nx * r.width;
        var ey = r.top + fe.ny * r.height + nameToFeetPx;
        var dPlayer = Math.hypot(ex - px, ey - py);
        var dPrev = hasPrev ? Math.hypot(ex - O.lastTargetScreenX, ey - O.lastTargetScreenY) : dPlayer;
        var score = hasPrev ? (dPrev * 0.65 + dPlayer * 0.35) : dPlayer;
        if (score < bestScore) {
          bestScore = score;
          best = { tx: ex, ty: ey };
        }
      }
      if (best) {
        return {
          px: px, py: py,
          hasTarget: true,
          tx: best.tx, ty: best.ty,
          targetSource: 'canvas_name'
        };
      }
    }

    // 4. Ưu tiên #3: Chấm đỏ quái gần nhất trên Minimap
    var freshMiniE = [];
    for (var k = 0; k < O.obsMiniEnemies.length; k++) {
      if (now - O.obsMiniEnemies[k].at < 420) freshMiniE.push(O.obsMiniEnemies[k]);
    }
    O.obsMiniEnemies = freshMiniE;
    if (O.obsMiniPlayer && now - O.obsMiniPlayer.at < 1500 && freshMiniE.length > 0) {
      var bestM = null, bestMD = 1e9;
      for (var m = 0; m < freshMiniE.length; m++) {
        var mdx = freshMiniE[m].nx - O.obsMiniPlayer.nx;
        var mdy = freshMiniE[m].ny - O.obsMiniPlayer.ny;
        var md = Math.hypot(mdx, mdy);
        if (md > 0.004 && md < bestMD) {
          bestMD = md;
          bestM = { dx: mdx, dy: mdy };
        }
      }
      if (bestM) {
        var scalePx = Math.min(r.width, r.height) * 2.2;
        return {
          px: px, py: py,
          hasTarget: true,
          tx: px + bestM.dx * scalePx,
          ty: py + bestM.dy * scalePx,
          targetSource: 'minimap_dot'
        };
      }
    }

    return {
      px: px, py: py,
      hasTarget: false,
      tx: px, ty: py,
      targetSource: 'none'
    };
  }

  function dispatchOrbitPointer(target, type, x, y) {
    try {
      var pEv = (typeof PointerEvent === 'function')
        ? new PointerEvent(type, {
            clientX: x,
            clientY: y,
            bubbles: true,
            cancelable: true,
            button: 0,
            buttons: type === 'pointerup' || type === 'mouseup' ? 0 : 1,
            pointerId: 1,
            pointerType: 'mouse',
            isPrimary: true
          })
        : null;
      if (pEv && type.indexOf('pointer') === 0) {
        target.dispatchEvent(pEv);
        return;
      }
      var mEv = new MouseEvent(type, {
        clientX: x,
        clientY: y,
        bubbles: true,
        cancelable: true,
        button: 0,
        buttons: type === 'mouseup' ? 0 : 1
      });
      target.dispatchEvent(mEv);
    } catch (e) {}
  }

  // Click chuột trái trực tiếp tại tọa độ màn hình (rawX, rawY) + cập nhật ước lượng độ lệch tâm estNdx/estNdy
  function clickScreenPoint(stage, rawX, rawY, basePx, basePy) {
    if (!stage || !stage.canvas || !stage.rect) return false;
    var r = stage.rect;
    var isPortrait = r.height > r.width;
    var minX = r.left + r.width * 0.14;
    var maxX = r.left + r.width * (isPortrait ? 0.86 : 0.82);
    var minY = r.top + r.height * (isPortrait ? 0.27 : 0.18);
    var maxY = r.top + r.height * (isPortrait ? 0.79 : 0.79);
    var cx = Math.max(minX, Math.min(maxX, rawX));
    var cy = Math.max(minY, Math.min(maxY, rawY));

    // Cập nhật dự phòng ước lượng vị trí tương đối trên hình thoi
    if (typeof basePx === 'number' && typeof basePy === 'number') {
      var minD = Math.min(r.width, r.height) || 360;
      var ddx = (cx - basePx) / minD;
      var ddy = (cy - basePy) / minD;
      O.estNdx = Math.max(-0.95, Math.min(0.95, O.estNdx + ddx * 0.42));
      O.estNdy = Math.max(-0.95, Math.min(0.95, O.estNdy + ddy * 0.56));
    }

    try {
      dispatchOrbitPointer(stage.canvas, 'pointerdown', cx, cy);
      dispatchOrbitPointer(stage.canvas, 'mousedown', cx, cy);
      setTimeout(function () {
        try {
          dispatchOrbitPointer(stage.canvas, 'pointerup', cx, cy);
          dispatchOrbitPointer(stage.canvas, 'mouseup', cx, cy);
          var cEv = new MouseEvent('click', {
            clientX: cx,
            clientY: cy,
            bubbles: true,
            cancelable: true,
            button: 0,
            buttons: 0
          });
          stage.canvas.dispatchEvent(cEv);
        } catch (eUp) {}
      }, 26);
      return true;
    } catch (eClk) {
      return false;
    }
  }

  // CÔNG THỨC XOAY VÒNG TRÒN ĐỒNG NHẤT (vlmauto-30):
  // - Đánh gần (R=90): tiến pha góc liên tục + đẩy ra ngoài hitbox thân quái
  // - Đánh xa (R=400): giữ tầm xa rộng quanh mục tiêu, lượn ngang khi quái áp sát
  // - Cả hai đều chặn sớm trước 4 cạnh & 4 góc hình thoi (predDanger >= 0.80 -> đảo chiều hoặc về giữa map)
  function computeNextCircularClick(ent, mapPos, params) {
    var px = ent.px, py = ent.py;
    var dx = px - ent.tx;
    var dy = py - ent.ty;
    var dist = Math.hypot(dx, dy);
    var curDanger = arenaDangerScore(mapPos.ndx, mapPos.ndy);

    var useContinuousTheta = (params.isMelee && dist <= params.rx * 1.35) || (dist <= 16);
    var baseAng = useContinuousTheta ? O.theta : Math.atan2(dy / 0.76, dx);

    var stepAng = params.stepRad;
    if (!params.isMelee) {
      if (dist < params.rx * 0.78) stepAng = Math.max(params.stepRad, Math.PI * 0.22);
      else if (dist > params.rx * 1.28) stepAng = params.stepRad * 0.55;
    } else if (dist > params.rx * 1.35) {
      stepAng = params.stepRad * 0.50;
    }

    var evalStep = function (gx, gy, ang, dir) {
      if (params.isMelee) {
        var safePt = pushOutOfEnemyHitbox(gx, gy, ent.tx, ent.ty);
        gx = safePt.x;
        gy = safePt.y;
      }
      var mvx = gx - px;
      var mvy = gy - py;
      var mvLen = Math.hypot(mvx, mvy);
      if (mvLen < params.minStridePx) {
        if (mvLen < 4) {
          var tangAng = baseAng + dir * (Math.PI * 0.5);
          mvx = Math.cos(tangAng) * params.minStridePx;
          mvy = Math.sin(tangAng) * params.minStridePx * 0.76;
        } else {
          var sUp = params.minStridePx / mvLen;
          mvx *= sUp;
          mvy *= sUp;
        }
        mvLen = Math.hypot(mvx, mvy) || params.minStridePx;
      } else if (mvLen > params.maxStridePx) {
        mvx = (mvx / mvLen) * params.maxStridePx;
        mvy = (mvy / mvLen) * params.maxStridePx;
        mvLen = params.maxStridePx;
      }

      var ux = mvx / (mvLen || 1);
      var uy = mvy / (mvLen || 1);
      var predDanger = arenaDangerScore(mapPos.ndx + ux * 0.17, mapPos.ndy + uy * 0.17);
      return { x: px + mvx, y: py + mvy, ang: ang, predDanger: predDanger, dir: dir };
    };

    var calcOrbitCandidate = function (dir) {
      var nextAng = baseAng + dir * stepAng;
      var gx = ent.tx + params.rx * Math.cos(nextAng);
      var gy = ent.ty + params.ry * Math.sin(nextAng);
      return evalStep(gx, gy, nextAng, dir);
    };

    var primary = calcOrbitCandidate(O.orbitDir);
    if (primary.predDanger < 0.80) {
      O.theta = primary.ang;
      return { x: primary.x, y: primary.y, mustReturnCenter: false };
    }

    // Chiều hiện tại tiến gần cạnh/góc -> đảo chiều xoay ngược lại vào lòng bản đồ
    var flipped = calcOrbitCandidate(-O.orbitDir);
    if (flipped.predDanger < 0.80 || (flipped.predDanger < primary.predDanger - 0.05 && flipped.predDanger < 0.88)) {
      O.orbitDir = -O.orbitDir;
      O.theta = flipped.ang;
      return { x: flipped.x, y: flipped.y, mustReturnCenter: false };
    }

    // Cả 2 chiều đều tiến vào vùng cạnh/góc -> lượn mạnh về giữa map hoặc bật chạy thẳng về giữa bản đồ
    var safer = (flipped.predDanger < primary.predDanger) ? flipped : primary;
    var toCx = -mapPos.ndx * 1.45;
    var toCy = -mapPos.ndy;
    var cLen = Math.hypot(toCx, toCy) || 1;
    var blended = evalStep(
      px + (safer.x - px) * 0.30 + (toCx / cLen) * params.maxStridePx * 0.70,
      py + (safer.y - py) * 0.30 + (toCy / cLen) * params.maxStridePx * 0.70,
      safer.ang,
      safer.dir
    );
    if (blended.predDanger < curDanger - 0.04 && blended.predDanger < 0.86) {
      O.orbitDir = safer.dir;
      O.theta = blended.ang;
      return { x: blended.x, y: blended.y, mustReturnCenter: false };
    }

    return { x: px, y: py, mustReturnCenter: true };
  }

  // Kích hoạt chế độ "CHẠY VỀ GIỮA BẢN ĐỒ TRƯỚC, TẤN CÔNG SAU":
  // Tạm dừng Auto đánh để nhân vật chạy thẳng một mạch về đúng giữa map, không bị quái kéo lại góc/cạnh!
  function startReturnToCenter(now, reason, lockMs) {
    O.returningToCenter = true;
    O.returnReason = reason || 'clear';
    O.returnStartedAt = now;
    O.returnLockUntil = Math.max(O.returnLockUntil || 0, now + (lockMs || 950));
    O.stuckBeats = 0;
    if (reason === 'low_hp') {
      O.healWaitUntil = now + 1300;
      O.lowHpCooldownUntil = now + 4500;
    }
    pauseGameAutoForReturn();
  }

  function finishReturnToCenter(now) {
    O.returningToCenter = false;
    O.returnReason = '';
    O.returnStartedAt = 0;
    O.returnLockUntil = 0;
    O.healWaitUntil = 0;
    O.lastClickAt = 0; // Sẵn sàng xoay vòng tròn ngay khi bắt đầu đánh
    O.estNdx = 0;
    O.estNdy = 0;
    resumeGameAutoAfterReturn();
  }

  function orbitTick() {
    if (!isTowerOrbitEnabled() || !isOrbitEligible()) {
      if (O.active || O.moving || O.wasInTower || window.__vlmOrbitCanvasSpyInstalled || window.__vlmOrbitSpyActive) {
        releaseOrbitKeys();
      }
      return false; // Báo scheduler chuyển về chế độ nghỉ 800ms ngoài tháp
    }

    var now = Date.now();
    var stage = refreshStageAndMinimapCache(now, !O.wasInTower);
    if (!stage) {
      if (O.active || O.moving || window.__vlmOrbitCanvasSpyInstalled) releaseOrbitKeys();
      return false;
    }

    installArenaCanvasSpy();

    if (!O.wasInTower) {
      O.wasInTower = true;
      O.towerEnteredAt = now;
      O.lastClickAt = 0;
      O.peakHpRatio = 1.0;
      O.lastHpRatio = -1;
      O.estNdx = 0;
      O.estNdy = 0;
    }

    // Quét minimap siêu nhẹ (56x56 offscreen, 260ms, tự bỏ qua nếu vector hook đang chạy)
    scanMinimapLightweight(now);

    var resolved = resolveOrbitStyle();
    var style = resolved.style || 'ranged';
    var params = computeOrbitParams(style, stage.rect);
    O.style = style;
    O.skillName = resolved.skill || '';

    // 1. Phát hiện vượt tầng (state / info / #stageLbl / tên quái "Tầng X" trên canvas)
    var curFloor = readCurrentTowerFloor();
    var stgEl = O.cachedStageLbl && O.cachedStageLbl.isConnected ? O.cachedStageLbl : (O.cachedStageLbl = document.getElementById('stageLbl'));
    var curStageText = stgEl ? noAccent(stgEl.textContent || '') : '';
    var floorChanged = false;
    if (curFloor > 0) {
      if (O.lastFloorNum > 0 && curFloor > O.lastFloorNum) floorChanged = true;
      O.lastFloorNum = curFloor;
    }
    if (curStageText && O.lastStageText && curStageText !== O.lastStageText && curStageText.indexOf('tang') >= 0) {
      floorChanged = true;
    }
    if (curStageText) O.lastStageText = curStageText;

    var mapPos = readTrueMapPosition(now);
    var ent = resolveScreenEntities(now, stage);
    var danger = arenaDangerScore(mapPos.ndx, mapPos.ndy);
    var centerDist = Math.abs(mapPos.ndx) + Math.abs(mapPos.ndy);

    // 2. Theo dõi lượng máu: mất >= 50% máu (hoặc HP <= 50%) -> TỰ ĐỘNG chạy ngay về giữa bản đồ để kịp hồi máu!
    var hpRatio = readCharacterHpRatio(now);
    if (hpRatio > 0) {
      if (hpRatio > O.peakHpRatio) O.peakHpRatio = hpRatio;
      if (hpRatio >= 0.90) O.peakHpRatio = hpRatio;
      O.lastHpRatio = hpRatio;
      var lostHalfHp = (hpRatio <= 0.50) || (O.peakHpRatio >= 0.70 && (O.peakHpRatio - hpRatio) >= 0.50);
      if (lostHalfHp && !O.returningToCenter && now >= (O.lowHpCooldownUntil || 0)) {
        startReturnToCenter(now, 'low_hp', 1400);
        O.peakHpRatio = hpRatio;
        if (now - O.lastNoteAt > 6000) {
          O.lastNoteAt = now;
          note('❤️ Máu giảm ' + Math.round(hpRatio * 100) + '% → chạy về giữa bản đồ hồi máu');
        }
      }
    }

    // 3. Vừa lên tầng -> CHẠY VỀ GIỮA BẢN ĐỒ TRƯỚC, SAU ĐÓ MỚI TẤN CÔNG QUÁI!
    if (floorChanged) {
      O.hadTargetInFloor = false;
      O.lastTargetAt = 0;
      O.noTargetSince = now;
      O.peakHpRatio = 1.0;
      O.obsEnemies = [];
      O.obsMiniEnemies = [];
      O.obsTargetRing = null;
      if (centerDist > 0.14) {
        startReturnToCenter(now, 'floor', 1300);
      }
    }

    // 4. Hết quái -> NGAY LẬP TỨC chạy về giữa bản đồ trước khi đợt mới ra (chạy về giữa TRƯỚC, đánh SAU)
    if (ent.hasTarget) {
      O.noTargetSince = 0;
    } else {
      if (!O.noTargetSince) O.noTargetSince = now;
      if (!O.returningToCenter && (now - O.noTargetSince >= 200) && centerDist > 0.16) {
        startReturnToCenter(now, 'clear', 1000);
      }
    }

    // 5. Chạm vùng cấm (bất kỳ cạnh hoặc góc nào của hình thoi, danger >= 0.90) -> lập tức chạy về giữa map
    if (danger >= 0.90 && !O.returningToCenter) {
      startReturnToCenter(now, 'wall', 900);
    }

    // 6. ĐANG CHẠY VỀ GIỮA BẢN ĐỒ (ưu tiên tuyệt đối: Auto đánh tạm tắt, click liên tục thẳng về tâm map cho tới khi tới giữa map!)
    if (O.returningToCenter) {
      var returnElapsed = now - (O.returnStartedAt || now);
      var reachedCenter = (centerDist <= 0.16);
      // Nếu là chạy về để hồi máu: chờ đã về tới giữa map VÀ (hồi lên >= 72% hoặc đủ thời gian hồi ngắn 1.3s)
      var healDone = (O.returnReason !== 'low_hp') || (hpRatio >= 0.72) || (now >= (O.healWaitUntil || 0));
      var timedOut = (returnElapsed > 2600);

      if ((reachedCenter && healDone) || timedOut) {
        finishReturnToCenter(now);
        return true;
      }

      // Đảm bảo Auto đánh vẫn tạm tắt trong lúc chạy về giữa map
      pauseGameAutoForReturn();

      if (!reachedCenter && now - (O.lastRetClickAt || 0) >= 150) {
        O.lastRetClickAt = now;
        var toCx = -mapPos.ndx * 1.45;
        var toCy = -mapPos.ndy;
        var lenC = Math.hypot(toCx, toCy);
        if (lenC > 0.01) {
          var strideC = Math.min(stage.rect.width, stage.rect.height) * 0.35;
          clickScreenPoint(stage, ent.px + (toCx / lenC) * strideC, ent.py + (toCy / lenC) * strideC, ent.px, ent.py);
        }
      }
      O.active = true;
      return true;
    }

    // 7. Đã ở giữa map nhưng chưa có mục tiêu -> giữ yên giữa map và bật sẵn Auto đánh chờ quái ra
    if (!ent.hasTarget) {
      ensureGameAutoOn(true);
      return true;
    }

    // 8. Nhịp tấn công & xoay vòng tròn (480ms đánh gần / 500ms đánh xa)
    if (now < O.autoPauseUntil) return true;
    if (now - O.lastClickAt < params.cycleMs) {
      ensureGameAutoOn(false);
      return true;
    }

    // Kẹt không nhúc nhích 2 nhịp khi đang lệch xa tâm -> kéo ngay về giữa bản đồ
    var movedM = Math.hypot(mapPos.ndx - O.prevMapX, mapPos.ndy - O.prevMapY);
    O.prevMapX = mapPos.ndx;
    O.prevMapY = mapPos.ndy;
    if (movedM < 0.025 && danger > 0.48) {
      O.stuckBeats++;
      if (O.stuckBeats >= 2) {
        startReturnToCenter(now, 'stuck', 900);
        return true;
      }
    } else {
      O.stuckBeats = 0;
    }

    O.hadTargetInFloor = true;
    O.lastTargetScreenX = ent.tx;
    O.lastTargetScreenY = ent.ty;
    O.lastTargetAt = now;

    // 9. Tính bước xoay vòng tròn đồng nhất (R=400 đánh xa / R=90 đánh gần), tuyệt đối tránh 4 cạnh & 4 góc
    var plan = computeNextCircularClick(ent, mapPos, params);
    if (plan.mustReturnCenter) {
      startReturnToCenter(now, 'wall', 900);
      return true;
    }

    var autoBtnBefore = findGameAutoButton();
    if (autoBtnBefore && detectGameAutoState(autoBtnBefore) !== 'off') {
      O.autoSawOn = true;
      O.autoPreMoveSig = autoBtnLightSignature(autoBtnBefore);
    }

    O.lastClickAt = now;
    O.lastAt = now;
    clickScreenPoint(stage, plan.x, plan.y, ent.px, ent.py);

    setTimeout(function () {
      try { if (A.cfg.on && !O.returningToCenter) ensureGameAutoOn(true); } catch (eA) {}
    }, 90);

    if (!O.active) {
      O.active = true;
      if (now - O.lastNoteAt > 20000) {
        O.lastNoteAt = now;
        var skInfo = O.skillName ? (' · ' + O.skillName) : '';
        note('🌀 Leo Tháp: xoay vòng tròn tránh cạnh/góc + tự về tâm (' + (style === 'melee' ? 'Đánh gần R90' : 'Đánh xa R400') + skInfo + ')');
      }
    }
    return true;
  }

  function scheduleNextOrbitTick() {
    if (window.__vlmOrbitTimer) {
      clearTimeout(window.__vlmOrbitTimer);
      window.__vlmOrbitTimer = 0;
    }
    var inTower = false;
    try { inTower = !!orbitTick(); } catch (eOrb) { inTower = false; }
    // Tối ưu CPU/GPU tối đa: 100ms khi đang trong Tháp, 800ms ở chế độ nghỉ ngoài Tháp!
    var delayMs = inTower ? 100 : 800;
    window.__vlmOrbitTimer = setTimeout(scheduleNextOrbitTick, delayMs);
  }

  window.__vlmAuto = {
    ver: VER,
    setCfg: setCfg,
    resetDay: resetDay,
    onMidnight000000: onMidnight000000,
    onNewVnDay: onNewVnDay,
    tick: tick,
    orbitTick: orbitTick,
    orbitState: function () {
      return {
        active: O.active,
        returning: O.returningToCenter,
        returnReason: O.returnReason,
        style: O.style,
        skill: O.skillName,
        hpRatio: O.lastHpRatio,
        theta: O.theta,
        orbitDir: O.orbitDir,
        miniPlayer: O.obsMiniPlayer,
        targetRing: O.obsTargetRing,
        enemies: O.obsEnemies.length
      };
    },
    trangThai: function () {
      return { on: !!A.cfg.on, busy: A.busy, last: A.last, lastAt: A.lastAt, st: A.st, orbit: { active: O.active, returning: O.returningToCenter, style: O.style, skill: O.skillName } };
    }
  };

  setCfg(CFG0, TOUCH0, IMM0);
  if (IMM0 && A.cfg.on) {
    setTimeout(function () { tick().catch(function () {}); }, 250);
  }
  window.__vlmTickTimer = setInterval(function () { tick().catch(function () {}); }, 15000);
  // Chỉ khởi động lịch orbit tự thích nghi (không cài Canvas2D spy sẵn khi chưa vào Tháp -> 0% lag ngoài Tháp!)
  window.__vlmOrbitTimer = setTimeout(scheduleNextOrbitTick, 250);
  return 'installed';
})();
    """.trimIndent()
}
