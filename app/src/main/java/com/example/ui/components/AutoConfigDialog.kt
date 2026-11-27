package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.webview.AutoConfig
private fun normalize130MapName(raw: String?): String {
    val norm = java.text.Normalizer.normalize((raw ?: "").lowercase().trim(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .replace('đ', 'd')
    return if (norm.contains("vi son") || norm.contains("son dao")) "Vi sơn đảo" else "Mạc Bắc Thảo Nguyên"
}



@Composable
fun AutoConfigDialog(
    tabIndex: Int,
    tabCount: Int,
    configs: List<AutoConfig>,
    characterName: String = "",
    characterSects: List<String> = emptyList(),
    onSelectTab: (Int) -> Unit,
    onToggleAuto: (Int, Boolean, AutoConfig) -> Unit = { _, _, _ -> },
    onSave: (Int, AutoConfig) -> Unit,
    onSaveAll: (AutoConfig) -> Unit = {},
    onResetCounters: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val currentConfig = configs.getOrElse(tabIndex) { AutoConfig() }
    var draft by remember(tabIndex) { mutableStateOf(currentConfig) }
    val currentSectRaw = characterSects.getOrElse(tabIndex) { "" }

    var k2kText by remember(tabIndex) { mutableStateOf(currentConfig.k2k.toString()) }
    var lbMuaText by remember(tabIndex) { mutableStateOf(currentConfig.lbMua.toString()) }
    var knbMuaText by remember(tabIndex) { mutableStateOf(currentConfig.knbMua.toString()) }
    var knbGiuText by remember(tabIndex) { mutableStateOf(currentConfig.knbGiu.toString()) }
    var statusNotice by remember(tabIndex) { mutableStateOf<String?>(null) }

    LaunchedEffect(tabIndex, currentConfig.on) {
        if (draft.on != currentConfig.on) {
            draft = draft.copy(on = currentConfig.on)
        }
    }

    fun buildDraftConfig(): AutoConfig = draft.copy(
        k2k = parseClamped(k2kText, AutoConfig.K2K_MAX),
        lbMua = parseClamped(lbMuaText, AutoConfig.LB_MUA_MAX),
        knbMua = parseClamped(knbMuaText, AutoConfig.KNB_MUA_MAX),
        knbGiu = parseClamped(knbGiuText, AutoConfig.KNB_GIU_MAX)
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("auto_config_dialog"),
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF1E293B),
            border = BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                val charSuffix = if (characterName.isNotBlank() && !characterName.startsWith("Tab ")) {
                    " · $characterName"
                } else {
                    ""
                }
                Text(
                    text = "🤖 TỰ ĐỘNG — TAB ${tabIndex + 1}$charSuffix",
                    color = Color(0xFFFBBF24),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Mỗi tab lưu cấu hình riêng và chạy độc lập qua kênh #vlmAuto.",
                    color = Color(0xFF94A3B8),
                    fontSize = 9.5.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .heightIn(max = minOf(380.dp, (LocalConfiguration.current.screenHeightDp * 0.56f).dp))
                        .verticalScroll(rememberScrollState())
                ) {
                    if (tabCount > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (i in 0 until tabCount) {
                                val isSelected = i == tabIndex
                                val isTabOn = if (isSelected) draft.on else (configs.getOrNull(i)?.on == true)
                                val chipShape = RoundedCornerShape(6.dp)
                                Row(
                                    modifier = Modifier
                                        .clip(chipShape)
                                        .background(if (isSelected) Color(0xFF1D4ED8) else Color(0xFF0F172A))
                                        .border(
                                            1.dp,
                                            if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155),
                                            chipShape
                                        )
                                        .clickable { onSelectTab(i) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                        .testTag("auto_tab_chip_$i"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(if (isTabOn) Color(0xFF10B981) else Color(0xFF475569))
                                    )
                                    Text(
                                text = "Thứ tự hoạt động & Leo Tháp",
                                color = Color(0xFFFCA5A5),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "• Thứ tự khi tích đủ: Dã Tẩu, Sát Thủ → Phó Bản → Thiên Bảo Khố (auto in-game) → Luyện Công (chọn map) hoặc Leo Tháp (Xa em ra / Bình thường).\n• Chỉ Leo Tháp: bỏ qua chuỗi hoạt động & dừng tự uống bình buff; vẫn nhận việc ngày nền chưa xong.",
                                color = Color(0xFFCBD5E1),
                                fontSize = 9.sp,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                            Text(
                                text = "Quỹ đạo Xa em ra chỉ chạy trong Leo Tháp (bước 20°: Đánh gần 90, Đánh xa 400; vượt tầng chạy về tâm map). Để Tự động tool sẽ tự nhận diện Môn phái & Chiêu chính trong game.",
                                color = Color(0xFF94A3B8),
                                fontSize = 9.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auto_master_switch"),
                        shape = RoundedCornerShape(6.dp),
                        color = if (draft.on) Color(0xFF064E3B) else Color(0xFF0F172A),
                        border = BorderStroke(
                            1.dp,
                            if (draft.on) Color(0xFF10B981) else Color(0xFF334155)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Bật tự động cho Tab ${tabIndex + 1}",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (draft.on) {
                                        "🟢 Đang chạy tự động cho Tab ${tabIndex + 1}"
                                    } else {
                                        "⚪ Đang tắt — gạt công tắc để auto app bắt đầu hoạt động"
                                    },
                                    color = if (draft.on) Color(0xFF6EE7B7) else Color(0xFF94A3B8),
                                    fontSize = 9.sp
                                )
                            }
                            Switch(
                                checked = draft.on,
                                onCheckedChange = { enabled ->
                                    val updated = buildDraftConfig().copy(on = enabled)
                                    draft = updated
                                    statusNotice = if (enabled) {
                                        "🟢 Đã bật tự động cho Tab ${tabIndex + 1}"
                                    } else {
                                        "⚪ Đã tắt tự động cho Tab ${tabIndex + 1}"
                                    }
                                    onToggleAuto(tabIndex, enabled, updated)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF10B981),
                                    uncheckedThumbColor = Color(0xFF94A3B8),
                                    uncheckedTrackColor = Color(0xFF1E293B)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    val normalizedTowerMode = AutoConfig.normalizeThapAfterMode(draft.thapAfterMode)
                    val isAfterTower = draft.afterDailyEnabled && draft.thap && !draft.afterDailyFarm
                    val isAnyTowerOrbit = (draft.thapNeDon || isAfterTower) && normalizedTowerMode == AutoConfig.THAP_MODE_ORBIT

                    // 1. Khối Chỉ Leo Tháp (ưu tiên tuyệt đối, dừng tự uống bình buff, vẫn nhận việc ngày nền)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .testTag("auto_only_tower_section"),
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF0F172A),
                        border = BorderStroke(1.dp, if (draft.thapNeDon) Color(0xFF10B981) else Color(0xFF334155))
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .heightIn(min = 44.dp)
                                        .clickable { draft = draft.copy(thapNeDon = !draft.thapNeDon) }
                                        .testTag("auto_check_thapNeDon"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = draft.thapNeDon,
                                        onCheckedChange = { checked -> draft = draft.copy(thapNeDon = checked) },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = Color(0xFF10B981),
                                            uncheckedColor = Color(0xFF64748B)
                                        )
                                    )
                                    Text(
                                        text = "Chỉ Leo Tháp",
                                        color = if (draft.thapNeDon) Color.White else Color(0xFFCBD5E1),
                                        fontSize = 10.5.sp,
                                        fontWeight = if (draft.thapNeDon) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                                val towerModes = listOf(
                                    Triple(AutoConfig.THAP_MODE_ORBIT, "Xa em ra", "auto_only_thap_mode_orbit"),
                                    Triple(AutoConfig.THAP_MODE_NORMAL, "Bình thường", "auto_only_thap_mode_normal")
                                )
                                towerModes.forEach { (modeKey, modeLabel, tag) ->
                                    val isSel = draft.thapNeDon && normalizedTowerMode == modeKey
                                    val shape = RoundedCornerShape(6.dp)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 36.dp)
                                            .clip(shape)
                                            .background(if (isSel) Color(0xFF065F46) else Color(0xFF1E293B))
                                            .border(1.dp, if (isSel) Color(0xFF10B981) else Color(0xFF475569), shape)
                                            .clickable {
                                                draft = draft.copy(
                                                    thapNeDon = true,
                                                    thapAfterMode = modeKey
                                                )
                                            }
                                            .testTag(tag),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = modeLabel,
                                            color = if (isSel) Color.White else Color(0xFF94A3B8),
                                            fontSize = 9.5.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Ưu tiên leo tháp liên tục, dừng tự uống bình buff, vẫn nhận việc ngày nền chưa xong.",
                                color = if (draft.thapNeDon) Color(0xFF6EE7B7) else Color(0xFF94A3B8),
                                fontSize = 9.sp,
                                modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
                            )
                        }
                    }

                    // 2. Chuỗi hoạt động ngày theo thứ tự: Dã Tẩu, Sát Thủ -> Phó Bản -> Thiên Bảo Khố
                    val dtStChecked = draft.dt || draft.st
                    AutoCheckRow("Dã Tẩu, Sát Thủ", dtStChecked, "auto_check_dt_st") { checked ->
                        draft = draft.copy(dt = checked, st = checked)
                    }
                    AutoCheckRow("Mua LBDT", draft.ldtMua, "auto_check_ldtMua") {
                        draft = draft.copy(ldtMua = it)
                    }
                    AutoCheckRow("Đổi Phúc Duyên", draft.pd, "auto_check_pd") {
                        draft = draft.copy(pd = it)
                    }
                    AutoCheckRow("Phó Bản", draft.pb, "auto_check_pb") {
                        draft = draft.copy(pb = it)
                    }
                    AutoCheckRow("Thiên Bảo Khố (auto in-game)", draft.tbk, "auto_check_tbk") {
                        draft = draft.copy(tbk = it)
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .testTag("auto_guild_shop_section"),
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF0F172A),
                        border = BorderStroke(
                            1.dp,
                            if (draft.tbkTinVat || draft.bangLbdt || draft.bangPd) Color(0xFF10B981) else Color(0xFF334155)
                        )
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Text(
                                text = "Cửa hàng Bang Hội (Vật phẩm): Mua Tín vật, LBDT, PD",
                                color = if (draft.tbkTinVat || draft.bangLbdt || draft.bangPd) Color(0xFF6EE7B7) else Color(0xFF94A3B8),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                val guildItems = listOf(
                                    Triple("Tín vật", draft.tbkTinVat, "auto_check_tbkTinVat"),
                                    Triple("LBDT", draft.bangLbdt, "auto_check_bangLbdt"),
                                    Triple("PD", draft.bangPd, "auto_check_bangPd")
                                )
                                guildItems.forEachIndexed { idx, (label, isChecked, tag) ->
                                    val toggle: (Boolean) -> Unit = { v ->
                                        draft = when (idx) {
                                            0 -> draft.copy(tbkTinVat = v)
                                            1 -> draft.copy(bangLbdt = v)
                                            else -> draft.copy(bangPd = v)
                                        }
                                    }
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 38.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isChecked) Color(0xFF065F46) else Color(0xFF1E293B))
                                            .border(
                                                1.dp,
                                                if (isChecked) Color(0xFF10B981) else Color(0xFF475569),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .clickable { toggle(!isChecked) }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                            .testTag(tag),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = toggle,
                                            modifier = Modifier.size(18.dp),
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = Color(0xFF10B981),
                                                uncheckedColor = Color(0xFF64748B)
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = label,
                                            color = if (isChecked) Color.White else Color(0xFFCBD5E1),
                                            fontSize = 10.sp,
                                            fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Tín vật TBK (tự mua + dùng +60p) • Lệnh Bài Dã Tẩu (×10) • Gói 10 Phúc Duyên (×10)",
                                color = Color(0xFF94A3B8),
                                fontSize = 8.5.sp,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                    }

                    // 3. Ưu tiên sau khi hết hoạt động: Luyện Công (+ Chọn map) HOẶC Leo Tháp (Xa em ra / Bình thường)
                    AutoCheckRow(
                        label = "Ưu tiên sau khi hết hoạt động (Dã Tẩu, Phó Bản, Thiên Bảo Khố)",
                        checked = draft.afterDailyEnabled,
                        testTag = "auto_check_afterDailyEnabled"
                    ) { enabled ->
                        val hasTarget = draft.afterDailyFarm || draft.thap
                        draft = draft.copy(
                            afterDailyEnabled = enabled,
                            afterDailyFarm = if (enabled && !hasTarget) true else draft.afterDailyFarm
                        )
                    }
                    AfterDailyActionSelector(
                        enabled = draft.afterDailyEnabled,
                        afterDailyFarm = draft.afterDailyFarm,
                        farmMap = draft.farmMap,
                        afterDailyThap = draft.thap && !draft.afterDailyFarm,
                        thapAfterMode = draft.thapAfterMode,
                        onToggleFarm = { checked ->
                            draft = draft.copy(
                                afterDailyEnabled = if (checked) true else draft.afterDailyEnabled,
                                afterDailyFarm = checked,
                                thap = if (checked) false else draft.thap
                            )
                        },
                        onSelectMap = { mapName ->
                            draft = draft.copy(
                                afterDailyEnabled = true,
                                afterDailyFarm = true,
                                thap = false,
                                farmMap = mapName
                            )
                        },
                        onToggleThap = { checked ->
                            draft = draft.copy(
                                afterDailyEnabled = if (checked) true else draft.afterDailyEnabled,
                                afterDailyFarm = if (checked) false else draft.afterDailyFarm,
                                thap = checked
                            )
                        },
                        onSelectThapMode = { modeKey ->
                            draft = draft.copy(
                                afterDailyEnabled = true,
                                afterDailyFarm = false,
                                thap = true,
                                thapAfterMode = modeKey
                            )
                        }
                    )

                    // 4. Cấu hình Leo Tháp: Môn phái, Chiêu chính 9x & Kiểu né đòn (dùng cho Chỉ Leo Tháp & Sau hoạt động -> Leo Tháp)
                    MainSkill9xSelector(
                        sectRaw = currentSectRaw,
                        selectedSkillId = draft.thapMainSkill,
                        resolvedStyle = draft.resolvedOrbitCombatStyle(),
                        onSelectSkill = { skillId ->
                            draft = draft.copy(
                                thapMainSkill = skillId,
                                thapNeDonStyle = AutoConfig.ORBIT_AUTO
                            )
                        }
                    )
                    OrbitStyleSelector(
                        selectedStyle = draft.thapNeDonStyle,
                        isOrbitEnabled = isAnyTowerOrbit,
                        onSelect = { style -> draft = draft.copy(thapNeDonStyle = style) }
                    )
                    AutoCheckRow("Nhận quà Điểm danh + Mốc cấp", draft.qua) {
                        draft = draft.copy(qua = it)
                    }
                    AutoCheckRow("Khấn tượng mỗi ngày (+20% kinh nghiệm 8 giờ)", draft.khan) {
                        draft = draft.copy(khan = it)
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tự động đổi nguyên liệu thừa ở Sự kiện / Tri Ân (dừng khi hết lượt):",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    val exchangeItems = listOf(
                        "Đại Hồng Bao",
                        "Hồng Bao",
                        "Tiên Thảo Lộ Đặc Biệt",
                        "Tiên Thảo Lộ",
                        "Quế Hoa Tửu"
                    )

                    exchangeItems.forEach { item ->
                        val isChecked = draft.doiNguyenLieu.contains(item)
                        AutoCheckRow("  ▫ Đổi $item", isChecked) { checked ->
                            val current = draft.doiNguyenLieu.toMutableSet()
                            if (checked) current.add(item) else current.remove(item)
                            draft = draft.copy(doiNguyenLieu = current)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    AutoCheckRow(
                        label = "Tiên Thảo Lộ hết thì dùng trong túi",
                        checked = draft.ttl,
                        testTag = "auto_check_ttl"
                    ) {
                        draft = draft.copy(ttl = it)
                    }
                    AutoCheckRow(
                        label = "… túi hết thì mua (20 KNB khóa)",
                        checked = draft.ttlMua,
                        testTag = "auto_check_ttlMua"
                    ) {
                        draft = draft.copy(ttlMua = it)
                    }
                    AutoCheckRow(
                        label = "Tiên Thảo Lộ Đặc Biệt hết thì dùng trong túi",
                        checked = draft.tt2,
                        testTag = "auto_check_tt2"
                    ) {
                        draft = draft.copy(tt2 = it)
                    }
                    AutoCheckRow(
                        label = "… túi hết thì mua (300 KNB khóa)",
                        checked = draft.tt2Mua,
                        testTag = "auto_check_tt2Mua"
                    ) {
                        draft = draft.copy(tt2Mua = it)
                    }
                    AutoCheckRow(
                        label = "Quế Hoa Tửu hết thì dùng trong túi",
                        checked = draft.qht,
                        testTag = "auto_check_qht"
                    ) {
                        draft = draft.copy(qht = it)
                    }
                    AutoCheckRow(
                        label = "… túi hết thì mua (20 KNB khóa)",
                        checked = draft.qhtMua,
                        testTag = "auto_check_qhtMua"
                    ) {
                        draft = draft.copy(qhtMua = it)
                    }
                    AutoCheckRow(
                        label = "Mua Thẻ Giao Dịch lúc 00:00:00 (200 KNB khóa)",
                        checked = draft.theGiaoDich,
                        testTag = "auto_check_theGiaoDich"
                    ) {
                        draft = draft.copy(theGiaoDich = it)
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = Color(0xFF334155))
                    Spacer(modifier = Modifier.height(6.dp))

                    AutoNumberRow(
                        label = "Đổi KNB → KNB khóa tối đa",
                        value = k2kText,
                        suffix = "KNB / ngày (0 = không đổi)",
                        max = AutoConfig.K2K_MAX,
                        testTag = "auto_num_k2k"
                    ) { k2kText = it }

                    AutoNumberRow(
                        label = "Mua Lệnh Bài Sát Thủ",
                        value = lbMuaText,
                        suffix = "cái / ngày (100 KNB / cái, 0 = không mua)",
                        max = AutoConfig.LB_MUA_MAX,
                        testTag = "auto_num_lbMua"
                    ) { lbMuaText = it }

                    AutoNumberRow(
                        label = "Mua Kim Nguyên Bảo ở Kỳ Trân Các",
                        value = knbMuaText,
                        suffix = "KNB / ngày (đồng bộ 00:05 & quét 10p, tối đa 1000)",
                        max = AutoConfig.KNB_MUA_MAX,
                        testTag = "auto_num_knbMua"
                    ) { knbMuaText = it }

                    AutoNumberRow(
                        label = "Giữ lại ít nhất",
                        value = knbGiuText,
                        suffix = "lượng",
                        max = AutoConfig.KNB_GIU_MAX,
                        testTag = "auto_num_knbGiu"
                    ) { knbGiuText = it }

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(5.dp),
                        color = Color(0xFF0F172A),
                        border = BorderStroke(1.dp, Color(0xFF7F1D1D))
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "Thứ tự hoạt động & Thiên Bảo Khố",
                                color = Color(0xFFFCA5A5),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "• Thứ tự khi tích đủ: Dã Tẩu → Phó Bản → Thiên Bảo Khố (60p/ngày; 120p nếu mua Tín vật) → Luyện Công (chọn map).\n• Trong Thiên Bảo Khố: Xa em ra = bật chạy vòng tròn né đòn; Bình thường = auto ingame. Khi cần đổi chế độ sớm sẽ tự bấm Rời → Bấm lại để rời.",
                                color = Color(0xFFCBD5E1),
                                fontSize = 9.sp,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                            Text(
                                text = "Quỹ đạo chạy vòng tròn chỉ hoạt động trong Thiên Bảo Khố (bước 20° quanh mục tiêu: bán kính 90 cho chiêu Đánh gần, 400 cho chiêu Đánh xa; vượt tầng lập tức chạy về tâm map).",
                                color = Color(0xFF94A3B8),
                                fontSize = 9.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                statusNotice?.let { msg ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = msg,
                        color = Color(0xFF6EE7B7),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auto_save_notice")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            onSave(tabIndex, buildDraftConfig())
                            statusNotice = if (draft.on) {
                                "✅ Đã lưu cài đặt cho Tab ${tabIndex + 1}"
                            } else {
                                "✅ Đã lưu cài đặt Tab ${tabIndex + 1} (gạt công tắc Bật tự động để chạy)"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("save_auto_config_button")
                    ) {
                        Text(
                            text = "Lưu Tab ${tabIndex + 1}",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    Button(
                        onClick = {
                            onSaveAll(buildDraftConfig())
                            statusNotice = "✅ Đã lưu cài đặt cho tất cả $tabCount tab (chỉ chạy tab đã bật công tắc)"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("save_all_auto_config_button")
                    ) {
                        Text(
                            text = "Lưu tất cả",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { onResetCounters(tabIndex) },
                        border = BorderStroke(1.dp, Color(0xFF7F1D1D)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFCA5A5)),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("auto_reset_counters_button")
                    ) {
                        Text("Đặt lại bộ đếm", fontSize = 10.sp)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, Color(0xFF475569)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCBD5E1)),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("auto_cancel_button")
                    ) {
                        Text("Đóng", fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AfterDailyActionSelector(
    enabled: Boolean,
    afterDailyFarm: Boolean,
    farmMap: String,
    afterDailyThap: Boolean,
    thapAfterMode: String,
    onToggleFarm: (Boolean) -> Unit,
    onSelectMap: (String) -> Unit,
    onToggleThap: (Boolean) -> Unit,
    onSelectThapMode: (String) -> Unit
) {
    var mapExpanded by remember { mutableStateOf(false) }
    val normalizedThapMode = AutoConfig.normalizeThapAfterMode(thapAfterMode)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 2.dp, bottom = 6.dp)
            .testTag("auto_after_daily_section"),
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF0F172A),
        border = BorderStroke(1.dp, if (enabled) Color(0xFF10B981) else Color(0xFF334155))
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                text = if (enabled) {
                    "Xong Dã Tẩu, Phó Bản & Thiên Bảo Khố sẽ tự chuyển:"
                } else {
                    "Tích dòng trên để chọn Luyện Công hoặc Leo Tháp sau khi hết hoạt động:"
                },
                color = if (enabled) Color(0xFF6EE7B7) else Color(0xFF94A3B8),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Hàng 1: Luyện Công + Chọn map
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clickable { onToggleFarm(!afterDailyFarm) }
                        .testTag("auto_check_afterDailyFarm"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = afterDailyFarm,
                        onCheckedChange = onToggleFarm,
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF10B981),
                            uncheckedColor = Color(0xFF64748B)
                        )
                    )
                    Text(
                        text = "Luyện Công",
                        color = if (afterDailyFarm) Color.White else Color(0xFFCBD5E1),
                        fontSize = 10.sp,
                        fontWeight = if (afterDailyFarm) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    val boxShape = RoundedCornerShape(6.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 40.dp)
                            .clip(boxShape)
                            .background(Color(0xFF1E293B))
                            .border(
                                1.dp,
                                if (afterDailyFarm && farmMap.isNotBlank()) Color(0xFF10B981) else Color(0xFF475569),
                                boxShape
                            )
                            .clickable { mapExpanded = true }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("auto_farm_map_dropdown"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = normalize130MapName(farmMap),
                            color = if (farmMap.isNotBlank()) Color.White else Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = "Chọn map",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = mapExpanded,
                        onDismissRequest = { mapExpanded = false },
                        modifier = Modifier
                            .background(Color(0xFF0F172A))
                            .heightIn(max = 320.dp)
                    ) {
                        listOf("Mạc Bắc Thảo Nguyên", "Vi sơn đảo").forEach { mName ->
                            val isChecked = normalize130MapName(farmMap) == mName
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (isChecked) "✓ $mName" else mName,
                                        color = if (isChecked) Color(0xFF6EE7B7) else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    mapExpanded = false
                                    onSelectMap(mName)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Hàng 2: Leo Tháp + Xa em ra / Bình thường
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clickable { onToggleThap(!afterDailyThap) }
                        .testTag("auto_check_afterDailyThap"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = afterDailyThap,
                        onCheckedChange = onToggleThap,
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF10B981),
                            uncheckedColor = Color(0xFF64748B)
                        )
                    )
                    Text(
                        text = "Leo Tháp",
                        color = if (afterDailyThap) Color.White else Color(0xFFCBD5E1),
                        fontSize = 10.sp,
                        fontWeight = if (afterDailyThap) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )
                }

                val modes = listOf(
                    Triple(AutoConfig.THAP_MODE_ORBIT, "Xa em ra", "auto_thap_mode_orbit"),
                    Triple(AutoConfig.THAP_MODE_NORMAL, "Bình thường", "auto_thap_mode_normal")
                )
                modes.forEach { (modeKey, modeLabel, tag) ->
                    val isSel = afterDailyThap && normalizedThapMode == modeKey
                    val shape = RoundedCornerShape(6.dp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 36.dp)
                            .clip(shape)
                            .background(if (isSel) Color(0xFF065F46) else Color(0xFF1E293B))
                            .border(1.dp, if (isSel) Color(0xFF10B981) else Color(0xFF475569), shape)
                            .clickable { onSelectThapMode(modeKey) }
                            .testTag(tag),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = modeLabel,
                            color = if (isSel) Color.White else Color(0xFF94A3B8),
                            fontSize = 9.5.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
@Composable
private fun AutoCheckRow(
    label: String,
    checked: Boolean,
    testTag: String? = null,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clickable { onChange(!checked) }
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChange,
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFF10B981),
                uncheckedColor = Color(0xFF64748B)
            )
        )
        Text(
            text = label,
            color = Color(0xFFCBD5E1),
            fontSize = 10.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MainSkill9xSelector(
    sectRaw: String,
    selectedSkillId: String,
    resolvedStyle: String?,
    onSelectSkill: (String) -> Unit
) {
    var sectExpanded by remember { mutableStateOf(false) }
    var skillExpanded by remember { mutableStateOf(false) }
    val allSects = remember {
        AutoConfig.ALL_9X_SKILLS.map { it.sectKey to it.sectLabel }.distinct()
    }
    val detectedSectKey = AutoConfig.normalizeSectKey(sectRaw)
    val selectedSkill = AutoConfig.findMainSkill(selectedSkillId)
    var manualSectKey by remember(sectRaw) { mutableStateOf<String?>(null) }
    val effectiveSectKey = manualSectKey ?: selectedSkill?.sectKey ?: detectedSectKey
    val effectiveSectLabel = allSects.firstOrNull { it.first == effectiveSectKey }?.second
    val availableSkills = if (effectiveSectKey != null) {
        AutoConfig.ALL_9X_SKILLS.filter { it.sectKey == effectiveSectKey }
    } else {
        AutoConfig.ALL_9X_SKILLS
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 4.dp, bottom = 6.dp)
    ) {
        Text(
            text = if (effectiveSectLabel != null) {
                "Cấu hình Leo Tháp • Môn phái: $effectiveSectLabel"
            } else {
                "Cấu hình Leo Tháp • Môn phái & Chiêu chính (Tự động hoặc chọn tay):"
            },
            color = if (effectiveSectLabel != null) Color(0xFF38BDF8) else Color(0xFFFBBF24),
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .padding(bottom = 4.dp)
                .testTag("auto_detected_sect_label")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Dropdown chọn Môn phái (tùy chọn lọc tay)
            Box(modifier = Modifier.weight(0.95f)) {
                val boxShape = RoundedCornerShape(6.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .clip(boxShape)
                        .background(Color(0xFF0F172A))
                        .border(1.dp, if (effectiveSectLabel != null) Color(0xFF38BDF8) else Color(0xFF475569), boxShape)
                        .clickable { sectExpanded = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("auto_manual_sect_dropdown"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = effectiveSectLabel ?: "Phái: Tự động",
                        color = if (effectiveSectLabel != null) Color.White else Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "Chọn môn phái",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = sectExpanded,
                    onDismissRequest = { sectExpanded = false },
                    modifier = Modifier
                        .background(Color(0xFF0F172A))
                        .heightIn(max = 320.dp)
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "🤖 Tự động nhận diện phái",
                                color = Color(0xFF6EE7B7),
                                fontSize = 11.sp
                            )
                        },
                        onClick = {
                            sectExpanded = false
                            manualSectKey = null
                            onSelectSkill("")
                        }
                    )
                    allSects.forEach { (sKey, sLabel) ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = sLabel,
                                    color = if (effectiveSectKey == sKey) Color(0xFF38BDF8) else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = if (effectiveSectKey == sKey) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                sectExpanded = false
                                manualSectKey = sKey
                                if (selectedSkill != null && selectedSkill.sectKey != sKey) {
                                    onSelectSkill("")
                                }
                            }
                        )
                    }
                }
            }

            // Dropdown chọn Chiêu chính 9x
            Box(modifier = Modifier.weight(1.35f)) {
                val boxShape = RoundedCornerShape(6.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .clip(boxShape)
                        .background(Color(0xFF0F172A))
                        .border(
                            1.dp,
                            if (selectedSkill != null) Color(0xFF10B981) else Color(0xFF475569),
                            boxShape
                        )
                        .clickable { skillExpanded = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("auto_main_skill_dropdown"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = selectedSkill?.displayName ?: "Chiêu: Tự động",
                        color = if (selectedSkill != null) Color.White else Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (selectedSkill != null) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "Chọn chiêu chính 9x",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = skillExpanded,
                    onDismissRequest = { skillExpanded = false },
                    modifier = Modifier
                        .background(Color(0xFF0F172A))
                        .heightIn(max = 320.dp)
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "🤖 Tự động nhận diện chiêu",
                                color = Color(0xFF6EE7B7),
                                fontSize = 11.sp
                            )
                        },
                        modifier = Modifier.testTag("auto_main_skill_clear"),
                        onClick = {
                            skillExpanded = false
                            onSelectSkill("")
                        }
                    )
                    availableSkills.forEach { skill ->
                        val styleText = if (skill.combatStyle == AutoConfig.ORBIT_MELEE) "Gần 90" else "Xa 400"
                        val label = if (effectiveSectKey != null) {
                            "${skill.displayName} · $styleText"
                        } else {
                            "[${skill.sectLabel}] ${skill.displayName} · $styleText"
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = label,
                                    color = if (selectedSkill?.id == skill.id) Color(0xFF6EE7B7) else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedSkill?.id == skill.id) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.testTag("auto_main_skill_option_${skill.id}"),
                            onClick = {
                                skillExpanded = false
                                manualSectKey = skill.sectKey
                                onSelectSkill(skill.id)
                            }
                        )
                    }
                }
            }
        }

        val resolvedText = when (resolvedStyle) {
            AutoConfig.ORBIT_MELEE -> "Tầm đánh: Đánh gần (giữ cự ly 90, bước 20°)"
            AutoConfig.ORBIT_RANGED -> "Tầm đánh: Đánh xa (giữ cự ly 400, bước 20°)"
            else -> "Tầm đánh: Tự động nhận diện theo Môn phái & Chiêu chính khi vào Tháp"
        }
        Text(
            text = resolvedText,
            color = Color(0xFF6EE7B7),
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .padding(top = 4.dp)
                .testTag("auto_orbit_resolved_label")
        )
    }
}
@Composable
private fun OrbitStyleSelector(
    selectedStyle: String,
    isOrbitEnabled: Boolean,
    onSelect: (String) -> Unit
) {
    val normalized = AutoConfig.normalizeOrbitStyle(selectedStyle)
    val options = listOf(
        Triple(AutoConfig.ORBIT_AUTO, "Tự động", "auto_orbit_style_auto"),
        Triple(AutoConfig.ORBIT_MELEE, "Đánh gần (90)", "auto_orbit_style_melee"),
        Triple(AutoConfig.ORBIT_RANGED, "Đánh xa (400)", "auto_orbit_style_ranged")
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 4.dp, bottom = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { (key, label, tag) ->
                val isSelected = normalized == key
                val shape = RoundedCornerShape(6.dp)
                val bgColor = when {
                    isSelected && isOrbitEnabled -> Color(0xFF065F46)
                    isSelected -> Color(0xFF1E3A5F)
                    else -> Color(0xFF0F172A)
                }
                val borderColor = when {
                    isSelected && isOrbitEnabled -> Color(0xFF10B981)
                    isSelected -> Color(0xFF38BDF8)
                    else -> Color(0xFF334155)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(shape)
                        .background(bgColor)
                        .border(1.dp, borderColor, shape)
                        .clickable { onSelect(key) }
                        .testTag(tag),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
        Text(
            text = "Chọn chiêu chính 9x ở trên để giữ khoảng cách 90 / 400 quanh mục tiêu trong Tháp",
            color = Color(0xFF64748B),
            fontSize = 8.5.sp,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

@Composable
private fun AutoNumberRow(
    label: String,
    value: String,
    suffix: String,
    max: Int,
    testTag: String,
    onChange: (String) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(text = label, color = Color(0xFFCBD5E1), fontSize = 10.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = value,
                onValueChange = { raw ->
                    val digits = raw.filter { it.isDigit() }
                    onChange(if (digits.isEmpty()) "" else digits.take(9))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0xFF475569),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                modifier = Modifier
                    .width(96.dp)
                    .height(52.dp)
                    .testTag(testTag)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = suffix,
                color = Color(0xFF94A3B8),
                fontSize = 9.sp,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            text = "tối đa $max",
            color = Color(0xFF64748B),
            fontSize = 8.sp
        )
    }
}

private fun parseClamped(text: String, max: Int): Int {
    val v = text.toIntOrNull() ?: 0
    return if (v < 0) 0 else if (v > max) max else v
}
