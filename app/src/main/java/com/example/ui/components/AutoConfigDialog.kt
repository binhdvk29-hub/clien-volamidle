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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.webview.AutoConfig

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
                    AutoCheckRow(
                        label = "Ưu tiên hành động khi hết lượt Dã Tẩu, Phó Bản",
                        checked = draft.afterDailyEnabled,
                        testTag = "auto_check_afterDailyEnabled"
                    ) { enabled ->
                        val hasSub = draft.afterDailyFarm || draft.thap
                        draft = draft.copy(
                            afterDailyEnabled = enabled,
                            afterDailyFarm = if (enabled && !hasSub) true else draft.afterDailyFarm
                        )
                    }
                    AfterDailyActionSelector(
                        enabled = draft.afterDailyEnabled,
                        afterDailyFarm = draft.afterDailyFarm,
                        farmMap = draft.farmMap,
                        afterDailyThap = draft.thap,
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
                                afterDailyEnabled = if (mapName.isNotBlank()) true else draft.afterDailyEnabled,
                                afterDailyFarm = if (mapName.isNotBlank()) true else draft.afterDailyFarm,
                                thap = if (mapName.isNotBlank()) false else draft.thap,
                                farmMap = mapName
                            )
                        },
                        onToggleThap = { checked ->
                            draft = draft.copy(
                                afterDailyEnabled = if (checked) true else draft.afterDailyEnabled,
                                thap = checked,
                                afterDailyFarm = if (checked) false else draft.afterDailyFarm
                            )
                        },
                        onSelectThapMode = { mode ->
                            draft = draft.copy(
                                afterDailyEnabled = true,
                                thap = true,
                                afterDailyFarm = false,
                                thapAfterMode = mode
                            )
                        }
                    )
                    AutoCheckRow(
                        label = "Chỉ Leo Tháp",
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
                                text = "Lưu ý tiền tệ & Chế độ Chỉ Leo Tháp",
                                color = Color(0xFFFCA5A5),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "• Ưu tiên hành động khi hết lượt: chọn Luyện Công + map để tự về map đã chọn, hoặc chọn Leo Tháp (Xa em ra = bật né đòn vòng tròn; Bình thường = chỉ auto ingame).\n• Chỉ Leo Tháp: dù đang làm gì cũng lập tức chuyển sang Chế độ: Leo Tháp (tự vào lại khi Rớt Tháp / mất kết nối).",
                                color = Color(0xFFCBD5E1),
                                fontSize = 9.sp,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                            Text(
                                text = "Quỹ đạo chạy vòng tròn chỉ hoạt động trong Thông Thiên Tháp (bước 20° quanh mục tiêu: bán kính 90 cho chiêu Đánh gần, 400 cho chiêu Đánh xa; vượt tầng lập tức chạy về tâm map).",
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
                    "Quét nhiệm vụ & Phó Bản còn lại — xong hết sẽ chạy mục đã chọn:"
                } else {
                    "Tích dòng trên để bật tự quét và chuyển sang hành động dưới:"
                },
                color = if (enabled) Color(0xFF6EE7B7) else Color(0xFF94A3B8),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Hàng 1: Luyện Công + Chọn map (2 ô cùng 1 hàng)
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
                            text = farmMap.ifBlank { "Mạc Bắc Thảo Nguyên" },
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
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "-- Chưa chọn map --",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.5.sp
                                )
                            },
                            onClick = {
                                mapExpanded = false
                                onSelectMap("Mạc Bắc Thảo Nguyên" )
                            }
                        )
                        AutoConfig.ALL_TRAINING_MAPS.filter { it.name == "M?c B?c Th?o Nguy�n" || it.name == "Vi son d?o" }.forEach { mapOpt -> val isSel = farmMap == mapOpt.name; DropdownMenuItem(text = { Text(text = "[${mapOpt.levelRange}] ${mapOpt.name}", color = if (isSel) Color(0xFF6EE7B7) else Color.White, fontSize = 10.5.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) }, onClick = { mapExpanded = false; onSelectMap(mapOpt.name) }) } }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Hàng 2: Leo Tháp + Xa em ra + Bình thường
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

                val thapModes = listOf(
                    Triple(AutoConfig.THAP_MODE_ORBIT, "Xa em ra", "auto_thap_mode_orbit"),
                    Triple(AutoConfig.THAP_MODE_NORMAL, "Bình thường", "auto_thap_mode_normal")
                )
                thapModes.forEach { (modeKey, modeLabel, tag) ->
                    val isSel = afterDailyThap && normalizedThapMode == modeKey
                    val shape = RoundedCornerShape(6.dp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 38.dp)
                            .clip(shape)
                            .background(if (isSel) Color(0xFF065F46) else Color(0xFF1E293B))
                            .border(
                                1.dp,
                                if (isSel) Color(0xFF10B981) else Color(0xFF475569),
                                shape
                            )
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
                    val styleText = if (skill.combatStyle == AutoConfig.ORBIT_MELEE) "Đánh gần (90)" else "Đánh xa (400)"
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
            AutoConfig.ORBIT_MELEE -> "Cách di chuyển: Đánh gần (giữ khoảng cách 90, bước 20°)"
            AutoConfig.ORBIT_RANGED -> "Cách di chuyển: Đánh xa (giữ khoảng cách 400, bước 20°)"
            else -> "Chưa chọn chiêu chính — hãy chọn chiêu 9x để xác định khoảng cách"
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
