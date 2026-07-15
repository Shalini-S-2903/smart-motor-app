package com.example.smartmotorapp.data.enums

enum class BleSetupStep {
    PERMISSION_CHECK,    // Step 1: Requesting Android permissions
    BLUETOOTH_CHECK,     // Step 2: Bluetooth radio switch layout state
    SCANNING,            // Step 3: 2-Second emulator mock delay spinner
    DEVICE_LIST,         // Step 4: Displaying mock motors found
    SETUP_COMPLETE       // Step 5: Sheet drops, unlocking main form
}