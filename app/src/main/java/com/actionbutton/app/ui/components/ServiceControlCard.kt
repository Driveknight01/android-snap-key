package com.actionbutton.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.actionbutton.app.model.ActionType
import com.actionbutton.app.model.TriggerType
import com.actionbutton.app.ui.theme.AppleBlue
import com.actionbutton.app.ui.theme.AppleOrange
import com.actionbutton.app.ui.theme.CardBackground
import com.actionbutton.app.ui.theme.CardBorder
import com.actionbutton.app.ui.theme.StatusGreen
import com.actionbutton.app.ui.theme.TextMuted
import com.actionbutton.app.ui.theme.TextPrimary
import com.actionbutton.app.ui.theme.TextSecondary

@Composable
fun ServiceControlCard(
    isServiceEnabled: Boolean,
    onServiceToggle: (Boolean) -> Unit,
    onlyScreenOff: Boolean,
    onOnlyScreenOffToggle: (Boolean) -> Unit,
    hapticEnabled: Boolean,
    onHapticToggle: (Boolean) -> Unit,
    triggerGesture: TriggerType,
    onSelectGestureClick: () -> Unit,
    currentAction: ActionType,
    onTestClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor by animateColorAsState(
        targetValue = if (isServiceEnabled) StatusGreen else TextMuted,
        label = "statusColor"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CardBackground)
            .border(1.dp, CardBorder, RoundedCornerShape(22.dp))
            .padding(18.dp)
    ) {
        // Master switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (isServiceEnabled) "Dịch vụ đang chạy" else "Dịch vụ tạm dừng",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.5.sp,
                    color = TextPrimary
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            )

            Switch(
                checked = isServiceEnabled,
                onCheckedChange = onServiceToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = StatusGreen,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = Color(0xFF2C2C2E)
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Trigger Gesture Selector Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF222226))
                .clickable { onSelectGestureClick() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(AppleBlue.copy(alpha = 0.16f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.TouchApp,
                        contentDescription = null,
                        tint = AppleBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Kiểu kích hoạt phím",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp
                        )
                    )
                    Text(
                        text = triggerGesture.title,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = AppleBlue,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Đổi",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Only when Screen is OFF / AOD Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF222226))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.ScreenLockPortrait,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Chỉ kích hoạt khi tắt màn hình",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.5.sp
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            )

            Switch(
                checked = onlyScreenOff,
                onCheckedChange = onOnlyScreenOffToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AppleOrange,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = Color(0xFF2C2C2E)
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Haptic feedback toggle row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF222226))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Vibration,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Rung phản hồi (Haptic)",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = TextPrimary,
                    fontSize = 13.5.sp
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            )

            Switch(
                checked = hapticEnabled,
                onCheckedChange = onHapticToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = currentAction.accentColor,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = Color(0xFF2C2C2E)
                )
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Test Trigger Button
        Button(
            onClick = onTestClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = currentAction.accentColor.copy(alpha = 0.18f),
                contentColor = currentAction.accentColor
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                currentAction.accentColor.copy(alpha = 0.4f)
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.Bolt,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Bấm thử tác vụ: ${currentAction.title}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            )
        }
    }
}
