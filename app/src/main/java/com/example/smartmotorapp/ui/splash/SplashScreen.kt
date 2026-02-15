package com.example.smartmotorapp.ui.splash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.smartmotorapp.di.BackendProvider

@Composable
fun SplashScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToHome: () -> Unit,
) {
    var checked by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val hasToken = BackendProvider.tokenStore.hasToken()
        checked = true
        if (hasToken) onNavigateToHome() else onNavigateToLogin()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (!checked) {
            CircularProgressIndicator()
        } else {
            Text("Loading…", style = MaterialTheme.typography.bodyLarge)
        }
    }
}
