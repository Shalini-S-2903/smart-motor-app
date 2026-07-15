import android.graphics.BlurMaskFilter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartmotorapp.ui.theme.TextSecondary
import java.util.Locale
import kotlin.io.path.Path

@Composable
fun GradientHaloAmpsGauge(
    currentAmps: Double,
    maxAmpsLimit: Double = 25.0,
    modifier: Modifier = Modifier,
    isOverloadSensorEnabled: Boolean = true,
    isDryRunSensorEnabled: Boolean = true,
    dryRunThreshold: Float = 5.0f,
    overloadThreshold: Float = 20.0f
) {
    val isRunning = currentAmps > 0.1
    val isDryRun = isRunning && currentAmps < dryRunThreshold
    val isOverload = currentAmps > overloadThreshold

    val progressFraction = (currentAmps.coerceIn(0.0, maxAmpsLimit) / maxAmpsLimit).toFloat()
    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(durationMillis = 800),
        label = "Progress"
    )

    // Match the exact sweeping bounds of the speed test meter
    val startAngle = 135f
    val totalSweepAngle = 270f
    val targetNeedleAngle = startAngle + (totalSweepAngle * animatedProgress)

    val dangerOrange = Color(0xFFF97316)
    val inactiveGrey = Color(0xFF94A3B8)
    val trackBgColor = Color(0xFF1E293B).copy(alpha = 0.06f)

    val dryRunBgColor by animateColorAsState(if (isDryRun) dangerOrange.copy(alpha = 0.12f) else Color(0xFFF1F5F9), label = "DryRunBg")
    val overloadBgColor by animateColorAsState(if (isOverload) dangerOrange.copy(alpha = 0.12f) else Color(0xFFF1F5F9), label = "OverloadBg")
    val dryRunIconColor by animateColorAsState(if (isDryRun) dangerOrange else inactiveGrey, label = "DryRunColor")
    val overloadIconColor by animateColorAsState(if (isOverload) dangerOrange else inactiveGrey, label = "OverloadColor")

    // The gradient spectrum used for both the track progress and matching needle tint
    val dynamicGradientColors = listOf(
        Color(0xFF06B6D4), // Cyan
        Color(0xFF10B981), // Emerald Green
        Color(0xFFF59E0B), // Amber
        Color(0xFFEF4444)  // Red
    )

    // Calculate dynamic needle color matching the active progress position
    val activeNeedleColor = remember(animatedProgress) {
        val index = (animatedProgress * (dynamicGradientColors.size - 1)).toInt()
        val nextIndex = (index + 1).coerceAtMost(dynamicGradientColors.size - 1)
        val remainder = (animatedProgress * (dynamicGradientColors.size - 1)) - index
        lerp(dynamicGradientColors[index], dynamicGradientColors[nextIndex], remainder)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // --- 1. LEFT SIDE BLOCK: DRY RUN INDICATOR ---
        if (isDryRunSensorEnabled) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(dryRunBgColor, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Rounded.Warning, contentDescription = null, tint = dryRunIconColor)
                }
                Text(
                    text = "DRY RUN",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isDryRun) dangerOrange else Color(0xFF64748B)
                )
            }
        } else {
            Spacer(modifier = Modifier.width(50.dp))
        }

        // --- 2. CENTER PIECE: SPEED TEST METRIC GAUGE ---
        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val centerX = width / 2f
                val centerY = height / 2f

                val strokeThickness = 14f
                val gaugeRadius = (width / 2f) - 34f
                val arcSize = Size(gaugeRadius * 2f, gaugeRadius * 2f)
                val topLeftOffset = Offset(centerX - gaugeRadius, centerY - gaugeRadius)

                val sweepBrush = Brush.sweepGradient(
                    colors = dynamicGradientColors,
                    center = Offset(centerX, centerY)
                )

                // --- A. BACKGROUND TRACK ---
                drawArc(
                    color = trackBgColor,
                    startAngle = startAngle,
                    sweepAngle = totalSweepAngle,
                    useCenter = false,
                    style = Stroke(width = strokeThickness, cap = StrokeCap.Round),
                    size = arcSize,
                    topLeft = topLeftOffset
                )

                // --- B. ACTIVE PROGRESS ARC ---
                if (animatedProgress > 0f) {
                    drawArc(
                        brush = sweepBrush,
                        startAngle = startAngle,
                        sweepAngle = totalSweepAngle * animatedProgress,
                        useCenter = false,
                        style = Stroke(width = strokeThickness, cap = StrokeCap.Round),
                        size = arcSize,
                        topLeft = topLeftOffset
                    )
                }

                // --- C. TICK MARKS & NUMERIC SCALE ---
                val totalTicks = 11
                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#94A3B8")
                    textSize = 22f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                }

                for (i in 0 until totalTicks) {
                    val tickAngle = startAngle + (totalSweepAngle / (totalTicks - 1)) * i
                    val angleRad = Math.toRadians(tickAngle.toDouble())

                    val innerTickRadius = gaugeRadius - 10f
                    val outerTickRadius = gaugeRadius - 2f

                    val tickStart = Offset(
                        (centerX + innerTickRadius * Math.cos(angleRad)).toFloat(),
                        (centerY + innerTickRadius * Math.sin(angleRad)).toFloat()
                    )
                    val tickEnd = Offset(
                        (centerX + outerTickRadius * Math.cos(angleRad)).toFloat(),
                        (centerY + outerTickRadius * Math.sin(angleRad)).toFloat()
                    )

                    drawLine(
                        color = Color(0xFFCBD5E1),
                        start = tickStart,
                        end = tickEnd,
                        strokeWidth = if (i % 2 == 0) 3f else 1.5f
                    )

                    // Draw Scale Figures around the outer edge
                    if (i % 2 == 0) {
                        val textRadius = gaugeRadius + 22f
                        val labelX = (centerX + textRadius * Math.cos(angleRad)).toFloat()
                        val labelY = (centerY + textRadius * Math.sin(angleRad)).toFloat() + 6f
                        val labelValue = ((maxAmpsLimit / (totalTicks - 1)) * i).toInt()

                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawText(labelValue.toString(), labelX, labelY, textPaint)
                        }
                    }
                }

                // --- D. UPGRADED MECHANICAL SPEEDOMETER NEEDLE (Tapered Shape) ---
                rotate(degrees = targetNeedleAngle, pivot = Offset(centerX, centerY)) {
                    // Clear out the center reading window by shifting the needle base outward
                    val needleBaseStart = gaugeRadius * 0.15f
                    val needleLength = gaugeRadius - 10f // Precise reach right up to the ticks

                    // Tail thickness at the base anchor vs the sharp tip thickness
                    val baseHalfWidth = 5f  // Total thickness at base = 10dp
                    val tipHalfWidth = 1.5f // Total thickness at tip = 3dp

                    val needlePath = Path().apply {
                        moveTo(centerX + needleBaseStart, centerY - baseHalfWidth)

                        lineTo(centerX + needleBaseStart, centerY + baseHalfWidth)

                        lineTo(centerX + needleLength, centerY + tipHalfWidth)

                        lineTo(centerX + needleLength, centerY - tipHalfWidth)

                        close()
                    }

                    // Fills the custom needle shape with your brilliant gradient color matching the track!
                    drawPath(
                        path = needlePath,
                        color = activeNeedleColor
                    )
                }

                drawCircle(
                    color = Color(0xFF0F172A),
                    radius = 7f,
                    center = Offset(centerX, centerY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.5f,
                    center = Offset(centerX, centerY)
                )
            }

            // --- E. LOWER TRIANGLE READOUT PANEL (Perfectly placed inside the gap) ---
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp) // Tucks it right up into that triangle area
            ) {
                Text(
                    text = String.format(Locale.US, "%.1f", currentAmps),
                    fontSize = 24.sp, // Slimmed down to sit perfectly within the gap boundary
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF0F172A),
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Amps",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8)
                )
            }
        }

        // --- 3. RIGHT SIDE BLOCK: OVERLOAD INDICATOR ---
        if (isOverloadSensorEnabled) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(overloadBgColor, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Rounded.Speed, contentDescription = null, tint = overloadIconColor)
                }
                Text(
                    text = "OVERLOAD",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isOverload) dangerOrange else Color(0xFF64748B)
                )
            }
        } else {
            Spacer(modifier = Modifier.width(50.dp))
        }
    }
}