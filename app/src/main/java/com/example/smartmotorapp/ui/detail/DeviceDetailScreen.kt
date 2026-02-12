package com.example.smartmotorapp.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.smartmotorapp.data.remote.EventDto
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    deviceId: String,
    onBack: () -> Unit,
    viewModel: DeviceDetailViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(deviceId) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
    ) { padding ->
        when {
            uiState.isLoading && uiState.device == null -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(padding).padding(32.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(padding).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { viewModel.refresh() }) { Text("Retry") }
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    uiState.device?.let { device ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (device.lastState.equals("ON", ignoreCase = true)) "ON" else "OFF",
                                style = MaterialTheme.typography.titleLarge,
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
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (uiState.turning) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                            Button(onClick = { viewModel.setDeviceOn() }) { Text("ON") }
                            Button(onClick = { viewModel.setDeviceOff() }) { Text("OFF") }
                        }
                    }
                    ActivityCard(events = uiState.events)
                }
            }
        }
    }
}

@Composable
private fun ActivityCard(events: List<EventDto>) {
    var expanded by remember { mutableStateOf(true) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Activity",
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                if (events.isEmpty()) {
                    Text(
                        text = "No events yet",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(events, key = { "${it.timestamp}-${it.type}" }) { event ->
                            ActivityRow(event = event)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(event: EventDto) {
    val icon = when (event.type) {
        "DRY_RUN" -> Icons.Default.Warning
        "TANK_FULL" -> Icons.Default.Check
        "SAFETY_TIMER" -> Icons.Default.Timer
        "OFFLINE" -> Icons.Default.CloudOff
        "ONLINE" -> Icons.Default.Cloud
        else -> Icons.Default.Warning
    }
    val tint = when (event.type) {
        "TANK_FULL", "ONLINE" -> Color(0xFF4CAF50)
        "DRY_RUN", "SAFETY_TIMER" -> Color(0xFFFF9800)
        "OFFLINE" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.message,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = formatRelativeTime(event.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            else -> SimpleDateFormat("MMM d", Locale.US).format(date)
        }
    }.getOrElse { isoTimestamp }
}
