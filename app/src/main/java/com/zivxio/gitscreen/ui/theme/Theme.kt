package com.zivxio.gitscreen.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val GitScreenPurple = Color(0xFF7C4DFF)
private val GitScreenPurpleDark = Color(0xFF4A2FB5)
private val GitScreenBackground = Color(0xFF1B1F3B)

private val LightColors = lightColorScheme(
    primary = GitScreenPurple,
    secondary = GitScreenPurpleDark
)

private val DarkColors = darkColorScheme(
    primary = GitScreenPurple,
    secondary = GitScreenPurpleDark,
    background = GitScreenBackground
)

@Composable
fun GitScreenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
