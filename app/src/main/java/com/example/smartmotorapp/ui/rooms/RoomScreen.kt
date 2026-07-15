package com.example.smartmotorapp.ui.rooms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.smartmotorapp.ui.components.RoomDetailCard

@Composable
fun RoomScreen(
    viewModel: RoomViewModel,
    onNavigateToDevices: () -> Unit,
    onNavigateToSchedules: () -> Unit,
    onNavigateToAddRoom: () -> Unit,
    onNavigateToRoomDetail: (Int) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadRooms()
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Rooms", style = MaterialTheme.typography.headlineMedium)
            Row {
                TextButton(onClick = onNavigateToDevices) {
                    Text("Devices")
                }
                TextButton(onClick = onNavigateToSchedules) {
                    Text("Schedules")
                }
            }
        }

        // Content
        if (uiState.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Loading rooms...")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                items(uiState.rooms) { room ->
                    RoomDetailCard(
                        room = room,
                        onClick = { onNavigateToRoomDetail(room.id) }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        // Floating Action Button for adding rooms
        FloatingActionButton(
            onClick = onNavigateToAddRoom,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Room",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}