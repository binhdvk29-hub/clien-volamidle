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

data class TrainingMapOption(
    val name: String,
    val levelRange: String,
    val groupName: String
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
    val afterDailyEnabled: Boolean = false,
    val afterDailyFarm: Boolean = false,
    val farmMap: String = "",
    val thap: Boolean = false,
    val thapAfterMode: String = THAP_MODE_NORMAL,
    val thapNeDon: Boolean = false,
    val thapNeDonStyle: String = ORBIT_AUTO,
    val thapMainSkill: String = "",
    val qht: Boolean = false,
    val qhtMua: Boolean = false,
    val tbkTinVat: Boolean = false,
    val bangLbdt: Boolean = false,
    val bangPd: Boolean = false,
    val doiNguyenLieu: Set<String> = emptySet(),
    val tbk: Boolean = false
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
        o.put("afterDailyEnabled", afterDailyEnabled)
        o.put("afterDailyFarm", afterDailyFarm)
        o.put("farmMap", farmMap)
        o.put("thap", thap)
        o.put("thapAfterMode", normalizeThapAfterMode(thapAfterMode))
        o.put("thapNeDon", thapNeDon)
        o.put("thapNeDonStyle", normalizeOrbitStyle(thapNeDonStyle))
        o.put("thapMainSkill", normalizeMainSkillId(thapMainSkill))
        o.put("qht", qht)
        o.put("qhtMua", qhtMua)
        o.put("tbkTinVat", tbkTinVat)
        o.put("bangLbdt", bangLbdt)
        o.put("bangPd", bangPd)
        o.put("tbk", tbk)

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

        const val THAP_MODE_ORBIT = "ORBIT"
        const val THAP_MODE_NORMAL = "NORMAL"

        fun normalizeThapAfterMode(raw: String?): String {
            return when (raw?.trim()?.uppercase()) {
                THAP_MODE_ORBIT -> THAP_MODE_ORBIT
                else -> THAP_MODE_NORMAL
            }
        }

        val ALL_TRAINING_MAPS: List<TrainingMapOption> = listOf(
            TrainingMapOption("Mạc Bắc Thảo Nguyên", "Cấp 130–140", "Vi sơn đảo"),
            TrainingMapOption("Vi sơn đảo", "Cấp 130–140", "Vi sơn đảo")
        )

                fun normalizeAllowed130Map(raw: String?): String {
            val clean = java.text.Normalizer.normalize((raw ?: "").lowercase().trim(), java.text.Normalizer.Form.NFD)
                .replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
                .replace('đ', 'd')
            if (clean.contains("vi son") || clean.contains("son dao")) return "Vi sơn đảo"
            return "Mạc Bắc Thảo Nguyên"
        }

        fun vlmSwipeAndTapMapScript(targetMap: String): String {
            val safeTarget = targetMap.replace("'", "\\'")
            return """
                (function() {
                    function noAcc(s) {
                        return String(s || '').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').trim();
                    }
                    var want = noAcc('');
                    if (want.indexOf('Vi sơn đảo') >= 0) want = 'Vi sơn đảo';
                    else want = 'Mạc Bắc Thảo Nguyên';

                    function realTap(el) {
                        if (!el) return false;
                        try { el.scrollIntoView({block: 'center', inline: 'nearest'}); } catch (e) {}
                        var r = el.getBoundingClientRect();
                        var cx = r.left + r.width / 2;
                        var cy = r.top + r.height / 2;
                        ['pointerdown', 'mousedown', 'touchstart', 'pointerup', 'mouseup', 'touchend', 'click'].forEach(function(evName) {
                            try {
                                var ev = new MouseEvent(evName, {bubbles: true, cancelable: true, view: window, clientX: cx, clientY: cy});
                                el.dispatchEvent(ev);
                            } catch (e) {}
                        });
                        try { el.click(); } catch (e) {}
                        return true;
                    }

                    function scrollListDownStep() {
                        var nodes = Array.prototype.slice.call(document.querySelectorAll('div, li, button, span, a'));
                        var scrolled = false;
                        nodes.forEach(function(n) {
                            try {
                                if (n.scrollHeight > n.clientHeight + 24 && n.clientHeight > 60) {
                                    var before = n.scrollTop;
                                    n.scrollTop = n.scrollHeight;
                                    if (n.scrollTop > before) scrolled = true;
                                }
                            } catch (e) {}
                        });
                        try {
                            var winBefore = window.scrollY || window.pageYOffset || 0;
                            window.scrollTo(0, document.body.scrollHeight || 99999);
                            if ((window.scrollY || 0) > winBefore) scrolled = true;
                        } catch (e) {}
                        return scrolled;
                    }

                    function findMapCandidate() {
                        var all = Array.prototype.slice.call(document.querySelectorAll('div, li, button, span, a, p'));
                        var best = null;
                        var bestScore = -9999;
                        for (var i = 0; i < all.length; i++) {
                            var el = all[i];
                            var txt = (el.innerText || el.textContent || '').trim();
                            if (!txt || txt.length > 60) continue;
                            var norm = noAcc(txt);
                            if (norm.indexOf(want) < 0) continue;
                            var isGroupHeader = (txt.indexOf('+') >= 0 || txt.indexOf('-') >= 0 || /cap\s*\d+/i.test(norm));
                            if (isGroupHeader) continue;
                            var r = el.getBoundingClientRect();
                            if (r.width <= 0 || r.height <= 0) continue;
                            var score = 100 - txt.length + (el.tagName === 'BUTTON' || el.tagName === 'LI' ? 20 : 0);
                            if (score > bestScore) {
                                bestScore = score;
                                best = el;
                            }
                        }
                        return best;
                    }

                    for (var s = 0; s < 6; s++) {
                        var hit = findMapCandidate();
                        if (hit) {
                            realTap(hit);
                            return 'TAPPED:' + want;
                        }
                        scrollListDownStep();
                    }
                    scrollListDownStep();
                    var finalHit = findMapCandidate();
                    if (finalHit) {
                        realTap(finalHit);
                        return 'TAPPED_AFTER_SCROLL:' + want;
                    }
                    return 'NOT_FOUND:' + want;
                })();
            """.trimIndent()
        }

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

            val hasTbkKey = o.has("tbk")
            val parsedTbk = if (hasTbkKey) {
                o.optBoolean("tbk", DEF.tbk)
            } else {
                o.optBoolean("thap", false) || o.optBoolean("thapNeDon", false)
            }
            val parsedAfterFarm = o.optBoolean("afterDailyFarm", DEF.afterDailyFarm)
            val parsedThap = if (hasTbkKey) {
                o.optBoolean("thap", DEF.thap) && !parsedAfterFarm
            } else {
                false
            }
            val parsedThapNeDon = if (hasTbkKey) o.optBoolean("thapNeDon", DEF.thapNeDon) else false
            val parsedAfterEnabled = if (o.has("afterDailyEnabled")) {
                o.optBoolean("afterDailyEnabled", DEF.afterDailyEnabled)
            } else {
                parsedAfterFarm || parsedThap
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
                afterDailyEnabled = parsedAfterEnabled,
                afterDailyFarm = parsedAfterFarm,
                farmMap = normalizeAllowed130Map(o.optString("farmMap", "Mạc Bắc Thảo Nguyên")),
                thap = parsedThap,
                thapAfterMode = normalizeThapAfterMode(o.optString("thapAfterMode", DEF.thapAfterMode)),
                thapNeDon = parsedThapNeDon,
                thapNeDonStyle = normalizeOrbitStyle(o.optString("thapNeDonStyle", DEF.thapNeDonStyle)),
                thapMainSkill = normalizeMainSkillId(o.optString("thapMainSkill", DEF.thapMainSkill)),
                qht = o.optBoolean("qht", DEF.qht),
                qhtMua = o.optBoolean("qhtMua", DEF.qhtMua),
                tbkTinVat = o.optBoolean("tbkTinVat", DEF.tbkTinVat),
                bangLbdt = o.optBoolean("bangLbdt", DEF.bangLbdt),
                bangPd = o.optBoolean("bangPd", DEF.bangPd),
                doiNguyenLieu = nguyenLieuSet,
                tbk = parsedTbk
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
        private const val SWEEP_7M_MS = 7L * 60L * 1000L
        const val SCRIPT_VER = "vlmauto-89"
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
    private var periodicKnbSweepJob: Job? = null
    private var periodic7MinSweepJob: Job? = null

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
        startPeriodic10MinKnbSweep()
        startPeriodic7MinSweep()
    }

    private fun startPeriodic7MinSweep() {
        if (periodic7MinSweepJob?.isActive == true) return
        periodic7MinSweepJob = scope.launch {
            while (true) {
                delay(SWEEP_7M_MS)
                try {
                    if (isSystemRunning() && isAnyEnabled()) {
                        trigger7MinSweepOnAllTabs()
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Throwable) {
                }
            }
        }
    }

    fun trigger7MinSweepOnAllTabs() {
        mainHandler.post {
            val total = tabCount().coerceAtMost(installedUrl.size)
            var delayIdx = 0
            for (i in 0 until total) {
                if (!isTabActive(i)) continue
                val tabCfg = cfgs[i]
                if (!tabCfg.on) continue
                val offsetMs = 300L * delayIdx
                delayIdx++
                mainHandler.postDelayed({
                    if (!isTabActive(i)) return@postDelayed
                    val wv = webViewAt(i) ?: return@postDelayed
                    val url = wv.url ?: return@postDelayed
                    if (url == "about:blank") return@postDelayed
                    val cfgJson = cfgs[i].toJson().toString()
                    if (installedUrl[i] != url) {
                        installedUrl[i] = url
                        runCatching {
                            wv.evaluateJavascript(
                                buildAutoScript(i, cfgJson, wv.lastTouchTimeMs, immediate = true),
                                null
                            )
                        }
                    }
                    runCatching {
                        wv.evaluateJavascript(
                            "(function(){try{var a=window.__vlmAuto;if(a&&a.onSweep7Min)a.onSweep7Min(" + cfgJson + ");else if(a&&a.tick)a.tick();}catch(e){}})();",
                            null
                        )
                    }
                }, offsetMs)
            }
        }
    }

    private fun startPeriodic10MinKnbSweep() {
        if (periodicKnbSweepJob?.isActive == true) return
        periodicKnbSweepJob = scope.launch {
            while (true) {
                delay(10L * 60L * 1000L) // Quét đồng bộ 10 phút / lần
                try {
                    if (isSystemRunning() && isAnyEnabled()) {
                        triggerKnbSyncWaveOnAllTabs()
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Throwable) {
                }
            }
        }
    }

    fun triggerKnbSyncWaveOnAllTabs() {
        mainHandler.post {
            val total = tabCount().coerceAtMost(installedUrl.size)
            var delayIdx = 0
            for (i in 0 until total) {
                if (!isTabActive(i)) continue
                val tabCfg = cfgs[i]
                if (!tabCfg.on || tabCfg.knbMua <= 0) continue
                val offsetMs = 350L * delayIdx
                delayIdx++
                mainHandler.postDelayed({
                    if (!isTabActive(i)) return@postDelayed
                    val wv = webViewAt(i) ?: return@postDelayed
                    val url = wv.url ?: return@postDelayed
                    if (url == "about:blank") return@postDelayed
                    val cfgJson = cfgs[i].toJson().toString()
                    if (installedUrl[i] != url) {
                        installedUrl[i] = url
                        runCatching {
                            wv.evaluateJavascript(
                                buildAutoScript(i, cfgJson, wv.lastTouchTimeMs, immediate = true),
                                null
                            )
                        }
                    }
                    runCatching {
                        wv.evaluateJavascript(
                            "(function(){try{var a=window.__vlmAuto;if(a&&a.syncKnbNow)a.syncKnbNow();}catch(e){}})();",
                            null
                        )
                    }
                }, offsetMs)
            }
        }
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
        periodicKnbSweepJob?.cancel()
        periodicKnbSweepJob = null
        periodic7MinSweepJob?.cancel()
        periodic7MinSweepJob = null
    }

    fun saveConfigOnly(tabIndex: Int, json: String?) {
        if (tabIndex !in cfgs.indices) return
        val wasOn = cfgs[tabIndex].on
        val updated = AutoConfig.fromJson(json)
        cfgs[tabIndex] = updated
        // Chi dong bo xuong WebView neu tab dang bat tu dong san (hoac vua bi tat);
        // khi tab dang bat -> bam Luu la quet va trien khai ngay lap tuc.
        if (!wasOn && !updated.on) return
        if (updated.on && (job == null || job?.isActive != true || midnightResetJob?.isActive != true || midnightTradeCardJob?.isActive != true)) {
            start()
        }
        val cfgJson = updated.toJson().toString()
        mainHandler.post {
            runCatching {
                injectTab(
                    i = tabIndex,
                    forceFull = updated.on,
                    cfgJson = cfgJson,
                    immediate = updated.on,
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

    fun onTabEnteredGame(tabIndex: Int) {
        if (tabIndex !in cfgs.indices || tabIndex >= tabCount()) return
        val tabCfg = cfgs[tabIndex]
        if (!tabCfg.on || !isSystemRunning()) return
        installedUrl[tabIndex] = null
        mainHandler.postDelayed({
            if (!isTabActive(tabIndex) || !cfgs[tabIndex].on || !isSystemRunning()) return@postDelayed
            injectTab(
                i = tabIndex,
                forceFull = true,
                cfgJson = cfgs[tabIndex].toJson().toString(),
                immediate = true,
                pushEvenIfOff = false
            )
            val wv = webViewAt(tabIndex) ?: return@postDelayed
            runCatching {
                wv.evaluateJavascript(
                    "(function(){try{var a=window.__vlmAuto;if(a&&a.onEnteredGame)a.onEnteredGame();}catch(e){}})();",
                    null
                )
            }
        }, 1_500L)
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
            buildAutoScript(i, cfgJson, if (immediate) 0L else wv.lastTouchTimeMs, immediate)
        } else {
            buildPushScript(cfgJson, if (immediate) 0L else wv.lastTouchTimeMs, immediate)
        }
        runCatching { wv.evaluateJavascript(script, null) }
    }

    private fun buildPushScript(cfgJson: String, touchMs: Long, immediate: Boolean): String {
        return "(function(){try{var a=window.__vlmAuto;if(a&&a.ver==='" + SCRIPT_VER +
            "'){a.setCfg(" + cfgJson + "," + touchMs + "," + immediate + ");" +
            (if (immediate) "if(a.orbitTick)try{a.orbitTick();}catch(_eo){}if(a.tick)a.tick();" else "") + "}}catch(e){}})();"
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
    if (window.__vlmTickDelayTimer) { clearTimeout(window.__vlmTickDelayTimer); window.__vlmTickDelayTimer = 0; }
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
    ttlMua: false, tt2: true, tt2Mua: false, pb: true, afterDailyEnabled: false, afterDailyFarm: false, farmMap: '', tbk: false, thap: false, thapAfterMode: 'NORMAL', thapNeDon: false, thapNeDonStyle: 'AUTO', thapMainSkill: '', qht: false, qhtMua: false };

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
    pbStartedAt: 0,
    dtLastProgressAt: 0,
    dtLastToday: -1,
    lastSweep10mAt: 0,
    knb0005Day: '',
    knbSyncUntil: 0,
    lastMapSelected: '',
    checkedQueue: [],
    koWaitingRecovery: false,
    koDetectedAt: 0,
    towerMetric: {
      floor: 0,
      secPerFloor: 0,
      floorsPer30m: 0,
      prevFloor: 0,
      prevSecPerFloor: 0,
      leftTowerAt: 0,
      floorEnteredAt: 0,
      wasInTower: false
    },
    info: null,
    gate: 0
  };

  var LOG_STATE = {
    lastRaw: '',
    lastNormKey: '',
    lastSentAt: 0,
    dupCount: 0,
    lastFlushTimer: 0
  };

  function isRareItemOrMilestone(txt) {
    var s = String(txt || '');
    var na = noAccent(s);
    if (na.indexOf('do pho') >= 0) return true;
    if (na.indexOf('hoang kim mon phai') >= 0 || /\bhkmp\b/.test(na)) return true;
    if (na.indexOf('can khon') >= 0 || na.indexOf('vo danh') >= 0 || na.indexOf('than ma') >= 0) return true;
    return false;
  }

  function buildCoalescedLogText(rawText, dupCount) {
    var clean = String(rawText || '').replace(/\s+/g, ' ').trim();
    if (!clean) return '';
    var prefix = isRareItemOrMilestone(clean) ? '🌟 [QUÝ] ' : '🤖 ';
    var suffix = dupCount > 1 ? (' (x' + dupCount + ')') : '';
    return prefix + clean + suffix;
  }

  function pushBridgeLog(formatted) {
    if (!formatted) return;
    A.last = formatted;
    A.lastAt = Date.now();
    try {
      if (window.AndroidBridge && window.AndroidBridge.onGameLog) {
        window.AndroidBridge.onGameLog(TAB, formatted);
      }
    } catch (e) {}
  }

  function normalizeLogGroupKey(txt) {
    return noAccent(txt || '')
      .replace(/\d+(?:[.,]\d+)?/g, '#')
      .replace(/\s+/g, ' ')
      .trim();
  }

  function getBossHkKillCountToday() {
    try {
      var o = JSON.parse(localStorage.getItem(dayKey('bossHkKills')) || 'null');
      if (o && o.d === vnDay() && isFinite(o.n)) return Math.max(0, Math.min(4, Math.floor(o.n)));
    } catch (eB) {}
    return 0;
  }

  function addBossHkKillCountToday() {
    try {
      var cur = getBossHkKillCountToday();
      if (cur >= 4) return cur;
      var next = cur + 1;
      localStorage.setItem(dayKey('bossHkKills'), JSON.stringify({ d: vnDay(), n: next }));
      return next;
    } catch (eB2) { return 0; }
  }

  function publishLiveTelemetry(extraStatus) {
    try {
      if (!window.AndroidBridge || !window.AndroidBridge.onLiveTelemetry) return;
      var I = A.info || {};
      var dt = I.dt || {};
      var dtToday = (dt.today !== undefined) ? (dt.today | 0) : 0;
      var dtMax = (dt.max !== undefined && (dt.max | 0) > 0) ? (dt.max | 0) : 200;
      var dtStr = dt.du ? (dtMax + '/' + dtMax + ' (Xong)') : (dtToday + '/' + dtMax);

      var knbCap = Math.min(KNB_NGAY, Math.max(0, A.cfg.knbMua | 0));
      var knbDone = demKnb();
      var knbStr = knbCap > 0 ? (knbDone + '/' + knbCap) : (knbDone + '/0');

      var dhbDone = dayGet('evDhb');
      var hbDone = dayGet('evHb');
      var bossHkDone = getBossHkKillCountToday();

      var payload = {
        daiHongBao: dhbDone + '/1',
        hongBao: hbDone + '/3',
        bossHoangKim: bossHkDone + '/4 lượt',
        daTauQuest: dtStr,
        knbSummary: knbStr,
        actionStatus: extraStatus ? String(extraStatus) : ''
      };
      window.AndroidBridge.onLiveTelemetry(TAB, JSON.stringify(payload));
    } catch (eTel) {}
  }

  function note(m) {
    var raw = String(m || '').replace(/\s+/g, ' ').trim();
    if (!raw) return;
    var na = noAccent(raw);
    // Đếm Boss Hoàng Kim đã hạ (tối đa 4 lượt/ngày); KHÔNG tính Boss Sát Thủ
    var isSatThuEvent = (na.indexOf('sat thu') >= 0 || (A.st && A.st.noi === 'satthu') || (A.info && A.info.sat));
    if (!isSatThuEvent && (na.indexOf('ha boss hoang kim') >= 0 || na.indexOf('boss hoang kim') >= 0) &&
        (na.indexOf('ha ') >= 0 || na.indexOf('xong') >= 0 || na.indexOf('tieu diet') >= 0 || na.indexOf('da ha') >= 0)) {
      addBossHkKillCountToday();
    }

    var now = Date.now();
    var gKey = normalizeLogGroupKey(raw);
    var isRare = isRareItemOrMilestone(raw);
    var windowMs = isRare ? 3500 : 6500;

    if (LOG_STATE.lastFlushTimer) {
      clearTimeout(LOG_STATE.lastFlushTimer);
      LOG_STATE.lastFlushTimer = 0;
    }

    if (gKey && gKey === LOG_STATE.lastNormKey && (now - LOG_STATE.lastSentAt) < windowMs) {
      LOG_STATE.dupCount = (LOG_STATE.dupCount || 1) + 1;
      var mergedNow = buildCoalescedLogText(raw, LOG_STATE.dupCount);
      if ((now - LOG_STATE.lastSentAt) >= 1200) {
        LOG_STATE.lastSentAt = now;
        LOG_STATE.lastRaw = raw;
        pushBridgeLog(mergedNow);
        publishLiveTelemetry('🤖 ' + raw + ' (x' + LOG_STATE.dupCount + ')');
      } else {
        LOG_STATE.lastRaw = raw;
        LOG_STATE.lastFlushTimer = setTimeout(function () {
          LOG_STATE.lastFlushTimer = 0;
          LOG_STATE.lastSentAt = Date.now();
          pushBridgeLog(buildCoalescedLogText(LOG_STATE.lastRaw, LOG_STATE.dupCount));
          publishLiveTelemetry('🤖 ' + LOG_STATE.lastRaw + ' (x' + LOG_STATE.dupCount + ')');
        }, 1250);
      }
      return;
    }

    LOG_STATE.lastRaw = raw;
    LOG_STATE.lastNormKey = gKey;
    LOG_STATE.lastSentAt = now;
    LOG_STATE.dupCount = 1;
    var singleText = buildCoalescedLogText(raw, 1);
    pushBridgeLog(singleText);
    publishLiveTelemetry(singleText);
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
      if (A.st && A.st.ten) {
        A._lastCharWho = String(A.st.ten);
        return A._lastCharWho;
      }
      if (A.info && (A.info.ten || A.info.name)) {
        A._lastCharWho = String(A.info.ten || A.info.name);
        return A._lastCharWho;
      }
      if (A._lastCharWho) return A._lastCharWho;
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

  var dayClear = function (k) {
    try {
      localStorage.removeItem(dayKey(k));
      localStorage.removeItem('vlm_auto_' + k);
    } catch (e) {}
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

  var dayClear = function (k) {
    try {
      localStorage.removeItem(dayKey(k));
      localStorage.removeItem('vlm_auto_' + k);
    } catch (e) {}
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
    var prevTtlMua = !!A.cfg.ttlMua;
    var prevTt2Mua = !!A.cfg.tt2Mua;
    var prevQhtMua = !!A.cfg.qhtMua;
    var prevTgd = !!A.cfg.theGiaoDich;
    try {
      var c = (typeof j === 'string') ? JSON.parse(j) : (j || {});
      var __prevFarmKey = (A.cfg && A.cfg.farmMap) ? normalizeMapCompareKey(A.cfg.farmMap) : '';
      var __prevEvJson = JSON.stringify((A.cfg && A.cfg.doiNguyenLieu) || []);
      var __prevTowerCfgSig = String((A.cfg && A.cfg.thapMainSkill) || '') + '|' + String((A.cfg && A.cfg.thapNeDonStyle) || '') + '|' + String((A.cfg && A.cfg.thapAfterMode) || '');
      A.cfg = Object.assign({}, DEF, c || {});
      A.cfg.afterDailyEnabled = !!A.cfg.afterDailyEnabled;
      A.cfg.afterDailyFarm = !!A.cfg.afterDailyFarm;
      var __hasTbkKey = !!(c && Object.prototype.hasOwnProperty.call(c, 'tbk'));
      if (__hasTbkKey) {
        A.cfg.tbk = !!c.tbk;
        A.cfg.thap = !!(A.cfg.thap && !A.cfg.afterDailyFarm);
        A.cfg.thapNeDon = !!A.cfg.thapNeDon;
      } else {
        A.cfg.tbk = !!(A.cfg.thap || A.cfg.thapNeDon);
        A.cfg.thap = false;
        A.cfg.thapNeDon = false;
      }
      var __newTowerCfgSig = String(A.cfg.thapMainSkill || '') + '|' + String(A.cfg.thapNeDonStyle || '') + '|' + String(A.cfg.thapAfterMode || '');
      if (__newTowerCfgSig !== __prevTowerCfgSig) {
        A._autoTowerProfile = null;
        A._lastOrbitStyleLogSig = '';
      }
      var __rawFarmNA = noAccent(A.cfg.farmMap || '').replace(/\s+/g, ' ').trim();
      if (__rawFarmNA.indexOf('vi son') >= 0 || __rawFarmNA.indexOf('son dao') >= 0) A.cfg.farmMap = 'Vi sơn đảo';
      else if (__rawFarmNA.indexOf('mac bac') >= 0 || __rawFarmNA.indexOf('thao nguyen') >= 0) A.cfg.farmMap = 'Mạc Bắc Thảo Nguyên';
      else A.cfg.farmMap = String(A.cfg.farmMap || '').trim();
      if (normalizeMapCompareKey(A.cfg.farmMap) !== __prevFarmKey) {
        A.lastMapSelected = '';
        delete A.cool.select_map;
        delete A.cool.select_map_force;
        A.forceMapSwitchUntil = Date.now() + 25000;
        A.immediate = true;
      }
      A.cfg.tbkTinVat = !!A.cfg.tbkTinVat;
      A.cfg.bangLbdt = !!A.cfg.bangLbdt;
      A.cfg.bangPd = !!A.cfg.bangPd;
      if (JSON.stringify(A.cfg.doiNguyenLieu || []) !== __prevEvJson) dayClear('evAllDone');
      if ((A.cfg.knbMua | 0) > demKnb()) dayClear('knbDone');
      if ((A.cfg.lbMua | 0) > dayGet('lb')) dayClear('lbDone');
      delete A.cool.khan;
      delete A.cool.qua;
      delete A.cool.ldt;
      delete A.cool.pd_open;
      delete A.cool.lb;
      delete A.cool.tt;
      delete A.cool.knb;
      delete A.cool.theGd;
      delete A.cool.evHongBao;
      delete A.cool.tbk_tinvat;
      delete A.cool.tbk_use_tinvat;
      delete A.cool.tbk_enter;
      A._lastDailyScanSig = '';
      A.tbkStatus = null;
      A.tbkProbedAt = 0;
      delete A.cool.tbk_probe;
      A.cfg.thapAfterMode = (String(A.cfg.thapAfterMode || '').toUpperCase().trim() === 'ORBIT') ? 'ORBIT' : 'NORMAL';
      A.cfg.thapNeDon = !!A.cfg.thapNeDon;
      A.cfg.thapNeDonStyle = normalizeOrbitStyleJs(A.cfg.thapNeDonStyle);
      A.cfg.thapMainSkill = String(A.cfg.thapMainSkill || '').trim();
    } catch (e) {}
    if (!imm && typeof t === 'number' && t > A.touchK) A.touchK = t;
    if (!A.cfg.on || A.cfg.thapAfterMode !== 'ORBIT' || (!A.cfg.thapNeDon && !(A.cfg.afterDailyEnabled && A.cfg.thap && !A.cfg.afterDailyFarm))) {
      try { if (typeof releaseOrbitKeys === 'function') releaseOrbitKeys(); } catch (eRel) {}
      try { if (typeof towerLockSet === 'function') towerLockSet(false); } catch (eTl) {}
    }
    if (!A.cfg.on) {
      A.started = 0;
      A.immediate = false;
      A.immUntil = 0;
      A.saveGraceUntil = 0;
      A.pendingTick = false;
    } else if (imm || !prevOn) {
      var nowCfg = Date.now();
      A.immediate = true;
      A.immUntil = nowCfg + 120000;
      A.started = nowCfg - 60000;
      A.saveTriggeredAt = nowCfg;
      A.saveGraceUntil = nowCfg + 18000;
      A.cfgSeq = (A.cfgSeq | 0) + 1;
      note('[Auto] Luu & quet ngay (TBK=' + (A.cfg.thap ? A.cfg.thapAfterMode : 'Tat') + ', bangTV=' + (A.cfg.tbkTinVat ? 1 : 0) + ', LB=' + (A.cfg.bangLbdt ? 1 : 0) + ', PD=' + (A.cfg.bangPd ? 1 : 0) + ', LC=' + ((A.cfg.afterDailyEnabled || A.cfg.afterDailyFarm) ? (A.cfg.farmMap || '1') : '0') + ', QHT=' + (A.cfg.qht ? 1 : 0) + ')');
      A.cool = {};
      A.lastMapSelected = '';
      A.pbOut = 0;
      A.tbkFailCount = 0;
      delete A.cool.tbk_tinvat;
      delete A.cool.tbk_use_tinvat;
      delete A.cool.evHongBao;
      delete A.cool.tbk_enter;
      A.koWaitingRecovery = false;
      if (A.cfg.afterDailyFarm || (A.cfg.afterDailyEnabled && !A.cfg.thap)) {
        A.forceMapSwitchUntil = nowCfg + 25000;
      }
      if (A.busy) A.pendingTick = true;
      publishLiveTelemetry('Ap dung cau hinh moi - dang quet & thuc thi ngay...');
    } else {
      if ((!prevTtl && A.cfg.ttl) || (!prevTt2 && A.cfg.tt2) || (!prevQht && A.cfg.qht) || (!prevTtlMua && A.cfg.ttlMua) || (!prevTt2Mua && A.cfg.tt2Mua) || (!prevQhtMua && A.cfg.qhtMua)) {
        delete A.cool.buff_scan_15m;
      }
      if (!prevTgd && A.cfg.theGiaoDich) {
        delete A.cool.theGd;
      }
      var checkKeys = ['dt', 'ldtMua', 'pd', 'st', 'pb', 'afterDailyEnabled', 'afterDailyFarm', 'thap', 'thapNeDon', 'qua', 'khan', 'ttl', 'tt2', 'qht', 'theGiaoDich'];
      for (var ck = 0; ck < checkKeys.length; ck++) {
        var kName = checkKeys[ck];
        if (A.cfg[kName] && A.checkedQueue.indexOf(kName) < 0) {
          A.checkedQueue.push(kName);
        }
      }
      A.immediate = true;
      A.immUntil = Math.max(A.immUntil || 0, Date.now() + 90000);
      publishLiveTelemetry('Quet tuan tu (' + A.checkedQueue.length + ' muc da tich)...');
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
      A.pbOut = 0;
      A.tbkFailCount = 0;
      delete A.cool.tbk_tinvat;
      delete A.cool.tbk_use_tinvat;
      delete A.cool.evHongBao;
      delete A.cool.tbk_enter; A.pbSeen = 0; A.gate = 0; A.cool = {};
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

  function onEnteredGame() {
    if (!A.cfg.on) return;
    A.immediate = true;
    A.immUntil = Math.max(A.immUntil || 0, Date.now() + 180000);
    A.started = Date.now() - 60000;
    delete A.cool.mode_quest;
    delete A.cool.mode_dg;
    delete A.cool.mode_farm;
    delete A.cool.mode_tower;
    publishLiveTelemetry('🟢 Vào game: đang quét chế độ & hoạt động...');
    setTimeout(function () { tick().catch(function () {}); }, 600);
  }

  function onNewVnDay(c) {
    if (c) setCfg(c, A.touchK, true);
    A.lastDay = vnDay();
    resetDay(true);
    A.immediate = true;
    A.immUntil = Date.now() + 180000;
    A.started = Date.now() - 60000;
    note('00:00:01 (GMT+7): làm mới ngày mới → quét Dã Tẩu, Phó Bản & hoạt động');
    publishLiveTelemetry('🌅 Ngày mới: đang quét lại Dã Tẩu, Phó Bản & hoạt động...');
    setTimeout(function () { tick().catch(function () {}); }, 1500);
    setTimeout(function () { tick().catch(function () {}); }, 5000);
  }

  function onSweep7Min(c) {
    if (!A.cfg.on) return;
    if (c) setCfg(c, A.touchK, true);
    A.lastSweep7mAt = Date.now();
    A.immediate = true;
    A.immUntil = Math.max(A.immUntil || 0, Date.now() + 120000);
    // Xóa các cooldown kiểm tra chế độ / map / lượt hoạt động để đối chiếu lại tình trạng thực tế
    delete A.cool.mode_quest;
    delete A.cool.mode_dg;
    delete A.cool.mode_farm;
    delete A.cool.mode_tower;
    delete A.cool.select_map;
    delete A.cool.ldt;
    delete A.cool.lb;
    delete A.cool.qua;
    publishLiveTelemetry('⏱️ Quét định kỳ 7p: đối chiếu nhân vật, lượt & chế độ...');
    setTimeout(function () { tick().catch(function () {}); }, 120);
  }

  var ready = function (k, ms) {
    if (A.cool[k] > Date.now()) return false;
    A.cool[k] = Date.now() + ms;
    return true;
  };

  var isUserControlling = function (graceMs) {
    var g = graceMs || 3500;
    if (window.__isUserTouching) return true;
    var now = Date.now();
    if (A.saveGraceUntil && now < A.saveGraceUntil) {
      var svAt = A.saveTriggeredAt || 0;
      if ((A.userAt || 0) <= svAt && (A.touchK || 0) <= svAt) return false;
    }
    if (now - A.userAt <= g) return true;
    if (now - (A.touchK || 0) <= g) return true;
    return false;
  };

  var ranh = function () {
    var now = Date.now();
    if (A.saveGraceUntil && now < A.saveGraceUntil) {
      return !isUserControlling(1500);
    }
    if (A.knbSyncUntil > now) {
      return !isUserControlling(2500);
    }
    if (A.immediate || A.immUntil > now) {
      return !isUserControlling(3500);
    }
    if (isUserControlling(4000)) return false;
    if (now - A.userAt <= 8000) return false;
    return now - (A.touchK || 0) > 6000;
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

  function getUiScaleMetrics() {
    var vw = window.innerWidth || (document.documentElement && document.documentElement.clientWidth) || 360;
    var vh = window.innerHeight || (document.documentElement && document.documentElement.clientHeight) || 640;
    var compact = (vw <= 350 || vh <= 370);
    var ultraCompact = (vw <= 235 || vh <= 290);
    return {
      vw: vw,
      vh: vh,
      compact: compact,
      ultraCompact: ultraCompact,
      minBtnW: ultraCompact ? 6 : (compact ? 9 : 14),
      minBtnH: ultraCompact ? 6 : (compact ? 8 : 10),
      minSlot: ultraCompact ? 12 : (compact ? 16 : 24),
      maxSlot: compact ? 112 : 124,
      minBagW: Math.max(68, Math.min(140, Math.floor(vw * 0.40))),
      minBagH: Math.max(56, Math.min(104, Math.floor(vh * 0.22))),
      minBarW: Math.max(18, Math.min(54, Math.floor(vw * 0.14)))
    };
  }

  function ensureStealthCss() {
    try {
      var id = '__vlm_stealth_css';
      var st = document.getElementById(id);
      if (st && st.dataset && st.dataset.ver === '4') return;
      if (!st) {
        st = document.createElement('style');
        st.id = id;
        (document.head || document.documentElement).appendChild(st);
      }
      if (st.dataset) st.dataset.ver = '4';
      var sel = [
        '#modal', '#cModal', '#cfModal', '#confirmModal', '#itemModal', '#detailModal',
        '#popup', '.popup', '.modal', '[role="dialog"]', '[aria-modal="true"]',
        '.confirm', '[data-confirm]', '.modal-backdrop', '.modal-overlay', '.backdrop', '.overlay',
        '.swal2-container', '.sheet', '.drawer',
        '#u20pop', '.u20-pop', '.u20-more', '.more-menu', '[data-more-pop]',
        '#t-inv', '#t-bag', '#t-tui', '#p-inv', '#p-bag', '#p-tui',
        '#tab-inv', '#tab-bag', '#tab-tui', '#invTab', '#bagTab', '#tuiTab',
        '#invPane', '#bagPane', '#tuiPane', '#invPanel', '#bagPanel',
        '#u20inv', '#u20bagPane', '#u20tui', '#u20sheet', '#u20drawer', '#u20panel',
        '#t-quest', '#p-quest', '#questPane', '#questTab', '#nvPane',
        '#t-guild', '#t-bang', '#p-guild', '#p-bang', '#guildPane', '#bangPane', '#u20guildPane',
        '[data-vlm-stealth-hide="1"]'
      ];
      var p1 = [], p2 = [];
      for (var i = 0; i < sel.length; i++) {
        p1.push('html.__vlm-stealth ' + sel[i]);
        p1.push('html.__vlm-stealth ' + sel[i] + ' *');
        p2.push('html.__vlm-stealth ' + sel[i]);
      }
      st.textContent =
        p1.join(', ') + ' { ' +
        '  pointer-events: none !important; ' +
        '  transition: none !important; ' +
        '  animation: none !important; ' +
        '} ' +
        p2.join(', ') + ' { ' +
        '  opacity: 0.001 !important; ' +
        '  z-index: -9999 !important; ' +
        '  position: fixed !important; ' +
        '  left: -99999px !important; ' +
        '  top: -99999px !important; ' +
        '}';
    } catch (e) {}
  }

  function stealthObj() {
    if (!window.__vlmStealth || !window.__vlmStealth.hideNode) {
      var prevCount = (window.__vlmStealth && window.__vlmStealth.count) ? (window.__vlmStealth.count | 0) : 0;
      var isStealthCandidatePanel = function (el) {
        if (!el || el.nodeType !== 1 || el === document.body || el === document.documentElement) return false;
        if (el.id === 'vlmAuto' || el.id === 'mswBtn' || el.id === 'modeSw' || el.id === 'tabs' || el.id === 'u20bar') return false;
        if (el.closest && el.closest('#tabs, #u20bar, #modeSw, #chatLines, #u20lines, #logBox, #u20log')) return false;
        if (el.querySelector && el.querySelector('canvas, #tabs, #u20bar')) return false;
        var cls = String(el.className || '').toLowerCase();
        var eid = String(el.id || '').toLowerCase();
        if (cls.indexOf('modal') >= 0 || cls.indexOf('popup') >= 0 || cls.indexOf('dialog') >= 0 ||
            cls.indexOf('drawer') >= 0 || cls.indexOf('sheet') >= 0 || cls.indexOf('confirm') >= 0 ||
            cls.indexOf('overlay') >= 0 || cls.indexOf('backdrop') >= 0 ||
            eid.indexOf('modal') >= 0 || eid.indexOf('inv') >= 0 || eid.indexOf('bag') >= 0 ||
            eid.indexOf('quest') >= 0 || eid.indexOf('guild') >= 0 || eid.indexOf('bang') >= 0) {
          return true;
        }
        var tx = '';
        try { tx = noAccent(el.textContent || ''); } catch (eTx) {}
        if (tx.length >= 10 && tx.length <= 2600) {
          if (tx.indexOf('dung 1') >= 0 || tx.indexOf('dung het') >= 0 || tx.indexOf('mo rong') >= 0 ||
              tx.indexOf('ng.luong') >= 0 || tx.indexOf('knb khoa') >= 0 || tx.indexOf('tin vat thien bao kho') >= 0 ||
              tx.indexOf('cua hang') >= 0 || tx.indexOf('huy theo doi') >= 0 || tx.indexOf('que hoa tuu') >= 0) {
            return true;
          }
        }
        try {
          var cs = window.getComputedStyle ? window.getComputedStyle(el) : null;
          if (cs && (cs.position === 'fixed' || cs.position === 'absolute') && (Number(cs.zIndex) || 0) >= 20) {
            var r = el.getBoundingClientRect ? el.getBoundingClientRect() : null;
            if (r && r.width >= 90 && r.height >= 60) return true;
          }
        } catch (eCs) {}
        return false;
      };

      var obj = {
        count: prevCount,
        tagged: [],
        obs: null,
        hideNode: function (el) {
          if (!el || el.nodeType !== 1 || el === document.body || el === document.documentElement) return;
          if (el.querySelector && el.querySelector('canvas, #tabs, #u20bar')) return;
          try {
            if (el.getAttribute('data-vlm-stealth-hide') !== '1') {
              el.setAttribute('data-vlm-stealth-hide', '1');
              this.tagged.push(el);
            }
          } catch (eHn) {}
        },
        startObserver: function () {
          if (this.obs || !window.MutationObserver || !document.body) return;
          var self = this;
          try {
            this.obs = new MutationObserver(function (muts) {
              if (!self.count) return;
              for (var i = 0; i < muts.length; i++) {
                var m = muts[i];
                if (m.type === 'childList' && m.addedNodes) {
                  for (var j = 0; j < m.addedNodes.length; j++) {
                    var nd = m.addedNodes[j];
                    if (isStealthCandidatePanel(nd)) self.hideNode(nd);
                  }
                } else if (m.type === 'attributes' && m.target) {
                  if (isStealthCandidatePanel(m.target)) self.hideNode(m.target);
                }
              }
            });
            this.obs.observe(document.body, {
              childList: true,
              subtree: true,
              attributes: true,
              attributeFilter: ['class', 'style', 'hidden']
            });
          } catch (eObs) {}
        },
        stopObserver: function () {
          if (this.obs) {
            try { this.obs.disconnect(); } catch (eDis) {}
            this.obs = null;
          }
        },
        clearTagged: function () {
          var arr = this.tagged || [];
          this.tagged = [];
          for (var i = 0; i < arr.length; i++) {
            try { if (arr[i]) arr[i].removeAttribute('data-vlm-stealth-hide'); } catch (eRm) {}
          }
          try {
            var leftover = document.querySelectorAll('[data-vlm-stealth-hide="1"]');
            for (var k = 0; k < leftover.length; k++) leftover[k].removeAttribute('data-vlm-stealth-hide');
          } catch (eRm2) {}
        },
        enter: function () {
          ensureStealthCss();
          this.count = (this.count | 0) + 1;
          try { document.documentElement.classList.add('__vlm-stealth'); } catch (e) {}
          this.startObserver();
        },
        leave: function () {
          this.count = Math.max(0, (this.count | 0) - 1);
          if (this.count === 0) {
            var self = this;
            self.stopObserver();
            setTimeout(function () {
              if (window.__vlmStealth && window.__vlmStealth.count === 0) {
                self.clearTagged();
                try { document.documentElement.classList.remove('__vlm-stealth'); } catch (e) {}
              }
            }, 150);
          }
        }
      };
      window.__vlmStealth = obj;
    }
    return window.__vlmStealth;
  }

  function isModalLocked() {
    return (window.__vlmModalLockUntil || 0) > Date.now();
  }

  async function waitModalLockFree(maxWaitMs) {
    var deadline = Date.now() + (maxWaitMs || 8000);
    while (isModalLocked() && Date.now() < deadline) {
      await sleep(140);
    }
    return !isModalLocked();
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
      var inSt = !!(document.documentElement && document.documentElement.classList.contains('__vlm-stealth'));
      if (el.getClientRects && el.getClientRects().length === 0) return false;
      var cur = el;
      while (cur && cur !== document.body && cur !== document.documentElement) {
        if (cur.hidden) return false;
        if (cur.classList && cur.classList.contains('hidden')) return false;
        var cs = window.getComputedStyle(cur);
        if (cs) {
          if (cs.display === 'none') return false;
          var isStNode = inSt && (cur.getAttribute && cur.getAttribute('data-vlm-stealth-hide') === '1');
          if (!isStNode) {
            if (cs.visibility === 'hidden' && !inSt) return false;
            if (parseFloat(cs.opacity) === 0) return false;
          }
        }
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

  var dayClear = function (k) {
    try {
      localStorage.removeItem(dayKey(k));
      localStorage.removeItem('vlm_auto_' + k);
    } catch (e) {}
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
  var tbkXong = function () { return dayGet('tbkx2') > 0; };
  var tbkClearXong = function () {
    try {
      var who = dayWho();
      localStorage.removeItem('vlm_auto_' + (who ? who + '_' : '') + 'tbkx');
      localStorage.removeItem('vlm_auto_tbkx');
      localStorage.removeItem('vlm_auto_' + (who ? who + '_' : '') + 'tbkx2');
      localStorage.removeItem('vlm_auto_tbkx2');
    } catch (e) {}
  };

  async function api(act, arg, ms) {
    var runOne = async function () {
      arg = arg || {};
      ms = ms || 45000;
      var waitOldEnd = Date.now() + 5000;
      while (document.getElementById('vlmAuto') && Date.now() < waitOldEnd) {
        await sleep(90);
      }
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
    };
    var prev = window.__vlmApiQueue || Promise.resolve();
    var next = prev.then(runOne, runOne);
    window.__vlmApiQueue = next.then(function () {}, function () {});
    return next;
  }

  function readDomModeFallback() {
    try {
      var mBtn = document.getElementById('mswBtn');
      var txt = mBtn ? noAccent(mBtn.textContent || '') : '';
      if (!txt) {
        var btns = document.querySelectorAll('button, [role="button"], .btn');
        var cap = Math.min(btns.length, 160);
        for (var i = 0; i < cap; i++) {
          var bt = noAccent(btns[i].textContent || '');
          if (bt.indexOf('che do:') === 0 || bt.indexOf('che do ') === 0) { txt = bt; break; }
        }
      }
      if (txt.indexOf('nhiem vu') >= 0 || txt.indexOf('da tau') >= 0) return 'quest';
      if (txt.indexOf('pho ban') >= 0) return 'dg';
      if (txt.indexOf('thien bao kho') >= 0 || txt.indexOf('bao kho') >= 0) return 'tbk';
      if (txt.indexOf('leo thap') >= 0) return 'tower';
      if (txt.indexOf('luyen cong') >= 0) return 'farm';
    } catch (eM) {}
    return '';
  }

  function buildFallbackInfoFromState() {
    var domMode = readDomModeFallback();
    var st = (A.st && Date.now() - A.stT < 30000) ? A.st : {};
    var curMode = st.mode || domMode || (A.info && A.info.mode) || 'farm';
    var lvl = (typeof st.lvl === 'number' && st.lvl > 0) ? st.lvl : ((A.info && A.info.lvl) || 100);
    var dtObj = (st.dt && typeof st.dt === 'object') ? st.dt : ((A.info && A.info.dt) || { today: 0, max: 200, con: 200, du: false });
    return {
      ok: true,
      _fallback: true,
      lvl: lvl,
      mode: curMode,
      dt: dtObj,
      ldt: (typeof st.ldt === 'number') ? st.ldt : ((A.info && A.info.ldt) || 0),
      lb: (typeof st.lb === 'number') ? st.lb : ((A.info && A.info.lb) || 0),
      dg: !!(st.dg || st.noi === 'phoban'),
      tower: !!(st.tower || st.noi === 'thap' || isInTowerNow()),
      sat: !!(st.sat || st.noi === 'satthu'),
      dead: !!(st.dead || st.trongThuong)
    };
  }

  async function info() {
    var i = await api('info', {}, 6500);
    if (i && i.ok) {
      if (!i.mode) {
        var dm = readDomModeFallback();
        if (dm) i.mode = dm;
      }
      A.info = i;
      return i;
    }
    var fb = buildFallbackInfoFromState();
    A.info = fb;
    return fb;
  }

  async function troThu(I) {
    if (A.cfg.thapNeDon) return;
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
    if (!A.cfg.khan) return;
    if (I && I.khan) { dayAdd('khanDone', 1); return; }
    if (dayGet('khanDone') > 0) return;
    if (I.dg || I.tower || I.sat || I.dead || !ranh() || !ready('khan', 90000)) return;
    var r = await api('khan', {}, 60000);
    if (r && r.ok && r.xong) {
      var I2 = await info();
      if (I2 && I2.khan) {
        dayAdd('khanDone', 1);
        note('khấn tượng: +20% kinh nghiệm 8 giờ (xong hôm nay)');
      }
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
    var pushSafe = function (val) {
      if (!val) return;
      var s = String(val).trim();
      if (!s || s.indexOf('data:') === 0 || s.length > 180) return;
      parts.push(s);
    };
    try {
      pushSafe(el.id);
      if (typeof el.className === 'string') pushSafe(el.className);
      var attrs = [
        'title', 'aria-label', 'alt', 'src', 'data-k', 'data-key', 'data-id',
        'data-buff', 'data-effect', 'data-name', 'data-type', 'data-item',
        'data-tip', 'data-tooltip', 'data-title', 'data-label', 'data-desc'
      ];
      for (var i = 0; i < attrs.length; i++) {
        var v = el.getAttribute ? el.getAttribute(attrs[i]) : null;
        if (v) pushSafe(v);
      }
      var imgs = el.querySelectorAll ? el.querySelectorAll('img') : [];
      var imgCap = Math.min(imgs.length, 4);
      for (var j = 0; j < imgCap; j++) {
        var img = imgs[j];
        pushSafe(img.getAttribute('src'));
        pushSafe(img.getAttribute('alt'));
        pushSafe(img.getAttribute('title'));
        pushSafe(img.getAttribute('data-k'));
        pushSafe(img.getAttribute('data-name'));
        pushSafe(img.getAttribute('data-tip'));
        pushSafe(img.getAttribute('data-tooltip'));
      }
      var rawTxt = (el.textContent || '').replace(/\s+/g, ' ').trim();
      if (rawTxt) parts.push(rawTxt.length > 260 ? rawTxt.slice(0, 260) : rawTxt);
    } catch (e) {}
    return parts.join(' ');
  }

  // Tra ve ca trang thai bat/tat lan thoi gian con lai (ms) cua tung vat pham tieu hao
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
      var anchorBottom = Math.max(190, Math.min(340, Math.floor((window.innerHeight || 600) * 0.52)));
      var anchorRight = Math.max(260, Math.floor((window.innerWidth || 800) * 0.85));
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
        var allowShort = !!(bEl.getAttribute && (bEl.getAttribute('data-buff') || bEl.getAttribute('data-k') || bEl.getAttribute('data-key'))) || !!(bEl.closest && bEl.closest('#buffs, #buffBar, #topBuffs, .buff, .buff-item, .buff-slot'));
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
    if (!rtNA || rtNA.length > 520) return false;
    if (kind === 'ttl') return rtNA.indexOf('tien thao lo') >= 0 && rtNA.indexOf('dac biet') < 0;
    if (kind === 'tt2') return rtNA.indexOf('tien thao lo dac biet') >= 0 || (rtNA.indexOf('tien thao') >= 0 && rtNA.indexOf('dac biet') >= 0);
    if (kind === 'qht') return rtNA.indexOf('que hoa tuu') >= 0 || rtNA.indexOf('que hoa') >= 0 || rtNA.indexOf('quehoa') >= 0;
    if (kind === 'tbktv') return rtNA.indexOf('tin vat thien bao kho') >= 0 || (rtNA.indexOf('tin vat') >= 0 && rtNA.indexOf('bao kho') >= 0);
    return false;
  }

  function stripCountText(raw) {
    return noAccent(raw || '')
      .replace(/\(\s*\d+\s*(?:\/\s*\d+)?\s*\)/g, ' ')
      .replace(/\[\s*\d+\s*\]/g, ' ')
      .replace(/^[^a-z0-9]+/i, '')
      .replace(/[^a-z0-9]+$/i, '')
      .replace(/\s+\d+(?:\/\d+)?$/, '')
      .replace(/\s+/g, ' ')
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

  function cleanUiLabel(raw) {
    return noAccent(String(raw || ''))
      .replace(/\(\s*\d+(?:\s*\/\s*\d+)?\s*\)/g, ' ')
      .replace(/[^a-z0-9\s]/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
  }

  function isBagPanelText(rawTxt) {
    var raw = String(rawTxt || '');
    var t = cleanUiLabel(raw);
    if (!t) return false;
    var hasMoRong = t.indexOf('mo rong') >= 0;
    var hasSort = t.indexOf('moi nhat') >= 0 || t.indexOf('sap xep') >= 0 || t.indexOf('pham chat') >= 0;
    var hasCur = (t.indexOf('ng luong') >= 0 || t.indexOf('ngan luong') >= 0) &&
                 (t.indexOf('kim ng bao') >= 0 || t.indexOf('knb khoa') >= 0);
    var hasCap = /\b\d{1,3}\s*\/\s*\d{2,3}\b/.test(raw);
    if (hasMoRong && (hasCur || hasCap || hasSort)) return true;
    if (hasCap && hasCur) return true;
    return false;
  }

  function findInlineBagByMoRong() {
    try {
      var btns = document.querySelectorAll('button, [role="button"], a, span, div');
      var cap = Math.min(btns.length, 420);
      var moRongEl = null;
      for (var i = 0; i < cap; i++) {
        var el = btns[i];
        if (!el || !isVis(el) || el.id === 'vlmAuto' || el.id === 'mswBtn') continue;
        if ((el.tagName === 'DIV' || el.tagName === 'SPAN') && el.children && el.children.length > 2) continue;
        if (el.closest && el.closest('#modal, #tabs, #u20bar, #chatLines, #u20lines, #logBox, #u20log')) continue;
        if (cleanUiLabel(el.textContent || '') === 'mo rong') {
          moRongEl = el;
          break;
        }
      }
      if (!moRongEl) return null;
      var cur = moRongEl.parentElement;
      var best = null;
      for (var d = 0; d < 7 && cur && cur !== document.body && cur !== document.documentElement; d++) {
        if (cur.querySelector && cur.querySelector('canvas')) break;
        if (cur.id === 'app' || cur.id === 'root' || cur.id === 'game') break;
        var tx = cur.textContent || '';
        if (isBagPanelText(tx)) {
          best = cur;
        }
        cur = cur.parentElement;
      }
      return best;
    } catch (eMr) {}
    return null;
  }

  function findVisibleBagRootNow() {
    var md = document.getElementById('modal');
    var inlineBag = findInlineBagByMoRong();
    if (inlineBag && isVis(inlineBag)) {
      return { root: inlineBag, md: md, mode: 'tab' };
    }
    if (md && !md.classList.contains('hidden')) {
      var mb = document.getElementById('mBody') || md;
      var rawMb = mb.textContent || '';
      var mt = cleanUiLabel(rawMb);
      if (isBagPanelText(rawMb) || mt.indexOf('hanh trang') >= 0 || mt.indexOf('tui do') >= 0 ||
          (mt.indexOf('vat pham') >= 0 && (mt.indexOf('trang bi') >= 0 || mt.indexOf('que hoa') >= 0 || mt.indexOf('tien thao') >= 0))) {
        return { root: mb, md: md, mode: 'modal' };
      }
    }
    var paneIds = [
      't-inv', 't-bag', 't-tui', 'p-inv', 'p-bag', 'p-tui',
      'tab-inv', 'tab-bag', 'tab-tui', 'invTab', 'bagTab', 'tuiTab',
      'invPane', 'bagPane', 'tuiPane', 'invPanel', 'bagPanel',
      'u20inv', 'u20bagPane', 'u20tui', 'u20sheet', 'u20drawer', 'u20panel', 'inv', 'bag', 'tui'
    ];
    for (var pi = 0; pi < paneIds.length; pi++) {
      var pe = document.getElementById(paneIds[pi]);
      if (pe && isVis(pe)) {
        return { root: pe, md: md, mode: 'tab' };
      }
    }
    var activePanes = document.querySelectorAll(
      '[data-pane="inv"], [data-pane="bag"], [data-pane="tui"], ' +
      '.tab-pane.active, .pane.on, .tab-content > .active, .sheet.open, .drawer.open'
    );
    for (var ai = 0; ai < activePanes.length; ai++) {
      var ap = activePanes[ai];
      if (!ap || !isVis(ap)) continue;
      if (ap.querySelector && ap.querySelector('canvas, #tabs, #u20bar')) continue;
      var rawAp = ap.textContent || '';
      var at = cleanUiLabel(rawAp);
      if (isBagPanelText(rawAp) || at.indexOf('hanh trang') >= 0 || at.indexOf('tui do') >= 0 ||
          at.indexOf('que hoa') >= 0 || at.indexOf('tien thao') >= 0 ||
          (at.indexOf('vat pham') >= 0 && at.indexOf('trang bi') >= 0)) {
        return { root: ap, md: md, mode: 'tab' };
      }
    }
    return null;
  }
  function collectHiddenBagTriggers() {
    var md = document.getElementById('modal');
    var out = [];
    var push = function (el) {
      if (!el) return;
      var target = el;
      if (target.tagName && (target.tagName === 'SPAN' || target.tagName === 'DIV' || target.tagName === 'I' || target.tagName === 'SVG')) {
        var parentBtn = target.closest ? target.closest('button, [role="button"], [role="tab"], a, .btn, #u20bar > *, #tabs > *') : null;
        if (parentBtn) target = parentBtn;
      }
      if (!target || out.indexOf(target) >= 0) return;
      if (md && !md.classList.contains('hidden') && md.contains(target)) return;
      if (target.id === 'vlmAuto' || target.id === 'mswBtn') return;
      if (target.closest && target.closest('#mswMenu, #chatLines, #u20lines, #logBox, #u20log, #chatBox')) return;
      if (!isVis(target) || target.disabled) return;
      out.push(target);
    };

    var isBagLabel = function (lbl) {
      if (!lbl || lbl.length > 26) return false;
      return lbl === 'hanh trang' || lbl === 'hanh tran' || lbl.indexOf('hanh tran') >= 0 || lbl.indexOf('hanh tr') === 0 ||
        lbl === 'tui do' || lbl === 'tui' || lbl === 'hanh ly' || lbl === 'kho do';
    };

    // 1. Uu tien ID chuan
    var bagIds = ['u20bag', 'bagBtn', 'tuiBtn', 'invBtn', 'bBag', 'btnBag', 'tabInv', 'tabBag', 'navBag', 'navInv'];
    for (var i = 0; i < bagIds.length; i++) push(document.getElementById(bagIds[i]));

    // 2. Uu tien nut trong thanh dieu huong (#u20bar, #tabs, nav)
    var navEls = document.querySelectorAll('#u20bar > *, #u20bar button, #u20bar a, #tabs > *, #tabs button, #tabs a, nav button, .nav button, .tabbar button, #u20pop button, .u20-pop button, .more-menu button');
    for (var ni = 0; ni < navEls.length; ni++) {
      var ne = navEls[ni];
      var nt = cleanUiLabel((ne.textContent || '') + ' ' + (ne.getAttribute('title') || '') + ' ' + (ne.getAttribute('aria-label') || ''));
      var dt = String(ne.getAttribute('data-tab') || ne.getAttribute('data-open') || ne.getAttribute('data-t') || ne.getAttribute('data-nav') || '').toLowerCase();
      if (isBagLabel(nt) || dt === 'bag' || dt === 'inv' || dt === 'tui' || dt === 't-inv' || dt === 't-bag') {
        push(ne);
      }
    }

    // 3. Cac nut / the co chu Hanh Trang / Hanh Tran / Tui Do
    var allBtns = document.querySelectorAll('button, [role="button"], [role="tab"], a, .btn, [onclick]');
    for (var ai = 0; ai < allBtns.length; ai++) {
      var bt = cleanUiLabel((allBtns[ai].textContent || '') + ' ' + (allBtns[ai].getAttribute('title') || '') + ' ' + (allBtns[ai].getAttribute('aria-label') || ''));
      if (isBagLabel(bt)) push(allBtns[ai]);
    }

    // 4. Data attribute ro rang (tranh [onclick*="inv"] bat nham invite)
    var attrEls = document.querySelectorAll(
      '[data-open="bag"], [data-open="inv"], [data-open="tui"], ' +
      '[data-tab="bag"], [data-tab="inv"], [data-tab="tui"], [data-tab="t-inv"], [data-tab="t-bag"], ' +
      '[data-t="inv"], [data-t="bag"], [data-t="tui"], [data-nav="bag"], [data-act="bag"], ' +
      '[title*="Hành trang" i], [aria-label*="Hành trang" i]'
    );
    for (var a = 0; a < attrEls.length; a++) push(attrEls[a]);

    // 5. Fallback div/span la
    var leafEls = document.querySelectorAll('div, span');
    var capLeaf = Math.min(leafEls.length, 420);
    for (var li = 0; li < capLeaf; li++) {
      if (leafEls[li].children && leafEls[li].children.length > 3) continue;
      var lt = cleanUiLabel(leafEls[li].textContent || '');
      if (isBagLabel(lt)) push(leafEls[li]);
    }
    return out;
  }

  async function openHiddenBagModal() {
    A._bagDiag = '';
    var already = findVisibleBagRootNow();
    if (already && already.root) {
      return { root: already.root, md: already.md, mode: 'already_open', prevTabBtn: null, moreBtn: null, bagBtn: null, hiddenInline: null };
    }

    var md = document.getElementById('modal');
    var bagCands = collectHiddenBagTriggers();
    var openedMoreBtn = null;
    if (bagCands.length === 0) {
      var moreBtn = document.getElementById('u20more') || document.getElementById('moreBtn') || document.getElementById('btnMore');
      if (!moreBtn) {
        var mEls = document.querySelectorAll('#u20bar button, #tabs button, button, [role="button"]');
        for (var mi = 0; mi < mEls.length; mi++) {
          var mt = cleanUiLabel(mEls[mi].textContent || '');
          if ((mt === 'them' || mt === 'menu' || mt === 'khac') && isVis(mEls[mi]) && !mEls[mi].disabled) {
            moreBtn = mEls[mi];
            break;
          }
        }
      }
      if (moreBtn && isVis(moreBtn) && !moreBtn.disabled) {
        openedMoreBtn = moreBtn;
        try { dispatchRealUiClick(moreBtn); } catch (eM) { try { moreBtn.click(); } catch (eM2) {} }
        await sleep(240);
        bagCands = collectHiddenBagTriggers();
      }
    }

    if (bagCands.length === 0) {
      try {
        var navSample = [];
        var nList = document.querySelectorAll('#u20bar > *, #tabs > *, button');
        for (var ns = 0; ns < Math.min(nList.length, 16); ns++) {
          if (!isVis(nList[ns])) continue;
          var lb = cleanUiLabel(nList[ns].textContent || nList[ns].id || '');
          if (lb && lb.length <= 18) navSample.push(lb);
        }
        A._bagDiag = 'no_btn[' + navSample.slice(0, 8).join('|') + ']';
      } catch (eDiag0) {}
      return null;
    }

    var prevTabBtn = document.querySelector('#tabs .on, #tabs .active, #tabs [aria-selected="true"], #u20bar .on, #u20bar .active');

    var findFallbackBagRoot = function () {
      var cands = document.querySelectorAll('.modal, .popup, .drawer, .sheet, .panel, [role="dialog"], [aria-modal="true"], div, section');
      var best = null;
      var bestArea = 999999999;
      var cap = Math.min(cands.length, 520);
      for (var ci = 0; ci < cap; ci++) {
        var el = cands[ci];
        if (!el || !isVis(el) || el === document.body || el === document.documentElement) continue;
        if (el.id === 'vlmAuto' || el.id === 'app' || el.id === 'root' || el.id === 'game' || el.id === 'wrap') continue;
        if (el.closest && el.closest('#tabs, #u20bar, #chatLines, #u20lines, #logBox, #u20log')) continue;
        if (el.querySelector && el.querySelector('canvas, #tabs, #u20bar')) continue;
        var rect = el.getBoundingClientRect ? el.getBoundingClientRect() : null;
        var __mBag = getUiScaleMetrics(); if (!rect || rect.width < __mBag.minBagW || rect.height < __mBag.minBagH) continue;
        var tx = cleanUiLabel(el.textContent || '');
        if (!tx || tx.length < 14) continue;
        var score = 0;
        if (tx.indexOf('hanh trang') >= 0 || tx.indexOf('tui do') >= 0) score += 2;
        if (tx.indexOf('vat pham') >= 0 || tx.indexOf('tieu hao') >= 0 || tx.indexOf('trang bi') >= 0) score += 1;
        if (tx.indexOf('que hoa') >= 0 || tx.indexOf('tien thao') >= 0 || tx.indexOf('su dung') >= 0 || tx.indexOf('dung') >= 0 || tx.indexOf('hanh trang trong') >= 0) score += 2;
        if (score >= 2) {
          var area = rect.width * rect.height;
          if (area < bestArea) { bestArea = area; best = el; }
        }
      }
      return best;
    };

    var maxTry = Math.min(bagCands.length, 3);
    for (var ti = 0; ti < maxTry; ti++) {
      var bagBtn = bagCands[ti];
      try { bagBtn.click(); } catch (eClick) {
        try { dispatchRealUiClick(bagBtn); } catch (eClick2) { continue; }
      }

      var retriedRealClick = false;
      var waitEnd = Date.now() + 1700;
      while (Date.now() < waitEnd) {
        if (md && !md.classList.contains('hidden')) {
          await sleep(150);
          return { root: document.getElementById('mBody') || md, md: md, mode: 'modal', prevTabBtn: null, moreBtn: openedMoreBtn, bagBtn: bagBtn, hiddenInline: null };
        }
        var foundVis = findVisibleBagRootNow();
        if (foundVis && foundVis.root) {
          await sleep(150);
          try { stealthObj().hideNode(foundVis.root); } catch (eShV) {}
          return { root: foundVis.root, md: md, mode: foundVis.mode, prevTabBtn: prevTabBtn, moreBtn: openedMoreBtn, bagBtn: bagBtn, hiddenInline: null };
        }
        if (!retriedRealClick && Date.now() > waitEnd - 1150) {
          retriedRealClick = true;
          try { dispatchRealUiClick(bagBtn); } catch (eClick3) {}
        }
        if (Date.now() > waitEnd - 850) {
          var fbRoot = findFallbackBagRoot();
          if (fbRoot) {
            var savedStyle = { opacity: fbRoot.style.opacity, pointerEvents: fbRoot.style.pointerEvents };
            try { stealthObj().hideNode(fbRoot); fbRoot.style.opacity = '0.001'; fbRoot.style.pointerEvents = 'none'; } catch (eH) {}
            await sleep(130);
            return { root: fbRoot, md: md, mode: 'fallback', prevTabBtn: prevTabBtn, moreBtn: openedMoreBtn, bagBtn: bagBtn, hiddenInline: savedStyle };
          }
        }
        await sleep(80);
      }
    }

    try {
      var firstLbl = cleanUiLabel(bagCands[0].textContent || bagCands[0].id || '');
      A._bagDiag = 'clicked=' + firstLbl + ',cands=' + bagCands.length;
    } catch (eDiag1) {}
    return null;
  }
  async function closeHiddenBagModal(ctx) {
    if (ctx && ctx.mode === 'already_open') return;
    if (ctx && ctx.mode === 'fallback' && ctx.root) {
      try {
        var clBtn = ctx.root.querySelector('#mClose, .close, [data-close], button[aria-label*="Close" i]');
        if (clBtn && isVis(clBtn)) clBtn.click();
        else if (ctx.bagBtn && ctx.bagBtn.isConnected && isVis(ctx.bagBtn)) ctx.bagBtn.click();
      } catch (eFbCl) {}
      try {
        if (ctx.hiddenInline) {
          ctx.root.style.opacity = ctx.hiddenInline.opacity || '';
          ctx.root.style.pointerEvents = ctx.hiddenInline.pointerEvents || '';
        }
      } catch (eFbRest) {}
    }
    await closeModalCleanly(ctx && ctx.md ? ctx.md : document.getElementById('modal'));
    if (ctx && ctx.mode === 'tab') {
      if (ctx.prevTabBtn && ctx.prevTabBtn.isConnected && isVis(ctx.prevTabBtn)) {
        try { ctx.prevTabBtn.click(); } catch (e) {}
        await sleep(90);
      }
      if (ctx.bagBtn && ctx.bagBtn.isConnected && findVisibleBagRootNow()) {
        try { ctx.bagBtn.click(); } catch (eBgCl) {}
        await sleep(90);
      }
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
    if (isBagPanelText(root.textContent || '')) return true;
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

  function isPlainStackCount(raw) {
    var s = String(raw || '').trim();
    if (!s || s.length > 5) return false;
    for (var k = 0; k < s.length; k++) {
      var c = s.charCodeAt(k);
      if (c < 48 || c > 57) return false;
    }
    return Number(s) > 0;
  }

  function getSlotVisualSig(slotEl) {
    if (!slotEl) return '';
    try {
      var img = slotEl.tagName === 'IMG' ? slotEl : (slotEl.querySelector ? slotEl.querySelector('img') : null);
      if (img) {
        var src = String(img.getAttribute('src') || img.src || '');
        if (src) return 'img:' + src.slice(-96);
      }
      var bg = (slotEl.style && slotEl.style.backgroundImage) ? String(slotEl.style.backgroundImage) : '';
      if (!bg && window.getComputedStyle) {
        bg = String(window.getComputedStyle(slotEl).backgroundImage || '');
      }
      if (bg && bg !== 'none') return 'bg:' + bg.slice(-96);
      var did = slotEl.getAttribute('data-id') || slotEl.getAttribute('data-iid') || slotEl.getAttribute('data-item') || '';
      if (did) return 'id:' + did;
    } catch (eSig) {}
    return '';
  }

  function findOpenItemDetailPopup(bagRoot) {
    var scopes = [
      document.getElementById('itemModal'),
      document.getElementById('detailModal'),
      document.getElementById('modal'),
      document.getElementById('cfModal'),
      document.getElementById('confirmModal'),
      document.getElementById('cModal'),
      document.querySelector('[role="dialog"]'),
      document.querySelector('[aria-modal="true"]'),
      document.querySelector('.item-detail'),
      document.querySelector('.popup')
    ];
    for (var i = 0; i < scopes.length; i++) {
      var sc = scopes[i];
      if (!sc || !isVis(sc) || sc === bagRoot) continue;
      if (sc.classList && sc.classList.contains('hidden')) continue;
      var txt = cleanUiLabel(sc.textContent || '');
      if (!txt || txt.length < 6) continue;
      if (txt.indexOf('dung 1') >= 0 || txt.indexOf('dung het') >= 0 || txt.indexOf('tach ra') >= 0 ||
          txt.indexOf('que hoa') >= 0 || txt.indexOf('tien thao') >= 0 || txt.indexOf('tin vat') >= 0 ||
          txt.indexOf('su dung') >= 0 || txt.indexOf('gia ky tran cac') >= 0) {
        return sc;
      }
    }
    return null;
  }

  async function closeItemDetailPopupOnly(popupEl) {
    if (!popupEl || !isVis(popupEl)) return;
    try {
      var closeBtn = popupEl.querySelector('#mClose, .close, [data-close], button[aria-label*="Close" i]');
      if (!closeBtn) {
        var btns = popupEl.querySelectorAll('button, [role="button"], span, div');
        for (var i = 0; i < btns.length; i++) {
          var b = btns[i];
          if (!isVis(b) || (b.children && b.children.length > 1)) continue;
          var rt = String(b.textContent || '').trim();
          var ct = cleanUiLabel(rt);
          if (rt === '\u00d7' || rt === '\u2715' || rt === '\u2716' || ct === 'x' || ct === 'dong' || ct === 'huy') {
            closeBtn = b;
            break;
          }
        }
      }
      if (closeBtn && isVis(closeBtn)) {
        try { closeBtn.click(); } catch (eC0) {}
      } else {
        await closeModalCleanly(document.getElementById('modal'));
      }
    } catch (eCl) {}
    var wEnd = Date.now() + 360;
    while (Date.now() < wEnd) {
      if (!popupEl.isConnected || !isVis(popupEl) || (popupEl.classList && popupEl.classList.contains('hidden'))) break;
      await sleep(45);
    }
  }

  function recordConsumableLocalTimer(kind) {
    try {
      if (kind !== 'qht') return;
      var until = Date.now() + 59 * 60 * 1000;
      A.qhtUsedUntil = until;
      var who = dayWho();
      localStorage.setItem('vlm_auto_' + (who ? who + '_' : '') + 'qhtUntil', String(until));
    } catch (eTm) {}
  }

  function isQhtLocalTimerActive() {
    try {
      var now = Date.now();
      if (A.qhtUsedUntil && now < A.qhtUsedUntil) return true;
      var who = dayWho();
      var saved = Number(localStorage.getItem('vlm_auto_' + (who ? who + '_' : '') + 'qhtUntil') || '0');
      if (saved && now < saved) {
        A.qhtUsedUntil = saved;
        return true;
      }
    } catch (eTm2) {}
    return false;
  }

  async function scanAndUseOneFromHiddenBagDom(kind) {
    if (!canRunHiddenConsumable()) return { scanned: false, used: false, reason: 'busy_or_modal' };

    lockModal(22000);
    stealthObj().enter();
    var ctx = null;
    try {
      ctx = await openHiddenBagModal();
      if (!ctx || !ctx.root) return { scanned: false, used: false, reason: 'cannot_open_bag' + (A._bagDiag ? ':' + A._bagDiag : '') };

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
        var txtLen = (ctx.root.textContent || '').trim().length;
        if (txtLen > 12 && (ctx.root.querySelectorAll('button, tr, li, div, img').length > 0)) {
          bagRendered = true;
        } else {
          return { scanned: false, used: false, reason: 'bag_not_rendered' };
        }
      }

      var isUseBtnLabel = function (bTxt) {
        var t = cleanUiLabel(bTxt || '');
        if (!t || t.indexOf('het') >= 0 || t.indexOf('tat ca') >= 0 || t.indexOf('ban') >= 0 ||
            t.indexOf('bo') >= 0 || t.indexOf('vut') >= 0 || t.indexOf('tach') >= 0 ||
            t.indexOf('cho') >= 0 || t.indexOf('thu') >= 0 || t.indexOf('kho') >= 0) return false;
        return t === 'dung 1' || t === 'dung' || t === 'dung ngay' ||
          t === 'su dung' || t === 'su dung 1' || t === 'an' || t === 'uong' || t === 'mo';
      };

      var hasOtherConsumable = function (txtNA) {
        if (kind === 'qht') return txtNA.indexOf('tien thao') >= 0;
        if (kind === 'ttl') return txtNA.indexOf('que hoa') >= 0 || txtNA.indexOf('dac biet') >= 0;
        if (kind === 'tt2') return txtNA.indexOf('que hoa') >= 0;
        return false;
      };

      var findUseBtnInScope = function (scopeEl) {
        if (!scopeEl || !isVis(scopeEl)) return null;
        var btns = scopeEl.querySelectorAll('button, [role="button"], a');
        var fallback = null;
        for (var j = 0; j < btns.length; j++) {
          if (!isVis(btns[j]) || btns[j].disabled) continue;
          var lbl = cleanUiLabel(btns[j].textContent || '');
          if (lbl === 'dung 1' || lbl === 'su dung 1') return btns[j];
          if (!fallback && isUseBtnLabel(btns[j].textContent || '')) fallback = btns[j];
        }
        return fallback;
      };

      var tryUseInsidePopup = async function (popupEl, slotSig) {
        if (!popupEl || !isVis(popupEl)) return false;
        var pTxt = noAccent(popupEl.textContent || '');
        if (!matchConsumableRowText(pTxt, kind) || hasOtherConsumable(pTxt)) return false;
        var uBtn = findUseBtnInScope(popupEl);
        if (!uBtn) return false;
        if (slotSig) {
          if (!A._slotSigCache) A._slotSigCache = {};
          A._slotSigCache[kind] = slotSig;
        }
        try { uBtn.click(); } catch (eU0) {}
        try { dispatchRealUiClick(uBtn); } catch (eU1) {}
        await sleep(360);
        // Neu co hop thoai xac nhan phu thi bam Xac nhan / Dong y
        var cf = findGuildConfirmButton(uBtn);
        if (cf && isVis(cf)) {
          try { cf.click(); } catch (eU2) {}
          await sleep(280);
        }
        recordConsumableLocalTimer(kind);
        await closeItemDetailPopupOnly(popupEl);
        return true;
      };

      // PASS 1: Khop nhanh qua metadata (title / data-* / text / img alt)
      var allNodes = ctx.root.querySelectorAll('.qrow, .item, .bag-item, .inv-item, .item-slot, [data-bag], [data-inv], [data-iid], [data-slot], [data-item], tr, li, div, span, p, button, img');
      var matches = [];
      for (var ri = 0; ri < allNodes.length; ri++) {
        var el = allNodes[ri];
        if (!isVis(el)) continue;
        var meta = noAccent(extractNodeMeta(el));
        if (!matchConsumableRowText(meta, kind)) continue;
        if (hasOtherConsumable(meta)) continue;
        matches.push({ el: el, len: meta.length });
      }
      matches.sort(function (a, b) { return a.len - b.len; });

      for (var mi = 0; mi < Math.min(matches.length, 4); mi++) {
        var targetEl = matches[mi].el;
        var directUse = null;
        var cur = targetEl;
        for (var depth = 0; depth < 4 && cur && cur !== ctx.root && cur !== document.body; depth++) {
          var curTxt = noAccent((cur.textContent || '').replace(/\s+/g, ' ').trim());
          if (curTxt.length > 320 || hasOtherConsumable(curTxt)) break;
          directUse = findUseBtnInScope(cur);
          if (directUse) break;
          cur = cur.parentElement;
        }
        if (directUse) {
          try { directUse.click(); } catch (eD0) {}
          await sleep(350);
          recordConsumableLocalTimer(kind);
          return { scanned: true, used: true, reason: 'used_inline' };
        }
        try { targetEl.click(); } catch (eT0) {}
        try { dispatchRealUiClick(targetEl); } catch (eT1) {}
        var pWait = Date.now() + 750;
        var pop = null;
        while (Date.now() < pWait) {
          pop = findOpenItemDetailPopup(ctx.root);
          if (pop) break;
          await sleep(75);
        }
        if (pop) {
          if (await tryUseInsidePopup(pop, getSlotVisualSig(targetEl))) {
            return { scanned: true, used: true, reason: 'used_meta_popup' };
          }
          await closeItemDetailPopupOnly(pop);
        }
      }

      // PASS 2: Do luoi o bieu tuong (Hanh trang dang icon + so xep chong)
      var collectStackableSlots = function (rootEl) {
        var list = [];
        var seenEl = [];
        var nodes = rootEl.querySelectorAll('div, button, li, span, a');
        var cap = Math.min(nodes.length, 600);
        for (var i = 0; i < cap; i++) {
          var n = nodes[i];
          if (!n || !isVis(n) || n === rootEl) continue;
          if (n.children && n.children.length > 5) continue;
          var r = n.getBoundingClientRect ? n.getBoundingClientRect() : null;
          var __sm = getUiScaleMetrics();
          if (!r || r.width < __sm.minSlot || r.width > __sm.maxSlot || r.height < __sm.minSlot || r.height > __sm.maxSlot) continue;
          if (Math.abs(r.width - r.height) > Math.max(18, Math.max(r.width, r.height) * 0.65)) continue;
          var rawT = String(n.textContent || '').trim();
          if (!isPlainStackCount(rawT)) continue;
          var hasVisual = !!(n.querySelector && n.querySelector('img, canvas, svg')) ||
            (n.style && n.style.backgroundImage && n.style.backgroundImage !== 'none');
          if (!hasVisual && window.getComputedStyle) {
            var bgImg = window.getComputedStyle(n).backgroundImage;
            if (bgImg && bgImg !== 'none') hasVisual = true;
          }
          if (!hasVisual) continue;
          var clickNode = n;
          if (clickNode.parentElement && clickNode.parentElement !== rootEl) {
            var pr = clickNode.parentElement.getBoundingClientRect ? clickNode.parentElement.getBoundingClientRect() : null;
            if (pr && pr.width <= __sm.maxSlot && pr.height <= __sm.maxSlot && isPlainStackCount(clickNode.parentElement.textContent || '')) {
              clickNode = clickNode.parentElement;
            }
          }
          if (seenEl.indexOf(clickNode) >= 0) continue;
          seenEl.push(clickNode);
          list.push({ el: clickNode, sig: getSlotVisualSig(clickNode), cnt: Number(rawT) || 0 });
        }
        return list;
      };

      var findScrollableBagBox = function (rootEl) {
        if (!rootEl) return null;
        var list = [rootEl];
        var ch = rootEl.querySelectorAll('div, section, ul');
        for (var i = 0; i < Math.min(ch.length, 60); i++) list.push(ch[i]);
        var best = null;
        var bestDiff = 24;
        for (var k = 0; k < list.length; k++) {
          var el = list[k];
          if (!el || !el.clientHeight) continue;
          var diff = (el.scrollHeight || 0) - el.clientHeight;
          if (diff > bestDiff) {
            bestDiff = diff;
            best = el;
          }
        }
        return best;
      };

      var cachedSig = (A._slotSigCache && A._slotSigCache[kind]) ? A._slotSigCache[kind] : '';
      var probedSigs = {};
      var probedNodes = [];
      var probedCount = 0;
      var maxProbe = 36;
      var totalSlotsSeen = 0;
      var scrollBox = findScrollableBagBox(ctx.root);
      var origScrollTop = scrollBox ? (scrollBox.scrollTop || 0) : 0;
      var maxScrollPass = scrollBox ? 3 : 0;

      try {
        for (var sp = 0; sp <= maxScrollPass && probedCount < maxProbe; sp++) {
          if (sp > 0 && scrollBox) {
            var nextTop = Math.min(scrollBox.scrollHeight, (scrollBox.scrollTop || 0) + Math.max(80, Math.floor(scrollBox.clientHeight * 0.8)));
            if (nextTop <= (scrollBox.scrollTop || 0) + 4) break;
            scrollBox.scrollTop = nextTop;
            await sleep(90);
          }
          var slots = collectStackableSlots(ctx.root);
          if (slots.length > totalSlotsSeen) totalSlotsSeen = slots.length;
          if (cachedSig && slots.length > 1) {
            slots.sort(function (a, b) {
              return (b.sig === cachedSig ? 1 : 0) - (a.sig === cachedSig ? 1 : 0);
            });
          }

          for (var si = 0; si < slots.length && probedCount < maxProbe; si++) {
            var sl = slots[si];
            if (probedNodes.indexOf(sl.el) >= 0) continue;
            if (sl.sig && sl.sig !== cachedSig && probedSigs[sl.sig]) continue;
            probedNodes.push(sl.el);
            if (sl.sig) probedSigs[sl.sig] = true;
            probedCount++;

            try { if (sl.el.scrollIntoView) sl.el.scrollIntoView({ block: 'nearest' }); } catch (eSv) {}
            try { sl.el.click(); } catch (eSl0) {
              try { dispatchRealUiClick(sl.el); } catch (eSl1) { continue; }
            }
            var popEnd = Date.now() + 420;
            var pop2 = null;
            while (Date.now() < popEnd) {
              pop2 = findOpenItemDetailPopup(ctx.root);
              if (pop2) {
                try { stealthObj().hideNode(pop2); } catch (eHp2) {}
                break;
              }
              await sleep(55);
            }
            if (!pop2) continue;
            if (await tryUseInsidePopup(pop2, sl.sig)) {
              return { scanned: true, used: true, reason: 'used_grid_probe' };
            }
            await closeItemDetailPopupOnly(pop2);
            await sleep(35);
          }
        }
      } finally {
        if (scrollBox) {
          try { scrollBox.scrollTop = origScrollTop; } catch (eSr) {}
        }
      }

      return { scanned: true, used: false, reason: 'not_found_in_bag(slots=' + totalSlotsSeen + ',probed=' + probedCount + ')' };
    } finally {
      await closeHiddenBagModal(ctx);
      stealthObj().leave();
      unlockModal();
    }
  }  async function useOneBottleFromHiddenBagDom(kind) {
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

      // Duong dan chuan: Ky Tran Cac -> Bang KNB khoa -> vat pham -> Mua -> nhap so 1 -> an Mua
      await moBangKnbKhoa(md);

      var isBuyBtnLabel = function (txt) {
        var bt = stripCountText(txt || '');
        return bt === 'mua' || bt.indexOf('mua') === 0 || bt === 'doi';
      };

      var hasOtherShopItem = function (txtNA) {
        if (kind === 'qht') return txtNA.indexOf('tien thao') >= 0;
        if (kind === 'ttl') return txtNA.indexOf('que hoa') >= 0 || txtNA.indexOf('dac biet') >= 0;
        if (kind === 'tt2') return txtNA.indexOf('que hoa') >= 0;
        return false;
      };

      var findRowBuyBtn = function () {
        var mb = document.getElementById('mBody') || md;
        var nodes = mb.querySelectorAll('.kc, .shoprow, .qrow, .item, tr, li, div, span, p');
        var matches = [];
        for (var i = 0; i < nodes.length; i++) {
          var el = nodes[i];
          if (!isVis(el)) continue;
          var meta = noAccent(extractNodeMeta(el));
          if (!matchConsumableRowText(meta, kind)) continue;
          if (hasOtherShopItem(meta)) continue;
          matches.push({ el: el, len: meta.length });
        }
        matches.sort(function (a, b) { return a.len - b.len; });
        for (var mi = 0; mi < matches.length; mi++) {
          var cur = matches[mi].el;
          for (var depth = 0; depth < 5 && cur && cur !== mb && cur !== document.body; depth++) {
            var curTxt = noAccent((cur.textContent || '').replace(/\s+/g, ' ').trim());
            if (curTxt.length > 360 || hasOtherShopItem(curTxt)) break;
            var btns = cur.querySelectorAll ? cur.querySelectorAll('button, [role="button"], a') : [];
            var buyCount = 0;
            var picked = null;
            for (var j = 0; j < btns.length; j++) {
              if (!isVis(btns[j]) || btns[j].disabled) continue;
              if (isBuyBtnLabel(btns[j].textContent || '')) {
                buyCount++;
                if (!picked) picked = btns[j];
              }
            }
            if (buyCount === 1 && picked) return picked;
            if (buyCount > 1) break;
            cur = cur.parentElement;
          }
        }
        return null;
      };

      var buyBtn = findRowBuyBtn();
      if (!buyBtn) {
        await clickKtcSubTab(md, ['bua & thuoc', 'bua va thuoc', 'thuoc', 'duoc pham', 'tieu hao', 'vat pham', 'dac biet', 'tat ca']);
        buyBtn = findRowBuyBtn();
      }
      if (!buyBtn) {
        var mbTabs = (document.getElementById('mBody') || md).querySelectorAll('[data-kb], [data-ktc-sub], [role="tab"], button, a');
        for (var ti = 0; ti < mbTabs.length; ti++) {
          var tb = mbTabs[ti];
          if (!isVis(tb) || tb.disabled) continue;
          var tTxt = stripCountText(tb.textContent || '');
          if (!tTxt || tTxt.indexOf('bang kim nguyen bao') >= 0 || tTxt === 'knb' || tTxt === 'mua' || tTxt === 'doi' || tTxt === 'huy' || tTxt === 'dong') continue;
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
        var confirmNoInp = findKtcBuyConfirmButton(null, buyBtn);
        if (confirmNoInp) {
          confirmNoInp.click();
        }
        await sleep(500);
      }
      return true;
    } finally {
      await closeModalCleanly(document.getElementById('modal'));
      stealthObj().leave();
      unlockModal();
    }
  }

  async function ensureOneConsumable(I, itemSpec) {
    var tuiSources = [(I && I.tui) ? I.tui : {}, (A.st && A.st.tui) ? A.st.tui : {}, (A.info && A.info.tui) ? A.info.tui : {}];
    var used = false;
    var bagConfirmedEmpty = false;
    var bagReason = '';

    // 1. Gop danh sach key tui (co dinh + quet dong key co so luong > 0 trong I.tui)
    var tryKeys = itemSpec.tuiKeys.slice();
    if (itemSpec.kind === 'qht') {
      for (var dts = 0; dts < tuiSources.length; dts++) {
        var srcObj = tuiSources[dts];
        if (!srcObj || typeof srcObj !== 'object') continue;
        var kList = Object.keys(srcObj);
        for (var dk = 0; dk < kList.length; dk++) {
          var rawK = kList[dk];
          if ((srcObj[rawK] | 0) <= 0) continue;
          var kNA = noAccent(rawK).toLowerCase();
          if ((kNA.indexOf('qht') >= 0 || kNA.indexOf('que') >= 0 || kNA.indexOf('tuu') >= 0 || kNA.indexOf('ruou') >= 0 || kNA.indexOf('wine') >= 0) &&
              kNA !== 'tt' && kNA !== 'ttsp' && tryKeys.indexOf(rawK) < 0) {
            tryKeys.unshift(rawK);
          }
        }
      }
    }

    for (var ki = 0; ki < tryKeys.length && !used; ki++) {
      var tk = tryKeys[ki];
      var hasQty = false;
      for (var ts = 0; ts < tuiSources.length; ts++) {
        if ((tuiSources[ts][tk] | 0) > 0) { hasQty = true; break; }
      }
      if (hasQty || (itemSpec.kind === 'qht' && tk === 'qht')) {
        var rTui = await api('dungTui', { k: tk, n: 1 }, hasQty ? 4500 : 1800);
        if (rTui && rTui.ok && (rTui.n | 0) > 0) {
          used = true;
          note('dung 1 binh ' + itemSpec.label + ' (trong tui)');
          break;
        }
      }
    }

    if (!used) {
      if (!I.dead && canRunHiddenConsumable()) {
        var bagScanRes = await scanAndUseOneFromHiddenBagDom(itemSpec.kind);
        bagReason = (bagScanRes && bagScanRes.reason) ? bagScanRes.reason : '';
        if (bagScanRes && bagScanRes.used) {
          used = true;
          note('dung 1 binh ' + itemSpec.label + ' (an trong tui)');
        } else if (bagScanRes && bagScanRes.scanned) {
          bagConfirmedEmpty = true;
        }
      }
    }

    // 2. Khi tui het va nguoi dung bat mua -> mua qua KTC an (Bang KNB khoa) roi dung ngay
    if (!used && bagConfirmedEmpty && itemSpec.allowBuy && canRunHiddenConsumable()) {
      var bought = await buyOneBottleFromHiddenKtcDom(itemSpec.kind);
      if (!bought && itemSpec.ktcKey) {
        var ktcRes = await api('muaKtc', { k: itemSpec.ktcKey, n: 1 }, 2500);
        if (ktcRes && ktcRes.ok && (ktcRes.n | 0) > 0) {
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
          for (var ki2 = 0; ki2 < tryKeys.length; ki2++) {
            var tk2 = tryKeys[ki2];
            if (itemSpec.kind === 'qht' && tk2 !== 'qht' && !(I2 && I2.tui && (I2.tui[tk2] | 0) > 0)) continue;
            var r2 = await api('dungTui', { k: tk2, n: 1 }, 2500);
            if (r2 && r2.ok && r2.n) { used = true; break; }
          }
          if (!used) {
            var useAfterBuy = await scanAndUseOneFromHiddenBagDom(itemSpec.kind);
            used = !!(useAfterBuy && useAfterBuy.used);
          }
        }
      }
      if (bought && used) {
        note('mua an (Bang KNB khoa) va dung 1 binh ' + itemSpec.label);
      } else if (bought && !used) {
        note('da mua an ' + itemSpec.label + ' - se dung o nhip ke tiep');
      }
    }

    if (!used && itemSpec.kind === 'qht' && ready('qht_diag', 60000)) {
      var tuiKeyList = Object.keys((I && I.tui) ? I.tui : {}).slice(0, 10).join(',');
      note('[QHT] chua dung duoc (' + (bagReason || 'no_scan') + ', buy=' + (itemSpec.allowBuy ? 1 : 0) + ', tui=' + (tuiKeyList || 'none') + ')');
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
    if (isQhtLocalTimerActive()) return true;
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
    if (A.cfg.thapNeDon) return;
    if (!A.cfg.ttl && !A.cfg.tt2 && !A.cfg.qht) return;
    if ((A.cool.buff_scan_15m || 0) > Date.now()) return;
    var hud = scanHudBuffs(I);
    var ttlExpiring = A.cfg.ttl && (!hud.ttl || ((hud.rem && hud.rem.ttl) || 0) <= BUFF_RENEW_LEAD_MS);
    var tt2Expiring = A.cfg.tt2 && (!hud.tt2 || ((hud.rem && hud.rem.tt2) || 0) <= BUFF_RENEW_LEAD_MS);
    var qhtExpiring = A.cfg.qht && !isQhtLocalTimerActive() && (!hud.qht || ((hud.rem && hud.rem.qht) || 0) <= BUFF_RENEW_LEAD_MS);

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
    if (A.cfg.thapNeDon) return;
    if ((I.lvl | 0) < 20) return;
    if ((I.ldtMax | 0) > 0 && (I.ldtMua | 0) >= (I.ldtMax | 0)) {
      dayAdd('ldtMuaDone', 1);
    }

    // 1. Mua Lenh Bai Da Tau bang KNB khoa (Ky Tran Cac) - chot xong khi dat max/ngay
    if (A.cfg.ldtMua && dayGet('ldtMuaDone') < 1) {
      var remKtc0 = Math.max(0, (I.ldtMax | 0) - (I.ldtMua | 0));
      var wantN0 = A.cfg.dt ? Math.min(need(I), remKtc0) : remKtc0;
      if (wantN0 > 0 && ready('ldt', 35000)) {
        var got = 0, doi = false;
        for (var i = 0; i < 3; i++) {
          var remKtc = Math.max(0, (I.ldtMax | 0) - (I.ldtMua | 0));
          var n = A.cfg.dt ? Math.min(need(I), remKtc) : remKtc;
          if (n <= 0) break;
          var r = await api('muaKtc', { k: 'ldt', n: n });
          var I2 = await info();
          if (I2) I = I2;
          if (r && r.ok && r.n > 0) got += r.n;
          if ((I.ldtMax | 0) > 0 && (I.ldtMua | 0) >= (I.ldtMax | 0)) {
            dayAdd('ldtMuaDone', 1);
            break;
          }
          if (r && r.ok && r.n >= n) break;
          if (doi || !(A.cfg.k2k > 0)) break;
          doi = true;
          var gia = (I.gia && I.gia.ldt) || 20;
          var can = (n - (r && r.n ? (r.n | 0) : 0)) * gia - (I.knbk | 0);
          if (!await k2k(can)) break;
          var I3 = await info();
          if (I3) I = I3;
        }
        if (got) note('mua ' + got + ' Lệnh Bài Dã Tẩu (KNB khóa)');
      }
    }

    // 2. Binh Phuc Duyen Lo + Ruong Phuc Duyen - doc lap voi Da Tau/need(I), co la mo ngay
    if (A.cfg.pd && ready('pd_open', 25000)) {
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
      var ruongTrongTui = (I.tui && (I.tui.ruongPd || I.tui.pdBox || I.tui.ruong)) ? (I.tui.ruongPd || I.tui.pdBox || I.tui.ruong | 0) : 0;
      for (var j = 0; j < 25 && ((I.fd | 0) >= 10 || ruongTrongTui > 0); j++) {
        var batchN = (I.fd | 0) >= 10 ? Math.min(20, Math.floor((I.fd | 0) / 10)) : Math.min(20, ruongTrongTui);
        if (batchN <= 0) break;
        var r5 = await api('ruong', { n: batchN });
        if (!r5 || !r5.ok || !(r5.n > 0)) break;
        mo += (r5.n | 0);
        if (ruongTrongTui > 0) ruongTrongTui = Math.max(0, ruongTrongTui - (r5.n | 0));
        var I5 = await info();
        if (!I5) break;
        I = I5;
      }
      if (mo > 0) note('mở hết ' + mo + ' Rương Phúc Duyên');
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
    if (items.length === 0 || dayGet('evAllDone') > 0) return;

    var canChay = function () {
      if (!A.cfg.on || !ranh()) return false;
      if (A.st && (A.st.o !== 'game' || Date.now() - A.stT >= 90000)) return false;
      if (A.st && ['phoban', 'bosstuan', 'thap', 'tbk', 'baokho'].indexOf(A.st.noi) >= 0) return false;
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
        var gt = cleanUiLabel(gc.textContent || gc.getAttribute('title') || gc.getAttribute('aria-label') || '');
        if (gt === 'phan thuong' || (gt.indexOf('phan thuong') >= 0 && gt.length <= 22)) {
          gift = gc;
          break;
        }
      }
    }
    if (!md || !md.classList.contains('hidden') || !gift || gift.disabled) return;
    if (!ready('evHongBao', 45000)) return;

    var giftMo = function () {
      return !md.classList.contains('hidden') && !!(document.querySelector('#mBody #giftTabs') || document.getElementById('giftTabs'));
    };

    var findEventTab = function () {
      var tabs = document.querySelectorAll('#mBody #giftTabs button, #giftTabs button, #mBody [data-g]');
      var fixedTabs = ['diem danh', 'moc cap', 'phuc duyen', 'thanh tuu'];
      var fallbackEv = null;
      for (var ti = 0; ti < tabs.length; ti++) {
        var tb = tabs[ti];
        if (!tb || tb.disabled || !isVis(tb)) continue;
        var dg = String(tb.getAttribute('data-g') || '').toLowerCase();
        var lbl = cleanUiLabel(tb.textContent || '');
        if (lbl.indexOf('giang sinh') >= 0 || lbl.indexOf('noel') >= 0 || lbl.indexOf('su kien') >= 0 ||
            lbl.indexOf('nha giao') >= 0 || lbl.indexOf('tet') >= 0 || lbl.indexOf('trung thu') >= 0 ||
            dg === 'gs' || dg === 'noel' || dg === 'xmas' || dg === 'ng' || dg === 'ev' || dg === 'event' || dg === 'sk') {
          return tb;
        }
        if (lbl && fixedTabs.indexOf(lbl) < 0 && !fallbackEv) {
          fallbackEv = tb;
        }
      }
      return fallbackEv;
    };

    var tenCuaHang = function (row) {
      if (!row) return '';
      var span = row.querySelector('span');
      var src = span || row;
      var clone = src.cloneNode(true);
      var rm = clone.querySelectorAll('small, button');
      for (var r = 0; r < rm.length; r++) rm[r].remove();
      var na = cleanUiLabel(clone.textContent || '');
      na = na.replace(/\s+\d+\s+moi\s+loai[\s\S]*/, '');
      na = na.replace(/\s+\d+\s+\d+\s+hom\s+nay[\s\S]*/, '');
      return na.replace(/^\d+\s+/, '').trim();
    };

    var findExchangeBtnInRow = function (row) {
      if (!row) return null;
      var btns = row.querySelectorAll('button, [role="button"]');
      for (var j = 0; j < btns.length; j++) {
        var b = btns[j];
        if (!b) continue;
        var attrs = b.attributes || [];
        for (var ai = 0; ai < attrs.length; ai++) {
          var an = attrs[ai].name || '';
          var av = String(attrs[ai].value || '');
          if (an.indexOf('data-') === 0 && av.indexOf('doi:') === 0) return b;
        }
        var lbl = cleanUiLabel(b.textContent || '');
        if (lbl === 'doi' || lbl.indexOf('doi ') === 0) {
          var rTxt = noAccent(row.textContent || '');
          if (/\d+\s*\/\s*\d+\s+hom\s+nay/i.test(rTxt) || rTxt.indexOf('moi loai') >= 0) return b;
        }
      }
      return null;
    };

    var nutTheoTen = function (ten) {
      if (!giftMo()) return null;
      var want = cleanUiLabel(ten);
      var rows = document.querySelectorAll('#mBody .qrow');
      for (var i = 0; i < rows.length; i++) {
        var b = findExchangeBtnInRow(rows[i]);
        if (!b) continue;
        if (tenCuaHang(rows[i]) === want) return b;
      }
      var allBtns = document.querySelectorAll('#mBody button');
      for (var k = 0; k < allBtns.length; k++) {
        var bk = allBtns[k];
        if (cleanUiLabel(bk.textContent || '') !== 'doi') continue;
        var parentRow = bk.closest ? (bk.closest('.qrow, .row, .item, li, tr') || bk.parentElement) : bk.parentElement;
        if (!parentRow) continue;
        var pTxt = noAccent(parentRow.textContent || '');
        if (!/\d+\s*\/\s*\d+\s+hom\s+nay/i.test(pTxt)) continue;
        if (tenCuaHang(parentRow) === want) return bk;
      }
      return null;
    };

    var dem = function (b) {
      var row = b && b.closest ? (b.closest('.qrow, .row, .item, li, tr') || b.parentElement) : null;
      if (!row) return null;
      var s = row.querySelector('small');
      var m = s ? /(\d+)\s*\/\s*(\d+)\s+hom\s+nay/i.exec(noAccent(s.textContent || '')) : null;
      if (!m) m = /(\d+)\s*\/\s*(\d+)\s+hom\s+nay/i.exec(noAccent(row.textContent || ''));
      if (!m) return null;
      var n = Number(m[1]), mx = Number(m[2]);
      if (!isFinite(n) || !isFinite(mx)) return null;
      if (n !== Math.floor(n) || mx !== Math.floor(mx)) return null;
      if (n < 0 || n > mx || mx > 1000) return null;
      return { n: n, mx: mx };
    };

    var anyNut = function () {
      for (var i = 0; i < items.length; i++) {
        if (nutTheoTen(items[i])) return true;
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
      var tab = findEventTab();
      if (!tab || tab.disabled) {
        A.cool.evHongBao = Date.now() + 5 * 60000;
        note('[SuKien] Khong tim thay tab Su kien trong Phan thuong');
        return;
      }
      if (!canChay()) return;
      tab.click();
      if (!await cho(anyNut)) {
        A.cool.evHongBao = Date.now() + 3 * 60000;
        note('[SuKien] Khong tim thay muc Doi nguyen lieu thua trong tab ' + cleanUiLabel(tab.textContent || ''));
        return;
      }

      var evSeen = 0;
      var evAllMax = true;
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
          try { if (b.scrollIntoView) b.scrollIntoView({ block: 'center' }); } catch (eSc) {}
          b.click();

          var endWait = Date.now() + 2600;
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
              await sleep(220);
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
            note('[SuKien] Chua doi them duoc ' + ten + ' (co the thieu nguyen lieu), bo qua');
            break;
          }
          await sleep(120);
        }
        if (count > 0) {
          note('Da doi ' + count + ' ' + ten + ' (Su kien Giang Sinh)');
        }
        var dAfter = dem(nutTheoTen(ten));
        if (dAfter !== null) {
          evSeen++;
          if (dAfter.n < dAfter.mx) evAllMax = false;
        } else {
          evAllMax = false;
        }
      }
      if (evSeen === items.length && evAllMax) {
        dayAdd('evAllDone', 1);
        note('[SuKien] Da doi du 100% cac muc da chon hom nay -> bo qua toi 00:00');
      } else if (evSeen > 0) {
        A.cool.evHongBao = Date.now() + 8 * 60000;
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

  function isAfter0005Vn() {
    var v = new Date(Date.now() + 7 * 3600e3);
    return (v.getUTCHours() * 60 + v.getUTCMinutes()) >= 5;
  }

  function viKnb(I) {
    var l = (A.st && typeof A.st.luong === 'number') ? A.st.luong :
      (I && typeof I.luong === 'number' ? I.luong : null);
    if (typeof l === 'number' && isFinite(l) && l >= 0) return Math.floor(l) * KNB_GIA;
    // Dự phòng đọc số vạn từ thanh HUD đỉnh màn hình (vd: "33.492 vạn")
    try {
      var nodes = document.querySelectorAll('span, div, b');
      var cap = Math.min(nodes.length, 220);
      for (var i = 0; i < cap; i++) {
        var el = nodes[i];
        if (!el || (el.children && el.children.length > 1)) continue;
        var txt = (el.textContent || '').trim();
        if (txt.length < 4 || txt.length > 24) continue;
        var m = /([\d.,]+)\s*v[aạ]n/i.exec(txt);
        if (m) {
          var van = Number(m[1].replace(/[.,]/g, ''));
          if (isFinite(van) && van >= 0) return Math.floor(van) * KNB_GIA;
        }
      }
    } catch (eV) {}
    return null;
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
        if (bt === 'ky tran cac' || bt.indexOf('ky tran') === 0 || bt === 'ktc' || (bt.indexOf('ky tran cac') >= 0 && bt.length <= 24)) {
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
    if (dayGet('knbDone') > 0) return;
    if (knbGhiLoi || !A.cfg.on || cap <= 0 || (I.lvl | 0) < 20 || !ranh()) return;
    // Mua KNB độc lập (không phụ thuộc Thẻ Giao Dịch), thực hiện từ 00:05 GMT+7 trở đi
    if (I.dg || I.tower || I.sat || I.dead) return;

    var daMua = demKnb();
    var con = Math.min(cap - daMua, KNB_NGAY - daMua, KNB_NGAY);
    if (typeof I.knbCon === 'number' && isFinite(I.knbCon)) con = Math.min(con, Math.max(0, I.knbCon | 0));
    if (con <= 0) { dayAdd('knbDone', 1); return; }

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
    if (A.st && A.st.o && A.st.o !== 'game' && (Date.now() - A.stT < 90000)) return;
    if (!A.cfg.on || (A.cfg.knbMua | 0) <= 0) return;

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
      if (demKnb() >= Math.min(cap, KNB_NGAY) || (I2 && typeof I2.knbCon === 'number' && I2.knbCon <= 0)) dayAdd('knbDone', 1);
    } else if (ready('knbIm', 15 * 60000)) {
      note('Kỳ Trân Các: đã bấm mua nhưng chưa thấy KNB tăng — kiểm tra lại');
    }
  }

  async function syncKnbNow() {
    if (!A.cfg.on || (A.cfg.knbMua | 0) <= 0) return;
    var cap = Math.min(A.cfg.knbMua | 0, KNB_NGAY);
    if (demKnb() >= cap) return;
    var waitBusyEnd = Date.now() + 12000;
    while (A.busy && Date.now() < waitBusyEnd) {
      await sleep(200);
    }
    if (A.busy) {
      A.pendingTick = true;
      return;
    }
    A.busy = true;
    try {
      A.knbSyncUntil = Date.now() + 35000;
      delete A.cool.knb;
      for (var attempt = 0; attempt < 2; attempt++) {
        if (!A.cfg.on || demKnb() >= cap) break;
        var I = await info();
        if (!I || I.dg || I.tower || I.sat || I.dead) break;
        delete A.cool.knb;
        await muaKnb(I);
        if (demKnb() >= cap) break;
        await sleep(1500);
      }
    } finally {
      A.busy = false;
      if (A.pendingTick) {
        A.pendingTick = false;
        setTimeout(function () { tick().catch(function () {}); }, 250);
      }
    }
  }

  function getHpMpRatio(I) {
    var hpRatio = 1.0, mpRatio = 1.0;
    try {
      var s = (A.st && Date.now() - A.stT < 15000) ? A.st : null;
      var hp = (s && typeof s.hp === 'number') ? s.hp : (typeof I.hp === 'number' ? I.hp : null);
      var maxHp = (s && typeof s.maxHp === 'number' && s.maxHp > 0) ? s.maxHp : ((I && typeof I.maxHp === 'number' && I.maxHp > 0) ? I.maxHp : null);
      if (hp !== null && maxHp) hpRatio = Math.max(0, Math.min(1, hp / maxHp));
      else if (s && typeof s.hpPct === 'number') hpRatio = Math.max(0, Math.min(1, s.hpPct > 1 ? s.hpPct / 100 : s.hpPct));

      var mp = (s && typeof s.mp === 'number') ? s.mp : (typeof I.mp === 'number' ? I.mp : null);
      var maxMp = (s && typeof s.maxMp === 'number' && s.maxMp > 0) ? s.maxMp : ((I && typeof I.maxMp === 'number' && I.maxMp > 0) ? I.maxMp : null);
      if (mp !== null && maxMp) mpRatio = Math.max(0, Math.min(1, mp / maxMp));
      else if (s && typeof s.mpPct === 'number') mpRatio = Math.max(0, Math.min(1, s.mpPct > 1 ? s.mpPct / 100 : s.mpPct));
    } catch (eR) {}
    return { hp: hpRatio, mp: mpRatio };
  }

  function hasHeavyInjury(I) {
    try {
      if (I && (I.dead || I.trongThuong || I.injured)) return true;
      if (A.st && Date.now() - A.stT < 15000) {
        if (A.st.dead || A.st.trongThuong || A.st.injured) return true;
        if (typeof A.st.hp === 'number' && A.st.hp <= 0) return true;
      }
    } catch (eIn) {}
    return false;
  }

  function updateTowerTelemetry(now, I, inTowerNow) {
    var tm = A.towerMetric;
    var curFloor = 0;
    try {
      var stg = document.getElementById('stageLbl');
      var m = stg ? /(\d+)/.exec(stg.textContent || '') : null;
      if (m) curFloor = Number(m[1]) || 0;
      if (!curFloor && A.st && (A.st.thapFloor || A.st.floor || A.st.tang)) curFloor = Number(A.st.thapFloor || A.st.floor || A.st.tang) || 0;
      if (!curFloor && I && (I.thapFloor || I.floor || I.tang)) curFloor = Number(I.thapFloor || I.floor || I.tang) || 0;
    } catch (eT) {}

    if (inTowerNow) {
      if (!tm.wasInTower) {
        tm.wasInTower = true;
        var outMins = tm.leftTowerAt > 0 ? Math.max(0, Math.round((now - tm.leftTowerAt) / 60000)) : 0;
        if (curFloor > 0 && tm.floor > 0 && curFloor < tm.floor) {
          // Vào lại tầng thấp hơn -> đo lại từ đầu nhưng vẫn giữ mốc tham chiếu lần trước (prevFloor / prevSecPerFloor)
          tm.prevFloor = tm.floor;
          tm.prevSecPerFloor = tm.secPerFloor;
          tm.floor = curFloor;
          tm.floorEnteredAt = now;
          note('đo lại Tháp từ tầng ' + curFloor + ' (lần trước: tầng ' + tm.prevFloor + ', ngoài Tháp ' + outMins + ' phút)');
        } else {
          tm.floorEnteredAt = now;
          if (curFloor > 0) tm.floor = curFloor;
        }
        tm.leftTowerAt = 0;
      } else if (curFloor > 0) {
        if (tm.floor > 0 && curFloor > tm.floor) {
          var elapsedSec = Math.max(1, Math.round((now - (tm.floorEnteredAt || now)) / 1000));
          tm.secPerFloor = elapsedSec;
          var runMins = Math.max(0.5, (now - (A.started || now - 60000)) / 60000);
          tm.floorsPer30m = Math.round((curFloor / runMins) * 30 * 10) / 10;
          tm.floorEnteredAt = now;
        }
        tm.floor = curFloor;
      }
    } else {
      if (tm.wasInTower) {
        tm.wasInTower = false;
        tm.leftTowerAt = now;
      }
      if (tm.floor > 0 && tm.leftTowerAt > 0) {
        var outsideMin = Math.max(0, Math.floor((now - tm.leftTowerAt) / 60000));
        var prevTag = tm.prevFloor > 0 ? (' · lần trước T' + tm.prevFloor) : '';
        var spdTag = tm.secPerFloor > 0 ? (tm.secPerFloor + 's/tầng · ') : '';
        A.last = '🤖 Tháp T' + tm.floor + ' (' + spdTag + 'ngoài Tháp ' + outsideMin + ' phút' + prevTag + ')';
      }
    }
  }

  // Danh sách nhóm bản đồ luyện công & thư mục con cấp độ / khu vực trong game
  var TRAINING_MAP_GROUPS = {
    'ba lang huyen': '',
    'hoa son': 'hoa son',
    'nga my phai': 'hoa son',
    'thien nhan giao tang 1': 'hoa son',
    'me cung day gieng': 'hoa son',
    'con lon phai': 'hoa son',
    'ngu doc giao': 'hoa son',
    'kiem cac tay bac': 'kiem cac tay bac',
    'manh ho dong': 'kiem cac tay bac',
    'hoa lang dong': 'kiem cac tay bac',
    'thuc cuong son': 'thuc cuong son',
    'tuyet bao dong tang 1': 'thuc cuong son',
    'yen tu dong': 'thuc cuong son',
    'me cung nhap mon': 'thuc cuong son',
    'diem thuong dong tang 1': 'diem thuong dong tang 1',
    'thien tam dong': 'thien tam dong',
    'day dong dinh ho tang 2': 'thien tam dong',
    'bang huyet dong': 'thien tam dong',
    'hoanh son phai': 'hoanh son phai',
    'lam du quan': 'lam du quan',
    'son bao dong': 'lam du quan',
    'linh coc dong': 'lam du quan',
    'chan nui truong bach': 'chan nui truong bach',
    'vu lang dong': 'chan nui truong bach',
    'phi thien dong': 'chan nui truong bach',
    'phu dung dong': 'chan nui truong bach',
    'nhan thach dong': 'chan nui truong bach',
    'sa mac son dong 1': 'sa mac son dong 1',
    'can vien dong': 'sa mac son dong 1',
    'khoa lang dong': 'sa mac son dong 1',
    'truong bach son nam': 'truong bach son nam',
    'truong bach son bac': 'truong bach son nam',
    'phong lang do': '',
    'mac bac thao nguyen': 'Vi sơn đảo',
    'vi son dao': 'Vi sơn đảo'
  };

  var KNOWN_CITY_NAMES = [
    'tuong duong', 'bien kinh', 'lam an', 'thanh do', 'duong chau',
    'dai ly', 'phuong tuong', 'ba lang', 'thong thien', 'ban do'
  ];

  function normalizeMapCompareKey(raw) {
    return noAccent(raw || '')
      .replace(/\[[^\]]*\]/g, ' ')
      .replace(/\([^)]*\)/g, ' ')
      .replace(/\bc[aấ]p\s*\d+[\s–-]*\d*\b/gi, ' ')
      .replace(/^[+\-•·*>,\s]+/, '')
      .replace(/\.+$/, '')
      .replace(/\s+/g, ' ')
      .trim();
  }

  function simulateMousePointerClick(el) {
    if (!el) return false;
    var ok = false;
    try {
      var rect = el.getBoundingClientRect ? el.getBoundingClientRect() : null;
      var hasRect = !!(rect && rect.width > 0 && rect.height > 0);
      var cx = hasRect ? Math.round(rect.left + rect.width * 0.5) : 10;
      var cy = hasRect ? Math.round(rect.top + rect.height * 0.5) : 10;

      if (typeof el.focus === 'function') {
        try { el.focus({ preventScroll: true }); } catch (eF1) { try { el.focus(); } catch (eF2) {} }
      }

      if (typeof PointerEvent === 'function') {
        try {
          el.dispatchEvent(new PointerEvent('pointerover', {
            view: window, bubbles: true, cancelable: true, composed: true,
            clientX: cx, clientY: cy, button: 0, buttons: 0, pointerId: 1, pointerType: 'mouse', isPrimary: true
          }));
          el.dispatchEvent(new PointerEvent('pointerenter', {
            view: window, bubbles: false, cancelable: false, composed: true,
            clientX: cx, clientY: cy, button: 0, buttons: 0, pointerId: 1, pointerType: 'mouse', isPrimary: true
          }));
          el.dispatchEvent(new PointerEvent('pointerdown', {
            view: window, bubbles: true, cancelable: true, composed: true,
            clientX: cx, clientY: cy, button: 0, buttons: 1, pointerId: 1, pointerType: 'mouse', isPrimary: true
          }));
        } catch (eP1) {}
      }

      el.dispatchEvent(new MouseEvent('mouseover', {
        view: window, bubbles: true, cancelable: true, composed: true,
        clientX: cx, clientY: cy, button: 0, buttons: 0
      }));
      el.dispatchEvent(new MouseEvent('mouseenter', {
        view: window, bubbles: false, cancelable: false, composed: true,
        clientX: cx, clientY: cy, button: 0, buttons: 0
      }));
      el.dispatchEvent(new MouseEvent('mousedown', {
        view: window, bubbles: true, cancelable: true, composed: true,
        clientX: cx, clientY: cy, button: 0, buttons: 1
      }));

      if (typeof PointerEvent === 'function') {
        try {
          el.dispatchEvent(new PointerEvent('pointerup', {
            view: window, bubbles: true, cancelable: true, composed: true,
            clientX: cx, clientY: cy, button: 0, buttons: 0, pointerId: 1, pointerType: 'mouse', isPrimary: true
          }));
        } catch (eP2) {}
      }

      el.dispatchEvent(new MouseEvent('mouseup', {
        view: window, bubbles: true, cancelable: true, composed: true,
        clientX: cx, clientY: cy, button: 0, buttons: 0
      }));

      if (typeof el.click === 'function') {
        el.click();
      }
      el.dispatchEvent(new MouseEvent('click', {
        view: window, bubbles: true, cancelable: true, composed: true,
        clientX: cx, clientY: cy, button: 0, buttons: 0
      }));

      if (typeof el.onclick === 'function') {
        try { el.onclick.call(el, new MouseEvent('click', { view: window, bubbles: true, cancelable: true, clientX: cx, clientY: cy })); } catch (eInline) {}
      }
      ok = true;
    } catch (eSim) {
      try { if (typeof el.click === 'function') { el.click(); ok = true; } } catch (eSimFb) {}
    }
    return ok;
  }

  function dispatchRealUiClick(el) {
    if (!el) return false;
    var ok = simulateMousePointerClick(el);
    try {
      var cur = el.parentElement;
      var depth = 0;
      while (cur && cur !== document.body && depth < 4) {
        var tag = (cur.tagName || '').toUpperCase();
        var hasClickAttr = cur.hasAttribute && (
          cur.hasAttribute('onclick') || cur.hasAttribute('data-map') ||
          cur.hasAttribute('data-id') || cur.hasAttribute('data-value') ||
          cur.getAttribute('role') === 'button'
        );
        if (tag === 'BUTTON' || tag === 'A' || tag === 'LI' || hasClickAttr || typeof cur.onclick === 'function') {
          if (isVis(cur) && !cur.disabled) {
            simulateMousePointerClick(cur);
            ok = true;
          }
        }
        cur = cur.parentElement;
        depth++;
      }
    } catch (eAnc) {}
    return ok;
  }

  function findMapPickerButton() {
    try {
      var md = document.getElementById('modal');
      var directIds = ['mapBtn', 'mMapBtn', 'btnMap', 'mapSelBtn', 'mapNameBtn', 'u20map', 'curMapBtn', 'mapLbl', 'curMap', 'mapSwitchBtn', 'bMap'];
      for (var i = 0; i < directIds.length; i++) {
        var el = document.getElementById(directIds[i]);
        if (el && isVis(el) && !el.disabled && (!md || !md.contains(el))) return el;
      }
      var attrEls = document.querySelectorAll('[data-open="map"], [data-act="map"], [data-map], [onclick*="map" i], [title*="Bản đồ" i], [aria-label*="Bản đồ" i]');
      for (var ai = 0; ai < attrEls.length; ai++) {
        var ae = attrEls[ai];
        if (ae && isVis(ae) && !ae.disabled && (!md || !md.contains(ae)) && ae.id !== 'vlmAuto') {
          var at = noAccent(ae.textContent || '');
          if (at.indexOf('hoa binh') < 0 && at.indexOf('che do') < 0) return ae;
        }
      }
      var winW = window.innerWidth || 800;
      var winH = window.innerHeight || 480;
      var els = document.querySelectorAll('button, [role="button"], .btn, a, div, span');
      var best = null, bestScore = -1;
      var cap = Math.min(els.length, 520);
      for (var bi = 0; bi < cap; bi++) {
        var b = els[bi];
        if (!b || !isVis(b) || b.disabled || b.id === 'vlmAuto' || b.id === 'mswBtn') continue;
        if (b.children && b.children.length > 3) continue;
        if (md && md.contains(b)) continue;
        if (b.closest && b.closest('#tabs, #chatLines, #u20lines, #logBox, #u20log, #modeSw, #mswMenu')) continue;
        var rect = b.getBoundingClientRect ? b.getBoundingClientRect() : null;
        if (!rect || rect.width < 24 || rect.height < 12 || rect.width > 280 || rect.height > 64) continue;
        if (rect.right < winW * 0.45 || rect.top < 8 || rect.top > winH * 0.65) continue;
        var rawText = (b.textContent || '').trim();
        var t = normalizeMapCompareKey(rawText);
        if (!t || t.length < 2 || t.length > 42) continue;
        if (/^[\d.,\s/k%]+$/.test(t)) continue;
        if (t.indexOf('hoa binh') >= 0 || t.indexOf('auto') >= 0 || t.indexOf('xuong ngua') >= 0 ||
            t.indexOf('len ngua') >= 0 || t.indexOf('xoay chieu') >= 0 || t.indexOf('che do') >= 0 ||
            t.indexOf('pk') === 0 || t.indexOf('do sat') >= 0 || t.indexOf('luc tay') >= 0 ||
            t.indexOf('van') === t.length - 3) continue;

        var score = 0;
        if (t.indexOf('mac bac') === 0 || t.indexOf('vi son') === 0) { score = 260; }
        for (var mk in TRAINING_MAP_GROUPS) {
          if (mk === t || (t.length >= 3 && mk.indexOf(t) === 0)) { score = 250; break; }
        }
        if (score === 0) {
          for (var ci = 0; ci < KNOWN_CITY_NAMES.length; ci++) {
            if (t.indexOf(KNOWN_CITY_NAMES[ci]) >= 0) { score = 200; break; }
          }
        }
        if (score === 0 && (b.tagName === 'BUTTON' || b.getAttribute('role') === 'button') && rawText.indexOf('...') >= 0) {
          score = 135;
        }
        if (score > bestScore) { bestScore = score; best = b; }
      }
      return bestScore >= 120 ? best : null;
    } catch (eMapBtn) {}
    return null;
  }

  function findOpenMapListContainer() {
    try {
      var winW = window.innerWidth || 800;
      var winH = window.innerHeight || 480;
      var cands = document.querySelectorAll('#mapModal, #mapList, #mapPop, #mapMenu, .popup, .modal, [role="dialog"], div, section, ul');
      var best = null;
      var bestArea = 999999999;
      var cap = Math.min(cands.length, 650);
      for (var i = 0; i < cap; i++) {
        var el = cands[i];
        if (!el || !isVis(el) || el.id === 'vlmAuto' || el === document.body || el === document.documentElement) continue;
        var rect = el.getBoundingClientRect ? el.getBoundingClientRect() : null;
        if (!rect || rect.width < 140 || rect.height < 110) continue;
        if (rect.width >= winW * 0.94 && rect.height >= winH * 0.94) continue;
        var txt = noAccent(el.textContent || '');
        if (!txt || txt.length < 24) continue;
        var hits = 0;
        if (txt.indexOf('mac bac thao nguyen') >= 0 && txt.indexOf('vi son dao') >= 0) hits += 2;
        if (txt.indexOf('130') >= 0 && txt.indexOf('140') >= 0) hits += 1;
        if (txt.indexOf('phong lang do') >= 0) hits += 1;
        if (txt.indexOf('truong bach son') >= 0) hits += 1;
        if (txt.indexOf('sa mac son dong') >= 0) hits += 1;
        if (txt.indexOf('ban do luyen cong') >= 0 || txt.indexOf('chon ban do') >= 0) hits += 2;
        if (hits >= 2) {
          var area = rect.width * rect.height;
          if (area < bestArea) {
            bestArea = area;
            best = el;
          }
        }
      }
      if (best) return best;
    } catch (eFindPop) {}
    return null;
  }

  function isAlreadyOnTrainingMap(I, targetMapName) {
    var rawNA = noAccent(targetMapName || '').replace(/\s+/g, ' ').trim();
    var isViSon = rawNA.indexOf('vi son') >= 0 || rawNA.indexOf('son dao') >= 0;
    var isMacBac = rawNA.indexOf('mac bac') >= 0 || rawNA.indexOf('thao nguyen') >= 0;
    var want = isViSon ? 'vi son dao' : (isMacBac ? 'mac bac thao nguyen' : normalizeMapCompareKey(targetMapName || ''));
    if (!want) return true;
    try {
      var pickerBtn = findMapPickerButton();
      if (pickerBtn && isVis(pickerBtn)) {
        var btnKey = normalizeMapCompareKey(pickerBtn.textContent || '');
        if (btnKey.length >= 4) {
          if (isViSon) {
            if (btnKey.indexOf('vi son') === 0 || btnKey.indexOf('son dao') >= 0) return true;
            if (btnKey.indexOf('mac bac') === 0) return false;
          } else if (isMacBac) {
            if (btnKey.indexOf('mac bac') === 0 || btnKey.indexOf('thao nguyen') >= 0) return true;
            if (btnKey.indexOf('vi son') === 0) return false;
          } else if (btnKey === want) {
            return true;
          }
        }
      }
      var curNames = [];
      if (A.st && A.st.map) curNames.push(normalizeMapCompareKey(A.st.map));
      if (A.st && A.st.mapName) curNames.push(normalizeMapCompareKey(A.st.mapName));
      if (A.st && A.st.tenMap) curNames.push(normalizeMapCompareKey(A.st.tenMap));
      if (I && I.map) curNames.push(normalizeMapCompareKey(I.map));
      if (I && I.mapName) curNames.push(normalizeMapCompareKey(I.mapName));
      for (var i = 0; i < curNames.length; i++) {
        var cn = curNames[i];
        if (!cn || cn.length < 3) continue;
        if (isViSon && (cn.indexOf('vi son') === 0 || cn === 'vi son dao')) return true;
        if (isMacBac && (cn.indexOf('mac bac') === 0 || cn === 'mac bac thao nguyen')) return true;
        if (cn === want) return true;
      }
    } catch (eChk) {}
    return false;
  }
  function scoreMapCandidateElement(el, wantKey, mode) {
    if (!el || !isVis(el) || el.disabled) return -1;
    if (el.id === 'vlmAuto' || el.id === 'mswBtn' || el.id === 'mClose') return -1;
    var childCount = el.children ? el.children.length : 0;
    if (childCount > 10) return -1;

    var rect = el.getBoundingClientRect ? el.getBoundingClientRect() : null;
    if (!rect || rect.width < 10 || rect.height < 6) return -1;
    if (rect.height > 180 || rect.width > 640) return -1;

    var rawText = (el.textContent || '').trim();
    if (!rawText || rawText.length > 110) return -1;

    var rawNA = noAccent(rawText).replace(/\s+/g, ' ').trim();
    if (rawNA.indexOf('ban do luyen cong') === 0 || rawNA.indexOf('chon ban do') === 0 || rawNA.indexOf('danh sach ban do') === 0) return -1;
    if (/^cap\s*\d+[\s–-]*\d*$/i.test(rawNA)) return -1;

    var t = normalizeMapCompareKey(rawText);
    if (!t || t.length < 2 || t.length > 72) return -1;

    var matched = false;
    var baseScore = 0;
    if (mode === 'exact') {
      matched = (t === wantKey);
      baseScore = 340;
    } else if (mode === 'prefix') {
      matched = (t.indexOf(wantKey) === 0 && (t.length === wantKey.length || t.charAt(wantKey.length) === ' '));
      baseScore = 260;
    } else if (mode === 'contains') {
      matched = (wantKey.length >= 3 && t.indexOf(wantKey) >= 0);
      baseScore = 170;
    }
    if (!matched) return -1;

    var tag = (el.tagName || '').toUpperCase();
    if (tag === 'BUTTON' || el.getAttribute('role') === 'button' || tag === 'A' || tag === 'LI') baseScore += 45;
    if (el.hasAttribute && (el.hasAttribute('onclick') || el.hasAttribute('data-map') || el.hasAttribute('data-id'))) baseScore += 35;
    if (childCount === 0) baseScore += 25;
    else if (childCount <= 4) baseScore += 15;
    if (rect.height <= 80) baseScore += 15;

    return baseScore;
  }

  function rankMapItemElements(root, wantKey) {
    if (!root) return [];
    var modes = ['exact', 'prefix', 'contains'];
    var els = root.querySelectorAll('button, [role="button"], .btn, span, div, li, a, tr, td, p');
    var scoredList = [];
    for (var m = 0; m < modes.length; m++) {
      for (var i = 0; i < els.length; i++) {
        var sc = scoreMapCandidateElement(els[i], wantKey, modes[m]);
        if (sc >= 0) {
          scoredList.push({ el: els[i], score: sc });
        }
      }
      if (scoredList.length > 0 && modes[m] === 'exact') break;
    }
    scoredList.sort(function (a, b) { return b.score - a.score; });
    var uniqueEls = [];
    for (var k = 0; k < scoredList.length; k++) {
      if (uniqueEls.indexOf(scoredList[k].el) < 0) uniqueEls.push(scoredList[k].el);
    }
    return uniqueEls;
  }

  function pickBestMapItemElement(root, wantKey, mode) {
    var list = rankMapItemElements(root, wantKey);
    return list.length > 0 ? list[0] : null;
  }

  async function ensureTrainingTabSelectedInMapPopup(popup) {
    if (!popup) return;
    try {
      var tabs = popup.querySelectorAll('button, [role="tab"], [role="button"], .tab, a, span, div');
      for (var i = 0; i < tabs.length; i++) {
        var tb = tabs[i];
        if (!tb || !isVis(tb) || tb.disabled || tb.id === 'vlmAuto') continue;
        if (tb.children && tb.children.length > 2) continue;
        var rect = tb.getBoundingClientRect ? tb.getBoundingClientRect() : null;
        if (!rect || rect.height > 72) continue;
        var tt = noAccent(tb.textContent || '').replace(/\s+/g, ' ').trim();
        if (tt === 'luyen cong' || tt === 'ban do luyen cong') {
          var isSel = (tb.getAttribute('aria-selected') === 'true') ||
            (tb.classList && (tb.classList.contains('active') || tb.classList.contains('on') || tb.classList.contains('selected')));
          if (!isSel) {
            dispatchRealUiClick(tb);
            await sleep(300);
          }
          break;
        }
      }
    } catch (eTabM) {}
  }

  function collectScrollContainers(root) {
    var scrollables = [];
    if (!root) return scrollables;
    try {
      var all = [root].concat(Array.prototype.slice.call(root.querySelectorAll('div, ul, ol, section, tbody')));
      for (var i = 0; i < all.length; i++) {
        var sEl = all[i];
        if (!sEl || !isVis(sEl)) continue;
        if (sEl.scrollHeight > sEl.clientHeight + 8) {
          scrollables.push(sEl);
        }
      }
    } catch (eColSc) {}
    return scrollables;
  }

  async function expandSubFoldersInMapPopup(popup, wantNA, groupNA) {
    if (!popup) return false;
    var expandedAny = false;
    try {
      var keysToOpen = [];
      if (groupNA) keysToOpen.push(normalizeMapCompareKey(groupNA));
      if (wantNA) keysToOpen.push(wantNA);

      var rows = popup.querySelectorAll('div, li, button, span, tr, section');
      for (var r = 0; r < rows.length; r++) {
        var row = rows[r];
        if (!row || !isVis(row) || row.id === 'vlmAuto') continue;
        var rect = row.getBoundingClientRect ? row.getBoundingClientRect() : null;
        if (!rect || rect.height > 140 || rect.width > 640) continue;
        var rt = noAccent(row.textContent || '').replace(/\s+/g, ' ').trim();
        if (!rt || rt.length > 130) continue;

        var matchesFolder = false;
        for (var k = 0; k < keysToOpen.length; k++) {
          var kw = keysToOpen[k];
          if (kw && kw.length >= 2 && rt.indexOf(kw) >= 0) {
            matchesFolder = true;
            break;
          }
        }
        var hasExpandMarker = (rt.indexOf('+') === 0 || rt.indexOf('▸') >= 0 || rt.indexOf('▶') >= 0 || rt.indexOf('▾') >= 0 || rt.indexOf('▼') >= 0 || /cap\s*\d+/i.test(rt));
        if (matchesFolder && (hasExpandMarker || row.getAttribute('aria-expanded') === 'false' || (row.className && /fold|group|folder|collapse|expand|header|cat/i.test(String(row.className))))) {
          var toggleBtn = row.querySelector('button, [role="button"], .toggle, .arrow, span') || row;
          try { if (row.scrollIntoView) row.scrollIntoView({ block: 'center' }); } catch (eScF) {}
          await sleep(160);
          dispatchRealUiClick(toggleBtn);
          await sleep(360);
          expandedAny = true;
        }
      }
    } catch (eExp) {}
    return expandedAny;
  }

  async function findMapItemAcrossScrolls(popup, wantNA, groupNA) {
    var curRoot = findOpenMapListContainer() || popup;
    var found = rankMapItemElements(curRoot, wantNA);
    if (found.length > 0) return found;

    var didExpand = await expandSubFoldersInMapPopup(curRoot, wantNA, groupNA);
    if (didExpand) {
      curRoot = findOpenMapListContainer() || curRoot;
      found = rankMapItemElements(curRoot, wantNA);
      if (found.length > 0) return found;
    }

    try {
      var scrollables = collectScrollContainers(curRoot);
      for (var s = 0; s < scrollables.length; s++) {
        var scBox = scrollables[s];
        var origTop = scBox.scrollTop || 0;
        var maxTop = Math.max(0, scBox.scrollHeight - scBox.clientHeight);
        var stepPx = Math.max(64, Math.floor(scBox.clientHeight * 0.65));
        for (var pos = 0; pos <= maxTop + stepPx; pos += stepPx) {
          var targetPos = Math.min(pos, maxTop);
          scBox.scrollTop = targetPos;
          try { scBox.dispatchEvent(new Event('scroll', { bubbles: true })); } catch (eEvSc) {}
          await sleep(130);

          var activeRoot = findOpenMapListContainer() || scBox;
          found = rankMapItemElements(activeRoot, wantNA);
          if (found.length > 0) return found;

          if (await expandSubFoldersInMapPopup(activeRoot, wantNA, groupNA)) {
            await sleep(220);
            activeRoot = findOpenMapListContainer() || activeRoot;
            found = rankMapItemElements(activeRoot, wantNA);
            if (found.length > 0) return found;
          }
          if (targetPos >= maxTop) break;
        }
        scBox.scrollTop = origTop;
      }
    } catch (eScr) {}
    return [];
  }

  function findMapConfirmButton(root) {
    var searchRoots = [root, document.getElementById('mBody'), document.getElementById('modal'), document.body];
    for (var s = 0; s < searchRoots.length; s++) {
      var sr = searchRoots[s];
      if (!sr || !isVis(sr)) continue;
      var btns = sr.querySelectorAll('button, [role="button"], .btn, a');
      for (var i = 0; i < btns.length; i++) {
        var b = btns[i];
        if (!b || !isVis(b) || b.disabled || b.id === 'vlmAuto' || b.id === 'mClose') continue;
        var bt = noAccent(b.textContent || '').replace(/\s+/g, ' ').trim();
        if (bt === 'vao' || bt === 'vao map' || bt === 'vao ban do' || bt === 'dich chuyen' || bt === 'xac nhan' || bt === 'chon') {
          return b;
        }
      }
    }
    return null;
  }

  async function selectTrainingMap(I, targetMapName) {
    var rawNA = noAccent(targetMapName || '').replace(/\s+/g, ' ').trim();
    var isViSon = rawNA.indexOf('vi son') >= 0 || rawNA.indexOf('son dao') >= 0;
    var isMacBac = rawNA.indexOf('mac bac') >= 0 || rawNA.indexOf('thao nguyen') >= 0;
    var targetClean = isViSon ? 'Vi sơn đảo' : (isMacBac ? 'Mạc Bắc Thảo Nguyên' : String(targetMapName || '').trim());
    if (!targetClean) return false;
    var wantNA = isViSon ? 'vi son dao' : (isMacBac ? 'mac bac thao nguyen' : normalizeMapCompareKey(targetClean));

    if (isAlreadyOnTrainingMap(I, targetClean)) {
      A.lastMapSelected = targetClean;
      A.forceMapSwitchUntil = 0;
      return true;
    }

    var forceNow = !!(A.forceMapSwitchUntil && Date.now() < A.forceMapSwitchUntil);
    if (!forceNow && (isUserControlling(4500) || isModalLocked())) return false;
    if (!forceNow && !ready('select_map', 8000)) return false;
    if (forceNow && !ready('select_map_force', 1500)) return false;

    var pickerBtn = findMapPickerButton();

    function isExactTargetPill(el, allowHidden) {
      if (!el || el.disabled) return false;
      if (!allowHidden && !isVis(el)) return false;
      if (el.id === 'vlmAuto' || el.id === 'mswBtn' || el.id === 'mClose') return false;
      if (pickerBtn && (el === pickerBtn || pickerBtn.contains(el) || el.contains(pickerBtn))) return false;
      if (el.closest && el.closest('#vlmAuto, #tabs, #chatLines, #u20lines, #logBox, #u20log, #modeSw, #mswMenu')) return false;
      var rawTxt = (el.textContent || '').replace(/\s+/g, ' ').trim();
      if (!rawTxt || rawTxt.length > 32) return false;
      if (rawTxt.indexOf('+') >= 0 || rawTxt.indexOf('-') >= 0 || rawTxt.indexOf('...') >= 0) return false;
      var normTxt = noAccent(rawTxt).replace(/\s+/g, ' ').trim();
      if (normTxt.indexOf('cap') >= 0 || /\d/.test(normTxt)) return false;
      if (isViSon) return normTxt === 'vi son dao';
      if (isMacBac) return normTxt === 'mac bac thao nguyen';
      return normalizeMapCompareKey(rawTxt) === wantNA;
    }

    function pickBestPill(root, allowHidden) {
      if (!root) return null;
      var nodes = root.querySelectorAll('button, [role="button"], .btn, div, span, li, a');
      var best = null;
      var bestScore = -1;
      for (var i = 0; i < nodes.length; i++) {
        var el = nodes[i];
        if (!isExactTargetPill(el, allowHidden)) continue;
        var clickEl = el;
        var par = el.parentElement;
        if (par && par !== root && isExactTargetPill(par, allowHidden) && (par.tagName === 'BUTTON' || par.getAttribute('role') === 'button')) {
          clickEl = par;
        }
        var sc = 100;
        if (clickEl.tagName === 'BUTTON' || clickEl.getAttribute('role') === 'button') sc += 80;
        if (clickEl.children && clickEl.children.length === 0) sc += 30;
        if (typeof clickEl.onclick === 'function') sc += 40;
        if (sc > bestScore) { bestScore = sc; best = clickEl; }
      }
      return best;
    }

    function verifySwitched() {
      if (isAlreadyOnTrainingMap(I, targetClean)) return true;
      var pbNow = findMapPickerButton() || pickerBtn;
      if (pbNow && isVis(pbNow)) {
        var pbKey = normalizeMapCompareKey(pbNow.textContent || '');
        if (isViSon && (pbKey.indexOf('vi son') === 0 || pbKey.indexOf('son dao') >= 0)) return true;
        if (isMacBac && (pbKey.indexOf('mac bac') === 0 || pbKey.indexOf('thao nguyen') >= 0)) return true;
        if (!isViSon && !isMacBac && pbKey === wantNA) return true;
      }
      return false;
    }

    // Buoc 1: Thu kich hoat ngam neu nut map da co san trong DOM an
    try {
      var silentPill = pickBestPill(document, true);
      if (silentPill) {
        try { silentPill.click(); } catch (eS0) {}
        await sleep(320);
        if (verifySwitched()) {
          A.lastMapSelected = targetClean;
          A.forceMapSwitchUntil = 0;
          note('Da chuyen ngam sang map Luyen Cong: ' + targetClean);
          return true;
        }
      }
    } catch (eSilent) {}

    // Buoc 2: Mo popup map o trang thai an (opacity: 0), cuon xuong nhom 130-140, bam dung nut con roi dong ngay
    if (!pickerBtn) return false;
    var openedPopup = null;
    var savedOpacity = null;
    var savedTransition = null;
    var popupWasAlreadyOpen = !!findOpenMapListContainer();

    try {
      if (!popupWasAlreadyOpen) {
        dispatchRealUiClick(pickerBtn);
      }
      var waitEnd = Date.now() + 2600;
      while (Date.now() < waitEnd) {
        openedPopup = findOpenMapListContainer();
        if (openedPopup) break;
        await sleep(60);
      }
      if (!openedPopup) return false;

      try {
        savedOpacity = openedPopup.style.opacity;
        savedTransition = openedPopup.style.transition;
        openedPopup.style.transition = 'none';
        openedPopup.style.opacity = '0';
      } catch (eHide) {}

      await sleep(120);
      openedPopup = findOpenMapListContainer() || openedPopup;

      function scrollPopupToBottom(root) {
        var scs = collectScrollContainers(root);
        for (var si = 0; si < scs.length; si++) {
          try { scs[si].scrollTop = scs[si].scrollHeight; } catch (eSc) {}
        }
      }

      scrollPopupToBottom(openedPopup);
      await sleep(100);

      var pillBtn = pickBestPill(openedPopup, false);

      // Chi bam dau '+' de mo nhom 130-140 neu nut con chua hien, tuyet doi khong bam dau '-'
      if (!pillBtn && (isViSon || isMacBac)) {
        var rows = openedPopup.querySelectorAll('div, li, tr, section');
        for (var ri = 0; ri < rows.length; ri++) {
          var row = rows[ri];
          if (!row || !isVis(row)) continue;
          var rTxt = noAccent(row.textContent || '').replace(/\s+/g, ' ').trim();
          if (rTxt.length > 100) continue;
          if (rTxt.indexOf('130') >= 0 && rTxt.indexOf('140') >= 0) {
            var plusBtn = null;
            var subBtns = row.querySelectorAll('button, span, div');
            for (var sb = 0; sb < subBtns.length; sb++) {
              if ((subBtns[sb].textContent || '').trim() === '+') {
                plusBtn = subBtns[sb];
                break;
              }
            }
            if (plusBtn) {
              dispatchRealUiClick(plusBtn);
              await sleep(220);
              scrollPopupToBottom(openedPopup);
              await sleep(100);
              break;
            }
          }
        }
        pillBtn = pickBestPill(openedPopup, false);
      }

      var switched = false;
      if (pillBtn) {
        try { if (pillBtn.scrollIntoView) pillBtn.scrollIntoView({ block: 'center', inline: 'nearest' }); } catch (eSv) {}
        await sleep(80);
        dispatchRealUiClick(pillBtn);
        await sleep(360);
        switched = verifySwitched();
        if (!switched && pillBtn.isConnected) {
          try { pillBtn.click(); } catch (eCk2) {}
          await sleep(360);
          switched = verifySwitched();
        }
      }

      if (switched) {
        A.lastMapSelected = targetClean;
        A.forceMapSwitchUntil = 0;
        note('Da chuyen sang map Luyen Cong: ' + targetClean);
        return true;
      }

      A.cool.select_map = Date.now() + 4000;
      return false;
    } catch (eMapSel) {
      return false;
    } finally {
      try {
        var stillOpen = findOpenMapListContainer();
        if (stillOpen && !popupWasAlreadyOpen) {
          var closeBtn = stillOpen.querySelector('#mClose, .close, [data-close], button[aria-label*="Đóng" i], button[aria-label*="Close" i]');
          if (closeBtn && isVis(closeBtn)) {
            dispatchRealUiClick(closeBtn);
          } else if (pickerBtn && isVis(pickerBtn)) {
            dispatchRealUiClick(pickerBtn);
          }
          await sleep(120);
        }
      } catch (eCl) {}
      try {
        if (openedPopup && savedOpacity !== null) {
          openedPopup.style.opacity = savedOpacity;
          openedPopup.style.transition = savedTransition || '';
        }
      } catch (eRest) {}
    }
  }
  function isDungeonListExhaustedInDom() {
    try {
      var md = document.getElementById('modal');
      if (!md || md.classList.contains('hidden')) return false;
      var txt = noAccent(md.textContent || '');
      if (txt.indexOf('pho ban') < 0 && txt.indexOf('to doi') < 0) return false;
      var totalBadges = (txt.match(/het luot/g) || []).length;
      var remainBadges = (txt.match(/con\s+\d+\s*\/\s*\d+/g) || []).length;
      return totalBadges >= 2 && remainBadges === 0;
    } catch (eDg) {}
    return false;
  }

  function parseTbkDetailPanel(rootEl) {
    var res = {
      found: false,
      usedMin: -1,
      maxMin: -1,
      barFull: false,
      btnCanEnter: false,
      btnExhausted: false,
      enterBtn: null,
      tvCount: -1,
      tvUsedToday: false,
      exhausted: false,
      summary: ''
    };
    if (!rootEl) return res;
    try {
      var rawAll = String(rootEl.textContent || '');
      var naAll = noAccent(rawAll).replace(/\s+/g, ' ').trim();
      var hasTbkPanel = (naAll.indexOf('thien bao kho') >= 0) &&
        (naAll.indexOf('hom nay') >= 0 || naAll.indexOf('vao thien bao kho') >= 0 ||
         naAll.indexOf('het phut hom nay') >= 0 || naAll.indexOf('huy theo doi') >= 0 ||
         naAll.indexOf('tin vat thien bao kho') >= 0);
      if (!hasTbkPanel) return res;
      res.found = true;

      // 1. Doc chinh xac "Hom nay: xx/60 phut" hoac "Hom nay: xx/120 phut"
      var mMin = /hom\s*nay\s*:?\s*(\d+)\s*\/\s*(\d+)\s*phut/i.exec(naAll);
      if (!mMin) mMin = /(\d+)\s*\/\s*(60|120)\s*phut/i.exec(naAll);
      if (mMin) {
        res.usedMin = Number(mMin[1]);
        res.maxMin = Number(mMin[2]);
      }

      // 2. Doc trang thai Tin vat Thien Bao Kho (so luong & hom nay 0/1 hay 1/1)
      var mTv = /tin\s*vat\s*thien\s*bao\s*kho\s*:?\s*(\d+)[^0-9]{0,20}hom\s*nay\s*(\d+)\s*\/\s*(\d+)/i.exec(naAll);
      if (mTv) {
        res.tvCount = Number(mTv[1]);
        res.tvUsedToday = Number(mTv[2]) >= Number(mTv[3]) && Number(mTv[3]) > 0;
      } else if (res.maxMin >= 120) {
        res.tvUsedToday = true;
      }

      // 3. Kiem tra thanh tien do mau vang dat da day 100% chua
      var bars = rootEl.querySelectorAll('[style*="width"], progress');
      for (var bi = 0; bi < Math.min(bars.length, 60); bi++) {
        var bar = bars[bi];
        if (!bar) continue;
        if (bar.tagName === 'PROGRESS') {
          var pv = Number(bar.value || 0), pm = Number(bar.max || 0);
          if (pm > 0 && pv >= pm) { res.barFull = true; break; }
        } else {
          var sw = String((bar.style && bar.style.width) || '');
          var mw = /(\d+(?:\.\d+)?)\s*%/.exec(sw);
          if (mw && Number(mw[1]) >= 99.5) {
            var r = bar.getBoundingClientRect ? bar.getBoundingClientRect() : null;
            var __mBar = getUiScaleMetrics(); if (r && r.height >= 2 && r.height <= 28 && r.width >= __mBar.minBarW) {
              res.barFull = true;
              break;
            }
          }
        }
      }

      // 4. Kiem tra nut chu trang bac phia tren "Huy theo doi":
      //    - Con thoi gian: "Vao Thien Bao Kho"
      //    - Het thoi gian: "Het phut hom nay - 0h mo lai"
      var btns = rootEl.querySelectorAll('button, [role="button"], .btn, a, div, span');
      var cap = Math.min(btns.length, 320);
      for (var i = 0; i < cap; i++) {
        var el = btns[i];
        if (!el || el.id === 'vlmAuto' || el.id === 'mswBtn') continue;
        if ((el.tagName === 'DIV' || el.tagName === 'SPAN') && el.children && el.children.length > 2) continue;
        if (el.closest && el.closest('#tabs, #u20bar, #mswMenu, #chatLines, #u20lines, #logBox, #u20log')) continue;
        var st = window.getComputedStyle ? window.getComputedStyle(el) : null;
        if (st && st.display === 'none') continue;
        var lbl = cleanUiLabel(el.textContent || '');
        if (!lbl || lbl.length > 48) continue;

        if (lbl.indexOf('het phut hom nay') >= 0 || lbl.indexOf('0h mo lai') >= 0 ||
            lbl.indexOf('het luot hom nay') >= 0) {
          res.btnExhausted = true;
        }
        if ((lbl === 'vao thien bao kho' || lbl === 'vao bao kho') &&
            (el.tagName === 'BUTTON' || el.getAttribute('role') === 'button' || (el.className && String(el.className).indexOf('btn') >= 0) || !res.enterBtn)) {
          if (!el.disabled) {
            res.enterBtn = el;
            res.btnCanEnter = true;
          }
        }
      }

      // 5. Ket luan chinh xac
      var hasMinByText = (res.maxMin > 0 && res.usedMin >= 0 && res.usedMin < res.maxMin);
      var fullByText = (res.maxMin > 0 && res.usedMin >= res.maxMin);

      if (res.btnExhausted) {
        res.exhausted = true;
      } else if (res.btnCanEnter && !fullByText) {
        res.exhausted = false;
      } else if (fullByText && !res.btnCanEnter) {
        res.exhausted = true;
      } else if (hasMinByText) {
        res.exhausted = false;
      } else if (res.barFull && !res.btnCanEnter) {
        res.exhausted = true;
      } else {
        res.exhausted = false;
      }

      var minStr = (res.maxMin > 0 && res.usedMin >= 0) ? (res.usedMin + '/' + res.maxMin + 'p') : '?';
      res.summary = minStr + (res.btnExhausted ? ' [Het phut hom nay - 0h mo lai]' : (res.btnCanEnter ? ' [Vao Thien Bao Kho]' : ''));
    } catch (eParse) {}
    return res;
  }

  function isTbkTimeExhaustedInDom(I) {
    try {
      if (I) {
        if (typeof I.tbkCon === 'number' && I.tbkCon <= 0) return true;
        if (typeof I.towerCon === 'number' && I.towerCon <= 0) return true;
        if (I.tbkHet === true || I.towerHet === true) return true;
        if (typeof I.tbkMin === 'number' && typeof I.tbkMax === 'number' && I.tbkMax > 0 && I.tbkMin >= I.tbkMax) return true;
      }
      var md = document.getElementById('modal');
      if (md && !md.classList.contains('hidden')) {
        var parsed = parseTbkDetailPanel(document.getElementById('mBody') || md);
        if (parsed.found) return parsed.exhausted;
      }
    } catch (eTbkEx) {}
    return false;
  }
  async function leaveThienBaoKho() {
    try { if (typeof releaseOrbitKeys === 'function') releaseOrbitKeys(); } catch (eRel) {}
    var findLeaveBtn = function (wantConfirmOnly) {
      var btns = document.querySelectorAll('button, [role="button"], .btn, a, div');
      var cap = Math.min(btns.length, 320);
      var firstRoi = null;
      for (var i = 0; i < cap; i++) {
        var b = btns[i];
        if (!b || !isVis(b) || b.disabled || b.id === 'vlmAuto' || b.id === 'mswBtn') continue;
        if (b.children && b.children.length > 2) continue;
        if (b.closest && b.closest('#mswMenu, #tabs, #u20bar, #chatLines, #u20lines, #logBox, #u20log')) continue;
        var bt = stripCountText(b.textContent || '');
        if (!bt || bt.length > 32) continue;
        if (bt === 'bam lai de roi' || bt.indexOf('bam lai de roi') >= 0 || bt === 'an lai de roi' || bt.indexOf('xac nhan roi') >= 0) {
          return { el: b, isConfirm: true };
        }
        if (!wantConfirmOnly && (bt === 'roi' || bt === 'roi khoi' || bt === 'roi thien bao kho' || bt === 'roi bao kho' || bt === 'thoat thien bao kho')) {
          if (!firstRoi) firstRoi = b;
        }
      }
      return firstRoi ? { el: firstRoi, isConfirm: false } : null;
    };

    var step1 = findLeaveBtn(false);
    if (!step1) return false;
    try { dispatchRealUiClick(step1.el); } catch (e1) { try { step1.el.click(); } catch (e2) {} }
    if (step1.isConfirm) {
      await sleep(500);
      return true;
    }

    var waitEnd = Date.now() + 1800;
    var clickedSecond = false;
    while (Date.now() < waitEnd) {
      await sleep(110);
      var step2 = findLeaveBtn(true);
      if (step2 && step2.el) {
        try { dispatchRealUiClick(step2.el); } catch (e3) { try { step2.el.click(); } catch (e4) {} }
        clickedSecond = true;
        break;
      }
      var dlgBtns = document.querySelectorAll('#cfModal button, #confirmModal button, #cModal button, [role="dialog"] button, .confirm button');
      for (var d = 0; d < dlgBtns.length; d++) {
        if (!isVis(dlgBtns[d]) || dlgBtns[d].disabled) continue;
        var dt = stripCountText(dlgBtns[d].textContent || '');
        if (dt === 'roi' || dt === 'xac nhan' || dt === 'dong y' || dt === 'ok') {
          try { dlgBtns[d].click(); } catch (e5) {}
          clickedSecond = true;
          break;
        }
      }
      if (clickedSecond) break;
    }
    if (!clickedSecond) {
      var retryBtn = findLeaveBtn(false);
      if (retryBtn && retryBtn.el) {
        try { dispatchRealUiClick(retryBtn.el); } catch (e6) { try { retryBtn.el.click(); } catch (e7) {} }
      }
    }
    await sleep(550);
    return true;
  }

  async function probeThienBaoKhoInQuestTab(doEnter) {
    var failOut = { checked: false, entered: false, exhausted: false, usedMin: -1, maxMin: -1, tvUsedToday: false, tvCount: -1, summary: '' };
    if (!canRunHiddenConsumable()) return failOut;

    var inStealthCheck = function () {
      return !!(document.documentElement && document.documentElement.classList.contains('__vlm-stealth'));
    };
    var isShown = function (el) {
      if (!el || !el.isConnected) return false;
      try {
        var cur = el;
        while (cur && cur !== document.body && cur !== document.documentElement) {
          if (cur.classList && cur.classList.contains('hidden')) return false;
          cur = cur.parentElement;
        }
        var st = window.getComputedStyle ? window.getComputedStyle(el) : null;
        if (st) {
          if (st.display === 'none') return false;
          if (!inStealthCheck() && st.visibility === 'hidden') return false;
        }
        var r = el.getBoundingClientRect ? el.getBoundingClientRect() : null;
        var __mQ = getUiScaleMetrics(); return !!(r && r.width >= __mQ.minBtnW && r.height >= __mQ.minBtnH);
      } catch (eS) { return isVis(el); }
    };

    var findQuestTabBtn = function () {
      var ids = ['questBtn', 'nvBtn', 'btnQuest', 'btnNv', 'u20quest', 'u20nv', 'tabQuest', 'tabNv'];
      for (var ii = 0; ii < ids.length; ii++) {
        var q = document.getElementById(ids[ii]);
        if (q && isShown(q) && !q.disabled) return q;
      }
      var attr = document.querySelectorAll('[data-tab*="quest" i], [data-tab*="nv" i], [data-open*="quest" i], [title*="Nhiệm vụ" i], [aria-label*="Nhiệm vụ" i]');
      for (var ai = 0; ai < attr.length; ai++) {
        if (isShown(attr[ai]) && !attr[ai].disabled) return attr[ai];
      }
      var all = document.querySelectorAll('#u20bar > *, #u20bar button, #tabs > *, #tabs button, #u20pop button, .u20-pop button, .more-menu button, button, [role="button"], [role="tab"], a');
      var cap = Math.min(all.length, 320);
      for (var i = 0; i < cap; i++) {
        var el = all[i];
        if (!el || !isShown(el) || el.disabled || el.id === 'vlmAuto' || el.id === 'mswBtn') continue;
        if (el.closest && el.closest('#modeSw, #mswMenu, #chatLines, #u20lines, #logBox, #u20log')) continue;
        var t = cleanUiLabel((el.textContent || '') + ' ' + (el.getAttribute('title') || '') + ' ' + (el.getAttribute('aria-label') || ''));
        if (t === 'nhiem vu') return el;
      }
      return null;
    };

    var getQuestRoot = function () {
      var md = document.getElementById('modal');
      if (md && !md.classList.contains('hidden')) return document.getElementById('mBody') || md;
      var pIds = ['t-quest', 'p-quest', 'questPane', 'questTab', 'nvPane'];
      for (var pi = 0; pi < pIds.length; pi++) {
        var pe = document.getElementById(pIds[pi]);
        if (pe && isShown(pe)) return pe;
      }
      return null;
    };

    var findTbkSidebarItem = function (root) {
      if (!root) return null;
      var els = root.querySelectorAll('button, [role="tab"], [role="button"], .tab, .qtab, li, a, div, span');
      var cap = Math.min(els.length, 360);
      var best = null;
      var bestScore = -1;
      for (var i = 0; i < cap; i++) {
        var el = els[i];
        if (!el || !isShown(el) || el.disabled || el.id === 'vlmAuto' || el.id === 'mswBtn') continue;
        if (el.closest && el.closest('#tabs, #u20bar, #modeSw, #mswMenu')) continue;
        var lbl = cleanUiLabel(el.textContent || '');
        if (!lbl || lbl.length > 52) continue;
        if (lbl.indexOf('vao thien bao kho') >= 0 || lbl.indexOf('tin vat') >= 0 || lbl.indexOf('huy theo doi') >= 0) continue;
        if (lbl === 'thien bao kho' || lbl.indexOf('thien bao kho ') === 0) {
          var target = el.closest ? (el.closest('button, [role="tab"], [role="button"], li, a') || el) : el;
          if (!target || !isShown(target) || target.disabled) continue;
          var tLbl = cleanUiLabel(target.textContent || '');
          if (tLbl.length > 64 || tLbl.indexOf('huy theo doi') >= 0 || tLbl.indexOf('hom nay') >= 0) continue;
          var r = target.getBoundingClientRect ? target.getBoundingClientRect() : null;
          var score = (lbl === 'thien bao kho' ? 200 : 140);
          if (target.tagName === 'BUTTON' || target.getAttribute('role') === 'tab') score += 50;
          if (r && r.width <= 260) score += 40;
          if (score > bestScore) {
            bestScore = score;
            best = target;
          }
        }
      }
      return best;
    };

    var prevTabBtn = document.querySelector('#tabs .on, #tabs .active, #tabs [aria-selected="true"], #u20bar .on, #u20bar .active');
    var openedMoreBtn = null;
    var nvBtn = null;

    lockModal(18000);
    stealthObj().enter();
    try {
      var root = getQuestRoot();
      var parsed = root ? parseTbkDetailPanel(root) : { found: false };

      if (!parsed.found) {
        if (!root) {
          nvBtn = findQuestTabBtn();
          if (!nvBtn) {
            var moreBtn = document.getElementById('u20more') || document.getElementById('moreBtn') || document.getElementById('btnMore');
            if (moreBtn && isShown(moreBtn) && !moreBtn.disabled) {
              openedMoreBtn = moreBtn;
              try { moreBtn.click(); } catch (eM0) {}
              await sleep(240);
              nvBtn = findQuestTabBtn();
            }
          }
          if (!nvBtn) return failOut;
          try { nvBtn.click(); } catch (eNv0) {}
          var retriedNv = false;
          var wNv = Date.now() + 1800;
          while (Date.now() < wNv) {
            await sleep(110);
            root = getQuestRoot();
            if (root && (findTbkSidebarItem(root) || parseTbkDetailPanel(root).found)) break;
            if (!retriedNv && Date.now() > wNv - 1150) {
              retriedNv = true;
              try { dispatchRealUiClick(nvBtn); } catch (eNv1) {}
            }
          }
        }

        if (!root) return failOut;
        parsed = parseTbkDetailPanel(root);

        if (!parsed.found) {
          var sideItem = findTbkSidebarItem(root);
          if (sideItem) {
            try { if (sideItem.scrollIntoView) sideItem.scrollIntoView({ block: 'center' }); } catch (eSc0) {}
            await sleep(60);
            try { sideItem.click(); } catch (eSi0) {}
            var retriedSide = false;
            var wSide = Date.now() + 1800;
            while (Date.now() < wSide) {
              await sleep(110);
              root = getQuestRoot() || root;
              parsed = parseTbkDetailPanel(root);
              if (parsed.found) break;
              if (!retriedSide && Date.now() > wSide - 1150) {
                retriedSide = true;
                try { dispatchRealUiClick(sideItem); } catch (eSi1) {}
              }
            }
          }
        }
      }

      if (!parsed || !parsed.found) return failOut;

      A.tbkProbedAt = Date.now();
      A.tbkStatus = {
        usedMin: parsed.usedMin,
        maxMin: parsed.maxMin,
        exhausted: parsed.exhausted,
        btnCanEnter: parsed.btnCanEnter,
        btnExhausted: parsed.btnExhausted,
        tvUsedToday: parsed.tvUsedToday,
        tvCount: parsed.tvCount,
        summary: parsed.summary,
        at: Date.now()
      };

      var out = {
        checked: true,
        entered: false,
        exhausted: parsed.exhausted,
        usedMin: parsed.usedMin,
        maxMin: parsed.maxMin,
        tvUsedToday: parsed.tvUsedToday,
        tvCount: parsed.tvCount,
        summary: parsed.summary
      };

      if (doEnter && !parsed.exhausted && parsed.enterBtn) {
        try { if (parsed.enterBtn.scrollIntoView) parsed.enterBtn.scrollIntoView({ block: 'center' }); } catch (eSc1) {}
        await sleep(80);
        try { parsed.enterBtn.click(); } catch (eEn0) {}
        var retriedEn = false;
        var wEn = Date.now() + 1600;
        while (Date.now() < wEn) {
          await sleep(120);
          var mdNow = document.getElementById('modal');
          var modalClosed = !mdNow || mdNow.classList.contains('hidden');
          if (isInTbkNow() || modalClosed) {
            out.entered = true;
            break;
          }
          if (!retriedEn && Date.now() > wEn - 1000 && parsed.enterBtn && parsed.enterBtn.isConnected && !parsed.enterBtn.disabled) {
            retriedEn = true;
            try { dispatchRealUiClick(parsed.enterBtn); } catch (eEn1) {}
          }
        }
        ensureGameAutoOn(false);
      }

      return out;
    } finally {
      await closeModalCleanly(document.getElementById('modal'));
      if (prevTabBtn && prevTabBtn.isConnected && isVis(prevTabBtn)) {
        try { prevTabBtn.click(); } catch (ePv) {}
      }
      if (openedMoreBtn && openedMoreBtn.isConnected) {
        try {
          var pop = document.querySelector('#u20pop, .u20-pop, .u20-more, .more-menu, [data-more-pop]');
          if (pop && isVis(pop)) openedMoreBtn.click();
        } catch (eMp) {}
      }
      stealthObj().leave();
      unlockModal();
    }
  }

  async function enterThienBaoKhoViaQuestTab() {
    return await probeThienBaoKhoInQuestTab(true);
  }
  function findGuildConfirmButton(excludeBtn) {
    var inStealth = !!(document.documentElement && document.documentElement.classList.contains('__vlm-stealth'));
    var isRectVisible = function (el) {
      if (!el || !el.isConnected) return false;
      try {
        var cur = el;
        while (cur && cur !== document.body && cur !== document.documentElement) {
          if (cur.classList && cur.classList.contains('hidden')) return false;
          cur = cur.parentElement;
        }
        var st = window.getComputedStyle ? window.getComputedStyle(el) : null;
        if (st) {
          if (st.display === 'none') return false;
          if (!inStealth && st.visibility === 'hidden') return false;
        }
        var r = el.getBoundingClientRect ? el.getBoundingClientRect() : null;
        var __mB = getUiScaleMetrics(); if (!r || r.width < __mB.minBtnW || r.height < __mB.minBtnH) return false;
        return true;
      } catch (eR) { return isVis(el); }
    };

    var all = document.querySelectorAll('button, [role="button"], input[type="submit"], input[type="button"], a, div, span');
    var best = null;
    var bestScore = -1;
    // Quet nguoc tu cuoi DOM len (popup xac nhan luon nam cuoi body) & khong cat nguong 220 nut
    for (var i = all.length - 1; i >= 0; i--) {
      var el = all[i];
      if (!el || el === excludeBtn || el.disabled) continue;
      if (el.id === 'vlmAuto' || el.id === 'mswBtn') continue;
      if ((el.tagName === 'DIV' || el.tagName === 'SPAN') && el.children && el.children.length > 2) continue;
      if (el.closest && el.closest('#tabs, #u20bar, #mswMenu, #chatLines, #u20lines, #logBox, #u20log')) continue;
      if (!isRectVisible(el)) continue;

      var t = cleanUiLabel(el.textContent || el.value || '');
      if (!t || t.length > 24) continue;
      if (t === 'huy' || t === 'huy bo' || t === 'dong' || t === 'dong lai' || t === 'khong' || t === 'bo qua') continue;

      var score = 0;
      if (t === 'dong y') score = 400;
      else if (t.indexOf('dong y') === 0) score = 320;
      else if (t === 'xac nhan') score = 260;
      else if (t === 'chap nhan') score = 220;
      else if (t === 'ok') score = 150;
      if (score <= 0) continue;

      if (el.tagName === 'BUTTON' || el.getAttribute('role') === 'button') score += 60;

      var cur = el.parentElement;
      for (var d = 0; d < 4 && cur && cur !== document.body; d++) {
        var pTxt = cleanUiLabel(cur.textContent || '');
        if (pTxt.length > 360) break;
        if (pTxt.indexOf('tin vat') >= 0 || pTxt.indexOf('cong hien') >= 0 || pTxt.indexOf('thien bao kho') >= 0) {
          score += 500;
          break;
        }
        if (pTxt.indexOf('huy') >= 0 && pTxt.indexOf('dong y') >= 0) {
          score += 300;
          break;
        }
        cur = cur.parentElement;
      }

      if (score > bestScore) {
        bestScore = score;
        best = el;
      }
    }
    return best;
  }
  function findGuildQtyInput() {
    var inStealth = !!(document.documentElement && document.documentElement.classList.contains('__vlm-stealth'));
    var isInpVis = function (el) {
      if (!el || !el.isConnected || el.disabled || el.readOnly) return false;
      try {
        var cur = el;
        while (cur && cur !== document.body && cur !== document.documentElement) {
          if (cur.classList && cur.classList.contains('hidden')) return false;
          cur = cur.parentElement;
        }
        var st = window.getComputedStyle ? window.getComputedStyle(el) : null;
        if (st) {
          if (st.display === 'none') return false;
          if (!inStealth && st.visibility === 'hidden') return false;
        }
        var r = el.getBoundingClientRect ? el.getBoundingClientRect() : null;
        var __mI = getUiScaleMetrics(); return !!(r && r.width >= Math.max(14, __mI.minBtnW + 4) && r.height >= __mI.minBtnH);
      } catch (eIv) { return isVis(el); }
    };

    var inps = document.querySelectorAll('input');
    var best = null;
    var bestScore = -1;
    for (var i = inps.length - 1; i >= 0; i--) {
      var el = inps[i];
      if (!el) continue;
      var tp = String(el.type || 'text').toLowerCase();
      if (tp === 'hidden' || tp === 'checkbox' || tp === 'radio' || tp === 'button' || tp === 'submit' || tp === 'file') continue;
      if (el.id === 'chatInp' || el.id === 'vlmAuto') continue;
      if (el.closest && el.closest('#tabs, #u20bar, #mswMenu, #chatLines, #u20lines, #logBox, #u20log, #chatBox, #vlmAuto')) continue;
      if (!isInpVis(el)) continue;

      var score = (tp === 'number' ? 120 : 80);
      var cur = el.parentElement;
      for (var d = 0; d < 4 && cur && cur !== document.body; d++) {
        var pTxt = cleanUiLabel(cur.textContent || '');
        if (pTxt.length > 420) break;
        if (pTxt.indexOf('lenh bai') >= 0 || pTxt.indexOf('phuc duyen') >= 0 || pTxt.indexOf('so luong') >= 0 ||
            pTxt.indexOf('cong hien') >= 0 || pTxt.indexOf('diem') >= 0) {
          score += 300;
          break;
        }
        if (pTxt.indexOf('huy') >= 0 && (pTxt.indexOf('doi') >= 0 || pTxt.indexOf('dong y') >= 0)) {
          score += 220;
          break;
        }
        cur = cur.parentElement;
      }
      if (score > bestScore) {
        bestScore = score;
        best = el;
      }
    }
    return best;
  }

  function findGuildQtyConfirmBtn(qtyInp, excludeBtn) {
    if (!qtyInp) return findGuildConfirmButton(excludeBtn);
    var inStealth = !!(document.documentElement && document.documentElement.classList.contains('__vlm-stealth'));
    var isBtnVis = function (el) {
      if (!el || !el.isConnected || el.disabled) return false;
      try {
        var cur = el;
        while (cur && cur !== document.body && cur !== document.documentElement) {
          if (cur.classList && cur.classList.contains('hidden')) return false;
          cur = cur.parentElement;
        }
        var st = window.getComputedStyle ? window.getComputedStyle(el) : null;
        if (st) {
          if (st.display === 'none') return false;
          if (!inStealth && st.visibility === 'hidden') return false;
        }
        var r = el.getBoundingClientRect ? el.getBoundingClientRect() : null;
        var __mC = getUiScaleMetrics(); return !!(r && r.width >= __mC.minBtnW && r.height >= __mC.minBtnH);
      } catch (eBv) { return isVis(el); }
    };

    var cur = qtyInp.parentElement;
    for (var d = 0; d < 5 && cur && cur !== document.body && cur !== document.documentElement; d++) {
      var btns = cur.querySelectorAll('button, [role="button"], input[type="submit"], input[type="button"], a');
      if (btns.length > 10) break;
      var best = null;
      var bestScore = -1;
      for (var i = 0; i < btns.length; i++) {
        var b = btns[i];
        if (!b || b === excludeBtn || !isBtnVis(b)) continue;
        var t = cleanUiLabel(b.textContent || b.value || '');
        if (!t || t.length > 24) continue;
        if (t === 'huy' || t === 'huy bo' || t === 'dong' || t === 'dong lai' || t === 'khong' ||
            t === 'bo qua' || t === 'max' || t === 'toi da' || t === 'x') continue;
        var sc = 0;
        if (t === 'doi') sc = 500;
        else if (t === 'dong y') sc = 460;
        else if (t === 'xac nhan') sc = 420;
        else if (t === 'chap nhan') sc = 380;
        else if (t.indexOf('doi ') === 0) sc = 340;
        else if (t === 'mua') sc = 300;
        else if (t === 'ok') sc = 200;
        if (sc > bestScore) {
          bestScore = sc;
          best = b;
        }
      }
      if (best) return best;
      cur = cur.parentElement;
    }
    return findGuildConfirmButton(excludeBtn);
  }

  async function muaTinVatThienBaoKho(I) {
    var needTv = !!(A.cfg.tbkTinVat && dayGet('tbkTv2') < 1);
    var needLb = !!(A.cfg.bangLbdt && dayGet('bangLbdt') < 1);
    var needPd = !!(A.cfg.bangPd && dayGet('bangPd') < 1);
    if (!A.cfg.on || (!needTv && !needLb && !needPd)) return false;
    if (I && (I.dead || I.dg || I.sat)) return false;
    if (!canRunHiddenConsumable()) return false;
    if (!ready('tbk_tinvat', 45000)) return false;

    var prevTabBtn = document.querySelector('#tabs .on, #tabs .active, #tabs [aria-selected="true"], #u20bar .on, #u20bar .active');
    var openedMoreBtn = null;

    var findBangHoiBtn = function () {
      var ids = ['guildBtn', 'bangBtn', 'btnBang', 'u20guild', 'u20bang', 'tabGuild', 'tabBang'];
      for (var ii = 0; ii < ids.length; ii++) {
        var g = document.getElementById(ids[ii]);
        if (g && isVis(g) && !g.disabled) return g;
      }
      var attr = document.querySelectorAll('[data-tab*="guild" i], [data-tab*="bang" i], [data-open*="guild" i], [data-open*="bang" i]');
      for (var ai = 0; ai < attr.length; ai++) {
        if (isVis(attr[ai]) && !attr[ai].disabled) return attr[ai];
      }
      var all = document.querySelectorAll('#u20bar > *, #u20bar button, #tabs > *, #tabs button, #u20pop button, .u20-pop button, .more-menu button, button, [role="button"], [role="tab"], a');
      var cap = Math.min(all.length, 320);
      for (var i = 0; i < cap; i++) {
        var el = all[i];
        if (!el || !isVis(el) || el.disabled || el.id === 'vlmAuto' || el.id === 'mswBtn') continue;
        if (el.closest && el.closest('#mswMenu, #chatLines, #u20lines, #logBox, #u20log')) continue;
        var t = cleanUiLabel((el.textContent || '') + ' ' + (el.getAttribute('title') || '') + ' ' + (el.getAttribute('aria-label') || ''));
        if (t === 'bang hoi' || t.indexOf('bang hoi') >= 0 || t === 'bang h' || t === 'bang') return el;
      }
      return null;
    };

    var boughtTinVat = false;
    var anyBought = false;
    var anyPointLack = false;

    lockModal(35000);
    stealthObj().enter();
    try {
      var bangBtn = findBangHoiBtn();
      if (!bangBtn) {
        var moreBtn = document.getElementById('u20more') || document.getElementById('moreBtn') || document.getElementById('btnMore');
        if (moreBtn && isVis(moreBtn) && !moreBtn.disabled) {
          openedMoreBtn = moreBtn;
          try { dispatchRealUiClick(moreBtn); } catch (eMo) { try { moreBtn.click(); } catch (eMo2) {} }
          await sleep(260);
          bangBtn = findBangHoiBtn();
        }
      }
      if (!bangBtn) {
        note('[Bang] Khong tim thay nut Bang Hoi de doi vat pham');
        return false;
      }

      try { bangBtn.click(); } catch (eB0) {}
      await sleep(180);
      try { dispatchRealUiClick(bangBtn); } catch (eB1) {}
      await sleep(340);

      var getGuildRoot = function () {
        var md = document.getElementById('modal');
        if (md && !md.classList.contains('hidden')) return document.getElementById('mBody') || md;
        var pIds = ['t-guild', 't-bang', 'p-guild', 'p-bang', 'tab-guild', 'tab-bang', 'guildTab', 'bangTab', 'guildPane', 'bangPane', 'u20guildPane'];
        for (var pi = 0; pi < pIds.length; pi++) {
          var pe = document.getElementById(pIds[pi]);
          if (pe && isVis(pe)) return pe;
        }
        return document.body;
      };

      var waitAndClickSubTab = async function (keywords, maxWaitMs) {
        var deadline = Date.now() + (maxWaitMs || 1500);
        while (Date.now() < deadline) {
          var root = getGuildRoot();
          if (root) {
            var els = root.querySelectorAll('button, [role="tab"], [role="button"], .tab, a, div, span');
            var cap = Math.min(els.length, 320);
            for (var i = 0; i < cap; i++) {
              var b = els[i];
              if (!b || !isVis(b) || b.disabled || b.id === 'vlmAuto' || b.id === 'mswBtn') continue;
              if (b.closest && b.closest('#tabs, #u20bar, #mswMenu, #chatLines, #u20lines, #logBox, #u20log')) continue;
              if ((b.tagName === 'DIV' || b.tagName === 'SPAN') && b.children && b.children.length > 2) continue;
              var bt = cleanUiLabel(b.textContent || '');
              if (!bt || bt.length > 24) continue;
              for (var k = 0; k < keywords.length; k++) {
                if (bt === keywords[k] || bt.indexOf(keywords[k]) === 0) {
                  try { b.click(); } catch (eK0) {}
                  try { dispatchRealUiClick(b); } catch (eK1) {}
                  await sleep(320);
                  return true;
                }
              }
            }
          }
          await sleep(110);
        }
        return false;
      };

      // Bang hoi -> Cua hang -> Vat pham
      var clickedShop = await waitAndClickSubTab(['cua hang', 'shop bang', 'tiem bang'], 1600);
      if (!clickedShop) {
        note('[Bang] Da mo Bang Hoi nhung khong thay muc Cua hang');
        return false;
      }
      await waitAndClickSubTab(['vat pham', 'dao cu', 'tieu hao'], 1400);

      var findGuildItemCard = function (matchFn, defaultMax) {
        var root = getGuildRoot();
        if (!root) return null;
        var nodes = root.querySelectorAll('.kc, .shoprow, .qrow, .item, tr, li, div, span, p');
        var matches = [];
        for (var ni = 0; ni < nodes.length; ni++) {
          var el = nodes[ni];
          if (!isVis(el)) continue;
          if (el.closest && el.closest('#tabs, #u20bar, #chatLines, #u20lines, #logBox, #u20log')) continue;
          var meta = cleanUiLabel(extractNodeMeta(el));
          if (!meta || meta.length > 420) continue;
          if (matchFn(meta)) matches.push({ el: el, len: meta.length });
        }
        matches.sort(function (a, b) { return a.len - b.len; });

        for (var mi = 0; mi < matches.length; mi++) {
          var cur = matches[mi].el;
          for (var depth = 0; depth < 6 && cur && cur !== root && cur !== document.body; depth++) {
            var rawTxt = String(cur.textContent || '');
            var curTxt = cleanUiLabel(rawTxt);
            if (curTxt.length > 460) break;
            var rowBtns = cur.querySelectorAll ? cur.querySelectorAll('button, [role="button"], a') : [];
            var doiBtns = [];
            var disabledDone = false;
            for (var rb = 0; rb < rowBtns.length; rb++) {
              var rBtn = rowBtns[rb];
              if (!isVis(rBtn)) continue;
              var rbt = cleanUiLabel(rBtn.textContent || '');
              if (rbt === 'da doi' || rbt === 'da mua' || rbt === 'het' || rbt === 'het hang') {
                disabledDone = true;
              } else if (rbt === 'doi' || rbt.indexOf('doi ') === 0 || rbt === 'mua' || rbt.indexOf('mua ') === 0) {
                doiBtns.push(rBtn);
              }
            }
            if (doiBtns.length > 1) break;
            if (doiBtns.length === 1 || disabledDone) {
              var mLim = /hom\s*nay\s*(\d+)\s*\/\s*(\d+)/i.exec(noAccent(rawTxt));
              var used = mLim ? Number(mLim[1]) : -1;
              var maxLim = mLim ? Number(mLim[2]) : defaultMax;
              var alreadyDone = disabledDone ||
                curTxt.indexOf('da doi') >= 0 || curTxt.indexOf('da mua') >= 0 || curTxt.indexOf('het hang') >= 0 ||
                (mLim && maxLim > 0 && used >= maxLim) ||
                (doiBtns.length === 1 && doiBtns[0].disabled);
              return {
                card: cur,
                btn: doiBtns.length === 1 ? doiBtns[0] : null,
                used: used,
                max: maxLim,
                alreadyDone: alreadyDone
              };
            }
            cur = cur.parentElement;
          }
        }
        return null;
      };

      var queue = [];
      if (needTv) {
        queue.push({
          key: 'tv',
          dayKey: 'tbkTv2',
          name: 'Tín vật Thiên Bảo Khố',
          needQty: false,
          defaultMax: 1,
          match: function (m) {
            return m.indexOf('tin vat thien bao kho') >= 0 || (m.indexOf('tin vat') >= 0 && m.indexOf('bao kho') >= 0);
          }
        });
      }
      if (needLb) {
        queue.push({
          key: 'lb',
          dayKey: 'bangLbdt',
          name: 'Lệnh Bài Dã Tẩu',
          needQty: true,
          defaultMax: 10,
          match: function (m) {
            return m.indexOf('lenh bai da tau') >= 0;
          }
        });
      }
      if (needPd) {
        queue.push({
          key: 'pd',
          dayKey: 'bangPd',
          name: 'Gói 10 Phúc Duyên',
          needQty: true,
          defaultMax: 10,
          match: function (m) {
            return m.indexOf('goi 10 phuc duyen') >= 0 || (m.indexOf('goi') >= 0 && m.indexOf('phuc duyen') >= 0);
          }
        });
      }

      for (var qi = 0; qi < queue.length; qi++) {
        var item = queue[qi];
        var cardInfo = null;
        var waitItem = Date.now() + 1600;
        while (Date.now() < waitItem) {
          cardInfo = findGuildItemCard(item.match, item.defaultMax);
          if (cardInfo) break;
          await sleep(120);
        }

        if (!cardInfo) {
          note('[Bang] Khong tim thay ' + item.name + ' trong Cua hang -> Vat pham');
          continue;
        }
        if (cardInfo.alreadyDone || !cardInfo.btn) {
          dayAdd(item.dayKey, 1);
          note('[Bang] Da doi du ' + item.name + ' hom nay');
          continue;
        }

        var beforeUsed = cardInfo.used;
        var maxLim = cardInfo.max > 0 ? cardInfo.max : item.defaultMax;
        var wantQty = (beforeUsed >= 0 && maxLim > beforeUsed) ? (maxLim - beforeUsed) : item.defaultMax;
        if (wantQty < 1) wantQty = 1;

        var buyBtn = cardInfo.btn;
        try { if (buyBtn.scrollIntoView) buyBtn.scrollIntoView({ block: 'center' }); } catch (eScr) {}
        await sleep(100);
        try { buyBtn.click(); } catch (eM0) {}

        var qtyInp = null;
        var confirmBtn = null;
        var retriedBuy = false;
        var waitPop = Date.now() + 2500;
        while (Date.now() < waitPop) {
          await sleep(100);
          if (item.needQty) {
            qtyInp = findGuildQtyInput();
            if (qtyInp) break;
          }
          confirmBtn = findGuildConfirmButton(buyBtn);
          if (confirmBtn && !item.needQty) break;
          if (!retriedBuy && Date.now() > waitPop - 1800) {
            retriedBuy = true;
            try { dispatchRealUiClick(buyBtn); } catch (eM1) {}
          }
        }

        if (item.needQty && qtyInp) {
          try { qtyInp.focus(); } catch (eFoc) {}
          try { fillInp(qtyInp, String(wantQty)); } catch (eFill) {}
          if (String(qtyInp.value || '') !== String(wantQty)) {
            try {
              qtyInp.value = String(wantQty);
              qtyInp.dispatchEvent(new Event('input', { bubbles: true }));
              qtyInp.dispatchEvent(new Event('change', { bubbles: true }));
            } catch (eSet) {}
          }
          await sleep(150);
          var qtyConfirm = findGuildQtyConfirmBtn(qtyInp, buyBtn);
          if (!qtyConfirm) {
            note('[Bang] Da nhap ' + wantQty + ' cho ' + item.name + ' nhung khong thay nut Doi xac nhan');
            continue;
          }
          try { qtyConfirm.click(); } catch (eQc0) {}
          await sleep(420);
          var stillInp = findGuildQtyInput();
          if (stillInp) {
            var qtyConfirm2 = findGuildQtyConfirmBtn(stillInp, buyBtn);
            if (qtyConfirm2) {
              try { dispatchRealUiClick(qtyConfirm2); } catch (eQc1) {}
              await sleep(450);
            }
          }
          // Neu game hien them hop thoai xac nhan Dong y sau khi nhap so luong
          var extraCf = findGuildConfirmButton(buyBtn);
          if (extraCf) {
            try { extraCf.click(); } catch (eEx0) {}
            await sleep(380);
            var extraCf2 = findGuildConfirmButton(buyBtn);
            if (extraCf2) {
              try { dispatchRealUiClick(extraCf2); } catch (eEx1) {}
              await sleep(380);
            }
          }
        } else if (confirmBtn) {
          try { confirmBtn.click(); } catch (eCf0) {}
          await sleep(420);
          var stillThere = findGuildConfirmButton(buyBtn);
          if (stillThere) {
            try { dispatchRealUiClick(stillThere); } catch (eCf1) {}
            await sleep(450);
          }
          var stillAfter2 = findGuildConfirmButton(buyBtn);
          if (stillAfter2) {
            note('[Bang] Da bam Dong y (' + item.name + ') nhung hop thoai van con');
            continue;
          }
        } else {
          note('[Bang] Da bam Doi (' + item.name + ') nhung khong thay hop thoai xac nhan');
          continue;
        }

        await sleep(420);
        var afterInfo = findGuildItemCard(item.match, item.defaultMax);
        var checkRoot = document.getElementById('modal') || getGuildRoot() || document.body;
        var checkTxt = cleanUiLabel(checkRoot ? checkRoot.textContent : '');
        var progressed = !!(afterInfo && (afterInfo.alreadyDone || (beforeUsed >= 0 && afterInfo.used > beforeUsed)));

        if (!progressed && (checkTxt.indexOf('khong du') >= 0 || checkTxt.indexOf('thieu diem') >= 0 || checkTxt.indexOf('that bai') >= 0)) {
          anyPointLack = true;
          note('[Bang] Chua doi duoc ' + item.name + ' (khong du diem cong hien)');
          continue;
        }
        if (!progressed && beforeUsed >= 0 && afterInfo && afterInfo.used === beforeUsed) {
          note('[Bang] Chua ghi nhan tang luot doi ' + item.name + ' (' + afterInfo.used + '/' + maxLim + '), se thu lai');
          continue;
        }

        var doneAll = !afterInfo || afterInfo.alreadyDone || (afterInfo.used >= maxLim);
        if (doneAll) {
          dayAdd(item.dayKey, 1);
        }
        anyBought = true;
        if (item.key === 'tv') {
          boughtTinVat = true;
          tbkClearXong();
          A.tbkFailCount = 0;
          note('Da doi 1 Tin vat Thien Bao Kho (+60p -> toi da 120p)');
        } else {
          var gotCount = (afterInfo && beforeUsed >= 0 && afterInfo.used > beforeUsed) ? (afterInfo.used - beforeUsed) : wantQty;
          note('Da doi ' + gotCount + ' ' + item.name + ' (Bang hoi -> Cua hang -> Vat pham)');
        }
        await sleep(250);
      }

      if (!anyBought && anyPointLack) {
        A.cool.tbk_tinvat = Date.now() + 600000;
      }
      return boughtTinVat;
    } finally {
      await closeModalCleanly(document.getElementById('modal'));
      if (prevTabBtn && prevTabBtn.isConnected && isVis(prevTabBtn)) {
        try { prevTabBtn.click(); } catch (ePr) {}
      }
      if (openedMoreBtn && openedMoreBtn.isConnected) {
        try {
          var pop = document.querySelector('#u20pop, .u20-pop, .u20-more, .more-menu, [data-more-pop]');
          if (pop && isVis(pop)) openedMoreBtn.click();
        } catch (eMp) {}
      }
      stealthObj().leave();
      unlockModal();
    }
  }
  async function dungTinVatThienBaoKho(I, forceAfterBuy) {
    if (!A.cfg.on || !A.cfg.tbkTinVat) return false;
    if (dayGet('tbkTvUse') >= 1) return false;
    if (I && (I.dead || I.dg || I.sat)) return false;
    if (isModalLocked()) await waitModalLockFree(5000);
    if (forceAfterBuy) {
      var mdWaitEnd = Date.now() + 1800;
      while (Date.now() < mdWaitEnd) {
        var mdPre = document.getElementById('modal');
        if (!mdPre || mdPre.classList.contains('hidden')) break;
        await closeModalCleanly(mdPre);
        await sleep(120);
      }
      if (isAuthOrProtectedModalOpen()) {
        note('[TBK] Tam hoan dung Tin vat vi dang mo hop thoai dang nhap/bao ve');
        return false;
      }
    } else if (!canRunHiddenConsumable()) {
      return false;
    }
    if (!forceAfterBuy && !ready('tbk_use_tinvat', 30000)) return false;
    if (forceAfterBuy) A.cool.tbk_use_tinvat = Date.now() + 15000;

    var inStealthCheck = function () {
      return !!(document.documentElement && document.documentElement.classList.contains('__vlm-stealth'));
    };

    var isElemShown = function (el) {
      if (!el || !el.isConnected) return false;
      try {
        var cur = el;
        while (cur && cur !== document.body && cur !== document.documentElement) {
          if (cur.classList && cur.classList.contains('hidden')) return false;
          cur = cur.parentElement;
        }
        var st = window.getComputedStyle ? window.getComputedStyle(el) : null;
        if (st) {
          if (st.display === 'none') return false;
          if (!inStealthCheck() && st.visibility === 'hidden') return false;
        }
        var r = el.getBoundingClientRect ? el.getBoundingClientRect() : null;
        var __mQ = getUiScaleMetrics(); return !!(r && r.width >= __mQ.minBtnW && r.height >= __mQ.minBtnH);
      } catch (eSh) { return isVis(el); }
    };

    var getQuestRoot = function () {
      var md = document.getElementById('modal');
      if (md && !md.classList.contains('hidden')) return document.getElementById('mBody') || md;
      var pIds = ['t-quest', 'p-quest', 'questPane', 'questTab', 'nvPane'];
      for (var pi = 0; pi < pIds.length; pi++) {
        var pe = document.getElementById(pIds[pi]);
        if (pe && isElemShown(pe)) return pe;
      }
      return null;
    };

    var isUseLabel = function (t) {
      if (!t || t.length > 36) return false;
      if (t.indexOf('bam lai') >= 0 || t.indexOf('an lai') >= 0 || t.indexOf('huy') >= 0 || t.indexOf('dong') >= 0) return false;
      return t === 'dung' || t === 'su dung' || t === 'dung ngay' ||
        /^dung\s*\(?\s*\d+\s*\)?$/.test(t) ||
        /^su\s*dung\s*\(?\s*\d+\s*\)?$/.test(t) ||
        t.indexOf('dung tin vat') === 0 ||
        t.indexOf('dung +60') === 0;
    };

    var isConfirmUseLabel = function (t) {
      if (!t || t.length > 44) return false;
      return t === 'bam lai de dung' || t.indexOf('bam lai de dung') >= 0 ||
        t === 'an lai de dung' || t.indexOf('an lai de dung') >= 0 ||
        t.indexOf('xac nhan dung') >= 0;
    };

    var findTbkSection = function () {
      var qRoot = getQuestRoot();
      var roots = qRoot ? [qRoot, document] : [document];
      for (var ri = 0; ri < roots.length; ri++) {
        var root = roots[ri];
        var nodes = root.querySelectorAll('div, section, li, tr, p, span');
        var matches = [];
        var cap = (root === document) ? Math.min(nodes.length, 3000) : nodes.length;
        for (var i = 0; i < cap; i++) {
          var el = nodes[i];
          if (!el || !isElemShown(el)) continue;
          if (el.id === 'vlmAuto' || el.id === 'mswBtn') continue;
          if (el.closest && el.closest('#tabs, #u20bar, #modeSw, #mswMenu, #chatLines, #u20lines, #logBox, #u20log, #vlmAuto')) continue;
          var lbl = cleanUiLabel(el.textContent || '');
          if (!lbl || lbl.length > 360) continue;
          if (lbl.indexOf('tin vat thien bao kho') >= 0 || (lbl.indexOf('tin vat') >= 0 && lbl.indexOf('bao kho') >= 0)) {
            matches.push({ el: el, len: lbl.length });
          }
        }
        matches.sort(function (a, b) { return a.len - b.len; });

        for (var mi = 0; mi < matches.length; mi++) {
          var cur = matches[mi].el;
          var fallbackSec = cur;
          for (var d = 0; d < 5 && cur && cur !== document.body && cur !== document.documentElement; d++) {
            if (cur.querySelector && cur.querySelector('canvas, #tabs, #u20bar')) break;
            var cTxt = cleanUiLabel(cur.textContent || '');
            if (cTxt.length > 720) break;
            fallbackSec = cur;
            var btns = cur.querySelectorAll ? cur.querySelectorAll('button, [role="button"], a') : [];
            for (var bi = 0; bi < btns.length; bi++) {
              var bt = cleanUiLabel(btns[bi].textContent || '');
              if (isUseLabel(bt) || isConfirmUseLabel(bt)) {
                return cur;
              }
            }
            cur = cur.parentElement;
          }
          if (fallbackSec) return fallbackSec;
        }
      }
      return null;
    };

    var parseTbkSecState = function (secEl) {
      if (!secEl) return { usedToday: false, cnt: -1 };
      var na = noAccent(secEl.textContent || '').replace(/\s+/g, ' ').trim();
      var pNa = na;
      if (secEl.parentElement) {
        var pt = noAccent(secEl.parentElement.textContent || '').replace(/\s+/g, ' ').trim();
        if (pt.length < 950) pNa = pt;
      }
      var usedToday = /hom\s*nay\s*1\s*\/\s*1/i.test(na) ||
        /\/\s*120\s*phut/i.test(pNa) ||
        /120\s*\/\s*120/i.test(pNa);
      var mCnt = /tin\s*vat\s*thien\s*bao\s*kho\s*:?\s*(\d+)/i.exec(na);
      var cnt = mCnt ? Number(mCnt[1]) : -1;
      return { usedToday: usedToday, cnt: cnt };
    };

    var findTbkUseBtnIn = function (secEl, confirmOnly) {
      if (!secEl) return null;
      var scopes = [secEl];
      if (secEl.parentElement && (secEl.parentElement.textContent || '').length < 950) {
        scopes.push(secEl.parentElement);
      }
      for (var si = 0; si < scopes.length; si++) {
        var btns = scopes[si].querySelectorAll('button, [role="button"], a');
        for (var i = 0; i < btns.length; i++) {
          var b = btns[i];
          if (!b || !isElemShown(b)) continue;
          if (b.id === 'vlmAuto' || b.id === 'mswBtn') continue;
          var t = cleanUiLabel(b.textContent || '');
          if (isConfirmUseLabel(t)) {
            return { el: b, isConfirm: true, disabled: !!b.disabled };
          }
          if (!confirmOnly && isUseLabel(t)) {
            return { el: b, isConfirm: false, disabled: !!b.disabled };
          }
        }
      }
      if (confirmOnly) {
        var cfModalBtn = findGuildConfirmButton(null);
        if (cfModalBtn && isElemShown(cfModalBtn) && !cfModalBtn.disabled) {
          return { el: cfModalBtn, isConfirm: true, disabled: false };
        }
      }
      return null;
    };

    var findQuestTabBtn = function () {
      var ids = ['questBtn', 'nvBtn', 'btnQuest', 'btnNv', 'u20quest', 'u20nv', 'tabQuest', 'tabNv'];
      for (var ii = 0; ii < ids.length; ii++) {
        var q = document.getElementById(ids[ii]);
        if (q && isElemShown(q) && !q.disabled) return q;
      }
      var attr = document.querySelectorAll('[data-tab*="quest" i], [data-tab*="nv" i], [data-open*="quest" i], [title*="Nhiệm vụ" i], [aria-label*="Nhiệm vụ" i]');
      for (var ai = 0; ai < attr.length; ai++) {
        if (isElemShown(attr[ai]) && !attr[ai].disabled) return attr[ai];
      }
      var prioSelectors = ['#u20bar > *, #u20bar button, #tabs > *, #tabs button, #u20pop button, .u20-pop button, .more-menu button', 'button, [role="button"], [role="tab"], a'];
      for (var ps = 0; ps < prioSelectors.length; ps++) {
        var all = document.querySelectorAll(prioSelectors[ps]);
        var cap = Math.min(all.length, 600);
        for (var i = 0; i < cap; i++) {
          var el = all[i];
          if (!el || !isElemShown(el) || el.disabled) continue;
          if (el.id === 'vlmAuto' || el.id === 'mswBtn') continue;
          if (el.closest && el.closest('#modeSw, #mswMenu, #chatLines, #u20lines, #logBox, #u20log')) continue;
          var t = cleanUiLabel((el.textContent || '') + ' ' + (el.getAttribute('title') || '') + ' ' + (el.getAttribute('aria-label') || ''));
          if (t === 'nhiem vu') return el;
        }
      }
      return null;
    };

    var findTbkTileBtn = function () {
      var qRoot = getQuestRoot();
      var roots = qRoot ? [qRoot, document] : [document];
      for (var ri = 0; ri < roots.length; ri++) {
        var root = roots[ri];
        var els = root.querySelectorAll('button, [role="tab"], [role="button"], .tab, .qtab, li, a, div, span');
        var cap = (root === document) ? Math.min(els.length, 2500) : els.length;
        var best = null;
        var bestScore = -1;
        for (var i = 0; i < cap; i++) {
          var el = els[i];
          if (!el || !isElemShown(el) || el.disabled) continue;
          if (el.id === 'vlmAuto' || el.id === 'mswBtn') continue;
          if (el.closest && el.closest('#tabs, #u20bar, #modeSw, #mswMenu, #chatLines, #u20lines, #logBox, #u20log, #vlmAuto')) continue;
          if ((el.tagName === 'DIV' || el.tagName === 'SPAN') && el.children && el.children.length > 3) continue;
          var t = cleanUiLabel(el.textContent || '');
          if (!t || t.length > 56) continue;
          if (t.indexOf('vao thien bao kho') >= 0 || t.indexOf('tin vat') >= 0 || t.indexOf('huy theo doi') >= 0) continue;
          if (t === 'thien bao kho' || t.indexOf('thien bao kho ') === 0) {
            var pBtn = el.closest ? (el.closest('button, [role="button"], [role="tab"], li, a') || el) : el;
            if (!pBtn || !isElemShown(pBtn) || pBtn.disabled) continue;
            var pLbl = cleanUiLabel(pBtn.textContent || '');
            if (pLbl.length > 68 || pLbl.indexOf('huy theo doi') >= 0 || pLbl.indexOf('hom nay') >= 0) continue;
            var score = (t === 'thien bao kho' ? 200 : 140) + (pBtn.tagName === 'BUTTON' || pBtn.getAttribute('role') === 'tab' ? 50 : 0);
            if (score > bestScore) {
              bestScore = score;
              best = pBtn;
            }
          }
        }
        if (best) return best;
      }
      return null;
    };

    var prevTabBtn = document.querySelector('#tabs .on, #tabs .active, #tabs [aria-selected="true"], #u20bar .on, #u20bar .active');
    var openedMoreBtn = null;
    var nvBtn = null;
    var wasAlreadyOpen = !!findTbkSection();

    lockModal(22000);
    stealthObj().enter();
    try {
      var sec = findTbkSection();
      if (!sec) {
        var tilePre = findTbkTileBtn();
        if (!tilePre) {
          nvBtn = findQuestTabBtn();
          if (!nvBtn) {
            var moreBtn = document.getElementById('u20more') || document.getElementById('moreBtn') || document.getElementById('btnMore');
            if (moreBtn && isElemShown(moreBtn) && !moreBtn.disabled) {
              openedMoreBtn = moreBtn;
              try { moreBtn.click(); } catch (eMo0) {}
              await sleep(240);
              nvBtn = findQuestTabBtn();
            }
          }
          if (!nvBtn) {
            note('[TBK] Khong tim thay nut Nhiem vu de dung Tin vat');
            return false;
          }
          try { nvBtn.click(); } catch (eNv0) {}
          var retriedNv = false;
          var wNv = Date.now() + 1600;
          while (Date.now() < wNv) {
            await sleep(110);
            sec = findTbkSection();
            tilePre = findTbkTileBtn();
            if (sec || tilePre) break;
            if (!retriedNv && Date.now() > wNv - 1050) {
              retriedNv = true;
              try { dispatchRealUiClick(nvBtn); } catch (eNv1) {}
            }
          }
        }

        if (!sec && tilePre) {
          try { if (tilePre.scrollIntoView) tilePre.scrollIntoView({ block: 'center' }); } catch (eSc0) {}
          await sleep(70);
          try { tilePre.click(); } catch (eTl0) {}
          var retriedTl = false;
          var wTl = Date.now() + 1600;
          while (Date.now() < wTl) {
            await sleep(110);
            sec = findTbkSection();
            if (sec) break;
            if (!retriedTl && Date.now() > wTl - 1050) {
              retriedTl = true;
              try { dispatchRealUiClick(tilePre); } catch (eTl1) {}
            }
          }
        }
      }

      if (!sec) {
        note('[TBK] Da mo Nhiem vu nhung khong thay muc Tin vat Thien Bao Kho');
        return false;
      }

      var st0 = parseTbkSecState(sec);
      if (st0.usedToday) {
        dayAdd('tbkTvUse', 1);
        tbkClearXong();
        A.tbkFailCount = 0;
        note('[TBK] Da dung Tin vat Thien Bao Kho hom nay (120p)');
        return true;
      }
      if (st0.cnt === 0) {
        note('[TBK] Chua co Tin vat Thien Bao Kho (0 cai) de dung');
        return false;
      }

      var u1 = findTbkUseBtnIn(sec, false);
      if (!u1 || !u1.el || u1.disabled) {
        var stChk = parseTbkSecState(sec);
        if (stChk.usedToday) {
          dayAdd('tbkTvUse', 1);
          tbkClearXong();
          A.tbkFailCount = 0;
          return true;
        }
        note('[TBK] Khong tim thay nut Dung Tin vat dang hoat dong');
        return false;
      }

      var u2 = null;
      if (u1.isConfirm) {
        u2 = u1;
      } else {
        try { if (u1.el.scrollIntoView) u1.el.scrollIntoView({ block: 'center' }); } catch (eSc1) {}
        await sleep(80);
        try { u1.el.click(); } catch (eU1a) {}

        var retriedU1 = false;
        var wCf = Date.now() + 2000;
        while (Date.now() < wCf) {
          await sleep(100);
          var curSec1 = findTbkSection() || sec;
          if (parseTbkSecState(curSec1).usedToday) {
            dayAdd('tbkTvUse', 1);
            tbkClearXong();
            A.tbkFailCount = 0;
            note('Da dung Tin vat Thien Bao Kho (+60p -> 120p/ngay)');
            return true;
          }
          u2 = findTbkUseBtnIn(curSec1, true);
          if (u2 && u2.el) break;
          if (!retriedU1 && Date.now() > wCf - 1350) {
            retriedU1 = true;
            var u1Now = findTbkUseBtnIn(curSec1, false);
            if (u1Now && u1Now.el && !u1Now.isConfirm) {
              try { dispatchRealUiClick(u1Now.el); } catch (eU1b) {}
            }
          }
        }
      }

      if (!u2 || !u2.el) {
        note('[TBK] Da bam Dung nhung khong thay nut Bam lai de dung');
        return false;
      }

      try { u2.el.click(); } catch (eU2a) {}
      var retriedU2 = false;
      var confirmed = false;
      var wDone = Date.now() + 2400;
      while (Date.now() < wDone) {
        await sleep(120);
        var curSec2 = findTbkSection() || sec;
        var st2 = parseTbkSecState(curSec2);
        var uNow2 = findTbkUseBtnIn(curSec2, false);
        if (st2.usedToday || (st0.cnt > 0 && st2.cnt >= 0 && st2.cnt < st0.cnt) || (uNow2 && !uNow2.isConfirm && uNow2.disabled)) {
          confirmed = true;
          break;
        }
        if (!retriedU2 && Date.now() > wDone - 1750 && uNow2 && uNow2.isConfirm && uNow2.el) {
          retriedU2 = true;
          try { dispatchRealUiClick(uNow2.el); } catch (eU2b) {}
        }
      }

      if (!confirmed) {
        note('[TBK] Da bam Bam lai de dung nhung chua thay cap nhat 120p, se thu lai');
        return false;
      }

      dayAdd('tbkTvUse', 1);
      tbkClearXong();
      A.tbkFailCount = 0;
      note('Da dung Tin vat Thien Bao Kho (+60p -> tong 120p/ngay)');
      return true;
    } finally {
      await closeModalCleanly(document.getElementById('modal'));
      if (!wasAlreadyOpen) {
        if (prevTabBtn && prevTabBtn.isConnected && isVis(prevTabBtn)) {
          try { prevTabBtn.click(); } catch (ePr2) {}
        } else if (nvBtn && nvBtn.isConnected && findTbkSection()) {
          try { nvBtn.click(); } catch (eFold) {}
        }
      }
      if (openedMoreBtn && openedMoreBtn.isConnected) {
        try {
          var pop = document.querySelector('#u20pop, .u20-pop, .u20-more, .more-menu, [data-more-pop]');
          if (pop && isVis(pop)) openedMoreBtn.click();
        } catch (eMp2) {}
      }
      stealthObj().leave();
      unlockModal();
    }
  }

  async function muaVaDungTinVatThienBaoKho(I) {
    var boughtTv = await muaTinVatThienBaoKho(I);
    if (A.cfg.on && A.cfg.tbkTinVat && dayGet('tbkTvUse') < 1) {
      if (boughtTv) {
        delete A.cool.tbk_use_tinvat;
        await sleep(450);
      }
      var usedTv = await dungTinVatThienBaoKho(I, !!boughtTv);
      if (boughtTv && !usedTv && A.cfg.on && dayGet('tbkTvUse') < 1) {
        await sleep(900);
        delete A.cool.tbk_use_tinvat;
        usedTv = await dungTinVatThienBaoKho(I, true);
      }
      return !!(boughtTv || usedTv);
    }
    return boughtTv;
  }
  async function cheDo(I) {
    var now = Date.now();
    var lv = (I.lvl | 0) > 0 ? (I.lvl | 0) : 100;
    var t = I.dt || {};
    var domMode = readDomModeFallback();
    var mode = domMode || I.mode || 'farm';
    var inTowerRightNow = !!(I.tower || (A.st && A.st.noi === 'thap') || isInTowerNow());
    updateTowerTelemetry(now, I, inTowerRightNow);

    // 1. Theo dõi tiến triển Dã Tẩu (chống kẹt 30p) & Phó Bản (chống kẹt 3h + nhận diện hết lượt nhanh 15s)
    var dtToday = (t.today | 0);
    if (dtToday !== A.dtLastToday) {
      A.dtLastToday = dtToday;
      A.dtLastProgressAt = now;
    } else if (!A.dtLastProgressAt) {
      A.dtLastProgressAt = now;
    }
    var dtStalled30m = !!(A.cfg.dt && !t.du && dtToday < (t.max | 0) && A.dtLastProgressAt > 0 && (now - A.dtLastProgressAt > 30 * 60000));

    var inPbNow = !!(I.dg || (A.st && A.st.noi === 'phoban') || mode === 'dg');
    if (inPbNow && !pbXong()) {
      if (!A.pbStartedAt) A.pbStartedAt = now;
    } else {
      A.pbStartedAt = 0;
    }
    var pbOver3h = !!(A.pbStartedAt > 0 && (now - A.pbStartedAt > 3 * 3600000));

    if (pbOver3h && !pbXong()) {
      dayAdd('pbx', 1);
      A.pbStartedAt = 0;
      A.pbOut = 0;
      note('Phó Bản quá 3 giờ không dứt → nhả về lịch bình thường (10p quét lại)');
    } else if (A.cfg.pb && !pbXong()) {
      var pbHetApi = (typeof I.pbCon === 'number' && I.pbCon <= 0) ||
        (typeof I.dgCon === 'number' && I.dgCon <= 0) ||
        (typeof I.dgLeft === 'number' && I.dgLeft <= 0) ||
        (I.pb && typeof I.pb.con === 'number' && I.pb.con <= 0) ||
        isDungeonListExhaustedInDom();
      if (pbHetApi) {
        dayAdd('pbx', 1);
        A.pbOut = 0;
        A.pbStartedAt = 0;
        note('xong phó bản hôm nay → chuyển tiếp hoạt động');
      } else {
        var trong = !!(I.dg || (A.st && A.st.noi === 'phoban'));
        var ltOk = !A.st || !A.st.lt || A.st.lt === 'noi' || A.st.lt === 'khong';
        var lien = A.pbSeen && now - A.pbSeen < 60000;
        A.pbSeen = now;
        if (mode !== 'dg' || trong || I.dead || I.sat || !ltOk || !lien) {
          A.pbOut = 0;
        } else if (!A.pbOut) {
          A.pbOut = now;
        } else if (now - A.pbOut > 15000) {
          dayAdd('pbx', 1);
          A.pbOut = 0;
          A.pbStartedAt = 0;
          note('xong phó bản hôm nay (hết lượt) → chuyển tiếp hoạt động');
        }
      }
    }

    if (isUserControlling(3500)) return;

    var forceOnlyTower = !!A.cfg.thapNeDon;

    // 2. Chờ hồi phục sau khi gục (hết trọng thương & HP/MP >= 95%) trước khi vào lại Tháp
    if (hasHeavyInjury(I)) {
      A.koWaitingRecovery = true;
      if (!A.koDetectedAt) A.koDetectedAt = now;
      return;
    }
    if (A.koWaitingRecovery) {
      var ratios = getHpMpRatio(I);
      var recoveredEnough = (ratios.hp >= 0.95 && ratios.mp >= 0.95) || (now - (A.koDetectedAt || now) > 25000);
      if (!recoveredEnough) {
        if (ready('ko_wait_note', 10000)) {
          note('đang hồi phục sau gục (HP ' + Math.round(ratios.hp * 100) + '% · MP ' + Math.round(ratios.mp * 100) + '%) trước khi vào Tháp');
        }
        return;
      }
      A.koWaitingRecovery = false;
      A.koDetectedAt = 0;
    }

    if (forceOnlyTower) {
      towerLockSet(true);
    }

    async function switchMode(targetMode) {
      if (targetMode !== 'tower') {
        try { if (typeof releaseOrbitKeys === 'function') releaseOrbitKeys(); } catch (eSwRel) {}
        var r = await api('cheDo', { m: targetMode }, 2200);
        if (r && r.ok && r.m === targetMode) {
          ensureGameAutoOn(false);
          return true;
        }
      }

      var modeKeywords = {
        farm: ['luyen cong'],
        dg: ['pho ban'],
        tower: ['leo thap'],
        quest: ['nhiem vu', 'da tau']
      };
      var kws = modeKeywords[targetMode] || [];

      var matchItemText = function (rawTxt) {
        var txt = noAccent(rawTxt || '').replace(/[^a-z0-9\s]/g, ' ').replace(/\s+/g, ' ').trim();
        if (!txt || txt.length > 28 || txt.indexOf('che do') === 0) return false;
        if (targetMode === 'tower' && (txt.indexOf('bao kho') >= 0 || txt.indexOf('thien bao') >= 0)) return false;
        for (var k = 0; k < kws.length; k++) {
          if (txt === kws[k] || txt.indexOf(kws[k]) >= 0) return true;
        }
        return false;
      };

      var findModeTriggerBtn = function () {
        var b = document.getElementById('mswBtn');
        if (b && isVis(b)) return b;
        var btns = document.querySelectorAll('button, [role="button"], .btn, div');
        var cap = Math.min(btns.length, 220);
        for (var i = 0; i < cap; i++) {
          var el = btns[i];
          if (!el || !isVis(el) || el.id === 'vlmAuto') continue;
          if (el.children && el.children.length > 3) continue;
          var bt = noAccent(el.textContent || '').replace(/\s+/g, ' ').trim();
          if (bt.indexOf('che do:') === 0 || bt.indexOf('che do ') === 0) return el;
        }
        return null;
      };

      var findModeMenuItem = function (requireVis, triggerEl) {
        if (targetMode !== 'tower') {
          var direct = document.querySelector('#mswMenu [data-msw="' + targetMode + '"], [data-msw="' + targetMode + '"], [data-mode="' + targetMode + '"]');
          if (direct && (!requireVis || isVis(direct))) return direct;
        } else {
          var directTower = document.querySelectorAll('#mswMenu [data-msw], [data-msw], [data-mode]');
          for (var dt = 0; dt < directTower.length; dt++) {
            var dEl = directTower[dt];
            if (!dEl || dEl.id === 'mswBtn' || dEl.id === 'vlmAuto') continue;
            if (requireVis && !isVis(dEl)) continue;
            if (matchItemText(dEl.textContent || '')) return dEl;
          }
        }

        var containers = [];
        var mMenu = document.getElementById('mswMenu');
        var mSw = document.getElementById('modeSw');
        if (mMenu) containers.push(mMenu);
        if (mSw && mSw !== mMenu) containers.push(mSw);
        if (requireVis) containers.push(document.body);

        var trigRect = (triggerEl && triggerEl.getBoundingClientRect) ? triggerEl.getBoundingClientRect() : null;
        var best = null;
        var bestScore = -99999;

        for (var ci = 0; ci < containers.length; ci++) {
          var rootEl = containers[ci];
          var nodes = rootEl.querySelectorAll('button, [role="button"], [role="menuitem"], .item, li, a, div, span');
          var cap = Math.min(nodes.length, 320);
          for (var i = 0; i < cap; i++) {
            var el = nodes[i];
            if (!el || el.id === 'mswBtn' || el.id === 'vlmAuto') continue;
            if (requireVis && !isVis(el)) continue;
            if (el.closest && el.closest('#modal, #tabs, #u20bar, #chatLines, #u20lines, #logBox, #u20log')) continue;
            if (el.children && el.children.length > 3) continue;
            if (!matchItemText(el.textContent || '')) continue;

            var score = 200 - (el.textContent || '').trim().length;
            if (mMenu && mMenu.contains(el)) score += 300;
            else if (mSw && mSw.contains(el)) score += 200;
            if (el.tagName === 'BUTTON' || el.getAttribute('role') === 'button' || el.getAttribute('role') === 'menuitem') score += 40;
            if (trigRect && el.getBoundingClientRect) {
              var er = el.getBoundingClientRect();
              var dx = Math.abs(er.left - trigRect.left);
              var dy = Math.abs(er.top - trigRect.top);
              if (dx <= 140 && dy <= 340) score += 160;
              else if (rootEl === document.body) score -= 120;
            }
            if (score > bestScore) {
              bestScore = score;
              best = el;
            }
          }
          if (best && ci < 2) return best;
        }
        return best;
      };

      var verifySwitched = async function () {
        var domM = readDomModeFallback();
        if (targetMode === 'tower') {
          if (domM === 'tower' || isInTowerNow()) return true;
          return false;
        }
        if (domM === targetMode) return true;
        var Icheck = await info();
        return !!(Icheck && Icheck.mode === targetMode);
      };

      try {
        var mBtn = findModeTriggerBtn();
        var menu = document.getElementById('mswMenu');
        var item = findModeMenuItem(true, mBtn);
        if (mBtn && (!item || (menu && menu.classList.contains('hidden')))) {
          try { mBtn.click(); } catch (eB0) {
            try { dispatchRealUiClick(mBtn); } catch (eB1) {}
          }
          await sleep(240);
          item = findModeMenuItem(true, mBtn);
          if (!item) {
            try { dispatchRealUiClick(mBtn); } catch (eB2) {}
            await sleep(240);
            item = findModeMenuItem(true, mBtn);
          }
        }
        if (!item) item = findModeMenuItem(false, mBtn);

        if (item) {
          try { item.click(); } catch (eI0) {
            try { dispatchRealUiClick(item); } catch (eI1) {}
          }
          await sleep(360);
          if (await verifySwitched()) {
            if (menu && !menu.classList.contains('hidden')) {
              try { menu.classList.add('hidden'); } catch (eM1) {}
            }
            ensureGameAutoOn(false);
            return true;
          }
          // Thu nhan lai bang su kien day du 1 lan neu click thuong chua an
          try { dispatchRealUiClick(item); } catch (eI2) {}
          await sleep(360);
          if (menu && !menu.classList.contains('hidden')) {
            try { menu.classList.add('hidden'); } catch (eM2) {}
          }
          if (await verifySwitched()) {
            ensureGameAutoOn(false);
            return true;
          }
        }

        if (targetMode !== 'tower') {
          var selects = document.querySelectorAll('select');
          for (var si = 0; si < selects.length; si++) {
            var sel = selects[si];
            if (!isVis(sel)) continue;
            var opts = sel.options || [];
            for (var oi = 0; oi < opts.length; oi++) {
              var opt = opts[oi];
              var ot = noAccent((opt.text || '') + ' ' + (opt.value || ''));
              var hit = (opt.value === targetMode);
              for (var kk = 0; !hit && kk < kws.length; kk++) {
                if (ot.indexOf(kws[kk]) >= 0) hit = true;
              }
              if (hit) {
                sel.value = opt.value;
                sel.dispatchEvent(new Event('change', { bubbles: true }));
                await sleep(280);
                if (readDomModeFallback() === targetMode) {
                  ensureGameAutoOn(false);
                  return true;
                }
              }
            }
          }
        } else if (ready('tower_switch_fail_note', 20000)) {
          note('[LeoThap] Chua bam duoc muc Leo Thap trong Che Do (btn=' + (mBtn ? 'ok' : 'null') + ', item=' + (item ? 'ok' : 'null') + ', mode=' + (readDomModeFallback() || '?') + ')');
        }
      } catch (eDom) {}
      return false;
    }
    // 3. UU TIEN KHUNG GIO: 4 moc Boss Hoang Kim (12:00, 15:30, 19:30, 22:00)
    var activeBossSlot = getActiveBossHkSlot();
    var savedBossPrev = bossHkPrevGet();
    if (activeBossSlot) {
      if (!savedBossPrev && mode && mode !== 'farm') {
        bossHkPrevSet(mode);
      }
      if (mode !== 'farm') {
        if (I.dead || isUserControlling(3500) || !ready('mode_bosshk', 6000)) return;
        if (await switchMode('farm')) {
          note('👹 Đến giờ Boss Hoàng Kim → chuyển Luyện Công đánh Boss');
        }
      } else {
        ensureGameAutoOn(false);
      }
      return;
    } else if (savedBossPrev) {
      if (forceOnlyTower) {
        bossHkPrevSet(null);
      } else if (!I.dead && !isUserControlling(3500) && ready('mode_bosshk_restore', 6000)) {
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

    // 4. Bộ quét việc sót 10 phút/lần: rà soát lại Dã Tẩu, Phó Bản và KNB
    if (now - (A.lastSweep10mAt || 0) >= 10 * 60000) {
      A.lastSweep10mAt = now;
      var knbCap = A.cfg.knbMua | 0;
      if (knbCap > 0 && demKnb() < Math.min(knbCap, KNB_NGAY) && !I.dg && !I.tower && !I.sat && !I.dead) {
        delete A.cool.knb;
        note('quét 10p: kiểm tra mua KNB (' + demKnb() + '/' + Math.min(knbCap, KNB_NGAY) + ')');
        await muaKnb(I);
      }
      if (!forceOnlyTower) {
        var hasMissedDt = !!(A.cfg.dt && lv >= 20 && !t.du && !dtStalled30m && ((t.today | 0) < (t.max | 0) || ((I.ldt | 0) > 0 && (t.con | 0) > 0)));
        var hasMissedPb = !!(A.cfg.pb && !pbXong() && !pbOver3h);
        if ((hasMissedDt || hasMissedPb) && (mode === 'tower' || mode === 'farm')) {
          note('quét 10p: phát hiện còn lượt ' + (hasMissedDt ? 'Dã Tẩu' : 'Phó Bản') + ' → ưu tiên xử lý');
        }
      }
    }

    // 5. Máy trạng thái kịch bản theo lượt hoạt động:
    //    (1) Nhiệm vụ (Dã Tẩu / Sát Thủ / Phong Lăng Độ) -> 'quest'
    //    (2) Phó Bản -> 'dg'
    //    (3) Ưu tiên hành động khi hết lượt -> 'farm' (+ Chọn map) hoặc 'tower' (Xa em ra / Bình thường)
    var justSaved = !!(A.saveGraceUntil && now < A.saveGraceUntil);
    var dtMax = (t.max | 0) > 0 ? (t.max | 0) : 200;
    var hasDtInfo = !!(I && I.ok && I.dt);
    var dtCon = hasDtInfo && A.cfg.dt && lv >= 20 && !t.du && !dtStalled30m &&
      ((t.today | 0) < dtMax || ((I.ldt | 0) > 0 && (t.con | 0) > 0));
    var stCon = A.cfg.st && lv >= 20 && (I.lb | 0) > 0;
    var pldCon = !!(I.pldCon > 0 || (A.st && A.st.noi === 'thuyen'));
    var pbCon = A.cfg.pb && !pbXong() && !pbOver3h;

    var tbkEnabled = !forceOnlyTower && !!A.cfg.tbk;
    var inTbkRightNow = isInTbkNow();
    inTowerRightNow = isInTowerNow();

    if (tbkEnabled && !tbkXong() && isTbkTimeExhaustedInDom(I)) {
      if (A.cfg.tbkTinVat && (dayGet('tbkTv2') < 1 || dayGet('tbkTvUse') < 1)) {
        await muaVaDungTinVatThienBaoKho(I);
      } else {
        var verEx = await probeThienBaoKhoInQuestTab(false);
        if (verEx && verEx.checked && verEx.exhausted) {
          dayAdd('tbkx2', 1);
          note('Da het thoi gian Thien Bao Kho hom nay (' + verEx.summary + ') -> bo qua toi 00:00');
        }
      }
    }

    var tbkCon = tbkEnabled && !tbkXong();
    var dailyDone = !dtCon && !stCon && !pldCon && !pbCon && !tbkCon;
    var afterTowerActive = !forceOnlyTower && !!(A.cfg.afterDailyEnabled && A.cfg.thap && !A.cfg.afterDailyFarm) && dailyDone;
    var afterFarmActive = !forceOnlyTower && !!(A.cfg.afterDailyEnabled && A.cfg.afterDailyFarm && !afterTowerActive) && dailyDone;
    var towerModeLabel = (A.cfg.thapAfterMode === 'ORBIT') ? 'Xa em ra' : 'Binh thuong';

    if (forceOnlyTower && justSaved && ready('only_tower_note', 8000)) {
      var rOnly = resolveOrbitStyle();
      var styleDesc = rOnly.style ? (' · ' + (rOnly.skill || rOnly.sect || 'Tu nhan dien') + ' -> ' + (rOnly.style === 'melee' ? 'Danh gan 90' : 'Danh xa 400')) : '';
      note('Che do Chi Leo Thap (' + towerModeLabel + styleDesc + ') · Dung tu uong buff');
    } else if (afterFarmActive && justSaved && ready('after_daily_ok_note', 8000)) {
      note('Da xong hoat dong -> chuyen Luyen Cong' + (A.cfg.farmMap ? (' (' + A.cfg.farmMap + ')') : ''));
    } else if (afterTowerActive && justSaved && ready('after_daily_ok_note', 8000)) {
      note('Da xong hoat dong -> chuyen Leo Thap (' + towerModeLabel + ')');
    } else if (!forceOnlyTower && A.cfg.afterDailyEnabled && !dailyDone && justSaved && ready('after_daily_wait_note', 8000)) {
      var pend = [];
      if (dtCon) pend.push('Da Tau ' + dtToday + '/' + dtMax);
      if (stCon) pend.push('Sat Thu (' + (I.lb | 0) + ')');
      if (pldCon) pend.push('Phong Lang Do');
      if (pbCon) pend.push('Pho Ban');
      if (tbkCon) pend.push('Thien Bao Kho' + (A.tbkStatus && A.tbkStatus.maxMin > 0 ? (' ' + A.tbkStatus.usedMin + '/' + A.tbkStatus.maxMin + 'p') : ''));
      note('Thu tu hoat dong con lai: ' + pend.join(' -> '));
    }

    async function enterThienBaoKho() {
      if (A.cfg.tbkTinVat && (dayGet('tbkTv2') < 1 || dayGet('tbkTvUse') < 1)) {
        await muaVaDungTinVatThienBaoKho(I);
      }
      var qRes = await probeThienBaoKhoInQuestTab(true);
      if (qRes && qRes.checked) {
        if (qRes.exhausted) return qRes;
        if (qRes.entered || isInTbkNow()) {
          qRes.entered = true;
          return qRes;
        }
        return qRes;
      }
      return { checked: false, entered: isInTbkNow(), exhausted: false, summary: '' };
    }

    // Thu tu: Neu Chi Leo Thap -> 'tower'.
    // Nguoc lai: (1) Da Tau, Sat Thu / PLD -> (2) Pho Ban -> (3) Thien Bao Kho -> (4) Luyen Cong hoac Leo Thap
    var want = forceOnlyTower
      ? 'tower'
      : ((dtCon || stCon || pldCon)
          ? 'quest'
          : (pbCon
              ? 'dg'
              : (tbkCon
                  ? 'tbk'
                  : (afterFarmActive
                      ? 'farm'
                      : (afterTowerActive ? 'tower' : null)))));
    var prev = prevGet();

    if (want === 'tower' && (inTowerRightNow || mode === 'tower')) {
      towerLockSet(true);
    } else {
      towerLockSet(false);
    }

    // Neu dang trong Thien Bao Kho nhung can roi som -> bam Roi -> Bam lai de roi
    if (inTbkRightNow && want && want !== 'tbk') {
      if (I.dead) return;
      if (!justSaved && isUserControlling(3500)) return;
      if (justSaved || ready('tbk_leave_early', 4500)) {
        A.cool.tbk_leave_early = Date.now() + 4500;
        var leftOk = await leaveThienBaoKho();
        if (leftOk) {
          note('Roi Thien Bao Kho som de chuyen ' + (MODE_VI[want] || want));
        }
      }
      return;
    }

    if (want === 'tbk') {
      if (I.dead || I.sat || I.dg) return;
      if (!justSaved && isUserControlling(3500)) return;
      if (inTbkRightNow) {
        A.tbkFailCount = 0;
        ensureGameAutoOn(false);
      } else if (justSaved || ready('tbk_enter', 8000)) {
        A.cool.tbk_enter = Date.now() + 8000;
        var entRes = await enterThienBaoKho();
        if (entRes && entRes.checked && entRes.exhausted) {
          var canExpand = !!(A.cfg.tbkTinVat && !entRes.tvUsedToday && (entRes.maxMin <= 60 || dayGet('tbkTvUse') < 1));
          if (canExpand) {
            delete A.cool.tbk_tinvat;
            delete A.cool.tbk_use_tinvat;
            var okLate = await muaVaDungTinVatThienBaoKho(I);
            if (okLate) {
              delete A.cool.tbk_enter;
              return;
            }
          }
          dayAdd('tbkx2', 1);
          A.tbkFailCount = 0;
          note('Da het thoi gian Thien Bao Kho hom nay (' + (entRes.summary || 'Het phut hom nay - 0h mo lai') + ') -> bo qua toi 00:00');
        } else if (entRes && entRes.entered) {
          A.tbkFailCount = 0;
          tbkClearXong();
          note('Vao Thien Bao Kho (Auto in-game' + (entRes.summary ? ', ' + entRes.summary : '') + ')');
        } else if (entRes && entRes.checked && !entRes.exhausted) {
          if (ready('tbk_retry_note', 25000)) {
            note('[TBK] Con thoi gian (' + entRes.summary + ') nhung chua vao duoc, se thu lai');
          }
        }
      }
    } else if (want === 'tower') {
      if (I.dead || I.sat || I.dg) return;
      if (!justSaved && isUserControlling(3500)) return;
      var domModeTower = readDomModeFallback();
      if (inTowerRightNow || domModeTower === 'tower') {
        ensureGameAutoOn(false);
        logTowerCombatProfileOnce();
      } else {
        if (!justSaved && (!ranh() || !ready('mode_tower', 4500))) return;
        A.cool.mode_tower = Date.now() + 4500;
        if (await switchMode('tower')) {
          note((forceOnlyTower ? 'Chi Leo Thap: ' : 'Xong hoat dong -> ') + 'Chuyen Leo Thap (' + towerModeLabel + ')');
          logTowerCombatProfileOnce();
        }
      }
    } else if (want && mode !== want) {
      if (I.dead || I.sat || I.dg) return;
      if (!justSaved && (!ranh() || !ready('mode_' + want, 3500))) return;
      var savedPrev = prev || mode;
      if (await switchMode(want)) {
        if (!prev && !afterFarmActive && !afterTowerActive) prevSet(savedPrev);
        A.pbOut = 0;
        A.cool['mode_' + want] = Date.now() + 3500;
        if (want === 'dg' && !A.pbStartedAt) A.pbStartedAt = now;
        if (want === 'farm' && afterFarmActive) {
          note('Chuyen Che do: Luyen Cong (xong Da Tau, Pho Ban, Thien Bao Kho)');
          if (A.cfg.farmMap) {
            await sleep(420);
            await selectTrainingMap(I, A.cfg.farmMap);
          }
        } else {
          note(want === 'dg'
            ? 'Chuyen Pho Ban (con luot)'
            : ('Chuyen Nhiem Vu (' + (dtCon ? ('Da Tau ' + dtToday + '/' + dtMax) : (stCon ? 'Sat Thu' : 'Phong Lang Do')) + ')'));
        }
      }
    } else if (want && mode === want) {
      ensureGameAutoOn(false);
      if (afterFarmActive && mode === 'farm' && A.cfg.farmMap && !I.dg && !I.tower && !I.sat && !I.dead) {
        if (!isAlreadyOnTrainingMap(I, A.cfg.farmMap)) {
          await selectTrainingMap(I, A.cfg.farmMap);
        }
      }
    } else if (!want && prev && MODE_VI[prev] && mode !== prev && (mode === 'quest' || mode === 'dg' || mode === 'tower')) {
      if (I.dg || I.sat || !ranh() || !ready('mode_restore', 8000)) return;
      if (await switchMode(prev)) {
        prevSet(null);
        note('hoàn tất tự động: trở về trạng thái ban đầu (' + MODE_VI[prev] + ')');
      }
    } else if (!want && prev && mode === prev) {
      prevSet(null);
    }
  }
  function syncDailyDoneFlags(I) {
    if (!I) return;
    try {
      if (I.khan === true) dayAdd('khanDone', 1);
      if ((I.ldtMax | 0) > 0 && (I.ldtMua | 0) >= (I.ldtMax | 0)) dayAdd('ldtMuaDone', 1);
      var knbCap = A.cfg.knbMua | 0;
      if (knbCap > 0) {
        var knbDoneNow = demKnb() >= Math.min(knbCap, KNB_NGAY) || (typeof I.knbCon === 'number' && isFinite(I.knbCon) && I.knbCon <= 0);
        if (knbDoneNow) dayAdd('knbDone', 1);
      }
      var lbCap = A.cfg.lbMua | 0;
      if (A.cfg.st && lbCap > 0) {
        var lbDoneNow = dayGet('lb') >= lbCap || ((I.lbMax | 0) > 0 && (I.lbMua | 0) >= (I.lbMax | 0));
        if (lbDoneNow) dayAdd('lbDone', 1);
      }
      if (A.cfg.pb && !pbXong()) {
        var pbHetNow = (typeof I.pbCon === 'number' && I.pbCon <= 0) ||
          (typeof I.dgCon === 'number' && I.dgCon <= 0) ||
          (typeof I.dgLeft === 'number' && I.dgLeft <= 0) ||
          (I.pb && typeof I.pb.con === 'number' && I.pb.con <= 0);
        if (pbHetNow) dayAdd('pbx', 1);
      }
    } catch (eSync) {}
  }

  function buildDailyChecklistReport(I) {
    var todo = [];
    var acts = [];
    var done = [];
    var lv = (I && (I.lvl | 0) > 0) ? (I.lvl | 0) : 100;

    if (A.cfg.qua) {
      var q = (I && I.qua) || {};
      if (q.login || q.lvms) todo.push('Qua');
      else done.push('Qua');
    }
    if (A.cfg.khan) {
      if (dayGet('khanDone') > 0 || (I && I.khan)) done.push('Khan');
      else todo.push('Khan');
    }
    if (A.cfg.ldtMua && lv >= 20) {
      var remLdt = Math.max(0, ((I && I.ldtMax) | 0) - ((I && I.ldtMua) | 0));
      if (dayGet('ldtMuaDone') > 0 || ((I && (I.ldtMax | 0) > 0) && remLdt <= 0)) done.push('LBDT-KTC');
      else if (!A.cfg.dt || need(I) > 0) todo.push('LBDT-KTC');
    }
    if (A.cfg.tbkTinVat || A.cfg.bangLbdt || A.cfg.bangPd) {
      var bangPend = [];
      if (A.cfg.tbkTinVat && (dayGet('tbkTv2') < 1 || dayGet('tbkTvUse') < 1)) bangPend.push('TV');
      if (A.cfg.bangLbdt && dayGet('bangLbdt') < 1) bangPend.push('LB');
      if (A.cfg.bangPd && dayGet('bangPd') < 1) bangPend.push('PD');
      if (bangPend.length > 0) todo.push('Bang(' + bangPend.join('+') + ')');
      else done.push('BangHoi');
    }
    if (A.cfg.theGiaoDich) {
      if (dayGet('theGd') >= 1) done.push('TheGD');
      else todo.push('TheGD');
    }
    if ((A.cfg.knbMua | 0) > 0 && lv >= 20) {
      if (dayGet('knbDone') > 0) done.push('KNB');
      else todo.push('KNB');
    }
    if (A.cfg.doiNguyenLieu && A.cfg.doiNguyenLieu.length > 0) {
      if (dayGet('evAllDone') > 0) done.push('SuKien');
      else todo.push('SuKien');
    }
    if (A.cfg.st && (A.cfg.lbMua | 0) > 0 && lv >= 20) {
      if (dayGet('lbDone') > 0) done.push('LB-ST');
      else todo.push('LB-ST');
    }

    if (A.cfg.dt && lv >= 20) {
      var t = (I && I.dt) || {};
      var dtMax = (t.max | 0) > 0 ? (t.max | 0) : 200;
      var dtCon = !!(I && I.ok && I.dt) && !t.du && ((t.today | 0) < dtMax || (((I.ldt | 0) > 0) && ((t.con | 0) > 0)));
      if (dtCon) acts.push('DaTau(' + (t.today | 0) + '/' + dtMax + ')');
      else if (I && I.ok && I.dt) done.push('DaTau');
    }
    if (A.cfg.pb) {
      if (pbXong()) done.push('PhoBan');
      else acts.push('PhoBan');
    }
    if (A.cfg.tbk && !A.cfg.thapNeDon) {
      var tbkTag = (A.tbkStatus && A.tbkStatus.maxMin > 0) ? ('TBK(' + A.tbkStatus.usedMin + '/' + A.tbkStatus.maxMin + 'p)') : 'TBK';
      if (tbkXong()) done.push(tbkTag);
      else acts.push(tbkTag);
    }

    var sig = todo.join(',') + '|' + acts.join(',') + '|' + done.join(',');
    return { todo: todo, acts: acts, done: done, sig: sig };
  }

  function logDailyChecklist(I, force) {
    try {
      var rep = buildDailyChecklistReport(I);
      if (!force && rep.sig === A._lastDailyScanSig) return;
      if (!force && !ready('daily_scan_log', 12000)) return;
      A._lastDailyScanSig = rep.sig;
      var parts = [];
      if (rep.todo.length) parts.push('Viec ngay: ' + rep.todo.join(', '));
      if (rep.acts.length) parts.push('HD con: ' + rep.acts.join(' -> '));
      if (rep.done.length) parts.push('Da xong (bo qua): ' + rep.done.join(', '));
      if (parts.length) note('[Quet ngay] ' + parts.join(' | '));
    } catch (eLog) {}
  }

  async function tick() {
    if (!A.cfg.on) return;
    if (A.busy) {
      if (A.saveGraceUntil && Date.now() < A.saveGraceUntil) A.pendingTick = true;
      return;
    }
    if (isUserControlling(1500)) return;
    var curTradeDay = vnTradeDay();
    if (curTradeDay !== A.lastTradeDay) {
      A.lastTradeDay = curTradeDay;
      delete A.cool.theGd;
      A.immediate = true;
      A.immUntil = Math.max(A.immUntil || 0, Date.now() + 180000);
    }
    var curDay = vnDay();
    if (curDay !== A.lastDay) {
      A.lastDay = curDay;
      resetDay(true);
      delete A.cool.khan;
      delete A.cool.qua;
      delete A.cool.ldt;
      delete A.cool.pd_open;
      delete A.cool.lb;
      delete A.cool.tt;
      delete A.cool.knb;
      delete A.cool.theGd;
      delete A.cool.evHongBao;
      delete A.cool.tbk_tinvat;
      delete A.cool.tbk_use_tinvat;
      delete A.cool.tbk_enter;
      A._lastDailyScanSig = '';
      A.tbkStatus = null;
      A.tbkProbedAt = 0;
      delete A.cool.tbk_probe;
      A.tbkFailCount = 0;
      A.immediate = true;
      A.immUntil = Date.now() + 180000;
      A.started = Date.now() - 60000;
      note('00:00:01 (GMT+7): sang ngay moi -> mo khoa va quet lai toan bo hoat dong ngay');
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
        note('ban game ' + (A.st.v | 0) + ' < 990 - khong chay tu dong');
      }
      return;
    }
    if (!ingame) { A.started = 0; return; }

    if (!A.started) A.started = Date.now();
    if (!A.immediate && A.immUntil <= Date.now() && Date.now() - A.started < 2500) return;

    A.busy = true;
    try {
      var I0 = await info();
      if (!I0) return;
      syncDailyDoneFlags(I0);
      logDailyChecklist(I0, !!(A.saveGraceUntil && Date.now() < A.saveGraceUntil));

      var runSeq = (A.cfgSeq | 0);
      var safeStep = async function (fn, tag) {
        if (!A.cfg.on || (A.cfgSeq | 0) !== runSeq) {
          A.pendingTick = true;
          return false;
        }
        var Ic = (await info()) || I0;
        syncDailyDoneFlags(Ic);
        try { await fn(Ic); } catch (eStep) {
          if (ready('err_' + tag, 30000)) note('[Loi ' + tag + '] ' + (eStep && eStep.message ? eStep.message : eStep));
        }
        return true;
      };

      // BUOC 1: Viec nhe chay qua API (khong can modal, chay moi luc)
      var apiTasks = [
        [qua, 'qua'],
        [troThu, 'troThu'],
        [daTau, 'daTau'],
        [muaLb, 'muaLb']
      ];
      for (var ai = 0; ai < apiTasks.length; ai++) {
        if (!await safeStep(apiTasks[ai][0], apiTasks[ai][1])) break;
      }

      // BUOC 2: Viec ngay can mo bang/modal -> chay TRUOC khi chui vao Pho Ban / Thien Bao Kho de khong bi bo sot
      var Ipre = (await info()) || I0;
      syncDailyDoneFlags(Ipre);
      var inBusyZone = !!(Ipre.dg || Ipre.tower || Ipre.sat || Ipre.dead || isInTowerNow() || isInTbkNow());
      if (!inBusyZone && A.cfg.on && (A.cfgSeq | 0) === runSeq) {
        var modalTasks = A.cfg.thapNeDon ? [
          [khan, 'khan'],
          [muaVaDungTinVatThienBaoKho, 'bangHoi'],
          [muaTheGiaoDich, 'theGd'],
          [muaKnb, 'knb'],
          [doiNguyenLieuThua, 'suKien']
        ] : [
          [suDungVatPhamTieuHao, 'buff'],
          [khan, 'khan'],
          [muaVaDungTinVatThienBaoKho, 'bangHoi'],
          [muaTheGiaoDich, 'theGd'],
          [muaKnb, 'knb'],
          [doiNguyenLieuThua, 'suKien']
        ];
        for (var mi = 0; mi < modalTasks.length; mi++) {
          if (!await safeStep(modalTasks[mi][0], modalTasks[mi][1])) break;
        }
        // Neu vua doi LBDT/Goi Phuc Duyen tu Bang Hoi -> mo Phuc Duyen / mua bo sung ngay
        if (!A.cfg.thapNeDon && A.cfg.pd && A.cfg.on && (A.cfgSeq | 0) === runSeq) {
          delete A.cool.pd_open;
          await safeStep(daTau, 'pdAfterBang');
        }
      }

      // BUOC 3: Chuyen & duy tri 3 hoat dong chinh theo thu tu: Da Tau -> Pho Ban -> Thien Bao Kho -> Luyen Cong
      if (A.cfg.on && (A.cfgSeq | 0) === runSeq) {
        var Imode = (await info()) || Ipre;
        syncDailyDoneFlags(Imode);
        try { await cheDo(Imode); } catch (eChe) {
          note('[Loi cheDo] ' + (eChe && eChe.message ? eChe.message : eChe));
        }
      }

      if (!A.cfg.on) return;

      // BUOC 4: Xac nhan ve dung map Luyen Cong sau khi xong ca Da Tau + Pho Ban + Thien Bao Kho
      try {
        var Iafter = (await info()) || I0;
        syncDailyDoneFlags(Iafter);
        var dtA = Iafter.dt || {};
        var lvA = (Iafter.lvl | 0) > 0 ? (Iafter.lvl | 0) : 100;
        var dtDoneA = !(A.cfg.dt && lvA >= 20 && !dtA.du && ((dtA.today | 0) < ((dtA.max | 0) > 0 ? (dtA.max | 0) : 200) || ((Iafter.ldt | 0) > 0 && (dtA.con | 0) > 0)));
        var stDoneA = !(A.cfg.st && lvA >= 20 && (Iafter.lb | 0) > 0);
        var pbDoneA = !(A.cfg.pb && !pbXong());
        var tbkDoneA = !(A.cfg.tbk && !tbkXong());
        var curM = readDomModeFallback() || Iafter.mode || 'farm';
        if (!A.cfg.thapNeDon && A.cfg.afterDailyEnabled && A.cfg.afterDailyFarm && !A.cfg.thap &&
            A.cfg.farmMap && dtDoneA && stDoneA && pbDoneA && tbkDoneA &&
            curM === 'farm' && !Iafter.dg && !Iafter.tower && !Iafter.sat && !Iafter.dead && !isInTowerNow() && !isInTbkNow() &&
            !isAlreadyOnTrainingMap(Iafter, A.cfg.farmMap)) {
          await selectTrainingMap(Iafter, A.cfg.farmMap);
        }
        logDailyChecklist(Iafter, false);
      } catch (eMapForce) {}

      publishLiveTelemetry('');
    } catch (e) {
    } finally {
      A.busy = false;
      if (A.immUntil <= Date.now()) A.immediate = false;
      if (A.pendingTick && A.cfg.on) {
        A.pendingTick = false;
        setTimeout(function () { tick().catch(function () {}); }, 180);
      }
    }
  }

  // =========================================================================
  // LEO THAP: LAY TOA DO TRUNG TAM MAP LAM DIEM TAP TRUNG, GIU AUTO GAME BAT
  // =========================================================================
  // Chi dung phim mui ten dieu huong hoac joystick (KHONG gui W/A/S/D hay click canvas de tranh doi chieu)
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
    orbitDir: 1, // +1 thuận chiều kim đồng hồ, -1 ngược chiều (tự đảo chiều để tránh 4 góc & 4 cạnh)
    lastAt: 0,
    moveStartedAt: 0,
    lastClickAt: 0,
    lastBeatIdx: -1,
    phaseStart: 0,
    towerEnteredAt: 0,
    wasInTower: false,
    lastFloorNum: -1,
    detectedFloorFromCanvas: -1,
    lastStageText: '',
    returningToCenter: false,
    returnStartedAt: 0,
    returnLockUntil: 0,
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
    miniDiamond: null,    // { cx, cy, rx, ry } tự hiệu chuẩn tâm & bán trục thật của hình thoi Minimap
    obsPlayer: null,      // { nx, ny, at } từ chữ tên nhân vật trên canvas chính
    obsTargetRing: null,  // { nx, ny, at } từ vòng tròn đỏ dưới chân mục tiêu ĐANG CHỌN
    obsEnemies: [],       // [{ nx, ny, name, at }] tất cả quái đang hiển thị trên canvas chính
    obsMiniPlayer: null,  // { nx, ny, ndx, ndy, at } chấm nhân vật chuẩn hóa theo tâm thật hình thoi
    obsMiniEnemies: [],   // [{ nx, ny, at }] các chấm đỏ quái trên Minimap
    miniCanvas: null,
    autoSawOn: false,
    autoPreMoveSig: '',
    lastAutoRestoreAt: 0,
    autoPauseUntil: 0,
    moveEndTimer: 0,
    lastNoteAt: 0,
    joyPtrActive: false,
    held: { up: false, down: false, left: false, right: false },
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

  function isMinimapCanvasEl(cv) {
    if (!cv || cv === O.targetEl) return false;
    var idCls = ((cv.id || '') + ' ' + (typeof cv.className === 'string' ? cv.className : '')).toLowerCase();
    if (idCls.indexOf('minimap') >= 0 || idCls.indexOf('radar') >= 0 || idCls === 'mm ' || idCls.indexOf('mm-') >= 0) return true;
    try {
      var r = cv.getBoundingClientRect ? cv.getBoundingClientRect() : null;
      if (!r || r.width < 36 || r.width > 260 || r.height < 32 || r.height > 240) return false;
      var inTopRight = r.right > (window.innerWidth || 800) * 0.50 && r.top < (window.innerHeight || 480) * 0.48;
      return inTopRight;
    } catch (e) { return false; }
  }

  function findMinimapCanvas() {
    if (O.miniCanvas && O.miniCanvas.isConnected && isVis(O.miniCanvas)) return O.miniCanvas;
    try {
      var cvs = document.querySelectorAll('canvas');
      for (var i = 0; i < cvs.length; i++) {
        if (isVis(cvs[i]) && isMinimapCanvasEl(cvs[i])) {
          O.miniCanvas = cvs[i];
          return cvs[i];
        }
      }
    } catch (e) {}
    return null;
  }

  // VÙNG CẤM HÌNH THOI CHUẨN HÓA THEO TÂM THẬT (ndx, ndy trong -1..+1):
  // - (0, 0) là đúng tâm hình thoi; |ndx| + |ndy| = 1.0 là đúng 4 cạnh tường chéo.
  // - Vùng chơi an toàn tuyệt đối: cách 4 cạnh >= 32% (edgeDist < 0.68) và cắt 4 góc nhọn (cornerDist < 0.56).
  // - Trả về: 0 = tâm bản đồ; >= 0.85 = cảnh báo cạnh/góc; >= 0.96 = bắt buộc quay về tâm ngay.
  function arenaDangerScore(ndx, ndy) {
    var edgeDist = Math.abs(ndx) + Math.abs(ndy);
    var cornerDist = Math.max(Math.abs(ndx), Math.abs(ndy));
    return Math.max(edgeDist / 0.68, cornerDist / 0.56);
  }

  function classifyMinimapRgb(rgb) {
    if (!rgb) return '';
    var r = rgb.r, g = rgb.g, b = rgb.b;
    // Chấm đỏ / cam = quái / boss trên minimap
    if (r >= 120 && (r - g) >= 48 && (r - b) >= 48) return 'enemy';
    // Chấm sáng của nhân vật (thực tế RGB 135..255 trên nền tối ~58)
    var avg = (r + g + b) / 3;
    if (avg >= 128 && g >= r - 35) return 'player';
    if (g >= 170 && r <= 145) return 'player';
    return '';
  }

  // Tự hiệu chuẩn tâm & bán trục hình thoi + quét chấm nhân vật/quái trực tiếp trên Minimap (10/10 khung hình)
  function scanMinimapPixels(now) {
    if (now - O.lastMiniScanAt < 85) return;
    O.lastMiniScanAt = now;
    var cv = findMinimapCanvas();
    if (!cv || cv.width < 20 || cv.height < 20) return;
    try {
      var ctx = cv.getContext('2d');
      if (!ctx) return;
      var w = cv.width, h = cv.height;
      var data = ctx.getImageData(0, 0, w, h).data;
      var bx1 = Math.max(3, Math.floor(w * 0.05)), bx2 = Math.min(w - 3, Math.floor(w * 0.95));
      var by1 = Math.max(3, Math.floor(h * 0.05)), by2 = Math.min(h - 3, Math.floor(h * 0.95));
      var step = Math.max(1, Math.floor(Math.min(w, h) / 100));

      // Bước 1: Tự đo biên hình thoi sàn xám (gray 25..120) để tìm chính xác tâm thật (dcx, dcy) và bán trục (drx, dry)
      var dMinX = w, dMaxX = 0, dMinY = h, dMaxY = 0, dCount = 0, floorSum = 0;
      for (var y = by1; y < by2; y += step) {
        for (var x = bx1; x < bx2; x += step) {
          var idx = (y * w + x) * 4;
          if (data[idx + 3] < 140) continue;
          var gAvg = (data[idx] + data[idx + 1] + data[idx + 2]) / 3;
          if (gAvg > 25 && gAvg < 120) {
            if (x < dMinX) dMinX = x;
            if (x > dMaxX) dMaxX = x;
            if (y < dMinY) dMinY = y;
            if (y > dMaxY) dMaxY = y;
            floorSum += gAvg;
            dCount++;
          }
        }
      }
      if (dCount < 20) return;

      var dcx = (dMinX + dMaxX) * 0.5;
      var dcy = (dMinY + dMaxY) * 0.5;
      var drx = Math.max(8, (dMaxX - dMinX) * 0.5);
      var dry = Math.max(6, (dMaxY - dMinY) * 0.5);
      var floorMed = floorSum / dCount;

      if (!O.miniDiamond) {
        O.miniDiamond = { cx: dcx, cy: dcy, rx: drx, ry: dry };
      } else {
        O.miniDiamond.cx = O.miniDiamond.cx * 0.70 + dcx * 0.30;
        O.miniDiamond.cy = O.miniDiamond.cy * 0.70 + dcy * 0.30;
        O.miniDiamond.rx = O.miniDiamond.rx * 0.70 + drx * 0.30;
        O.miniDiamond.ry = O.miniDiamond.ry * 0.70 + dry * 0.30;
      }
      var md = O.miniDiamond;

      // Bước 2: Tìm chấm sáng nhất (nhân vật, vượt nền >= +40) và chấm đỏ (quái) bên trong hình thoi thật
      var maxBright = 0;
      var pList = [];
      var enemies = [];
      var py1 = Math.max(by1, Math.floor(md.cy - md.ry * 1.06));
      var py2 = Math.min(by2, Math.ceil(md.cy + md.ry * 1.06));
      var px1 = Math.max(bx1, Math.floor(md.cx - md.rx * 1.06));
      var px2 = Math.min(bx2, Math.ceil(md.cx + md.rx * 1.06));

      for (var py = py1; py <= py2; py += step) {
        for (var px = px1; px <= px2; px += step) {
          if ((Math.abs(px - md.cx) / (md.rx * 1.08) + Math.abs(py - md.cy) / (md.ry * 1.08)) > 1.08) continue;
          var pIdx = (py * w + px) * 4;
          if (data[pIdx + 3] < 140) continue;
          var r = data[pIdx], g = data[pIdx + 1], b = data[pIdx + 2];
          var lum = (r + g + b) / 3;
          if (lum >= floorMed + 40 && g >= r - 35) {
            if (lum > maxBright) maxBright = lum;
            pList.push({ x: px, y: py, w: lum - floorMed, lum: lum });
          } else if (r >= 120 && (r - g) >= 48 && (r - b) >= 48) {
            if (enemies.length < 24) enemies.push({ nx: px / w, ny: py / h, at: now });
          }
        }
      }

      if (pList.length > 0) {
        var cut = Math.max(floorMed + 40, maxBright - 24);
        var sx = 0, sy = 0, sw = 0;
        for (var k = 0; k < pList.length; k++) {
          if (pList[k].lum >= cut) {
            sx += pList[k].x * pList[k].w;
            sy += pList[k].y * pList[k].w;
            sw += pList[k].w;
          }
        }
        if (sw > 0) {
          var fx = sx / sw, fy = sy / sw;
          O.obsMiniPlayer = {
            nx: fx / w,
            ny: fy / h,
            ndx: (fx - md.cx) / md.rx,
            ndy: (fy - md.cy) / md.ry,
            at: now
          };
        }
      }
      // Xóa ngay danh sách chấm đỏ cũ khi minimap sạch quái để lập tức chạy về tâm
      O.obsMiniEnemies = enemies;
    } catch (ePix) {
      // Nếu canvas bị tainted thì dùng fallback từ vector hook
    }
  }

  function handleCanvasSpyText(ctx, text, rawX, rawY) {
    try {
      if (!ctx || !ctx.canvas) return;
      var cv = ctx.canvas;
      if (cv !== O.targetEl || cv.width < 160 || cv.height < 160) return;
      var txt = String(text || '').replace(/\s+/g, ' ').trim();
      if (!txt || txt.length < 3 || txt.length > 42) return;
      if (/^[\d.,+\-%/\s]+$/.test(txt)) return;
      var tNA = noAccent(txt);

      // Bắt số tầng trực tiếp từ tên quái hoặc thông báo trên canvas (vd: "Hoa Ho Tang 2", "Tang 48")
      var flMatch = /\btang\s*(\d{1,4})\b/.exec(tNA);
      if (flMatch) {
        var flNum = Number(flMatch[1]);
        if (isFinite(flNum) && flNum > 0) {
          O.detectedFloorFromCanvas = flNum;
        }
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

      // Khóa loại trừ vùng tên trên đầu nhân vật ở giữa màn hình để KHÔNG BAO GIỜ nhận nhầm tên mình thành quái
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
      if (!isStroke || !ctx || !ctx.canvas || ctx.canvas !== O.targetEl) return;
      var cv = ctx.canvas;
      var rMax = Math.max(radW || 0, radH || 0);
      if (rMax < 8 || rMax > 75) return;
      var sRgb = parseCssRgb(ctx.strokeStyle);
      if (!sRgb || sRgb.r < 190 || sRgb.g > 95 || sRgb.b > 95) return;

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
      if (isFinite(nx) && isFinite(ny) && nx >= 0.04 && nx <= 0.96 && ny >= 0.16 && ny <= 0.84) {
        O.obsTargetRing = { nx: nx, ny: ny, at: Date.now() };
      }
    } catch (eShp) {}
  }

  // Chỉ cài Canvas2D hook tối giản (bỏ fillRect & fill để giảm >95% tải GPU/CPU) khi đang trong Tháp
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
          arc: proto.arc,
          ellipse: proto.ellipse,
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

  // Gỡ trả nguyên bản 100% Canvas2D prototype khi rời Tháp / chuyển chế độ (0% lag trên Xiaomi & máy yếu)
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
      if (orig.arc) proto.arc = orig.arc;
      if (orig.ellipse) proto.ellipse = orig.ellipse;
      if (orig.stroke) proto.stroke = orig.stroke;
      if (orig.beginPath) proto.beginPath = orig.beginPath;
    } catch (eUnhook) {}
  }

  function findGameAutoButton() {
    try {
      var md = document.getElementById('modal');
      var directIds = ['autoBtn', 'bAuto', 'btnAuto', 'u20auto', 'autoAtk', 'atkAuto', 'tAuto', 'autoPlay', 'btnTuDanh', 'tuDanhBtn', 'u20Auto'];
      for (var i = 0; i < directIds.length; i++) {
        var el = document.getElementById(directIds[i]);
        if (el && isVis(el) && !el.disabled && (!md || !md.contains(el))) return el;
      }
      var attrEls = document.querySelectorAll(
        '[data-act="auto"], [data-cmd="auto"], [data-btn="auto"], [data-k="auto"], [data-auto], ' +
        '[onclick*="toggleAuto" i], [onclick*="autoAtk" i], [onclick*="tuDanh" i], [onclick*="setAuto" i]'
      );
      for (var ai = 0; ai < attrEls.length; ai++) {
        var ae = attrEls[ai];
        if (ae && isVis(ae) && !ae.disabled && (!md || !md.contains(ae)) && ae.id !== 'vlmAuto') return ae;
      }
      var btns = document.querySelectorAll('button, [role="button"], .btn, [onclick]');
      for (var bi = 0; bi < btns.length; bi++) {
        var b = btns[bi];
        if (!b || b.id === 'vlmAuto' || b.id === 'mswBtn' || !isVis(b) || b.disabled) continue;
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
          return b;
        }
        if (!t || t.length > 28) continue;
        if (t === 'auto' || t.indexOf('auto ') === 0 || t.indexOf('auto:') === 0 ||
            t === 'tu danh' || t.indexOf('tu danh') === 0 || t === 'tu chien' ||
            t.indexOf('tu chien') === 0 || t.indexOf('auto danh') >= 0 ||
            t === 'bat auto' || t === 'tat auto' || t === 'dang auto') {
          return b;
        }
      }
    } catch (eAutoBtn) {}
    return null;
  }

  function autoBtnVisualSignature(btn) {
    if (!btn) return '';
    var bg = '';
    try {
      var cs = window.getComputedStyle ? window.getComputedStyle(btn) : null;
      if (cs) {
        bg = (cs.backgroundColor || '') + '|' + (cs.color || '') + '|' +
          (cs.borderColor || '') + '|' + (cs.boxShadow || '') + '|' + (cs.filter || '') + '|' + (cs.opacity || '');
      }
    } catch (eCs) {}
    var inlineStyle = (btn.getAttribute && btn.getAttribute('style')) || '';
    return String(btn.className || '') + '||' + String(inlineStyle) + '||' + noAccent(btn.textContent || '') + '||' + bg;
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

      try {
        var cs = window.getComputedStyle ? window.getComputedStyle(btn) : null;
        if (cs && cs.filter && cs.filter.indexOf('grayscale') >= 0) return 'off';
      } catch (eFilt) {}

      if (O.autoSawOn && O.autoPreMoveSig) {
        var curSig = autoBtnVisualSignature(btn);
        if (curSig && curSig !== O.autoPreMoveSig) return 'off';
      }
    } catch (eDet) {}
    return 'unknown';
  }

  function ensureGameAutoOn(afterMovePulse) {
    try {
      if (isUserControlling(2500)) return false;
      var now = Date.now();
      if (!afterMovePulse && now - O.lastAutoRestoreAt < 250) return false;
      if (afterMovePulse && now - O.lastAutoRestoreAt < 90) return false;

      var btn = findGameAutoButton();
      var state = detectGameAutoState(btn);
      if (state === 'on') {
        O.autoSawOn = true;
        if (btn) O.autoPreMoveSig = autoBtnVisualSignature(btn);
        return false;
      }

      if (btn && state === 'off') {
        O.lastAutoRestoreAt = now;
        O.autoPauseUntil = now + 80;
        try { btn.click(); } catch (eClk) {}
        return true;
      }

      if (afterMovePulse && btn && O.autoSawOn && O.autoPreMoveSig) {
        var sigNow = autoBtnVisualSignature(btn);
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
    uninstallArenaCanvasSpy();
    O.active = false;
    O.wasInTower = false;
    O.towerEnteredAt = 0;
    O.lastFloorNum = -1;
    O.detectedFloorFromCanvas = -1;
    O.lastStageText = '';
    O.returningToCenter = false;
    O.returnStartedAt = 0;
    O.returnLockUntil = 0;
    O.noTargetSince = 0;
    O.hadTargetInFloor = false;
    O.prevMapX = 0;
    O.prevMapY = 0;
    O.stuckBeats = 0;
    O.lastTargetScreenX = 0;
    O.lastTargetScreenY = 0;
    O.lastTargetAt = 0;
    O.lastRetClickAt = 0;
    O.lastClickAt = 0;
    O.lastAt = 0;
    O.phaseStart = 0;
    O.lastBeatIdx = -1;
    O.orbitDir = 1;
    O.miniDiamond = null;
    O.miniCanvas = null;
    O.targetEl = null;
    O.obsEnemies = [];
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

  function autoDetectTowerCombatProfile() {
    var now = Date.now();
    if (A._autoTowerProfile && (now - (A._autoTowerProfile.at || 0)) < 20000 && A._autoTowerProfile.style) {
      return A._autoTowerProfile;
    }
    var sectRaw = '';
    try {
      if (A.st) sectRaw = String(A.st.phai || A.st.sect || A.st.monPhai || '');
      if (!sectRaw && A.info) sectRaw = String(A.info.phai || A.info.sect || A.info.monPhai || '');
    } catch (eS) {}
    var sectNA = noAccent(sectRaw);

    var found = null;

    // Tang 1: Doc ten chieu tu API / trang thai nhan vat
    var tryApiSkills = function (src) {
      if (!src || typeof src !== 'object' || found) return;
      var fields = [src.mainSkill, src.activeSkill, src.skill, src.chieu, src.curSkill];
      if (Array.isArray(src.skills)) {
        for (var si = 0; si < src.skills.length; si++) {
          var sk = src.skills[si];
          fields.push(typeof sk === 'string' ? sk : (sk && (sk.name || sk.ten || sk.id || sk.label)));
        }
      }
      for (var fi = 0; fi < fields.length; fi++) {
        var rawF = noAccent(String(fields[fi] || ''));
        if (!rawF) continue;
        var mF = matchSkillInList(rawF, ORBIT_MELEE_SKILLS);
        if (mF) { found = { style: 'melee', skill: mF.label, sect: sectRaw, source: 'auto_api' }; return; }
        var rF = matchSkillInList(rawF, ORBIT_RANGED_SKILLS);
        if (rF) { found = { style: 'ranged', skill: rF.label, sect: sectRaw, source: 'auto_api' }; return; }
      }
    };
    tryApiSkills(A.st);
    tryApiSkills(A.info);

    // Tang 2: Quet thanh chieu thuc tren HUD DOM (uu tien o chieu dang bat/chon)
    if (!found) {
      try {
        var nodes = document.querySelectorAll('button, [role="button"], .skill, .sk, [data-skill], [title], [aria-label]');
        var cap = Math.min(nodes.length, 260);
        var bestHit = null;
        var bestScore = -1;
        for (var i = 0; i < cap; i++) {
          var el = nodes[i];
          if (!el || !isVis(el) || el.id === 'vlmAuto' || el.id === 'mswBtn') continue;
          if (el.closest && el.closest('#modal, #chatLines, #u20lines, #logBox, #u20log, #mswMenu, #tabs, #u20bar')) continue;
          var txt = noAccent((el.textContent || '') + ' ' + (el.getAttribute('title') || '') + ' ' + (el.getAttribute('aria-label') || '') + ' ' + (el.getAttribute('data-skill') || ''));
          if (!txt || txt.length > 90) continue;
          var mD = matchSkillInList(txt, ORBIT_MELEE_SKILLS);
          var rD = mD ? null : matchSkillInList(txt, ORBIT_RANGED_SKILLS);
          var hit = mD || rD;
          if (!hit) continue;
          var cls = String(el.className || '').toLowerCase();
          var score = 100;
          if (cls.indexOf('active') >= 0 || cls.indexOf('on') >= 0 || cls.indexOf('sel') >= 0 ||
              cls.indexOf('cur') >= 0 || el.getAttribute('aria-pressed') === 'true') {
            score += 80;
          }
          if (txt.length <= 36) score += 25;
          if (score > bestScore) {
            bestScore = score;
            bestHit = { style: mD ? 'melee' : 'ranged', skill: hit.label, sect: sectRaw, source: 'auto_hud' };
          }
        }
        if (bestHit) found = bestHit;
      } catch (eHud) {}
    }

    // Tang 3: Suy luan theo Mon phai (neu game an ten chieu tren HUD)
    if (!found && sectNA) {
      if (sectNA.indexOf('thien vuong') >= 0) {
        found = { style: 'melee', skill: '', sect: 'Thiên Vương Bang', source: 'auto_sect' };
      } else if (sectNA.indexOf('con lon') >= 0 || sectNA.indexOf('nga mi') >= 0 || sectNA.indexOf('nga my') >= 0 ||
                 sectNA.indexOf('duong mon') >= 0 || sectNA.indexOf('cai bang') >= 0 ||
                 sectNA.indexOf('thuy yen') >= 0 || sectNA.indexOf('ngu doc') >= 0) {
        found = { style: 'ranged', skill: '', sect: sectRaw, source: 'auto_sect' };
      } else if (sectNA.indexOf('vo dang') >= 0) {
        found = { style: (sectNA.indexOf('kiem') >= 0 ? 'melee' : 'ranged'), skill: '', sect: sectRaw, source: 'auto_sect' };
      } else if (sectNA.indexOf('thien nhan') >= 0) {
        found = { style: (sectNA.indexOf('kich') >= 0 || sectNA.indexOf('chien') >= 0 ? 'melee' : 'ranged'), skill: '', sect: sectRaw, source: 'auto_sect' };
      } else if (sectNA.indexOf('thieu lam') >= 0) {
        found = { style: (sectNA.indexOf('dao') >= 0 ? 'ranged' : 'melee'), skill: '', sect: sectRaw, source: 'auto_sect' };
      }
    }

    var res = found || { style: '', skill: '', sect: sectRaw, source: 'none' };
    res.at = now;
    A._autoTowerProfile = res;
    return res;
  }

  function resolveOrbitStyle() {
    var cfgStyle = normalizeOrbitStyleJs(A.cfg.thapNeDonStyle);
    var cleanedSkill = String(A.cfg.thapMainSkill || '').replace(/\([^)]*\)/g, ' ');
    var chosenRaw = noAccent(cleanedSkill);
    var mChosen = chosenRaw ? matchSkillInList(chosenRaw, ORBIT_MELEE_SKILLS) : null;
    var rChosen = chosenRaw ? matchSkillInList(chosenRaw, ORBIT_RANGED_SKILLS) : null;

    if (cfgStyle === 'MELEE') {
      return { style: 'melee', skill: (mChosen || rChosen) ? (mChosen || rChosen).label : '', sect: '', source: 'manual_style' };
    }
    if (cfgStyle === 'RANGED') {
      return { style: 'ranged', skill: (mChosen || rChosen) ? (mChosen || rChosen).label : '', sect: '', source: 'manual_style' };
    }

    if (mChosen) return { style: 'melee', skill: mChosen.label, sect: '', source: 'manual_skill' };
    if (rChosen) return { style: 'ranged', skill: rChosen.label, sect: '', source: 'manual_skill' };

    return autoDetectTowerCombatProfile();
  }

  function logTowerCombatProfileOnce() {
    try {
      if (A.cfg.thapAfterMode !== 'ORBIT') return;
      var r = resolveOrbitStyle();
      var effStyle = r.style || 'ranged';
      var sig = (r.source || '') + '|' + (r.skill || '') + '|' + (r.sect || '') + '|' + effStyle;
      if (A._lastOrbitStyleLogSig === sig) return;
      A._lastOrbitStyleLogSig = sig;
      var modeStr = (effStyle === 'melee') ? 'Danh gan (90)' : 'Danh xa (400)';
      var whoStr = r.skill ? ('Chieu: ' + r.skill) : (r.sect ? ('Phai: ' + r.sect) : 'Mac dinh');
      var srcStr = (r.source && r.source.indexOf('manual') === 0) ? 'Chon tay' : 'Tu nhan dien';
      note('[LeoThap] ' + srcStr + ' (' + whoStr + ') -> ' + modeStr);
    } catch (eLogOrb) {}
  }

  function isInTbkNow() {
    try {
      if (A.st && Date.now() - A.stT < 15000) {
        if (A.st.o && A.st.o !== 'game') return false;
        if (A.st.dead || (typeof A.st.hp === 'number' && A.st.hp <= 0)) return false;
        if (A.st.noi) {
          var noiS = noAccent(String(A.st.noi)).toLowerCase();
          if (noiS === 'tbk' || noiS === 'baokho' || noiS === 'thienbaokho' ||
              noiS.indexOf('baokho') >= 0 || noiS.indexOf('thienbao') >= 0) return true;
        }
      }
      if (A.info && !A.info.dead && A.info.tbk === true) return true;
      var mBtn = document.getElementById('mswBtn');
      var mTxt = mBtn ? noAccent(mBtn.textContent || '') : '';
      var stg = document.getElementById('stageLbl');
      var stTxt = stg ? noAccent(stg.textContent || '') : '';
      if (mTxt.indexOf('thien bao') >= 0 || mTxt.indexOf('bao kho') >= 0 ||
          stTxt.indexOf('thien bao') >= 0 || stTxt.indexOf('bao kho') >= 0) {
        if (stTxt.indexOf('chua di') < 0) return true;
      }
      if (mTxt.indexOf('leo thap') < 0 && !(A.info && A.info.dg)) {
        var rBtns = document.querySelectorAll('button, [role="button"], .btn');
        var rCap = Math.min(rBtns.length, 140);
        for (var ri = 0; ri < rCap; ri++) {
          var rb = rBtns[ri];
          if (!rb || !isVis(rb) || rb.disabled || rb.id === 'vlmAuto' || rb.id === 'mswBtn') continue;
          if (rb.closest && rb.closest('#modal, #mswMenu, #tabs, #u20bar, #chatLines, #u20lines, #logBox, #u20log')) continue;
          var rt = cleanUiLabel(rb.textContent || '');
          if (rt === 'roi' || rt === 'bam lai de roi' || rt === 'roi thien bao kho' || rt === 'roi bao kho') {
            return true;
          }
        }
      }
    } catch (eTbkNow) {}
    return false;
  }

  function isInTowerNow() {
    try {
      if (isInTbkNow()) return false;
      if (A.st && Date.now() - A.stT < 15000) {
        if (A.st.o && A.st.o !== 'game') return false;
        if (A.st.dead || (typeof A.st.hp === 'number' && A.st.hp <= 0)) return false;
        if (A.st.noi) {
          var noiS = noAccent(String(A.st.noi)).toLowerCase();
          if (noiS === 'thap' || (noiS.indexOf('thap') >= 0 && noiS.indexOf('baokho') < 0)) return true;
        }
      }
      if (A.info) {
        if (A.info.dead) return false;
        if (A.info.tower === true) return true;
      }
      if (readDomModeFallback() === 'tower') {
        var stg = document.getElementById('stageLbl');
        var stTxt = stg ? noAccent(stg.textContent || '') : '';
        if (stTxt.indexOf('chua di') >= 0) return false;
        if (!stg || !stTxt || stTxt.indexOf('tang') >= 0 || stTxt.indexOf('thap') >= 0 || !(A.info && A.info.dg)) {
          return true;
        }
      }
    } catch (e) {}
    return false;
  }

  function isTowerOrbitEnabled() {
    if (!A.cfg.on) return false;
    if (A.cfg.thapAfterMode !== 'ORBIT') return false;
    if (A.cfg.thapNeDon) return true;
    return !!(A.cfg.afterDailyEnabled && A.cfg.thap && !A.cfg.afterDailyFarm);
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
        if (pe && !pe.classList.contains('hidden') && isVis(pe)) return false;
      }
    } catch (e) { return false; }
    return !isInTbkNow() && isInTowerNow();
  }

  function findPrimaryGameCanvas() {
    var best = null;
    var bestArea = 0;
    try {
      var canvases = document.querySelectorAll('canvas');
      for (var i = 0; i < canvases.length; i++) {
        var c = canvases[i];
        if (!isVis(c)) continue;
        if (c.closest && c.closest('#modal, #minimap, .minimap')) continue;
        if (isMinimapCanvasEl(c)) continue;
        var rect = c.getBoundingClientRect ? c.getBoundingClientRect() : null;
        if (!rect || rect.width < 120 || rect.height < 120) continue;
        var area = rect.width * rect.height;
        if (area > bestArea) {
          bestArea = area;
          best = { canvas: c, rect: rect };
        }
      }
    } catch (e) {}
    return best;
  }

  // Công thức elip mặt đất isometric đồng nhất cho Đánh gần (R=90, 12 nhịp/vòng) và Đánh xa (R=400, 18 nhịp/vòng)
  function computeOrbitParams(style, stageRect) {
    var minDim = stageRect ? Math.min(stageRect.width, stageRect.height) : 360;
    var isMelee = (style === 'melee');
    var rx = isMelee
      ? Math.max(54, Math.min(76, minDim * 0.165))   // R=90: ôm sát ngay ngoài hitbox thân quái
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
    // Đẩy điểm click ra khỏi hộp thân/đầu quái (rộng ±28px, cao 6..56px trên chân quái) để 100% click chạm nền đất
    if (Math.abs(gx - tx) < 28 && (ty - gy) > 6 && (ty - gy) < 56) {
      var side = (gx >= tx) ? 1 : -1;
      return { x: tx + side * 36, y: gy };
    }
    return { x: gx, y: gy };
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
      var stg = document.getElementById('stageLbl');
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
        return {
          hasMapPos: true,
          ndx: (px - mapCx) / (mw * 0.5),
          ndy: (py - mapCy) / (mh * 0.5),
          source: 'state'
        };
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

    return { hasMapPos: false, ndx: 0, ndy: 0, source: 'none' };
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
    if (O.obsTargetRing && now - O.obsTargetRing.at < 380) {
      var rx = r.left + O.obsTargetRing.nx * r.width;
      var ry = r.top + O.obsTargetRing.ny * r.height;
      return {
        px: px, py: py,
        hasTarget: true,
        tx: rx, ty: ry,
        targetSource: 'red_ring'
      };
    }

    // 3. Ưu tiên #2: Tên quái hiển thị trên canvas chính (tươi 360ms để nhận biết hết quái cực nhanh)
    var freshEnemies = [];
    for (var i = 0; i < O.obsEnemies.length; i++) {
      var e = O.obsEnemies[i];
      if (now - e.at < 360) freshEnemies.push(e);
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
      if (now - O.obsMiniEnemies[k].at < 450) freshMiniE.push(O.obsMiniEnemies[k]);
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

  // Click chuột trái trực tiếp tại tọa độ màn hình (rawX, rawY), tự kẹp vào vùng sàn đấu an toàn không đè HUD
  function clickScreenPoint(stage, rawX, rawY) {
    if (!stage || !stage.canvas || !stage.rect) return false;
    var r = stage.rect;
    var isPortrait = r.height > r.width;
    var minX = r.left + r.width * 0.14;
    var maxX = r.left + r.width * (isPortrait ? 0.86 : 0.82);
    var minY = r.top + r.height * (isPortrait ? 0.27 : 0.18);
    var maxY = r.top + r.height * (isPortrait ? 0.79 : 0.79);
    var cx = Math.max(minX, Math.min(maxX, rawX));
    var cy = Math.max(minY, Math.min(maxY, rawY));

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
      }, 28);
      return true;
    } catch (eClk) {
      return false;
    }
  }

  // CÔNG THỨC XOAY VÒNG TRÒN ĐỒNG NHẤT (vlmauto-29):
  // - Đánh gần (R=90): tiến pha góc liên tục (không bị loạn atan2 khi sát quái) + đẩy ra ngoài hitbox thân quái
  // - Đánh xa (R=400): giữ vành tròn rộng quanh mục tiêu, lượn ngang khi quái áp sát
  // - Cả hai đều dự đoán arenaDangerScore trên hình thoi thật để tránh tuyệt đối 4 cạnh và 4 góc
  function computeNextCircularClick(ent, mapPos, params) {
    var px = ent.px, py = ent.py;
    var dx = px - ent.tx;
    var dy = py - ent.ty;
    var dist = Math.hypot(dx, dy);
    var curDanger = mapPos.hasMapPos ? arenaDangerScore(mapPos.ndx, mapPos.ndy) : 0;

    // Chọn góc gốc ổn định:
    // - Nếu đang ở xa vành tròn (dist > rx * 1.25) -> bám theo góc thực tế để kéo mượt về đúng vành tròn
    // - Nếu đang bám sát vành tròn (đặc biệt là Đánh gần R=90) -> dùng pha liên tục O.theta để xoay tròn đều không giật
    var useContinuousTheta = (params.isMelee && dist <= params.rx * 1.35) || (dist <= 16);
    var baseAng = useContinuousTheta ? O.theta : Math.atan2(dy / 0.76, dx);

    var stepAng = params.stepRad;
    if (!params.isMelee) {
      if (dist < params.rx * 0.78) stepAng = Math.max(params.stepRad, Math.PI * 0.22); // ~40° lượn ngang mở tầm xa
      else if (dist > params.rx * 1.28) stepAng = params.stepRad * 0.55;
    } else if (dist > params.rx * 1.35) {
      stepAng = params.stepRad * 0.50; // Áp sát nhanh về đúng bán kính 90
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
      var predDanger = 0;
      if (mapPos.hasMapPos) {
        // Dự đoán dịch chuyển trong hệ tọa độ hình thoi chuẩn hóa (-1..+1)
        predDanger = arenaDangerScore(mapPos.ndx + ux * 0.16, mapPos.ndy + uy * 0.16);
      }
      return { x: px + mvx, y: py + mvy, ang: ang, predDanger: predDanger, dir: dir };
    };

    var calcOrbitCandidate = function (dir) {
      var nextAng = baseAng + dir * stepAng;
      var gx = ent.tx + params.rx * Math.cos(nextAng);
      var gy = ent.ty + params.ry * Math.sin(nextAng);
      return evalStep(gx, gy, nextAng, dir);
    };

    var primary = calcOrbitCandidate(O.orbitDir);
    if (!mapPos.hasMapPos || primary.predDanger < 0.85) {
      O.theta = primary.ang;
      return { x: primary.x, y: primary.y, mustReturnCenter: false };
    }

    // Chiều hiện tại sắp chạm 4 cạnh hoặc 4 góc -> đảo chiều xoay ngược lại
    var flipped = calcOrbitCandidate(-O.orbitDir);
    if (flipped.predDanger < 0.85 || (flipped.predDanger < primary.predDanger - 0.05 && flipped.predDanger < 0.94)) {
      O.orbitDir = -O.orbitDir;
      O.theta = flipped.ang;
      return { x: flipped.x, y: flipped.y, mustReturnCenter: false };
    }

    // Cả 2 chiều đều chạm vùng cấm -> lượn mạnh hướng về tâm bản đồ hoặc bật hẳn chế độ về tâm
    var safer = (flipped.predDanger < primary.predDanger) ? flipped : primary;
    var toCx = -mapPos.ndx * 1.45;
    var toCy = -mapPos.ndy;
    var cLen = Math.hypot(toCx, toCy) || 1;
    var blended = evalStep(
      px + (safer.x - px) * 0.35 + (toCx / cLen) * params.maxStridePx * 0.65,
      py + (safer.y - py) * 0.35 + (toCy / cLen) * params.maxStridePx * 0.65,
      safer.ang,
      safer.dir
    );
    if (blended.predDanger < curDanger - 0.03 && blended.predDanger < 0.92) {
      O.orbitDir = safer.dir;
      O.theta = blended.ang;
      return { x: blended.x, y: blended.y, mustReturnCenter: false };
    }

    return { x: px, y: py, mustReturnCenter: true };
  }

  function startReturnToCenter(now, lockMs) {
    O.returningToCenter = true;
    O.returnStartedAt = now;
    if (lockMs && lockMs > 0) {
      O.returnLockUntil = Math.max(O.returnLockUntil || 0, now + lockMs);
    }
    O.stuckBeats = 0;
  }

  function orbitTick() {
    if (!isTowerOrbitEnabled() || !isOrbitEligible()) {
      if (O.active || O.moving || O.wasInTower || window.__vlmOrbitCanvasSpyInstalled || window.__vlmOrbitSpyActive) {
        releaseOrbitKeys();
      }
      return false;
    }

    var stage = findPrimaryGameCanvas();
    if (!stage) {
      if (O.active || O.moving || window.__vlmOrbitCanvasSpyInstalled) releaseOrbitKeys();
      return false;
    }

    installArenaCanvasSpy();
    window.__vlmOrbitSpyActive = true;
    O.targetEl = stage.canvas;

    var now = Date.now();
    if (!O.wasInTower) {
      O.wasInTower = true;
      O.towerEnteredAt = now;
      O.lastClickAt = 0;
    }

    // Tự hiệu chuẩn hình thoi & quét chấm nhân vật trên Minimap mỗi 85ms
    scanMinimapPixels(now);

    var resolved = resolveOrbitStyle();
    var style = resolved.style || 'ranged';
    var params = computeOrbitParams(style, stage.rect);
    O.style = style;
    O.skillName = resolved.skill || '';

    // 1. Phát hiện vượt tầng (từ state/info/#stageLbl HOẶC trực tiếp từ tên quái/thông báo trên canvas)
    var curFloor = readCurrentTowerFloor();
    var stgEl = document.getElementById('stageLbl');
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
    var danger = mapPos.hasMapPos ? arenaDangerScore(mapPos.ndx, mapPos.ndy) : 0;
    var centerDist = mapPos.hasMapPos ? (Math.abs(mapPos.ndx) + Math.abs(mapPos.ndy)) : 0;

    if (floorChanged) {
      O.hadTargetInFloor = false;
      O.lastTargetAt = 0;
      O.noTargetSince = now;
      O.obsEnemies = [];
      O.obsMiniEnemies = [];
      O.obsTargetRing = null;
      startReturnToCenter(now, 1200); // Khóa về tâm tối thiểu 1.2s khi vừa vượt tầng
    }

    // 2. Phát hiện hết mục tiêu (diệt xong quái của đợt/tầng) -> NGAY LẬP TỨC chạy nhanh về chính giữa bản đồ
    if (ent.hasTarget) {
      O.noTargetSince = 0;
    } else {
      if (!O.noTargetSince) O.noTargetSince = now;
      if (!O.returningToCenter && (now - O.noTargetSince >= 220) && mapPos.hasMapPos && centerDist > 0.18) {
        startReturnToCenter(now, 950);
      }
    }

    // 3. Phát hiện tiến vào vùng cấm (sát bất kỳ cạnh nào hoặc 4 góc hình thoi) -> lập tức quay về giữa map
    if (mapPos.hasMapPos && danger >= 0.96 && !O.returningToCenter) {
      startReturnToCenter(now, 800);
    }

    // 4. Trạng thái chạy nhanh thẳng về chính giữa bản đồ (sau khi hết mục tiêu / vượt tầng / chạm cạnh-góc)
    if (O.returningToCenter) {
      if (!mapPos.hasMapPos) {
        O.returningToCenter = false;
        O.returnStartedAt = 0;
        O.returnLockUntil = 0;
        ensureGameAutoOn(true);
        return true;
      }
      var returnElapsed = now - (O.returnStartedAt || now);
      var reachedCenter = (centerDist <= 0.18);
      if (reachedCenter || returnElapsed > 2800) {
        O.returningToCenter = false;
        O.returnStartedAt = 0;
        O.returnLockUntil = 0;
        O.lastClickAt = 0; // Sẵn sàng bắt vòng tròn ngay khi có mục tiêu mới
        ensureGameAutoOn(true);
        return true;
      }
      if (now - (O.lastRetClickAt || 0) >= 170) {
        O.lastRetClickAt = now;
        // Bù tỷ lệ isometric 2:1 (trục ngang hình thoi rộng hơn trục dọc ~1.45 lần)
        var toCx = -mapPos.ndx * 1.45;
        var toCy = -mapPos.ndy;
        var lenC = Math.hypot(toCx, toCy) || 1;
        var strideC = Math.min(stage.rect.width, stage.rect.height) * 0.34;
        clickScreenPoint(stage, ent.px + (toCx / lenC) * strideC, ent.py + (toCy / lenC) * strideC);
        ensureGameAutoOn(true);
      }
      O.active = true;
      return true;
    }

    // 5. Nếu đang ở giữa map mà chưa có mục tiêu -> giữ yên ở giữa map chờ đợt quái mới
    if (!ent.hasTarget) {
      ensureGameAutoOn(true);
      return true;
    }

    // 6. Kiểm tra nhịp thời gian giữa 2 lần click vòng tròn (480ms đánh gần / 500ms đánh xa)
    if (now < O.autoPauseUntil) return true;
    if (now - O.lastClickAt < params.cycleMs) {
      ensureGameAutoOn(false);
      return true;
    }

    // Kiểm tra kẹt đứng yên 2 nhịp khi lệch xa tâm map -> quay về trung tâm map ngay
    if (mapPos.hasMapPos) {
      var movedM = Math.hypot(mapPos.ndx - O.prevMapX, mapPos.ndy - O.prevMapY);
      O.prevMapX = mapPos.ndx;
      O.prevMapY = mapPos.ndy;
      if (movedM < 0.025 && danger > 0.50) {
        O.stuckBeats++;
        if (O.stuckBeats >= 2) {
          startReturnToCenter(now, 800);
          return true;
        }
      } else {
        O.stuckBeats = 0;
      }
    }

    // Lưu vị trí mục tiêu đang bám
    O.hadTargetInFloor = true;
    O.lastTargetScreenX = ent.tx;
    O.lastTargetScreenY = ent.ty;
    O.lastTargetAt = now;

    // 7. Tính tọa độ click kế tiếp theo công thức đồng nhất (R=400 đánh xa / R=90 đánh gần), tránh tuyệt đối 4 cạnh & 4 góc
    var plan = computeNextCircularClick(ent, mapPos, params);
    if (plan.mustReturnCenter && mapPos.hasMapPos) {
      startReturnToCenter(now, 800);
      return true;
    }

    var autoBtnBefore = findGameAutoButton();
    if (autoBtnBefore && detectGameAutoState(autoBtnBefore) !== 'off') {
      O.autoSawOn = true;
      O.autoPreMoveSig = autoBtnVisualSignature(autoBtnBefore);
    }

    O.lastClickAt = now;
    O.lastAt = now;
    clickScreenPoint(stage, plan.x, plan.y);

    setTimeout(function () {
      try { if (A.cfg.on) ensureGameAutoOn(true); } catch (eA) {}
    }, 80);

    if (!O.active) {
      O.active = true;
      if (now - O.lastNoteAt > 20000) {
        O.lastNoteAt = now;
        var skInfo = O.skillName ? (' · ' + O.skillName) : '';
        note('🌀 Leo Tháp: xoay vòng tròn tránh cạnh/góc (' + (style === 'melee' ? 'Đánh gần R90' : 'Đánh xa R400') + skInfo + ')');
      }
    }
    return true;
  }

  window.__vlmAuto = {
    ver: VER,
    setCfg: setCfg,
    resetDay: resetDay,
    onEnteredGame: onEnteredGame,
    onMidnight000000: onMidnight000000,
    onNewVnDay: onNewVnDay,
    onSweep7Min: onSweep7Min,
    tick: tick,
    syncKnbNow: syncKnbNow,
    orbitTick: orbitTick,
    orbitState: function () {
      return {
        active: O.active,
        moving: O.moving,
        style: O.style,
        skill: O.skillName,
        theta: O.theta,
        orbitDir: O.orbitDir,
        miniPlayer: O.obsMiniPlayer,
        targetRing: O.obsTargetRing,
        enemies: O.obsEnemies.length
      };
    },
    trangThai: function () {
      return { on: !!A.cfg.on, busy: A.busy, last: A.last, lastAt: A.lastAt, st: A.st, orbit: { active: O.active, moving: O.moving, style: O.style, skill: O.skillName } };
    }
  };

  setCfg(CFG0, TOUCH0, IMM0);
  if (IMM0 && A.cfg.on) {
    setTimeout(function () { tick().catch(function () {}); }, 250);
  }
  function scheduleNextOrbitTick() {
    if (window.__vlmOrbitTimer) {
      clearTimeout(window.__vlmOrbitTimer);
      window.__vlmOrbitTimer = 0;
    }
    var inTower = false;
    try { inTower = !!orbitTick(); } catch (eOrb) { inTower = false; }
    // Tối ưu thiết bị yếu / Xiaomi: 90ms khi đang trong Tháp, nghỉ 900ms ngoài Tháp (không giữ Canvas2D hook ngoài Tháp)
    var delayMs = inTower ? 90 : 900;
    window.__vlmOrbitTimer = setTimeout(scheduleNextOrbitTick, delayMs);
  }

  var __tabPhaseMs = ((TAB | 0) % 6) * 2300;
  window.__vlmTickDelayTimer = setTimeout(function () {
    window.__vlmTickDelayTimer = 0;
    tick().catch(function () {});
    window.__vlmTickTimer = setInterval(function () { tick().catch(function () {}); }, 15000);
  }, __tabPhaseMs);
  window.__vlmOrbitTimer = setTimeout(scheduleNextOrbitTick, 250);
  return 'installed';
})();
    """.trimIndent()
}

        fun vlmForceTapElement(targetMap: String): String {
            val safeTarget = targetMap.replace("'", "\\'")
            return """
                (function() {
                    function norm(s) {
                        return String(s || '').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').replace(/\s+/g, ' ').trim();
                    }
                    var want = norm('');
                    if (want.indexOf('vi son dao') >= 0) want = 'vi son dao';
                    else want = 'mac bac thao nguyen';

                    function hitPoint(x, y, el) {
                        var opts = {bubbles: true, cancelable: true, view: window, clientX: x, clientY: y, screenX: x, screenY: y, button: 0, buttons: 1};
                        try {
                            if (window.PointerEvent) {
                                el.dispatchEvent(new PointerEvent('pointerdown', Object.assign({}, opts, {pointerId: 1, pointerType: 'touch', isPrimary: true})));
                                el.dispatchEvent(new PointerEvent('pointerup', Object.assign({}, opts, {pointerId: 1, pointerType: 'touch', isPrimary: true, buttons: 0})));
                            }
                        } catch (e) {}
                        ['mousedown', 'mouseup', 'click'].forEach(function(t) {
                            try { el.dispatchEvent(new MouseEvent(t, opts)); } catch (e) {}
                        });
                        try { el.click(); } catch (e) {}
                    }

                    var candidates = Array.prototype.slice.call(document.querySelectorAll('button, div, span, a, p, li'));
                    var picked = null;
                    var bestScore = -999999;
                    for (var i = 0; i < candidates.length; i++) {
                        var el = candidates[i];
                        var raw = (el.innerText || el.textContent || '').replace(/\s+/g, ' ').trim();
                        if (!raw || raw.length > 36) continue;
                        var n = norm(raw);
                        if (n !== want && n.indexOf(want) < 0) continue;
                        if (/cap\s*\d+/i.test(n) || raw.indexOf('·') >= 0 || raw.indexOf('+') >= 0 || raw.indexOf('-') >= 0) continue;
                        var r = el.getBoundingClientRect();
                        if (r.width < 20 || r.height < 14) continue;
                        var score = (n === want ? 300 : 100) - raw.length + (r.top > window.innerHeight * 0.5 ? 200 : 0);
                        if (score > bestScore) {
                            bestScore = score;
                            picked = el;
                        }
                    }

                    if (picked) {
                        var rect = picked.getBoundingClientRect();
                        hitPoint(rect.left + rect.width / 2, rect.top + rect.height / 2, picked);
                        return 'TAPPED:' + want;
                    }

                    for (var j = 0; j < candidates.length; j++) {
                        var topEl = candidates[j];
                        var rTop = topEl.getBoundingClientRect();
                        var nTop = norm(topEl.innerText || topEl.textContent || '');
                        if (rTop.top < window.innerHeight * 0.25 && rTop.left > window.innerWidth * 0.55 && rTop.width >= 24 && (nTop.indexOf('mac bac') >= 0 || nTop.indexOf('vi son dao') >= 0 || nTop.indexOf('thao nguyen') >= 0)) {
                            hitPoint(rTop.left + rTop.width / 2, rTop.top + rTop.height / 2, topEl);
                            return 'OPENED_PANEL:' + want;
                        }
                    }
                    return 'NO_TARGET:' + want;
                })();
            """.trimIndent()
        }