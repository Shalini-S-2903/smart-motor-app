package com.example.smartmotorapp.ui.adddevice

import android.content.Intent
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun AddDeviceScreen(
    viewModel: AddDeviceViewModel,
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Poll WiFi SSID every 2 seconds to show connection status
    var currentSsid by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val wm = context.applicationContext.getSystemService(WifiManager::class.java)
            currentSsid = wm?.connectionInfo?.ssid?.removeSurrounding("\"") ?: ""
            delay(2000)
        }
    }
    val isOnHotspot = currentSsid == "SmartMotor-Setup"

    LaunchedEffect(uiState.isClaimed) {
        if (uiState.isClaimed) {
            onNavigateBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Pair New Device",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Step 1: WiFi Connect
        PairingStepCard(
            stepNumber = 1,
            title = "Connect to Hotspot",
            description = "Power on your SmartMotor device. It will broadcast a WiFi hotspot named \"SmartMotor-Setup\". Open WiFi settings and connect to it."
        ) {
            if (isOnHotspot) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Connected to SmartMotor-Setup ✓",
                        color = Color(0xFF4CAF50),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (currentSsid.isNotEmpty()) "Connected to: $currentSsid (not the hotspot)" else "Not connected to hotspot",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Button(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Wifi, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Open WiFi Settings")
            }
        }

        Spacer(Modifier.height(16.dp))

        // Step 2: Provision
        PairingStepCard(
            stepNumber = 2,
            title = "Configure WiFi",
            description = "Enter your home WiFi details below and send them to the device."
        ) {
            OutlinedTextField(
                value = uiState.wifiSsid,
                onValueChange = { viewModel.updateWifiSsid(it) },
                label = { Text("WiFi SSID (Name)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isProvisioning && !uiState.provisionSuccess
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.wifiPass,
                onValueChange = { viewModel.updateWifiPass(it) },
                label = { Text("WiFi Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isProvisioning && !uiState.provisionSuccess
            )
            Spacer(Modifier.height(12.dp))

            if (uiState.provisionSuccess) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Credentials sent! Wait for device to restart.",
                        color = Color(0xFF4CAF50),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                Button(
                    onClick = { viewModel.provisionDevice() },
                    enabled = !uiState.isProvisioning && uiState.wifiSsid.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isProvisioning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Send to Device")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Step 3: Register
        PairingStepCard(
            stepNumber = 3,
            title = "Register Device",
            description = "IMPORTANT: After Step 2 is done, disconnect from 'SmartMotor-Setup' WiFi and go back to your normal WiFi/Mobile data, then click Register."
        ) {
            Button(
                onClick = { viewModel.claimDevice() },
                enabled = !uiState.isLoading && uiState.deviceId != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Registering...")
                } else {
                    Text("Register Device")
                }
            }

            uiState.errorMessage?.let { msg ->
                val displayMsg = if (msg.contains("resolve host", ignoreCase = true)) {
                    "Cannot reach backend. Please switch OFF 'SmartMotor-Setup' WiFi and use your regular internet, then try again."
                } else msg
                
                Text(
                    text = displayMsg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        TextButton(onClick = onNavigateBack) {
            Text("Cancel")
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stepNumber.toString(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            content()
        }
    }
}