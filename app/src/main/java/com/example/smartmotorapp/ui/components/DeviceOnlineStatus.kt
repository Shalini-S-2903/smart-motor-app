package com.example.smartmotorapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DeviceOnlineStatusRow(
    id: String,
    description: String?,
    isOnline: Boolean,
    phaseR: Boolean,
    phaseY: Boolean,
    phaseB: Boolean,
    modifier: Modifier = Modifier
) {
    // Shared color definitions matching your app style guide
    val textPrimary = Color(0xFF0F172A)
    val textSecondary = Color(0xFF64748B)
    val statusGreen = Color(0xFF10B981)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Side: Device Name & Details Location
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = id,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = textPrimary
            )
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = textSecondary
                )
            }
        }

        // Right Side: Combined Hardware Lines + Network State
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // --- MINIMALIST R Y B PHASE MATRIX ---
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiniPhaseIndicatorCard(label = "R", isHealthy = phaseR, activeColor = Color(0xFFEF4444))
                MiniPhaseIndicatorCard(label = "Y", isHealthy = phaseY, activeColor = Color(0xFFFBBF24))
                MiniPhaseIndicatorCard(label = "B", isHealthy = phaseB, activeColor = Color(0xFF2563EB))
            }

            // --- CLOUD NETWORK STATUS BADGE ---
            Box(
                modifier = Modifier
                    .background(
                        color = if (isOnline) statusGreen.copy(alpha = 0.12f) else Color.Red.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (isOnline) "ONLINE" else "OFFLINE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isOnline) statusGreen else Color.Red
                )
            }
        }
    }
}

@Composable
private fun MiniPhaseIndicatorCard(
    label: String,
    isHealthy: Boolean,
    activeColor: Color
) {
    Box(
        modifier = Modifier
            .size(width = 18.dp, height = 18.dp)
            .background(
                color = if (isHealthy) activeColor else Color(0xFFCBD5E1),
                shape = RoundedCornerShape(4.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            // Switches to dark charcoal text only on the bright Yellow background for accessibility
            color = if (isHealthy && label == "Y") Color(0xFF1E293B) else Color.White
        )
    }
}