package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OledBlackScreen(
    onUnlock: () -> Unit,
    isNotificationWakePaused: Boolean = false,
    hasDndPolicyAccess: Boolean = false,
    onOpenWakeSetup: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var currentTime by remember {
        mutableStateOf(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()))
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        onUnlock()
                    }
                )
            }
            .testTag("oled_black_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "🌙 CHẾ ĐỘ TREO MÁY (OLED BLACK SCREEN)",
                color = Color(0xFF262626),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = currentTime,
                color = Color(0xFF404040),
                fontSize = 32.sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "⚡ Đang duy trì kết nối game 24/7 (WakeLock kích hoạt)",
                color = Color(0xFF262626),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (isNotificationWakePaused) {
                Text(
                    text = "🔕 Đã tạm dừng tự sáng màn hình do thông báo (tự khôi phục khi mở khóa)",
                    color = Color(0xFF334155),
                    fontSize = 10.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("oled_wake_paused_status")
                )
            } else if (!hasDndPolicyAccess) {
                val pillShape = RoundedCornerShape(6.dp)
                Box(
                    modifier = Modifier
                        .clip(pillShape)
                        .background(Color(0xFF0B0F17))
                        .border(1.dp, Color(0xFF1E293B), pillShape)
                        .clickable { onOpenWakeSetup() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("oled_grant_dnd_button")
                ) {
                    Text(
                        text = "⚙️ Chưa chặn tự sáng thông báo · Chạm để bật",
                        color = Color(0xFF475569),
                        fontSize = 10.5.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "👉 CHẠM ĐÚP 2 LẦN ĐỂ MỞ KHÓA & LÀM SÁNG MÀN HÌNH",
                color = Color(0xFF333333),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}
