package com.example.smartmotorapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.smartmotorapp.ui.navigation.AppNavGraph
import com.example.smartmotorapp.ui.theme.SmartMotorAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartMotorAppTheme {
                AppNavGraph()
            }
        }
    }
}
