import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ManualAutoButton(
    isAutoMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val cobaltColor = Color(0xFF2563EB)
    val lightBlueTint = Color(0xFFEFF6FF)
    val unselectedGrey = Color(0xFFF1F5F9)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- AUTO CARD ---
        Row(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isAutoMode) lightBlueTint else unselectedGrey)
                .clickable { onModeChange(true) }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isAutoMode,
                onClick = { onModeChange(true) },
                colors = RadioButtonDefaults.colors(
                    selectedColor = cobaltColor,
                    unselectedColor = Color(0xFF94A3B8)
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "AUTO",
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isAutoMode) cobaltColor else Color(0xFF475569)
            )
        }

        // --- MANUAL CARD ---
        Row(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (!isAutoMode) lightBlueTint else unselectedGrey)
                .clickable { onModeChange(false) }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = !isAutoMode,
                onClick = { onModeChange(false) },
                colors = RadioButtonDefaults.colors(
                    selectedColor = cobaltColor,
                    unselectedColor = Color(0xFF94A3B8)
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "MANUAL",
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (!isAutoMode) cobaltColor else Color(0xFF475569)
            )
        }
    }
}