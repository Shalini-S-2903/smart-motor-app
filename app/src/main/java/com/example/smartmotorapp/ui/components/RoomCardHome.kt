package com.example.smartmotorapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smartmotorapp.data.remote.RoomDto
import com.example.smartmotorapp.ui.components.reusableComponents.PrimaryCardButton
import com.example.smartmotorapp.ui.theme.AppSubtitleText
import com.example.smartmotorapp.ui.theme.BodyMediumText
import com.example.smartmotorapp.ui.theme.InActiveText
import com.example.smartmotorapp.ui.theme.TextPrimary
import com.example.smartmotorapp.ui.theme.White
import com.example.smartmotorapp.ui.theme.cobaltBlue

@Composable
fun RoomCardHome(
    room: RoomDto,
    isRoomEnabled: Boolean,
    onClick: () -> Unit = {},
    onToggle: (Boolean) -> Unit = {},
    onSchedule: () -> Unit = {},
) {
    Card(
        modifier = Modifier
            .width(300.dp)
            .clickable { onClick() }
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp), // Inner padding
        ) {
            // Card Title (Dark Slate Grey)
            AppSubtitleText(
                text = room.name,
                color = TextPrimary, // Ensure this is Color(0xFF1F2937)
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Subtitle with People/Group Icon (Preserved)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Group,
                    contentDescription = "Devices",
                    tint = InActiveText, // Subtler color for the icon
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                BodyMediumText(
                    text = "${room.deviceCount} Devices",
                    color = InActiveText // Standard label grey Color(0xFF6B7280)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Action Buttons Area
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 1. SCHEDULE (Always Neutral)
                PrimaryCardButton (
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    text = "Schedule",
                    icon = Icons.Rounded.Schedule,
                    onClick = onSchedule
                )

                // 2. GLOBAL TOGGLE (Solid Cobalt when Active)
                val isActive = isRoomEnabled
                PrimaryCardButton (
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    text = if (isActive) "Global OFF" else "Global ON",
                    icon = Icons.Rounded.PowerSettingsNew,
                    // Solid Cobalt when ON, Neutral Grey when OFF
                    containerColor = if (isActive) cobaltBlue else Color(0xFFF3F4F6),
                    contentColor = if (isActive) Color.White else Color(0xFF4B5563),
                    onClick = { onToggle(!isRoomEnabled) }
                )
            }
        }
    }
}