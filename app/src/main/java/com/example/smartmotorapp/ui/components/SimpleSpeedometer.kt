package com.example.smartmotorapp.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SimpleSpeedometer(
    currentValue: Float,
    maxValue: Float,
    accentColor: Color = Color(0xFF1E40AF)
) {
    val sweepAngle = 180f
    val startAngle = 180f
    val progress = (currentValue / maxValue).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1000), // Smoother needle movement
        label = "needle"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = 8.dp.toPx() // Slightly thinner for a more professional look
        val center = Offset(size.width / 2, size.height / 1.2f) // Adjusted center for better fit
        val radius = size.width / 2 - strokeWidth

        // 1. Draw Background Track (Light Gray Arc)
        drawArc(
            color = Color(0xFFF3F4F6),
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            size = Size(radius * 2, radius * 2),
            topLeft = Offset(center.x - radius, center.y - radius)
        )

        // 2. Draw Active Range (Using the accentColor parameter)
        drawArc(
            color = accentColor,
            startAngle = startAngle,
            sweepAngle = sweepAngle * animatedProgress,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            size = Size(radius * 2, radius * 2),
            topLeft = Offset(center.x - radius, center.y - radius)
        )

        // 3. Draw the Needle
        val needleAngle = startAngle + (sweepAngle * animatedProgress)
        val needleLength = radius - 15f
        val endX = center.x + needleLength * cos(Math.toRadians(needleAngle.toDouble())).toFloat()
        val endY = center.y + needleLength * sin(Math.toRadians(needleAngle.toDouble())).toFloat()

        drawLine(
            color = Color(0xFF111827), // Dark slate needle
            start = center,
            end = Offset(endX, endY),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )

        // 4. Center Pivot Point
        drawCircle(color = Color(0xFF111827), radius = 4.dp.toPx(), center = center)
    }
}