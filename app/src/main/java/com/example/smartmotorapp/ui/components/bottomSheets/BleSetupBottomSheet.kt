package com.example.smartmotorapp.ui.components.bottomSheets

import com.example.smartmotorapp.data.enums.BleSetupStep

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleSetupBottomSheet(
    currentStep: BleSetupStep,
    onGrantPermissionClick: () -> Unit,      //  Restored old naming convention
    onEnableBluetoothClick: () -> Unit,     //  Restored missing parameter mapping
    onSimulateDevicesClick: () -> Unit,     // Added backup simulation bridge
    onMotorSelected: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(confirmValueChange = { false }) // Locked layout
    val cobaltBlue = Color(0xFF1D4ED8)

    //  Watches the SCANNING step state and triggers transition after 2 seconds
    if (currentStep == BleSetupStep.SCANNING) {
        LaunchedEffect(Unit) {
            delay(2000) // Spin for precisely 2 seconds to mimic device discovery
            onSimulateDevicesClick() // Shifts state directly into BleSetupStep.DEVICE_LIST
        }
    }

    ModalBottomSheet(
        onDismissRequest = { /* Keep empty to block layout tap dismissal */ },
        sheetState = sheetState,
        containerColor = Color.White,
        dragHandle = null // Enforces controlled stepper route navigation
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            when (currentStep) {
                BleSetupStep.PERMISSION_CHECK -> {
                    Text("Permission Required", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                    Text("To pair with the smart hardware unit over Bluetooth, please grant nearby device detection system permissions.", textAlign = TextAlign.Center, color = Color(0xFF64748B), style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = onGrantPermissionClick,
                        colors = ButtonDefaults.buttonColors(containerColor = cobaltBlue),
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Grant Permission", fontWeight = FontWeight.Bold)
                    }
                }

                //  Added protection block layout just in case the system hits radio switch validations
                BleSetupStep.BLUETOOTH_CHECK -> {
                    Text("Bluetooth Disabled", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                    Text("Please flip your system radio switch active to connect directly with your hardware device.", textAlign = TextAlign.Center, color = Color(0xFF64748B), style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = onEnableBluetoothClick,
                        colors = ButtonDefaults.buttonColors(containerColor = cobaltBlue),
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Enable Bluetooth", fontWeight = FontWeight.Bold)
                    }
                }

                BleSetupStep.SCANNING -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    CircularProgressIndicator(color = cobaltBlue, modifier = Modifier.size(44.dp))
                    Text("Searching for nearby Smart Motors over BLE...", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = Color(0xFF475569))
                    Spacer(modifier = Modifier.height(8.dp))
                }

                BleSetupStep.DEVICE_LIST -> {
                    Text("Available Hardware Detected", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                    Text("Select your Smart Motor unit to transmit Wi-Fi settings:", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)

                    //  Mock System Hardware Device Names
                    val mockMotors = listOf("SmartMotor_Gym_Main_ESP32", "SmartMotor_Sump_Backup_LE")

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        mockMotors.forEach { motorName ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                                    .clickable { onMotorSelected(motorName) }
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(motorName, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                                Text("Connect", color = cobaltBlue, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}