package com.example.smartmotorapp.ui.schedules

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Premium Color Palette
private val CobaltBlue = Color(0xFF1E3A8A)
private val CobaltLight = Color(0xFFEFF6FF)
private val TextDark = Color(0xFF0F172A)
private val TextMuted = Color(0xFF475569)
private val InputBackground = Color(0xFFF8FAFC)
private val CardBorderColor = Color(0xFFE2E8F0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleCreateScreen(
    deviceId: String? = null,
    roomId: Int? = null,
    onNavigateBack: () -> Unit,
    viewModel: ScheduleCreateViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    var showDatePicker by remember { mutableStateOf<Boolean?>(null) } // true for ON, false for OFF
    var showTimePicker by remember { mutableStateOf<Boolean?>(null) } // true for ON, false for OFF

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) onNavigateBack()
    }

    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                title = {
                    Text(
                        text = "Set Schedule",
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        color = TextDark
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = CobaltBlue
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState)
        ) {
            if (uiState.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().clip(CircleShape),
                    color = CobaltBlue,
                    trackColor = CobaltLight
                )
                Spacer(Modifier.height(16.dp))
            }

            uiState.errorMessage?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // --- SECTION 1: IDENTITY INPUT ---
            Text(
                text = "GENERAL SETTINGS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CobaltBlue,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
            )

            OutlinedTextField(
                value = uiState.name,
                onValueChange = { viewModel.updateName(it) },
                label = { Text("Schedule Name") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. Morning Garden Watering") },
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CobaltBlue,
                    focusedLabelColor = CobaltBlue,
                    unfocusedBorderColor = CardBorderColor,
                    unfocusedContainerColor = InputBackground,
                    focusedContainerColor = Color.White,
                    focusedTextColor = TextDark,
                    unfocusedTextColor = TextDark
                )
            )

            Spacer(Modifier.height(24.dp))

            // --- SECTION 2: REPETITION CONTROL ---
            Text(
                text = "FREQUENCY",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CobaltBlue,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(InputBackground)
                    .border(1.dp, CardBorderColor, RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "ONEOFF" to "One-time",
                    "DAILY" to "Daily"
                ).forEach { (typeKey, labelText) ->
                    val isSelected = uiState.type == typeKey
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color.White else Color.Transparent)
                            .clickable { viewModel.updateType(typeKey) }
                            .then(
                                if (isSelected) Modifier.border(0.5.dp, CardBorderColor, RoundedCornerShape(8.dp))
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = labelText,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) CobaltBlue else TextMuted
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // --- SECTION 3: ON TRIGGER ---
            Text(
                text = "START METRIC",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CobaltBlue,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            TriggerBlock(
                title = "Turn ON",
                accentColor = CobaltBlue,
                backgroundColor = CobaltLight,
                isOneOff = uiState.type == "ONEOFF",
                dateText = uiState.onDate.toString(),
                timeText = uiState.onTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                onDateClick = { showDatePicker = true },
                onTimeClick = { showTimePicker = true }
            )

            Spacer(Modifier.height(24.dp))

            // --- SECTION 4: AUTO-OFF SWITCH ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(InputBackground)
                    .border(1.dp, CardBorderColor, RoundedCornerShape(16.dp))
                    .clickable { viewModel.toggleSetOff(!uiState.setOffSchedule) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = if (uiState.setOffSchedule) CobaltBlue else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Set Auto-OFF Time",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextDark
                        )
                        Text(
                            text = "Automatically shut down active run loop",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }
                Checkbox(
                    checked = uiState.setOffSchedule,
                    onCheckedChange = { viewModel.toggleSetOff(it) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = CobaltBlue,
                        uncheckedColor = TextMuted
                    )
                )
            }

            // --- SECTION 5: OFF TRIGGER ---
            AnimatedVisibility(
                visible = uiState.setOffSchedule,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    TriggerBlock(
                        title = "Turn OFF",
                        accentColor = Color(0xFFDC2626),
                        backgroundColor = Color(0xFFFEF2F2),
                        isOneOff = uiState.type == "ONEOFF",
                        dateText = uiState.offDate.toString(),
                        timeText = uiState.offTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                        onDateClick = { showDatePicker = false },
                        onTimeClick = { showTimePicker = false }
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            // --- SECTION 6: SAVE ACTION ---
            Button(
                onClick = { viewModel.saveSchedule(deviceId, roomId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !uiState.isLoading && uiState.name.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CobaltBlue,
                    disabledContainerColor = CobaltBlue.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = "Save Schedule",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    // --- WORKING MATERIAL 3 DATE PICKER INTEGRATION ---
    if (showDatePicker != null) {
        val isOnState = showDatePicker == true
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = if (isOnState) {
                uiState.onDate.atStartOfDayInEpochMillis()
            } else {
                uiState.offDate.atStartOfDayInEpochMillis()
            }
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selectedDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            if (isOnState) {
                                viewModel.updateOnDate(selectedDate)
                            } else {
                                viewModel.updateOffDate(selectedDate)
                            }
                        }
                        showDatePicker = null
                    }
                ) {
                    Text("OK", color = CobaltBlue, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = null }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    selectedDayContainerColor = CobaltBlue,
                    todayContentColor = CobaltBlue,
                    selectedDayContentColor = Color.White
                )
            )
        }
    }

    // --- WORKING MATERIAL 3 TIME PICKER INTEGRATION ---
    if (showTimePicker != null) {
        val isOnState = showTimePicker == true
        val currentTime = if (isOnState) uiState.onTime else uiState.offTime
        val timePickerState = rememberTimePickerState(
            initialHour = currentTime.hour,
            initialMinute = currentTime.minute,
            is24Hour = true
        )

        Dialog(onDismissRequest = { showTimePicker = null }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color.White,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .width(IntrinsicSize.Min)
                    .height(IntrinsicSize.Min)
                    .background(Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Select Time",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                        modifier = Modifier
                            .align(Alignment.Start)
                            .padding(bottom = 20.dp)
                    )

                    TimePicker(
                        state = timePickerState,
                        colors = TimePickerDefaults.colors(
                            selectorColor = CobaltBlue,
                            clockDialSelectedContentColor = Color.White,
                            periodSelectorSelectedContainerColor = CobaltLight,
                            periodSelectorSelectedContentColor = CobaltBlue,
                            timeSelectorSelectedContainerColor = CobaltLight,
                            timeSelectorSelectedContentColor = CobaltBlue
                        )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showTimePicker = null }) {
                            Text("Cancel", color = TextMuted)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                val selectedTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                                if (isOnState) {
                                    viewModel.updateOnTime(selectedTime)
                                } else {
                                    viewModel.updateOffTime(selectedTime)
                                }
                                showTimePicker = null
                            }
                        ) {
                            Text("OK", color = CobaltBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// Helper conversion extension to feed system epochs safely into picker instances
private fun LocalDate.atStartOfDayInEpochMillis(): Long {
    return this.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

@Composable
fun TriggerBlock(
    title: String,
    accentColor: Color,
    backgroundColor: Color,
    isOneOff: Boolean,
    dateText: String,
    timeText: String,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorderColor, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header Indicator Pill
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(accentColor, CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title.uppercase(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isOneOff) {
                    // Date Display Box
                    Row(
                        modifier = Modifier
                            .weight(1.2f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(InputBackground)
                            .clickable { onDateClick() }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = dateText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            tint = TextMuted.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Time Display Box
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(backgroundColor)
                        .clickable { onTimeClick() }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = timeText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = accentColor
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        tint = accentColor.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}