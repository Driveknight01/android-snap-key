package com.actionbutton.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.actionbutton.app.model.ActionType
import com.actionbutton.app.ui.theme.TextMuted
import com.actionbutton.app.ui.theme.TextPrimary
import kotlin.math.absoluteValue

@Composable
fun AppleActionWheel(
    pagerState: PagerState,
    actions: List<ActionType>,
    isDndPermissionGranted: Boolean,
    onRequestDndPermission: () -> Unit,
    onActionSelected: (ActionType) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (page in actions.indices) {
                onActionSelected(actions[page])
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 90.dp),
            pageSpacing = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
        ) { page ->
            val action = actions[page]
            val pageOffset = (
                (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            ).absoluteValue

            val scale by animateFloatAsState(
                targetValue = (1f - (pageOffset * 0.22f)).coerceIn(0.78f, 1f),
                animationSpec = tween(durationMillis = 200),
                label = "scale"
            )

            val alpha by animateFloatAsState(
                targetValue = (1f - (pageOffset * 0.55f)).coerceIn(0.35f, 1f),
                animationSpec = tween(durationMillis = 200),
                label = "alpha"
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .scale(scale)
            ) {
                if (pageOffset < 0.8f) {
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        action.accentColor.copy(alpha = 0.25f * (1f - pageOffset)),
                                        Color.Transparent
                                    )
                                )
                            )
                            .blur(20.dp)
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(170.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF23242A).copy(alpha = alpha),
                                    Color(0xFF131317).copy(alpha = alpha)
                                )
                            )
                        )
                        .border(
                            width = if (pageOffset < 0.5f) 2.dp else 1.dp,
                            brush = if (pageOffset < 0.5f) {
                                Brush.sweepGradient(
                                    listOf(
                                        action.accentColor,
                                        Color(0xFF333540),
                                        action.accentColor
                                    )
                                )
                            } else {
                                Brush.linearGradient(listOf(Color(0xFF2C2D35), Color(0xFF1D1E24)))
                            },
                            shape = CircleShape
                        )
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(126.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        action.accentColor.copy(alpha = 0.22f),
                                        Color(0xFF141416)
                                    )
                                )
                            )
                            .border(
                                width = 1.dp,
                                color = action.accentColor.copy(alpha = 0.35f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = action.getIcon(),
                            contentDescription = action.title,
                            tint = if (pageOffset < 0.5f) action.accentColor else TextMuted,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        val currentAction = actions[pagerState.currentPage]
        Text(
            text = currentAction.title,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                color = TextPrimary
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = currentAction.subtitle,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = currentAction.accentColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            ),
            textAlign = TextAlign.Center
        )

        if (currentAction == ActionType.DND && !isDndPermissionGranted) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFEF4444).copy(alpha = 0.16f))
                    .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .clickable { onRequestDndPermission() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Cần cấp quyền Không làm phiền (Chạm để cấp)",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFFEF4444),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            actions.indices.forEach { index ->
                val isSelected = pagerState.currentPage == index
                val width by animateDpAsState(
                    targetValue = if (isSelected) 22.dp else 6.dp,
                    animationSpec = tween(durationMillis = 250),
                    label = "dotWidth"
                )
                val dotColor = if (isSelected) currentAction.accentColor else Color(0xFF2C2C2E)

                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .height(6.dp)
                        .width(width)
                        .clip(RoundedCornerShape(3.dp))
                        .background(dotColor)
                )
            }
        }
    }
}
