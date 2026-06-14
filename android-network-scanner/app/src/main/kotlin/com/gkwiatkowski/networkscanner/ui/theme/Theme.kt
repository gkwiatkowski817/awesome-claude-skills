package com.gkwiatkowski.networkscanner.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = ScannerCyan,
    onPrimary = androidx.compose.ui.graphics.Color.Black,
    secondary = ScannerIndigo,
    tertiary = ScannerGreen,
    background = androidx.compose.ui.graphics.Color(0xFF0A0E1A),
    surface = androidx.compose.ui.graphics.Color(0xFF111827),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF1E293B),
)

private val LightColors = lightColorScheme(
    primary = ScannerBlue,
    secondary = ScannerIndigo,
    tertiary = ScannerGreen,
)

@Composable
fun NetworkScannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
