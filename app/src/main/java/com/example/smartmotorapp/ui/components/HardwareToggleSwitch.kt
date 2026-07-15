import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CustomSlidingToggle(
    isAutoMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    // Smooth background color transition
    val backgroundColor by animateColorAsState(
        targetValue = if (isAutoMode) Color(0xFF1E3A8A) else Color(0xFF64748B),
        animationSpec = tween(durationMillis = 250),
        label = "BgColor"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // The Custom Sliding Track (Pill Shape)
        Box(
            modifier = Modifier
                .width(260.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(backgroundColor)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) {
                    onModeChange(!isAutoMode)
                }
                .padding(4.dp)
        ) {
            // Animated Width / Position allocation for the white sliding selector background
            val targetOffset by animateDpAsState(
                targetValue = if (isAutoMode) 0.dp else 126.dp,
                animationSpec = tween(durationMillis = 250),
                label = "SliderPosition"
            )

            // The White Sliding Background Indicator
            Box(
                modifier = Modifier
                    .offset(x = targetOffset)
                    .width(126.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
            )

            // Text Labels Overlay
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // AUTO Label
                Text(
                    text = "AUTO",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isAutoMode) Color(0xFF1E3A8A) else Color(0xFFE2E8F0)
                )

                // MANUAL Label
                Text(
                    text = "MANUAL",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (!isAutoMode) Color(0xFF64748B) else Color(0xFFE2E8F0)
                )
            }
        }
    }
}