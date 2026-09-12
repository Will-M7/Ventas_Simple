package com.example.sistema_tienda.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StoreColors = lightColorScheme(
    primary = Color(0xFF6F5A8E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DDF5),
    secondary = Color(0xFF81748F),
    secondaryContainer = Color(0xFFF0EAF4),
    background = Color(0xFFFCFAFD),
    surface = Color.White,
    surfaceVariant = Color(0xFFF1EEF3)
)

@Composable
fun SistematiendaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = StoreColors, content = content)
}
