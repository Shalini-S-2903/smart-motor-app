package com.example.smartmotorapp.ui.adddevice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.smartmotorapp.MainActivity
import com.example.smartmotorapp.data.enums.BleSetupStep
import com.example.smartmotorapp.ui.components.bottomSheets.BleSetupBottomSheet
import kotlinx.coroutines.delay

// Standard styling colors matching your design theme variables
val CobaltBlue = Color(0xFF1D4ED8)
val AppBackground = Color(0xFFF8FAFC)
val TextPrimary = Color(0xFF1E293B)
val TextSecondary = Color(0xFF64748B)
val StatusGreen = Color(0xFF16A34A)
val StatusRed = Color(0xFFDC2626)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceScreen(
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current

    //Directly bind to the MainActivity instance over the window space
    val viewModel: AddDeviceViewModel = remember {
        (context as MainActivity).let { activity ->
            activity.viewModels<AddDeviceViewModel>().value
        }
    }
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    // Evaluates layout steps BEFORE flashing the sheet visibility flag
    LaunchedEffect(Unit) {
        val hasScanPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

        val hasConnectPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        // Determine target step locally to bypass StateFlow emission delays
        val targetStep = if (hasScanPermission && hasConnectPermission) {
            BleSetupStep.SCANNING
        } else {
            BleSetupStep.PERMISSION_CHECK
        }

        //  Set the setup step state FIRST
        viewModel.advanceToStep(targetStep)

        //  Make it visible SECOND. This ensures Compose reads the updated step instantly!
        viewModel.updateBottomSheetVisibility(show = true)
    }

    // Navigation trigger upon successful cloud claim
    LaunchedEffect(uiState.isClaimed) {
        if (uiState.isClaimed) {
            onNavigateBack()
        }
    }

    // Success Dialogue Window
    if (uiState.isClaimed) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Card(
                modifier = Modifier
                    .size(280.dp)
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color(0xFFDCFCE7), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Success",
                            tint = StatusGreen,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("Registration Successful", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Linking your device to cloud...", style = MaterialTheme.typography.bodySmall, color = TextSecondary, textAlign = TextAlign.Center)
                }
            }
        }
    }
    LaunchedEffect(uiState.currentSetupStep) {
        if (uiState.currentSetupStep == BleSetupStep.SCANNING) {
            delay(2000) // Spin the progress loader for exactly 2 seconds
            viewModel.advanceToStep(BleSetupStep.DEVICE_LIST) // Automatically switch to the mock device list
        }
    }

    if (uiState.showBottomSheet) {
        BleSetupBottomSheet(
            currentStep = uiState.currentSetupStep,
            onGrantPermissionClick = {
                // Triggers the system alert dialog box
                (context as? MainActivity)?.checkAndRequestBluetoothSystem()
            },
            onEnableBluetoothClick = {
                // Workaround fallback to force step forward if needed
                viewModel.advanceToStep(BleSetupStep.SCANNING)
            },
            onSimulateDevicesClick = {
                viewModel.advanceToStep(BleSetupStep.DEVICE_LIST)
            },
            onMotorSelected = { selectedName ->
                // Saves the mock motor name to state, sets currentStep = SETUP_COMPLETE, and hides sheet
                viewModel.selectDiscoveredMotor(selectedName)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configure Hardware", fontWeight = FontWeight.ExtraBold, color = TextPrimary, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.resetSetupState()
                        onNavigateBack()
                    }) {
                        Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
        containerColor = AppBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // 🟢 CONNECTED HARDWARE STATUS CARD (Replaces old Card 1)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)), // Very Soft Green
                border = BorderStroke(1.dp, Color(0xFFDCFCE7))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BluetoothConnected,
                        contentDescription = "Bluetooth Status",
                        tint = StatusGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(text = "Paired Hardware Unit", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontWeight = FontWeight.Medium)
                        Text(
                            text = uiState.selectedMotorName ?: "No Hardware Connected",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // --- STEP 1: PROVISION LOCALLY OVER CHIP SERVER (Old Card 2 re-numbered) ---
            PairingStepCard(
                stepNumber = 1,
                title = "Configure Location WiFi",
                description = "Provide the local WiFi name and password that the motor should use to link directly to the internet server."
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.wifiSsid,
                    onValueChange = { viewModel.updateWifiSsid(it) },
                    label = { Text("WiFi Network Name (SSID)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    enabled = !uiState.isProvisioning && !uiState.provisionSuccess
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.wifiPass,
                    onValueChange = { viewModel.updateWifiPass(it) },
                    label = { Text("WiFi Access Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    enabled = !uiState.isProvisioning && !uiState.provisionSuccess
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (uiState.provisionSuccess) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = StatusGreen.copy(alpha = 0.08f),
                        border = BorderStroke(0.5.dp, StatusGreen.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(Icons.Rounded.CloudUpload, contentDescription = null, tint = StatusGreen, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Credentials transmitted via BLE!",
                                color = StatusGreen,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = { viewModel.provisionDevice() },
                        enabled = !uiState.isProvisioning && uiState.wifiSsid.isNotBlank() && uiState.selectedMotorName != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CobaltBlue)
                    ) {
                        if (uiState.isProvisioning) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Transmit Credentials", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // --- STEP 2: CLOUD REGISTER OWNERSHIP (Old Card 3 re-numbered) ---
            PairingStepCard(
                stepNumber = 2,
                title = "Register Device on Cloud",
                description = "Name your device profile and complete registration to connect your phone safely to the smart motor metrics dashboard."
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                val hasDeviceId = true // Set true to keep visible for testing screenshots
                val isTitleValid = uiState.deviceTitle.isNotBlank()

                if (hasDeviceId) {
                    OutlinedTextField(
                        value = uiState.deviceTitle,
                        onValueChange = { viewModel.updateDeviceTitle(it) },
                        label = { Text("Title") },
                        placeholder = { Text("e.g., Main Sump Motor") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = uiState.deviceDescription,
                        onValueChange = { viewModel.updateDeviceDescription(it) },
                        label = { Text("Description (Optional)") },
                        placeholder = { Text("e.g., Next to gym pool entry") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }

                Button(
                    onClick = { viewModel.claimDevice() },
                    enabled = !uiState.isLoading && isTitleValid && uiState.selectedMotorName != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CobaltBlue, disabledContainerColor = Color(0xFFE2E8F0))
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Binding Profile...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Rounded.AppRegistration, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Complete Registration", fontWeight = FontWeight.Bold)
                    }
                }

                AnimatedVisibility(visible = uiState.errorMessage != null) {
                    uiState.errorMessage?.let { msg ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = StatusRed.copy(alpha = 0.08f),
                            border = BorderStroke(0.5.dp, StatusRed.copy(alpha = 0.2f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            Text(text = msg, color = StatusRed, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, modifier = Modifier.padding(12.dp), textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}


@Composable
fun PairingStepCard(
    stepNumber: Int,
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(0.5.dp, Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = CobaltBlue.copy(alpha = 0.1f),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stepNumber.toString(),
                            color = CobaltBlue,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = 10.dp),
                lineHeight = 20.sp
            )

            content()
        }
    }
}