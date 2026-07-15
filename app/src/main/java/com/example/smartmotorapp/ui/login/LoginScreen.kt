package com.example.smartmotorapp.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartmotorapp.R
import com.example.smartmotorapp.ui.theme.cobaltBlue
import kotlinx.coroutines.delay
import kotlin.math.sin

private val TextDark = Color(0xFF1E293B)
private val TextMuted = Color(0xFF64748B)
private val FieldBackground = Color(0xFFF8FAFC)

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) onLoginSuccess()
    }

    // --- HARDWARE ICON CAROUSEL (SERIOUS INDUSTRIAL ICONS) ---
    var currentIconIndex by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2800)
            currentIconIndex = (currentIconIndex + 1) % 4
        }
    }

    // --- VIBRATING / PULSING BLUR WAVE ANIMATION ---
    val infiniteTransition = rememberInfiniteTransition(label = "RadarTelemetry")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "GlowPulse"
    )

    Surface(
        color = Color.White,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // --- TOP COBALT CARD WITH CRISP DOODLE BACKGROUND ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .clip(RoundedCornerShape(bottomStart = 48.dp, bottomEnd = 48.dp))
                    .background(cobaltBlue),
                contentAlignment = Alignment.Center
            ) {
                // Faint overlay of your doodle background asset (Adjust alpha to your preference)
                Image(
                    painter = painterResource(id = R.drawable.doodle_bg),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.22f)
                )

                // --- VIBRATING / PULSING BLUR WAVE ANIMATION ---
                val infiniteTransition = rememberInfiniteTransition(label = "RadarTelemetry")
                val pulseProgress by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = (2 * Math.PI).toFloat(),
                    animationSpec = infiniteRepeatable(
                        animation = tween(2200, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "GlowPulse"
                )

                // Calculate dynamic size expanding wide/shrinking, and a matching vibrating blur intensity
                val dynamicPulseSize = 100.dp + (kotlin.math.sin(pulseProgress) * 16).dp
                val dynamicBlurRadius = 4.dp + (kotlin.math.sin(pulseProgress) * 4).dp

                // --- HARDWARE ICON CAROUSEL (SETTINGS & SPANNER ONLY) ---
                var currentIconIndex by remember { mutableStateOf(0) }
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(2800)
                        currentIconIndex = (currentIconIndex + 1) % 2 // Alternates strictly between the 2 icons
                    }
                }

                // Vibrating Aura Circle Layer (Expands wide, shrinks, and changes blur intensity)
                Box(
                    modifier = Modifier
                        .size(dynamicPulseSize)
                        .blur(dynamicBlurRadius)
                        .background(Color.White.copy(alpha = 0.18f), shape = CircleShape)
                )

                // Clean Core Container Solid Ring Overlay
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.1f),
                    modifier = Modifier.size(115.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {

                        // Solid White Center Display Frame
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            modifier = Modifier.size(80.dp),
                            shadowElevation = 6.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Crossfade(
                                    targetState = currentIconIndex,
                                    animationSpec = tween(durationMillis = 500, easing = EaseInOutCubic),
                                    label = "IndustrialCarousel"
                                ) { index ->
                                    val iconVector = when (index) {
                                        0 -> Icons.Default.Settings  // Serious Industrial Gear Hub
                                        else -> Icons.Default.Build  // Configuration Spanner Tool
                                    }

                                    Icon(
                                        imageVector = iconVector,
                                        contentDescription = null,
                                        tint = cobaltBlue,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- LOWER FORM CONTENT CANVAS ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(36.dp))

                Text(
                    text = "Welcome Back",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = TextDark,
                    letterSpacing = (-0.5).sp
                )

                Text(
                    text = "Sign in to manage your smart motor",
                    fontSize = 14.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 6.dp)
                )

                Spacer(Modifier.height(36.dp))

                uiState.errorMessage?.let { msg ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
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

                // --- EMAIL INPUT ---
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        viewModel.clearError()
                    },
                    label = { Text("Email Address") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !uiState.isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = cobaltBlue,
                        focusedLabelColor = cobaltBlue,
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                        unfocusedContainerColor = FieldBackground,
                        focusedContainerColor = Color.White
                    )
                )

                Spacer(Modifier.height(18.dp))

                // --- PASSWORD INPUT ---
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        viewModel.clearError()
                    },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    enabled = !uiState.isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = cobaltBlue,
                        focusedLabelColor = cobaltBlue,
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                        unfocusedContainerColor = FieldBackground,
                        focusedContainerColor = Color.White
                    )
                )

                Spacer(Modifier.height(36.dp))

                // --- LOGIN SUBMIT BUTTON ---
                Button(
                    onClick = { viewModel.login(email, password) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    enabled = !uiState.isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = cobaltBlue,
                        disabledContainerColor = cobaltBlue.copy(alpha = 0.6f)
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        AnimatedVisibility(
                            visible = uiState.isLoading,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(end = 8.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        }
                        Text(
                            text = if (uiState.isLoading) "Signing in..." else "Log in",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))

                // --- SIGN UP LINK ---
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    Text(
                        text = "New to SmartMotor?",
                        fontSize = 14.sp,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Create Account",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = cobaltBlue,
                        modifier = Modifier.clickable { onNavigateToRegister() }
                    )
                }
            }
        }
    }
}
