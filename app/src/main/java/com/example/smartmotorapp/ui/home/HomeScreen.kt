package com.example.smartmotorapp.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddHome
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Light
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PaintingStyle.Companion.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartmotorapp.R
import com.example.smartmotorapp.data.remote.DeviceDto
import com.example.smartmotorapp.data.remote.RoomDto
import com.example.smartmotorapp.ui.components.DeviceCardHome
import com.example.smartmotorapp.ui.components.RoomCardHome
import com.example.smartmotorapp.ui.theme.AppBackground
import com.example.smartmotorapp.ui.theme.AppSubtitleText
import com.example.smartmotorapp.ui.theme.AppTitleText
import com.example.smartmotorapp.ui.theme.TextPrimary
import com.example.smartmotorapp.ui.theme.White
import com.example.smartmotorapp.ui.theme.cobaltBlue
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

private const val POWER_MAX_W = 1000

val MockRooms = listOf(
    RoomDto(
        id = 1,
        name = "Main Pump House",
        description = "Primary water supply motor",
        createdAt = "2024-03-20T10:00:00Z",
        isActive = true,
        deviceCount = 2
    ),
    RoomDto(
        id = 2,
        name = "Guest Wing",
        description = "Auxiliary supply",
        createdAt = "2024-03-21T11:00:00Z",
        isActive = true,
        deviceCount = 1
    ),
    RoomDto(
        id = 3,
        name = "Garden Irrigation",
        description = null,
        createdAt = "2024-03-22T09:30:00Z",
        isActive = false,
        deviceCount = 0
    )
)
val MockDevices = listOf(
    DeviceDto(
        id = "Main Motor 01",
        online = true,
        // This will always be 30 seconds ago
        lastSeen = java.time.Instant.now().minusSeconds(30).toString(),
        lastState = "ON",
        lastPower = 12.5,
        description = "hello1"
    ),
    DeviceDto(
        id = "Borewell Pump",
        online = true,
        // This will always be 15 minutes ago
        lastSeen = java.time.Instant.now().minus(15, java.time.temporal.ChronoUnit.MINUTES).toString(),
        lastState = "OFF",
        lastPower = 0.0,
        description = "hello1"
    ),
    DeviceDto(
        id = "Gate Light",
        online = false,
        // This will always be 2 hours ago
        lastSeen = java.time.Instant.now().minus(2, java.time.temporal.ChronoUnit.HOURS).toString(),
        lastState = "OFF",
        lastPower = null,
        description = "hello1"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onDeviceClick: (String) -> Unit = {},
    onRoomClick: (Int, String) -> Unit = { _, _ -> },
    onAddDevice: () -> Unit = {},
    onCreateRoom: () -> Unit = {},
    onScheduleDevice: (String) -> Unit = {},
    onScheduleRoom: (Int) -> Unit = {},
    onLogout: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val rooms by viewModel.rooms.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val enabledRoomIds = remember { mutableStateListOf<Int>() }

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
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = { AppTitleText(text = stringResource(R.string.smart_motor_app)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppBackground,
                    actionIconContentColor = TextPrimary
                ),
                actions = {
                    IconButton(onClick = onAddDevice) {
                        Icon(Icons.Default.Add, contentDescription = "Add device", tint = cobaltBlue)
                    }
                    IconButton(onClick = onCreateRoom) {
                        Icon(Icons.Default.AddHome, contentDescription = "Create room", tint = cobaltBlue)
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.Logout, contentDescription = "Log out", tint = cobaltBlue)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddDevice, // Or your specific action
                containerColor = cobaltBlue,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Device",
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        // Optional: Ensures the FAB doesn't overlap with the bottom of the list
        floatingActionButtonPosition = FabPosition.End
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {

            Image(
                painter = painterResource(id = R.drawable.doodle_bg),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.tint(
                    AppBackground.copy(alpha = 0.8f),
                    blendMode = BlendMode.SrcOver
                )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(start = 12.dp)
            ) {
                if (uiState.backendOffline){
                    Column(
                        modifier = Modifier
                            .fillMaxSize(),
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
                }else  {
                    LazyColumn(modifier = Modifier) {
                        item {
                            AppTitleText(text = "Welcome Shalini!", modifier = Modifier.padding(top = 8.dp))
                        }
                        item {
                            AppSubtitleText(text = stringResource(id = R.string.rooms),modifier = Modifier.padding(vertical = 8.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(MockRooms, key = { it.id }) { room -> //change to rooms
                                    val isCurrentlyOn = enabledRoomIds.contains(room.id)
                                    RoomCardHome(
                                        room = room,
                                        onClick = { onRoomClick(room.id, room.name) },
                                        isRoomEnabled = isCurrentlyOn,
                                        onToggle = { shouldTurnOn ->
                                            if (shouldTurnOn) {
                                                enabledRoomIds.add(room.id)
                                                viewModel.setRoomOn(room.id) // Call API
                                            } else {
                                                enabledRoomIds.remove(room.id)
                                                viewModel.setRoomOff(room.id) // Call API
                                            }
                                        },
                                        onSchedule = { onScheduleRoom(room.id) }
                                    )
                                }
                            }
                        }
                        item {
                            AppSubtitleText(text = stringResource(id = R.string.devices),modifier = Modifier.padding(vertical = 8.dp))
                        }

                        if (MockDevices.isEmpty()){
                            item {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
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
                        }else{
                            // Group devices into pairs for a 2-column grid
                            val chunkedDevices = MockDevices.chunked(2)
                            // 1. REMOVE the chunking logic. Use the plain list of devices.
                            items(chunkedDevices, key = { it.first().id  }) { rowDevices ->
                                // 2. No 'Row', no 'weight'. Just the DeviceRow by itself.
                                // This allows it to take the FULL width and only the height it needs.
                                for (device in rowDevices){
                                    DeviceCardHome (
                                        device = device,
                                        turningId = uiState.turningId,
                                        onTurnOn = { viewModel.setDeviceOn(device.id) },
                                        onTurnOff = { viewModel.setDeviceOff(device.id) },
                                        onSchedule = { onScheduleDevice(device.id) },
                                        onClick = { onDeviceClick(device.id) }
                                    )
                                }
                            }
                        }

                    }
                }
                if (uiState.isInitialLoading){
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
fun getRelativeTime(isoString: String?): String {
    // If the isoString is the same for all devices in your mock file,
    // the UI will look the same. Check your MockData.kt!
    return remember(isoString) {
        if (isoString.isNullOrBlank()) return@remember "Never"

        try {
            val instant = java.time.Instant.parse(isoString)
            val now = java.time.Instant.now()
            val diffInSeconds = java.time.Duration.between(instant, now).seconds

            // Debug: print(diffInSeconds) to see if it's actually 0 for all

            when {
                diffInSeconds < 60 -> "Just now"
                diffInSeconds < 3600 -> "${diffInSeconds / 60} mins ago"
                diffInSeconds < 86400 -> "${diffInSeconds / 3600} hours ago"
                else -> "${diffInSeconds / 86400} days ago"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }
}



