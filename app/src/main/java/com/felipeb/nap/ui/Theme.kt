package com.felipeb.nap.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Night = Color(0xFF0F1222)
val NightSurface = Color(0xFF1A1E36)
val Moon = Color(0xFFB8C4FF)
val Star = Color(0xFFFFD37A)

val EnergyHigh = Color(0xFF6EE7A8)
val EnergyOk = Color(0xFFB8E36E)
val EnergyLow = Color(0xFFFFC266)
val EnergyCritical = Color(0xFFFF7A85)

fun energyColor(energy: Int): Color = when {
    energy >= 75 -> EnergyHigh
    energy >= 55 -> EnergyOk
    energy >= 35 -> EnergyLow
    else -> EnergyCritical
}

private val colors = darkColorScheme(
    primary = Moon,
    onPrimary = Night,
    secondary = Star,
    background = Night,
    surface = Night,
    surfaceContainer = NightSurface,
    surfaceContainerHigh = Color(0xFF232847),
    surfaceContainerHighest = Color(0xFF2B3154),
    onSurface = Color(0xFFE6E8F5),
    onSurfaceVariant = Color(0xFF9EA3C0),
    outlineVariant = Color(0xFF363C60),
)

@Composable
fun NapTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
