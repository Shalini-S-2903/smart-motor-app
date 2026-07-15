package com.example.smartmotorapp.ui.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.smartmotorapp.data.remote.DeviceDto
import com.example.smartmotorapp.ui.home.getRelativeTime
import com.example.smartmotorapp.ui.theme.SmartMotorAppTheme
import com.example.smartmotorapp.ui.theme.TextPrimary
import com.example.smartmotorapp.ui.theme.TextSecondary
import com.example.smartmotorapp.ui.theme.cobaltBlue

@Composable
fun DeviceCardHome(
    device: DeviceDto,
    turningId: String?,
    onTurnOn: () -> Unit,
    onTurnOff: () -> Unit,
    onSchedule: () -> Unit,
    onClick: () -> Unit,
) {
    val isOn = device.lastState.equals("ON", ignoreCase = true)
    val isOnline = device.online
    val amperage = (device.lastPower ?: 0.0).toFloat()

    // Calculate relative time: "2 mins ago" instead of "10:00"
    val relativeTime = getRelativeTime(device.lastSeen ?: "")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // GAUGE SECTION
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(100.dp)
            ) {
                Box(modifier = Modifier.size(80.dp)) {
                    SimpleSpeedometer(
                        currentValue = amperage,
                        maxValue = 20f,
                        accentColor = cobaltBlue
                    )
                }
                Text(
                    text = "${amperage} A",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // INFO SECTION
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = device.id,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )

                    // The Status Indicator
                    StatusIndicator(isOnline = isOnline)
                }

                // Updated Relative Time Display
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = TextSecondary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Updated $relativeTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = if (isOn) onTurnOff else onTurnOn,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isOn) cobaltBlue else Color(0xFFF3F4F6),
                            contentColor = if (isOn) Color.White else TextPrimary
                        )
                    ) {
                        Text(if (isOn) "STOP" else "START", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onSchedule,
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null)
                    }
                }
            }
        }
    }
}
@Preview(showBackground = true)
@Composable
fun DeviceCardPreview() {
    val mockDevice = DeviceDto(
        id = "Main Motor 01",
        lastState = "ON",
        lastPower = 12.5,
        online = true,
        lastSeen = "2026-05-12T10:00:00Z",
        description = "chumma"
    )
    // Wrap it in your Theme to ensure colors/typography exist
    SmartMotorAppTheme {
        DeviceCardHome(
            device = mockDevice,
            turningId = null,
            onTurnOn = {},
            onTurnOff = {},
            onSchedule = {},
            onClick = {}
        )
    }
}