package com.example.smartmotorapp.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartmotorapp.data.remote.DeviceDto
import kotlinx.coroutines.delay

private const val POWER_MAX_W = 1000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onDeviceClick: (String) -> Unit = {},
    onAddDevice: () -> Unit = {},
    onLogout: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.loadDevices(initial = true)
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Devices") },
                actions = {
                    IconButton(onClick = onAddDevice) {
                        Icon(Icons.Default.Add, contentDescription = "Add device")
                    }
                    Button(onClick = onLogout) { Text("Log out") }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        when {
            uiState.isInitialLoading && uiState.devices.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.backendOffline -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                    Text(
                        text = "Server unreachable",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Check internet or try again",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                    )
                    Button(onClick = { viewModel.loadDevices(initial = true) }) {
                        Text("Retry")
                    }
                }
            }
            uiState.devices.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No devices connected",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Power on your controller to see it here",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
            else -> {
                val deviceList = uiState.devices.values.toList()
                LazyColumn(
                    modifier = Modifier
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(deviceList, key = { it.id }) { device ->
                        DeviceRow(
                            device = device,
                            turningId = uiState.turningId,
                            onTurnOn = { viewModel.setDeviceOn(device.id) },
                            onTurnOff = { viewModel.setDeviceOff(device.id) },
                            onClick = { onDeviceClick(device.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: DeviceDto,
    turningId: String?,
    onTurnOn: () -> Unit,
    onTurnOff: () -> Unit,
    onClick: () -> Unit,
) {
    val isPending = device.id == turningId
    val powerW = (device.lastPower ?: 0.0).toFloat().coerceIn(0f, POWER_MAX_W.toFloat())
    val powerProgress = powerW / POWER_MAX_W
    val animatedProgress by animateFloatAsState(
        targetValue = powerProgress,
        animationSpec = tween(durationMillis = 300),
        label = "power",
    )
    val powerBarColor = when {
        powerW < 20 -> Color.Gray
        powerW < 300 -> Color(0xFF4CAF50)
        powerW < 700 -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }
    val isOn = device.lastState.equals("ON", ignoreCase = true)
    val runningSeconds = remember { mutableIntStateOf(0) }
    LaunchedEffect(device.lastState) {
        if (!isOn) {
            runningSeconds.intValue = 0
            return@LaunchedEffect
        }
        runningSeconds.intValue = 0
        while (true) {
            delay(1000)
            runningSeconds.intValue += 1
        }
    }
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(if (isOn) pulse else 1f),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = device.id,
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (device.online) Color(0xFF4CAF50)
                                else MaterialTheme.colorScheme.error,
                            ),
                    )
                    Text(
                        text = if (device.online) "Online" else "Offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (device.online) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "State: ${device.lastState ?: "—"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Live Power: %.0f W".format(powerW),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (isOn) {
                    val sec = runningSeconds.intValue
                    Text(
                        text = "%d:%02d".format(sec / 60, sec % 60),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .padding(top = 8.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(8.dp)
                        .background(powerBarColor),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isPending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                    )
                }
                Button(
                    onClick = onTurnOn,
                    enabled = !isPending,
                ) { Text("ON") }
                Button(
                    onClick = onTurnOff,
                    enabled = !isPending,
                ) { Text("OFF") }
            }
        }
    }
}
