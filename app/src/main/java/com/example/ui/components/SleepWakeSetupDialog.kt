package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

@Composable
fun SleepWakeSetupDialog(
    hasDndPolicyAccess: Boolean,
    isAlreadyInSleepMode: Boolean,
    onOpenDndSettings: () -> Unit,
    onOpenDisplaySettings: () -> Unit,
    onConfirmSleepMode: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        title = {
            Text(
                text = stringResource(R.string.sleep_wake_dialog_title),
                color = Color(0xFFE2E8F0),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = if (hasDndPolicyAccess) Color(0xFF064E3B) else Color(0xFF451A03),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(
                            if (hasDndPolicyAccess) {
                                R.string.sleep_wake_status_dnd_granted
                            } else {
                                R.string.sleep_wake_status_dnd_missing
                            }
                        ),
                        color = if (hasDndPolicyAccess) Color(0xFFA7F3D0) else Color(0xFFFDE68A),
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier
                            .padding(10.dp)
                            .testTag("sleep_wake_dnd_status_text")
                    )
                }

                Text(
                    text = stringResource(R.string.sleep_wake_lift_note),
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp,
                    lineHeight = 15.5.sp
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOpenDndSettings,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hasDndPolicyAccess) Color(0xFF1E293B) else Color(0xFFD97706),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sleep_wake_dnd_settings_btn")
                    ) {
                        Text(
                            text = stringResource(
                                if (hasDndPolicyAccess) {
                                    R.string.sleep_wake_btn_dnd_settings
                                } else {
                                    R.string.sleep_wake_btn_grant_dnd
                                }
                            ),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenDisplaySettings,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sleep_wake_display_settings_btn")
                    ) {
                        Text(
                            text = stringResource(R.string.sleep_wake_btn_display_settings),
                            color = Color(0xFF93C5FD),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!isAlreadyInSleepMode) {
                Button(
                    onClick = onConfirmSleepMode,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4F46E5),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("sleep_wake_enter_sleep_btn")
                ) {
                    Text(
                        text = stringResource(R.string.sleep_wake_btn_enter_sleep),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("sleep_wake_close_btn")
                ) {
                    Text(
                        text = stringResource(R.string.sleep_wake_btn_close),
                        fontSize = 11.5.sp
                    )
                }
            }
        },
        dismissButton = {
            if (!isAlreadyInSleepMode) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("sleep_wake_close_btn")
                ) {
                    Text(
                        text = stringResource(R.string.sleep_wake_btn_close),
                        fontSize = 11.5.sp
                    )
                }
            }
        }
    )
}
