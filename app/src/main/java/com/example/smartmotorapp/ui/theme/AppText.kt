package com.example.smartmotorapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun AppTitleText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TextPrimary
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
        fontWeight = FontWeight.Medium
    )
}

@Composable
fun AppSubtitleText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TextSecondary,
    fontWeight: FontWeight = FontWeight.Medium
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = fontWeight
    )
}

@Composable
fun BodyMediumText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = InActiveText,
    fontWeight: FontWeight = FontWeight.Normal
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = fontWeight
    )
}