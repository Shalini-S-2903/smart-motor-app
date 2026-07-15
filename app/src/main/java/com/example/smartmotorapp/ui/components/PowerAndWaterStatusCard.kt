package com.example.smartmotorapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PhaseConnectionStatusCard(
    phaseR: Boolean,
    phaseY: Boolean,
    phaseB: Boolean,
    modifier: Modifier = Modifier
) {
    val borderColor = Color(0xFFE2E8F0)
    val textSecondary = Color(0xFF64748B)

    // Determine overall system health based on the RYB lines
    val isAllHealthy = phaseR && phaseY && phaseB

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(0.5.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ==========================================
            // LEFT SIDE: THREE-PHASE POWER (R, Y, B)
            // ==========================================
            // Changed from weight() to wrapContentWidth() so it takes only what it needs
            Column(
                modifier = Modifier.wrapContentWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Phase Line Connection",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = textSecondary
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PhaseIndicatorDot(label = "R", isActive = phaseR, activeColor = Color(0xFFEF4444))
                    PhaseIndicatorDot(label = "Y", isActive = phaseY, activeColor = Color(0xFFFBBF24))
                    PhaseIndicatorDot(label = "B", isActive = phaseB, activeColor = Color(0xFF2563EB))
                }
            }

            // Sleek Vertical Divider - Added explicit end padding to push the badge cleanly
            Box(
                modifier = Modifier
                    .padding(start = 12.dp, end = 12.dp)
                    .height(40.dp)
                    .width(1.dp)
                    .background(borderColor)
            )

            // ==========================================
            // RIGHT SIDE: SYSTEM HEALTH STATUS BADGE
            // ==========================================
            // Takes up all remaining space, eliminating the text-wrapping pressure
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (isAllHealthy) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color(0xFF10B981).copy(alpha = 0.12f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "ⓘ ALL PHASE HEALTHY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF059669),
                            maxLines = 1
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color(0xFFF97316).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "▲ SYSTEM ERRORS DETECTED",
                            fontSize = 11.sp, // Restored back to standard 11.sp cleanly
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFEA580C),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

