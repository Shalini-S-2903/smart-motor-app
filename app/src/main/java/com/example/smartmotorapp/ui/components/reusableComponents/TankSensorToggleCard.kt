package com.example.smartmotorapp.ui.components.reusableComponents

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartmotorapp.ui.theme.cobaltBlue

@Composable
fun TankSensorToggleCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isTankFull: Boolean,
    autoStartChecked: Boolean,
    onAutoStartChange: (Boolean) -> Unit,
    autoStopChecked: Boolean,
    onAutoStopChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(0.5.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier
                .animateContentSize()
                .padding(16.dp)
        ) {
            // --- TOP ROW: MASTER HARDWARE CONTROL ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.White, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WaterDrop,
                        contentDescription = null,
                        tint = Color(0xFF2563EB), // Sleek Cobalt Blue
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Overhead Tank Sensor", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    Text(text = "Monitors FULL/LOW water levels", fontSize = 11.sp, color = Color(0xFF94A3B8))
                }

                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = cobaltBlue
                    )
                )
            }

            // --- BOTTOM EXPANDED PANEL: TELEMETRY STATUS & CONTROLS ---
            if (checked) {
                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = Color(0xFFE2E8F0), thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(14.dp))

                // Real-time Tank State Banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isTankFull) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(if (isTankFull) Color(0xFF16A34A) else Color(0xFFDC2626), shape = CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isTankFull) "Tank Status: FULL" else "Tank Status: EMPTY",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isTankFull) Color(0xFF15803D) else Color(0xFFB91C1C)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "AUTOMATION MODE CONFIG",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Toggle A: Auto Start
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Auto Start when Empty", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF334155))
                        Text(text = "Turns motor ON automatically when tank runs dry", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }
                    Switch(
                        checked = autoStartChecked,
                        onCheckedChange = onAutoStartChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = cobaltBlue
                        ),
                        modifier = Modifier.graphicsLayer(scaleX = 0.85f, scaleY = 0.85f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Toggle B: Auto Stop
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Auto Stop when Full", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF334155))
                        Text(text = "Turns motor OFF automatically when tank is filled", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }
                    Switch(
                        checked = autoStopChecked,
                        onCheckedChange = onAutoStopChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = cobaltBlue
                        ),
                        modifier = Modifier.graphicsLayer(scaleX = 0.85f, scaleY = 0.85f)
                    )
                }
            }
        }
    }
}