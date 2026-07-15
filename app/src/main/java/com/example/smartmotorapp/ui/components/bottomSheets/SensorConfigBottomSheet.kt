package com.example.smartmotorapp.ui.components.bottomSheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartmotorapp.ui.components.SensorToggleCard
import com.example.smartmotorapp.ui.components.reusableComponents.TankSensorToggleCard
import com.example.smartmotorapp.ui.detail.DeviceDetailUiState
import com.example.smartmotorapp.ui.detail.SensorEvent
import com.example.smartmotorapp.ui.theme.AppBackground
import com.example.smartmotorapp.ui.theme.TextPrimary
import com.example.smartmotorapp.ui.theme.TextSecondary
import com.example.smartmotorapp.ui.theme.cobaltBlue
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorConfigBottomSheet(
    isOpen: Boolean,
    sheetState: SheetState,
    state: DeviceDetailUiState,
    onEvent: (SensorEvent) -> Unit,
    onDismissRequest: () -> Unit
) {
    if (isOpen) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            containerColor = Color.White,
            dragHandle = { BottomSheetDefaults.DragHandle(color = TextSecondary) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, bottom = 32.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // --- HEADER ROW ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Hardware Sensor Controller",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Enable or disable physical telemetry inputs",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close Panel", tint = TextPrimary)
                    }
                }

                Divider(thickness = 0.5.dp, color = TextSecondary)

                // --- SENSOR ITEMS STACK ---

                // 1. Overhead Tank Sensor
                TankSensorToggleCard(
                    checked = state.isWaterSensorEnabled,
                    onCheckedChange = { onEvent(SensorEvent.ToggleWaterSensor(it)) },
                    isTankFull = state.isTankFull,
                    autoStartChecked = state.autoStartWhenEmpty,
                    onAutoStartChange = { onEvent(SensorEvent.ToggleAutoStart(it)) },
                    autoStopChecked = state.autoStopWhenFull,
                    onAutoStopChange = { onEvent(SensorEvent.ToggleAutoStop(it)) }
                )

                // 2. Dry Run Protection (With subtle built-in sliding threshold adjustment)
                SensorToggleCard(
                    title = "Dry Run Protection",
                    subtitle = "Prevents motor damage when dry",
                    icon = Icons.Rounded.Shield,
                    checked = state.isDryRunSensorEnabled,
                    onCheckedChange = { onEvent(SensorEvent.ToggleDryRun(it)) },
                    showSlider = true,
                    sliderValue = state.dryRunThreshold,
                    onSliderValueChange = { onEvent(SensorEvent.UpdateDryRunThreshold(it)) },
                    sliderRange = 0.0f..15.0f,
                    sliderLabel = String.format(Locale.US, "< %.1f A", state.dryRunThreshold),
                    sliderColor = cobaltBlue
                )

                // 3. Overload Protection (With subtle built-in sliding threshold adjustment)
                SensorToggleCard(
                    title = "Overload Protection",
                    subtitle = "Detects excessive amp draw spikes",
                    icon = Icons.Rounded.Speed,
                    checked = state.isOverloadSensorEnabled,
                    onCheckedChange = { onEvent(SensorEvent.ToggleOverload(it)) },
                    showSlider = true,
                    sliderValue = state.overloadThreshold,
                    onSliderValueChange = { onEvent(SensorEvent.UpdateOverloadThreshold(it)) },
                    sliderRange = 15.0f..30.0f,
                    sliderLabel = String.format(Locale.US, "> %.1f A", state.overloadThreshold)
                )

                // 4. 3-Phase Voltage Analyzer
                SensorToggleCard(
                    title = "3-Phase Voltage Analyzer",
                    subtitle = "Tracks health across Phase R, Y, and B",
                    icon = Icons.Rounded.FlashOn,
                    checked = state.isPhaseSensorEnabled,
                    onCheckedChange = { onEvent(SensorEvent.TogglePhase(it)) }
                )
            }
        }
    }
}
