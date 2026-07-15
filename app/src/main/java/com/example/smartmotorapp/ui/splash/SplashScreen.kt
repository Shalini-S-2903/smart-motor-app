package com.example.smartmotorapp.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartmotorapp.ui.theme.cobaltBlue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private val TextDark = Color(0xFF0F172A)
private val TextMuted = Color(0xFF94A3B8)

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    // --- STEP 1: SPRINGS & TRANSITIONS ---
    val scaleAnim = remember { Animatable(0f) }
    val alphaAnim = remember { Animatable(0f) }

    // --- STEP 2: MATHEMATICAL COSMIC DATA STREAM ROTATION ---
    val infiniteAnimation = rememberInfiniteTransition(label = "TelemetryStream")

    // Smoothly loops values from 0 to 2π for calculating wave oscillations
    val timeRotation by infiniteAnimation.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "TimeLoop"
    )

    // Pulse value for the core echo rings
    val radarPulse by infiniteAnimation.animateFloat(
        initialValue = 0f,
        targetValue = 320f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseOutQuad),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarPulse"
    )

    LaunchedEffect(key1 = true) {
        launch {
            scaleAnim.animateTo(
                targetValue = 1.1f, // Energetic bounce overshoot
                animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)
            )
            scaleAnim.animateTo(1.0f, animationSpec = tween(200)) // Settle down
        }
        launch {
            alphaAnim.animateTo(1f, animationSpec = tween(1200, easing = EaseInOutCubic))
        }

        delay(3200) // Give the user enough time to appreciate the beautiful grid effects
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {

        // --- HIGH-FIDELITY TELEMETRY LAYER CANVAS ---
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .alpha(alphaAnim.value * 0.35f) // Keeps backdrop elements cleanly non-distracting
        ) {
            val canvasCenter = center

            // 1. Draw Technical Crosshairs / Center Grid Targets
            drawLine(
                color = cobaltBlue.copy(alpha = 0.2f),
                start = Offset(canvasCenter.x - 60f, canvasCenter.y),
                end = Offset(canvasCenter.x + 60f, canvasCenter.y),
                strokeWidth = 1.5.dp.toPx()
            )
            drawLine(
                color = cobaltBlue.copy(alpha = 0.2f),
                start = Offset(canvasCenter.x, canvasCenter.y - 60f),
                end = Offset(canvasCenter.x, canvasCenter.y + 60f),
                strokeWidth = 1.5.dp.toPx()
            )

            // 2. Continuous Expanding Radar Echoes
            drawCircle(
                color = cobaltBlue,
                radius = radarPulse,
                alpha = (1f - (radarPulse / 320f)).coerceIn(0f, 0.5f),
                style = Stroke(width = 1.dp.toPx())
            )

            // 3. Floating Node Constellation Points (Calculated via Wave Oscillations)
            val totalNodes = 6
            for (i in 0 until totalNodes) {
                val angleOffset = (i * (2 * Math.PI / totalNodes)).toFloat()
                val currentAngle = timeRotation + angleOffset

                // Add varied orbit distances for interesting layering depth
                val orbitRadius = 220f + (sin(timeRotation * 2 + i) * 30f)

                val xNodePosition = canvasCenter.x + cos(currentAngle) * orbitRadius
                val yNodePosition = canvasCenter.y + sin(currentAngle) * orbitRadius

                // Draw connecting satellite nodes
                drawCircle(
                    color = cobaltBlue,
                    radius = 4.dp.toPx(),
                    center = Offset(xNodePosition, yNodePosition),
                    alpha = 0.6f
                )

                // Draw fine constellation tracking lines
                drawLine(
                    color = cobaltBlue.copy(alpha = 0.15f),
                    start = canvasCenter,
                    end = Offset(xNodePosition, yNodePosition),
                    strokeWidth = 0.75.dp.toPx()
                )
            }
        }

        // --- CORE BRAND INTERFACE LAYER ---
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .scale(scaleAnim.value)
                    .alpha(alphaAnim.value)
                    .size(110.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Hub,
                    contentDescription = null,
                    tint = cobaltBlue,
                    modifier = Modifier.size(68.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "SmartMotor",
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                color = TextDark,
                letterSpacing = (-0.8).sp,
                modifier = Modifier.alpha(alphaAnim.value)
            )

            Text(
                text = "IOT METRICS & HARDWARE RELAYS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = cobaltBlue,
                letterSpacing = 3.sp,
                modifier = Modifier
                    .alpha(alphaAnim.value)
                    .padding(top = 8.dp)
            )
        }

        // --- HANDSHAKE FOOTER TEXT ---
        Text(
            text = "ESTABLISHING SECURE API RELAY CLOUD...",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = TextMuted,
            letterSpacing = 2.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 44.dp)
                .alpha(alphaAnim.value)
        )
    }
}