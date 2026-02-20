package com.example.smartmotorapp.ui.roomdetail

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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
fun RoomDetailScreen(
    roomId: Int,
    roomName: String,
    onBack: () -> Unit,
    viewModel: RoomDetailViewModel = viewModel(),
) {
    val devices by viewModel.devices.collectAsState()
    val allDevices by viewModel.allDevices.collectAsState()
    val loading by viewModel.loading.collectAsState()
    var showAddDeviceDialog by remember { mutableStateOf(false) }
    var deviceIdToRemove by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(roomId) {
        viewModel.load(roomId)
    }

    LaunchedEffect(showAddDeviceDialog) {
        if (showAddDeviceDialog) viewModel.loadAvailableDevices()
    }

    val roomDeviceIds = remember(devices) { devices.map { it.id }.toSet() }
    val availableToAdd = remember(allDevices, roomDeviceIds) {
        allDevices.filter { it.id !in roomDeviceIds }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(roomName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDeviceDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add device")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { viewModel.turnOn(roomId) }) {
                    Text("ON")
                }
                Button(onClick = { viewModel.turnOff(roomId) }) {
                    Text("OFF")
                }
            }

            if (loading && devices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(devices, key = { it.id }) { device ->
                        DeviceCardReadOnly(
                            device = device,
                            onRemoveClick = { deviceIdToRemove = device.id },
                        )
                    }
                }
            }
        }
    }

    if (showAddDeviceDialog) {
        AlertDialog(
            onDismissRequest = { showAddDeviceDialog = false },
            title = { Text("Add device to room") },
            text = {
                if (availableToAdd.isEmpty()) {
                    Text(
                        if (allDevices.isEmpty()) "Loading devices…" else "No devices available to add. All your devices are already in this room.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(availableToAdd, key = { it.id }) { device ->
                            Text(
                                text = device.id,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.addDevice(roomId, device.id)
                                        showAddDeviceDialog = false
                                    }
                                    .padding(12.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddDeviceDialog = false }) {
                    Text("Done")
                }
            },
        )
    }

    deviceIdToRemove?.let { deviceId ->
        AlertDialog(
            onDismissRequest = { deviceIdToRemove = null },
            title = { Text("Remove device from room") },
            text = { Text("Remove $deviceId from $roomName?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeDevice(roomId, deviceId)
                        deviceIdToRemove = null
                    },
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deviceIdToRemove = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun DeviceCardReadOnly(
    device: DeviceDto,
    onRemoveClick: (() -> Unit)? = null,
) {
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
                    if (onRemoveClick != null) {
                        IconButton(
                            onClick = onRemoveClick,
                            modifier = Modifier.padding(start = 4.dp),
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Remove from room",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
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
        }
    }
}
