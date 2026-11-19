package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CharacterStats
import com.example.model.TabLifecycleState

@Composable
fun SidebarView(
    isMasterAutoRunning: Boolean,
    tabCount: Int,
    characterStatsList: List<CharacterStats>,
    selectedTabIndex: Int,
    onSelectTab: (Int) -> Unit,
    onLoginAll: () -> Unit,
    onStopAll: () -> Unit,
    onEditAccount: (Int) -> Unit,
    onToggleSidebar: () -> Unit,
    modifier: Modifier = Modifier.width(265.dp),
    getAccount: ((Int) -> com.example.model.AccountConfig)? = null,
    isBlackScreenSynced: Boolean = false,
    onSyncBlackScreen: (Boolean) -> Unit = {},
    isHideMapSynced: Boolean = false,
    onSyncHideMap: (Boolean) -> Unit = {},
    isHideGameSynced: Boolean = false,
    onSyncHideGame: (Boolean) -> Unit = {},
    isPcMode: Boolean = false,
    isLandscape: Boolean = false,
    onTogglePcMode: () -> Unit = {},
    onSyncSellExcess: () -> Unit = {}
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .testTag("control_sidebar"),
        color = Color(0xFF131924),
        border = BorderStroke(1.dp, Color(0xFF222F42))
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BẢNG ĐIỀU KHIỂN",
                    color = Color(0xFF93C5FD),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                IconButton(
                    onClick = onToggleSidebar,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("toggle_sidebar_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Ẩn bảng",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("master_control_row"),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onLoginAll,
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .testTag("master_login_all_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF15803D),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Đăng nhập",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "ĐĂNG NHẬP HẾT",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                Button(
                    onClick = onStopAll,
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .testTag("master_stop_all_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFB91C1C),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Dừng",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "DỪNG TẤT CẢ",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "DANH SÁCH NHÂN VẬT ($tabCount TAB)",
                color = Color(0xFF94A3B8),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                for (tabIdx in 0 until tabCount) {
                    val stats = characterStatsList.getOrElse(tabIdx) { CharacterStats(tabIndex = tabIdx) }
                    val isSelected = selectedTabIndex == tabIdx
                    val account = getAccount?.invoke(tabIdx)

                    CharacterCard(
                        tabIndex = tabIdx,
                        stats = stats,
                        account = account,
                        isSelected = isSelected,
                        onSelect = { onSelectTab(tabIdx) },
                        onEditAccount = { onEditAccount(tabIdx) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))

            val activeStats = characterStatsList.getOrElse(selectedTabIndex) { CharacterStats(tabIndex = selectedTabIndex) }
            Text(
                text = "THÔNG SỐ TAB ${selectedTabIndex + 1}: ${activeStats.characterName}",
                color = Color(0xFFFBBF24),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("inventory_quest_panel"),
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StatRow(label = "🧧 Đại hồng bao", value = activeStats.daiHongBao, valueColor = Color(0xFFF87171))
                    StatRow(label = "🧧 Hồng bao", value = activeStats.hongBao, valueColor = Color(0xFFFCA5A5))
                    StatRow(label = "🗼 Kỷ lục tháp", value = activeStats.thapRecord, valueColor = Color(0xFF67E8F9))
                    StatRow(label = "👹 Boss HK hạ", value = activeStats.bossHoangKim, valueColor = Color(0xFFFDE047))
                    StatRow(label = "📜 Dã Tẩu", value = activeStats.daTauQuest, valueColor = Color(0xFF86EFAC))
                    if (activeStats.tasksCompleted.isNotBlank()) {
                        StatRow(label = "✅ Hoàn thành", value = activeStats.tasksCompleted, valueColor = Color(0xFF10B981))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))

            SyncedToggleCheckboxRow(
                label = "Màn hình đen",
                checked = isBlackScreenSynced,
                tabCount = tabCount,
                testTag = "sync_black_screen_checkbox",
                onToggle = { onSyncBlackScreen(!isBlackScreenSynced) }
            )

            Spacer(modifier = Modifier.height(6.dp))

            SyncedToggleCheckboxRow(
                label = "🗺️ Xoá hiển thị bản đồ",
                checked = isHideMapSynced,
                tabCount = tabCount,
                testTag = "sync_hide_map_checkbox",
                onToggle = { onSyncHideMap(!isHideMapSynced) }
            )

            Spacer(modifier = Modifier.height(6.dp))

            SyncedToggleCheckboxRow(
                label = "👁️ Ẩn game",
                checked = isHideGameSynced,
                tabCount = tabCount,
                testTag = "sync_hide_game_checkbox",
                onToggle = { onSyncHideGame(!isHideGameSynced) }
            )

            Spacer(modifier = Modifier.height(6.dp))

            SyncedToggleCheckboxRow(
                label = if (isLandscape) "🖥️ PC (Toàn màn hình 70%)" else "🖥️ PC (Chỉ màn ngang)",
                checked = isLandscape && isPcMode,
                tabCount = tabCount,
                testTag = "sync_pc_mode_checkbox",
                onToggle = { if (isLandscape) onTogglePcMode() }
            )

            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable { onSyncSellExcess() }
                    .testTag("sync_sell_excess_button"),
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(1.dp, Color(0xFF0284C7))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "🧹 Bán đồ thừa",
                        color = Color.White,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "Đồng bộ $tabCount Tab",
                        color = Color(0xFF38BDF8),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun CharacterCard(
    tabIndex: Int,
    stats: CharacterStats,
    account: com.example.model.AccountConfig?,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEditAccount: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clickable { onSelect() }
            .testTag("char_card_tab_$tabIndex"),
        shape = RoundedCornerShape(5.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF1E293B) else Color(0xFF0F172A)
        ),
        border = BorderStroke(
            1.dp,
            if (isSelected) Color(0xFF38BDF8) else Color(0xFF1E293B)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 7.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val dotColor = when (stats.state) {
                        TabLifecycleState.IN_GAME -> Color(0xFF10B981)
                        TabLifecycleState.LOGGING_IN -> Color(0xFFF59E0B)
                        TabLifecycleState.IDLE -> Color(0xFF64748B)
                        TabLifecycleState.OFFLINE -> Color(0xFFEF4444)
                    }

                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Tab ${tabIndex + 1}: ${stats.characterName}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = when (stats.state) {
                        TabLifecycleState.IN_GAME -> "🟢 Online"
                        TabLifecycleState.LOGGING_IN -> "🟡 Đang vào"
                        TabLifecycleState.IDLE -> "⚪ Chờ mở"
                        TabLifecycleState.OFFLINE -> "🔴 Đã dừng"
                    },
                    color = when (stats.state) {
                        TabLifecycleState.IN_GAME -> Color(0xFF34D399)
                        TabLifecycleState.LOGGING_IN -> Color(0xFFFBBF24)
                        TabLifecycleState.IDLE -> Color(0xFF94A3B8)
                        TabLifecycleState.OFFLINE -> Color(0xFFEF4444)
                    },
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }

            Text(
                text = "Phái: ${stats.sect} | Cấp ${stats.level} - ${stats.expPercent}",
                color = Color(0xFFCBD5E1),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stats.lastStatus,
                    color = Color(0xFF64748B),
                    fontSize = 8.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                val accountLabel = if (account != null && account.username.isNotBlank()) "🔑 ${account.username} (Đổi)" else "🔑 TK (Đổi)"
                Surface(
                    modifier = Modifier
                        .clickable { onEditAccount() }
                        .testTag("edit_account_tab_$tabIndex"),
                    shape = RoundedCornerShape(3.dp),
                    color = Color(0xFF334155)
                ) {
                    Text(
                        text = accountLabel,
                        color = Color(0xFFF1F5F9),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncedToggleCheckboxRow(
    label: String,
    checked: Boolean,
    tabCount: Int,
    testTag: String,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable { onToggle() }
            .testTag(testTag),
        shape = RoundedCornerShape(5.dp),
        color = if (checked) Color(0xFF064E3B) else Color(0xFF0F172A),
        border = BorderStroke(
            1.dp,
            if (checked) Color(0xFF10B981) else Color(0xFF334155)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White)
                        .border(
                            width = 1.dp,
                            color = if (checked) Color(0xFF059669) else Color(0xFF64748B),
                            shape = RoundedCornerShape(3.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (checked) {
                        Text(
                            text = "✓",
                            color = Color(0xFF047857),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (checked) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "🟢 Đang bật ($tabCount Tab)",
                    color = Color(0xFF6EE7B7),
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun StatRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFF94A3B8),
            fontSize = 10.sp,
            maxLines = 1
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
