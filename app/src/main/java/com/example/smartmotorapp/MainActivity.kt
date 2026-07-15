package com.example.smartmotorapp

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.smartmotorapp.data.enums.BleSetupStep
import com.example.smartmotorapp.di.BackendProvider
import com.example.smartmotorapp.ui.navigation.AppNavGraph
import com.example.smartmotorapp.ui.theme.SmartMotorAppTheme
import com.example.smartmotorapp.ui.adddevice.AddDeviceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val addDeviceViewModel: AddDeviceViewModel by viewModels()

    // 🛡Handles the native "Allow app to access nearby devices?" dialog box
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        //  LOG 1: See exactly what permissions Android is returning
        Log.d("BLE_DEBUG", "--- Permission Result Callback Triggered ---")
        permissions.forEach { (permission, isGranted) ->
            Log.d("BLE_DEBUG", "Permission: $permission | Granted: $isGranted")
        }

        val scanGranted = permissions[Manifest.permission.BLUETOOTH_SCAN] ?: false
        val connectGranted = permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: false

        Log.d("BLE_DEBUG", "scanGranted evaluation: $scanGranted")
        Log.d("BLE_DEBUG", "connectGranted evaluation: $connectGranted")

        if (scanGranted || connectGranted) {
            Log.d("BLE_DEBUG", "SUCCESS: Moving state to SCANNING step!")
            addDeviceViewModel.advanceToStep(BleSetupStep.SCANNING)
        } else {
            Log.d("BLE_DEBUG", "FAILURE: Staying on PERMISSION_CHECK step.")
            addDeviceViewModel.advanceToStep(BleSetupStep.PERMISSION_CHECK)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch(Dispatchers.IO) {
            BackendProvider.init(applicationContext)
        }
        actionBar?.hide()          // Hides core platform ActionBars
        setContent {
            SmartMotorAppTheme {
                AppNavGraph()
            }
        }
    }

    /**
     * Call this when the user clicks "Grant Permission" inside the bottom sheet.
     */
    fun checkAndRequestBluetoothSystem() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            // Fires the OS alert box dialog only when clicked
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            // 🎉 If permissions were already granted previously, jump directly to the loader
            addDeviceViewModel.advanceToStep(BleSetupStep.SCANNING)
        }
    }
}