import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartmotorapp.data.remote.DeviceDto
import com.example.smartmotorapp.data.remote.EventDto
import com.example.smartmotorapp.ui.components.AnimatedWaterTankCard
import com.example.smartmotorapp.ui.components.PhaseConnectionStatusCard
import com.example.smartmotorapp.ui.components.bottomSheets.SensorConfigBottomSheet
import com.example.smartmotorapp.ui.detail.DeviceDetailViewModel
import com.example.smartmotorapp.ui.detail.SensorEvent
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

private val CobaltBlue = Color(0xFF1E3A8A)
private val AppBackground = Color(0xFFF8FAFC)
private val TextPrimary = Color(0xFF0F172A)
private val TextSecondary = Color(0xFF64748B)
private val StatusGreen = Color(0xFF10B981)
private val BorderColor = Color(0xFFE2E8F0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    deviceId: String,
    onBack: () -> Unit,
    onNavigateToScheduler: () -> Unit,    // Callbacks for separate screen navigation
    onNavigateToInstantTimer: () -> Unit, // Callbacks for separate screen navigation
    viewModel: DeviceDetailViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    // Recovery Mode Configuration (Independent of physical controls)
    var isAutoMode by remember { mutableStateOf(true) }
    var isActivityExpanded by remember { mutableStateOf(true) }

    // --- BOTTOM SHEET CONFIGURATION ---
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSensorConfigSheet by remember { mutableStateOf(false) }

    val useMockFallback = uiState.error != null || uiState.device == null

    val isTankFullCurrentState = if (useMockFallback) {
        true
    } else {
        uiState.isTankFull
    }

    val displayDevice = if (useMockFallback) {
        DeviceDto(
            id = deviceId,
            description = "Next to gym pool entry",
            lastState = "ON",
            lastPower = 5.7,
            online = true,
            lastSeen = "2026-05-12T10:00:00Z",
            phaseR = true,       // Red phase healthy
            phaseY = false,       // Yellow phase healthy
            phaseB = true,       // Blue phase healthy (change to false to test your "ERR" state!)
            waterLevelStatus = "FULL" //FULL,FILLING,LOW
        )
    } else {
        uiState.device
    }

    val displayEvents = if (useMockFallback) {
        listOf(
            EventDto(timestamp = "2026-06-01T21:45:00Z", type = "TANK_FULL", message = "Overhead Tank is full. System turned off automatically."),
            EventDto(timestamp = "2026-06-01T18:30:00Z", type = "ONLINE", message = "Device reconnected to local cloud network."),
            EventDto(timestamp = "2026-06-01T14:20:00Z", type = "SAFETY_TIMER", message = "Safety timer tripped: Run time exceeded 45 mins."),
            EventDto(timestamp = "2026-06-01T09:15:00Z", type = "DRY_RUN", message = "Dry run condition detected. Motor stopped instantly.")
        )
    } else {
        uiState.events
    }

    val isCurrentlyOn = displayDevice?.lastState.equals("ON", ignoreCase = true)
    val currentAmpsReading = if (isCurrentlyOn) "12.4 A" else "0.0 A (Stopped)"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(deviceId, fontWeight = FontWeight.ExtraBold, color = TextPrimary, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    // Clicking this now comfortably pulls open our Sensor Configuration Sheet Panel
                    IconButton(onClick = { showSensorConfigSheet = true }) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = CobaltBlue)
                        } else {
                            Icon(imageVector = Icons.Rounded.Tune, contentDescription = "Configure Sensors", tint = CobaltBlue)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
        containerColor = AppBackground
    ) { padding ->

        when {
            false -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CobaltBlue)
                }
            }
            else -> {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    // --- SECTION 1: REALISTIC GAUGE DASHBOARD HOUSING ---
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(0.5.dp, BorderColor)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Header details info bar
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = displayDevice?.id ?: "Main Motor 01",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = TextPrimary
                                        )
                                        if (!displayDevice?.description.isNullOrBlank()) {
                                            Text(
                                                text = displayDevice!!.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary
                                            )
                                        }
                                    }

                                    // Connected Status Tag
                                    Box(
                                        modifier = Modifier
                                            .background(StatusGreen.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp))
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = if (displayDevice?.online == true) "CONNECTED" else "OFFLINE",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (displayDevice?.online == true) StatusGreen else Color.Red
                                        )
                                    }
                                }

                                Divider(thickness = 0.5.dp, color = BorderColor, modifier = Modifier.padding(vertical = 4.dp))

                                // High-Fidelity Tactical Dashboard Gauge
                                GradientHaloAmpsGauge(
                                    currentAmps = displayDevice?.lastPower ?: 0.0,
                                    isDryRunSensorEnabled = uiState.isDryRunSensorEnabled,
                                    isOverloadSensorEnabled = uiState.isOverloadSensorEnabled,
                                    dryRunThreshold = uiState.dryRunThreshold,
                                    overloadThreshold = uiState.overloadThreshold
                                )
                            }
                        }
                    }

                    // --- TANK STATUS CARD ---
                    if (uiState.isWaterSensorEnabled) {
                        item {
                            AnimatedWaterTankCard(
                                waterLevelStatus = displayDevice?.waterLevelStatus ?: "FULL"
                            )
                        }
                    }

                    // PHASE CONNECTION & TANK LEVEL HARDWARE INDICATORS ---
                    if (uiState.isPhaseSensorEnabled) {
                        item {
                            PhaseConnectionStatusCard(
                                phaseR = displayDevice?.phaseR ?: true,   // Safe defaults if backend null
                                phaseY = displayDevice?.phaseY ?: true,
                                phaseB = displayDevice?.phaseB ?: false,  // Example showing Phase B failed/off
                            )
                        }
                    }
                    // --- SECTION 2: SYSTEM MANAGEMENT CONTROLS ---
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(0.5.dp, BorderColor)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                                Text(
                                    text = "Recovery Mode Strategy",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )

                                // Perfect Color-matched Radio Component
                                ManualAutoButton(
                                    isAutoMode = isAutoMode,
                                    onModeChange = { isAutoMode = it }
                                )

                                Text(
                                    text = if (isAutoMode)
                                        "System will auto-resume running if power cuts out mid-operation."
                                    else
                                        "System stays safely turned off following a power outage until manually cleared.",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                                )

                                Divider(thickness = 0.5.dp, color = BorderColor, modifier = Modifier.padding(vertical = 4.dp))

                                // Global Action Trigger Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Global Motor Switch",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (uiState.turning) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF2563EB))
                                        }

                                        Button(
                                            onClick = { viewModel.setDeviceOn() },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isCurrentlyOn) StatusGreen else Color(0xFFE2E8F0),
                                                contentColor = if (isCurrentlyOn) Color.White else TextPrimary
                                            )
                                        ) {
                                            Text("ON", fontWeight = FontWeight.ExtraBold)
                                        }

                                        Button(
                                            onClick = { viewModel.setDeviceOff() },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (!isCurrentlyOn) Color.Red else Color(0xFFE2E8F0),
                                                contentColor = if (!isCurrentlyOn) Color.White else TextPrimary
                                            )
                                        ) {
                                            Text("OFF", fontWeight = FontWeight.ExtraBold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // --- SECTION 3: CONSOLIDATED TIMERS (NAVIGATES TO SEPARATE SCREENS) ---
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(0.5.dp, BorderColor)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Timers & Automation", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)

                                // Route to separate Schedule Screen
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(AppBackground, shape = RoundedCornerShape(10.dp))
                                        .clickable { onNavigateToScheduler() }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = CobaltBlue, modifier = Modifier.size(20.dp))
                                        Column {
                                            Text("Schedule Timer", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                            Text("Daily 06:00 AM - 07:30 AM", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                        }
                                    }
                                    Icon(Icons.Rounded.ChevronRight, contentDescription = "Open Scheduler Screen", tint = TextSecondary)
                                }

                                // Route to separate Instant Timer Screen
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(AppBackground, shape = RoundedCornerShape(10.dp))
                                        .clickable { onNavigateToInstantTimer() }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Icon(Icons.Rounded.HourglassTop, contentDescription = null, tint = CobaltBlue, modifier = Modifier.size(20.dp))
                                        Column {
                                            Text("Instant Timer", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                            Text("Not set (Auto cut-off)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                        }
                                    }
                                    Icon(Icons.Rounded.ChevronRight, contentDescription = "Open Instant Timer Screen", tint = TextSecondary)
                                }
                            }
                        }
                    }

                    // --- SECTION 4: ACTIVITY LOG ---
                    item (key = "activity_header") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.History, contentDescription = null, tint = CobaltBlue)
                                Text(text = "Activity Log", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                            IconButton(onClick = {
                                isActivityExpanded = !isActivityExpanded
                                if (isActivityExpanded) {
                                    coroutineScope.launch {
                                        lazyListState.animateScrollToItem(index = lazyListState.layoutInfo.totalItemsCount - 1)
                                    }
                                }
                            }) {
                                Icon(
                                    imageVector = if (isActivityExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = TextSecondary
                                )
                            }
                        }
                    }

                    if (isActivityExpanded) {
                        if (displayEvents.isEmpty()) {
                            item {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White,
                                    border = BorderStroke(0.5.dp, BorderColor)
                                ) {
                                    Text(
                                        text = "No recent activity events recorded.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(16.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            itemsIndexed(displayEvents) { index, event ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White,
                                    border = BorderStroke(0.5.dp, BorderColor)
                                ) {
                                    Box(modifier = Modifier.padding(12.dp)) {
                                        ActivityRow(event = event)
                                    }
                                }
                            }
                        }
                    }
                }
                SensorConfigBottomSheet(
                    isOpen = showSensorConfigSheet,
                    sheetState = sheetState,
                    state = uiState.copy(
                        isTankFull = isTankFullCurrentState,
                        isWaterSensorEnabled = uiState.isWaterSensorEnabled,
                        autoStartWhenEmpty = uiState.autoStartWhenEmpty,
                        autoStopWhenFull = uiState.autoStopWhenFull
                    ),
                    onEvent = { event ->
                        viewModel.onSensorEvent(event)
                    },
                    onDismissRequest = { showSensorConfigSheet = false }
                )
            }
        }
    }
}

@Composable
private fun ActivityRow(event: EventDto) {
    val icon = when (event.type) {
        "TANK_FULL" -> Icons.Default.Check
        "DRY_RUN" -> Icons.Default.Warning
        "SAFETY_TIMER" -> Icons.Default.Timer
        "OFFLINE" -> Icons.Default.CloudOff
        "ONLINE" -> Icons.Default.Cloud
        else -> Icons.Default.Warning
    }
    val tint = when (event.type) {
        "TANK_FULL", "ONLINE" -> StatusGreen
        "DRY_RUN", "SAFETY_TIMER" -> Color(0xFFFF9800)
        "OFFLINE" -> MaterialTheme.colorScheme.error
        else -> TextSecondary
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(tint.copy(alpha = 0.1f), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = event.message, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(
                text = formatRelativeTime(event.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

private fun formatRelativeTime(isoTimestamp: String): String {
    return runCatching {
        val base = isoTimestamp.substringBefore(".")
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val date = format.parse(base) ?: return isoTimestamp
        val now = System.currentTimeMillis()
        val then = date.time
        val diffMs = now - then
        val diffSec = TimeUnit.MILLISECONDS.toSeconds(diffMs)
        val diffMin = TimeUnit.MILLISECONDS.toMinutes(diffMs)
        val diffHours = TimeUnit.MILLISECONDS.toHours(diffMs)
        val diffDays = TimeUnit.MILLISECONDS.toDays(diffMs)
        when {
            diffSec < 60 -> "Just now"
            diffMin < 60 -> "${diffMin} min ago"
            diffHours < 24 -> "${diffHours} hr ago"
            diffDays == 1L -> "Yesterday"
            diffDays < 7 -> "$diffDays days ago"
            else -> SimpleDateFormat("MMM d, yyyy", Locale.US).format(date)
        }
    }.getOrElse { isoTimestamp }
}