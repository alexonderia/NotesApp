package com.example.notesapp.core.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import com.example.notesapp.ui.theme.NoteAppBackgroundLight
import com.example.notesapp.ui.theme.NoteInkBlue
import com.example.notesapp.ui.theme.NotePaperLight
import com.example.notesapp.ui.theme.Pink40
import com.example.notesapp.ui.theme.Pink80
import com.example.notesapp.ui.theme.Purple80
import com.example.notesapp.ui.theme.PurpleGrey40
import com.example.notesapp.ui.theme.PurpleGrey80
import com.example.notesapp.ui.theme.Typography

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
)

private val LightColorScheme = lightColorScheme(
    primary = NoteInkBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E0FF),
    onPrimaryContainer = Color(0xFF0F274D),
    secondary = PurpleGrey40,
    onSecondary = Color.White,
    tertiary = Pink40,
    background = NoteAppBackgroundLight,
    onBackground = Color(0xFF1B1B1F),
    surface = NotePaperLight,
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFE2E5EC),
    onSurfaceVariant = Color(0xFF45464F),
    surfaceContainerLow = Color(0xFFF6F7FA),
    surfaceContainerHighest = Color(0xFFE2E5EC),
    outline = Color(0xFF767680),
    outlineVariant = Color(0xFFC6C6D0),
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
