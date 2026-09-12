package com.example.sistema_tienda

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Sistematienda",
    ) {
        DesktopApp()
    }
}
