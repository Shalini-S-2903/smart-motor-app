package com.example.smartmotorapp.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

@Composable
fun AnimatedWaterTankCard(
    waterLevelStatus: String, // Accepts "FULL", "LOW", or "FILLING"
    modifier: Modifier = Modifier
) {
    val statusClean = waterLevelStatus.uppercase()

    // 1. Map status smoothly to specific height levels inside the glass cylinder
    val targetWaterHeightProgress by animateFloatAsState(
        targetValue = when (statusClean) {
            "FULL" -> 0.80f     // High level
            "LOW" -> 0.20f      // Danger low level
            else -> 0.50f       //  FILLING: Sits perfectly at 50%
        },
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "WaterLevelHeight"
    )

    // 2. Continuous wave animation loop
    val infiniteTransition = rememberInfiniteTransition(label = "WaveMovement")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WaveOffsetShift"
    )

    // Palette Configuration
    val cobaltBlue = Color(0xFF2563EB)
    val tealWater = Color(0xFF0EA5E9)
    val alertRed = Color(0xFFEF4444)
    val alertAmber = Color(0xFFF59E0B)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(90.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFE2E8F0))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ==========================================
            // LEFT: WATER CYLINDER CANVAS
            // ==========================================
            Box(
                modifier = Modifier
                    .width(70.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF1F5F9))
                    .border(1.5.dp, Color(0xFFCBD5E1), RoundedCornerShape(10.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height

                    val waterLevelY = height * (1f - targetWaterHeightProgress)

                    // Match water gradient colors to system state
                    val gradientColors = if (statusClean == "LOW") {
                        listOf(alertAmber, alertRed) // Dangerous low alert
                    } else {
                        listOf(tealWater, cobaltBlue) // Stable healthy blue fluid
                    }

                    clipRect {
                        val wavePath = Path().apply {
                            moveTo(0f, height)
                            lineTo(0f, waterLevelY)

                            val waveLength = width
                            // Make waves slightly faster/choppier when actively filling up!
                            val waveAmplitude = if (statusClean == "FILLING") 8f else 5f

                            for (x in 0..width.toInt()) {
                                val relativeX = x.toFloat() / waveLength
                                val y = waterLevelY + sin(relativeX * 2f * Math.PI.toFloat() + waveOffset) * waveAmplitude
                                lineTo(x.toFloat(), y)
                            }

                            lineTo(width, height)
                            close()
                        }

                        drawPath(
                            path = wavePath,
                            brush = Brush.verticalGradient(colors = gradientColors)
                        )
                    }
                }
            }

            // ==========================================
            // RIGHT: THREE-STATE CONTEXT TEXT INFO
            // ==========================================
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = when (statusClean) {
                        "FULL" -> "TANK STATUS: FULL"
                        "LOW" -> "TANK STATUS: LOW"
                        else -> "TANK STATUS: FILLING"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = when (statusClean) {
                        "FULL" -> Color(0xFF0284C7)
                        "LOW" -> alertRed
                        else -> cobaltBlue // Looks beautiful and matches your icons
                    },
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when (statusClean) {
                        "FULL" -> "Optimal capacity. System running normally."
                        "LOW" -> "Warning: Water cutoff threshold reached."
                        else -> "Motor active. Reservoir levels are rising safely."
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}