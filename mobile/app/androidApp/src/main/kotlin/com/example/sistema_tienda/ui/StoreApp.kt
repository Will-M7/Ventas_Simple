package com.example.sistema_tienda.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.example.sistema_tienda.ui.theme.SistematiendaTheme
import com.example.sistema_tienda.data.AppContainer
import com.example.sistema_tienda.ui.navigation.AppNavigation

@Composable
fun StoreApp() {
    SistematiendaTheme { AppNavigation(AppContainer(LocalContext.current)) }
}
