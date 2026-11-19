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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.webview.AutoConfig

/**
 * Hộp thoại 🤖 Tự động tách riêng cho từng tab — mỗi tab có cấu hình riêng, lưu riêng
 * trong PreferencesManager và thực thi độc lập trên chính WebView của tab đó.
 */
@Composable
fun AutoConfigDialog(
    tabIndex: Int,
    tabCount: Int,
    configs: List<AutoConfig>,
    characterName: String = "",
    characterSects: List<String> = emptyList(),
    onSelectTab: (Int) -> Unit,
    onSave: (Int, AutoConfig) -> Unit,
    onSaveAll: (AutoConfig) -> Unit = {},
    onResetCounters: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val currentConfig = configs.getOrElse(tabIndex) { AutoConfig() }
    var draft by remember(tabIndex, currentConfig) { mutableStateOf(currentConfig) }
    val currentSectRaw = characterSects.getOrElse(tabIndex) { "" }

    // Ô số giữ dạng chuỗi để người dùng xoá trắng được khi đang gõ; đổi sang số lúc Lưu.
    var k2kText by remember(tabIndex, currentConfig) { mutableStateOf(currentConfig.k2k.toString()) }
    var lbMuaText by remember(tabIndex, currentConfig) { mutableStateOf(currentConfig.lbMua.toString()) }
    var knbMuaText by remember(tabIndex, currentConfig) { mutableStateOf(currentConfig.knbMua.toString()) }
    var knbGiuText by remember(tabIndex, currentConfig) { mutableStateOf(currentConfig.knbGiu.toString()) }

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
                        .heightIn(max = 380.dp)
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
                                        text = "Tab ${i + 1}",
                                        color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Công tắc tổng riêng cho tab đang chọn.
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
                                        "🟢 Đang bật riêng cho Tab ${tabIndex + 1}"
                                    } else {
                                        "⚪ Đang tắt (Tab ${tabIndex + 1})"
                                    },
                                    color = if (draft.on) Color(0xFF6EE7B7) else Color(0xFF94A3B8),
                                    fontSize = 9.sp
                                )
                            }
                            Switch(
                                checked = draft.on,
                                onCheckedChange = { draft = draft.copy(on = it) },
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
                    AutoCheckRow("Dã Tẩu tối đa: bật Trợ thủ, Chế độ Nhiệm Vụ khi còn lượt", draft.dt) {
                        draft = draft.copy(dt = it)
                    }
                    AutoCheckRow("Mua Lệnh Bài Dã Tẩu bằng KNB khóa (20 / cái)", draft.ldtMua) {
                        draft = draft.copy(ldtMua = it)
                    }
                    AutoCheckRow("Bình Phúc Duyên Lộ + Rương Phúc Duyên (lấy Lệnh Bài Dã Tẩu)", draft.pd) {
                        draft = draft.copy(pd = it)
                    }
                    AutoCheckRow("Sát Thủ: tự gọi khi có Lệnh Bài", draft.st) {
                        draft = draft.copy(st = it)
                    }
                    AutoCheckRow("Tự chạy phó bản: Chế độ Phó Bản tới khi hết lượt, rồi Dã Tẩu, rồi về chế độ cũ", draft.pb) {
                        draft = draft.copy(pb = it)
                    }
                    AutoCheckRow("Ưu tiên Leo Tháp khi hết lượt Phó Bản / Dã Tẩu", draft.thap) {
                        draft = draft.copy(thap = it)
                    }
                    AutoCheckRow(
                        label = "Leo Tháp: chạy vòng tròn quanh mục tiêu né sát thương",
                        checked = draft.thapNeDon,
                        testTag = "auto_check_thapNeDon"
                    ) {
                        draft = draft.copy(thapNeDon = it)
                    }
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
                        isOrbitEnabled = draft.thapNeDon,
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
                        label = "Mua Thẻ Giao Dịch lúc 00:00:00 (200 KNB khóa, ưu tiên trước khi mua KNB)",
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
                        suffix = "KNB / ngày (tối đa 1000 KNB / ngày, 0 = không mua)",
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
                                text = "Lưu ý tiền tệ & Tự động riêng từng Tab",
                                color = Color(0xFFFCA5A5),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Kim Nguyên Bảo mua bằng vạn lượng: tự mở Kỳ Trân Các → Bảng Kim Nguyên Bảo → Mua, " +
                                    "điền số lượng (tối đa 1.000 KNB / ngày) rồi ấn Đổi. \"Giữ lại ít nhất\" là SÀN ngân lượng bảo toàn.",
                                color = Color(0xFFCBD5E1),
                                fontSize = 9.sp,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                            Text(
                                text = "Chuẩn giờ Việt Nam (GMT+7): Khi bật Mua Thẻ Giao Dịch, lúc 00:00:00 sẽ ưu tiên mua ẩn trước " +
                                    "(Kỳ Trân Các → Bảng KNB khóa → Giao dịch → Thẻ Giao Dịch → Mua → 1 → Mua); nếu chưa xong thì chưa mua KNB bằng tiền vạn. " +
                                    "Sau đó thực hiện Điểm danh / Đổi nguyên liệu / Mua KNB, rồi Tự chạy phó bản ➔ Dã Tẩu tối đa và khôi phục chế độ ban đầu. " +
                                    "Trong Leo Tháp, hệ thống lấy tọa độ trung tâm map làm điểm tập trung (giới hạn bán kính quanh trung tâm, không chạy xa mục tiêu), " +
                                    "đọc chiêu chính 9x đã chọn để đi vòng Đánh gần / Đánh xa theo nhịp và tự giữ Auto game luôn BẬT để nhân vật liên tục tung chiêu. " +
                                    "Vật phẩm tiêu hao quét vùng buff mỗi 15 phút và tự dùng ẩn 1 bình mỗi loại còn thiếu.",
                                color = Color(0xFF94A3B8),
                                fontSize = 9.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Hàng 1: Lưu Tab x | Lưu tất cả
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { onSave(tabIndex, buildDraftConfig()) },
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
                        onClick = { onSaveAll(buildDraftConfig()) },
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

                // Hàng 2: Đặt lại bộ đếm | Hủy
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
                        Text("Hủy", fontSize = 10.sp)
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
    var expanded by remember { mutableStateOf(false) }
    val detectedSect = AutoConfig.sectDisplayLabel(sectRaw)
    val sectSkills = AutoConfig.skills9xForSect(sectRaw)
    val availableSkills = if (sectSkills.isNotEmpty()) sectSkills else AutoConfig.ALL_9X_SKILLS
    val selectedSkill = AutoConfig.findMainSkill(selectedSkillId)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 4.dp, bottom = 6.dp)
    ) {
        Text(
            text = if (detectedSect != null) {
                "Môn phái: $detectedSect • Chọn chiêu chính 9x:"
            } else {
                "Chưa đọc được môn phái (vào game để lọc theo phái) • Chọn chiêu 9x:"
            },
            color = if (detectedSect != null) Color(0xFF38BDF8) else Color(0xFFFBBF24),
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .padding(bottom = 4.dp)
                .testTag("auto_detected_sect_label")
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            val boxShape = RoundedCornerShape(6.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(boxShape)
                    .background(Color(0xFF0F172A))
                    .border(
                        1.dp,
                        if (selectedSkill != null) Color(0xFF10B981) else Color(0xFF475569),
                        boxShape
                    )
                    .clickable { expanded = true }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("auto_main_skill_dropdown"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = when {
                        selectedSkill == null -> "-- Chọn chiêu chính 9x --"
                        detectedSect != null && selectedSkill.sectLabel != detectedSect ->
                            "${selectedSkill.displayName} (${selectedSkill.sectLabel})"
                        else -> selectedSkill.displayName
                    },
                    color = if (selectedSkill != null) Color.White else Color(0xFF94A3B8),
                    fontSize = 10.5.sp,
                    fontWeight = if (selectedSkill != null) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = "Chọn chiêu chính 9x",
                    tint = Color(0xFF94A3B8)
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color(0xFF0F172A))
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "-- Chưa chọn chiêu --",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    },
                    modifier = Modifier.testTag("auto_main_skill_clear"),
                    onClick = {
                        expanded = false
                        onSelectSkill("")
                    }
                )
                availableSkills.forEach { skill ->
                    val styleText = if (skill.combatStyle == AutoConfig.ORBIT_MELEE) "Đánh gần" else "Đánh xa"
                    val label = if (detectedSect != null) {
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
                            expanded = false
                            onSelectSkill(skill.id)
                        }
                    )
                }
            }
        }

        val resolvedText = when (resolvedStyle) {
            AutoConfig.ORBIT_MELEE -> "Cách di chuyển cho chiêu đã chọn: Đánh gần (vòng hẹp, theo nhịp)"
            AutoConfig.ORBIT_RANGED -> "Cách di chuyển cho chiêu đã chọn: Đánh xa (vòng rộng, theo nhịp)"
            else -> "Chưa chọn chiêu chính — hãy chọn chiêu 9x để xác định cách di chuyển"
        }
        Text(
            text = resolvedText,
            color = if (resolvedStyle != null) Color(0xFF6EE7B7) else Color(0xFFFCA5A5),
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
        Triple(AutoConfig.ORBIT_AUTO, "Theo chiêu", "auto_orbit_style_auto"),
        Triple(AutoConfig.ORBIT_MELEE, "Đánh gần", "auto_orbit_style_melee"),
        Triple(AutoConfig.ORBIT_RANGED, "Đánh xa", "auto_orbit_style_ranged")
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
            text = "Chọn chiêu chính 9x ở trên để app xác định cách di chuyển chậm theo nhịp",
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
