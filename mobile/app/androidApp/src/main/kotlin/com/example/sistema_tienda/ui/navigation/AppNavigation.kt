package com.example.sistema_tienda.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sistema_tienda.data.AppContainer
import com.example.sistema_tienda.domain.model.User
import com.example.sistema_tienda.ui.screens.*
import com.example.sistema_tienda.ui.viewmodels.*

@Composable fun AppNavigation(container: AppContainer) {
    val sessionVm: SessionViewModel = viewModel(factory = SimpleFactory { SessionViewModel(container) })
    val sessionInvalidated by container.sessionInvalidated.collectAsState()
    LaunchedEffect(sessionInvalidated) { if (sessionInvalidated > 0) sessionVm.forceLogout() }
    when (val state = sessionVm.state.collectAsState().value) {
        SessionState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        SessionState.LoggedOut -> LoginScreen(container) { sessionVm.accept(it) }
        is SessionState.Error -> ErrorScreen(state.message)
        is SessionState.LoggedIn -> StoreNavigation(state.user, container, sessionVm)
    }
}

@Composable private fun StoreNavigation(user: User, c: AppContainer, sessionVm: SessionViewModel) {
    val admin = user.rol.equals("admin", true)
    val routes = if (admin) listOf("dashboard" to "Dashboard", "sale" to "Nueva venta", "products" to "Productos", "merchandise" to "Mercadería", "categories" to "Categorías", "inventory" to "Inventario", "sales" to "Ventas", "reports" to "Reportes", "users" to "Usuarios", "settings" to "Configuración") else listOf("dashboard" to "Inicio", "sale" to "Nueva venta", "products" to "Productos", "merchandise" to "Mercadería", "my_sales" to "Mis ventas", "my_reports" to "Mis reportes", "settings" to "Configuración")
    var route by remember { mutableStateOf("dashboard") }
    var drawerOpen by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    LaunchedEffect(drawerOpen) { if (drawerOpen) drawerState.open() else drawerState.close() }
    ModalNavigationDrawer(drawerState = drawerState, gesturesEnabled = true, drawerContent = {
        ModalDrawerSheet { Spacer(Modifier.height(24.dp)); Text("Sistematienda", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(24.dp)); Text("${user.nombre} · ${user.rol}", modifier = Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(16.dp)); routes.forEach { (key, label) -> NavigationDrawerItem(label = { Text(label) }, selected = route == key, onClick = { route = key; drawerOpen = false }, modifier = Modifier.padding(horizontal = 12.dp)) }; Spacer(Modifier.weight(1f)); NavigationDrawerItem(label = { Text("Cerrar sesión") }, selected = false, onClick = { sessionVm.logout(); drawerOpen = false }, modifier = Modifier.padding(12.dp)); Spacer(Modifier.height(12.dp)) }
    }) {
        Scaffold(topBar = { TopAppBar(title = { Text(routes.firstOrNull { it.first == route }?.second ?: "Sistematienda") }, navigationIcon = { IconButton(onClick = { drawerOpen = true }) { Text("☰") } }, actions = { TextButton(onClick = { route = "settings" }) { Text("⚙") } }) }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) { when (route) {
                "dashboard" -> DashboardScreen(c, user, onNavigate = { route = it })
                "sale" -> NewSaleScreen(c, user)
                "products" -> ProductsScreen(c, user)
                "merchandise" -> MerchandiseScreen(c, user, onCreate = { route = "products" })
                "categories" -> CategoriesScreen(c)
                "inventory" -> InventoryScreen(c)
                "sales", "my_sales" -> SalesScreen(c, admin)
                "reports", "my_reports" -> ReportsScreen(c, admin)
                "users" -> UsersScreen(c)
                "settings" -> ServerSettingsScreen(c)
            } }
        }
    }
}

@Composable private fun ErrorScreen(message: String) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(message, color = MaterialTheme.colorScheme.error) } }
class SimpleFactory<T>(private val create: () -> T) : androidx.lifecycle.ViewModelProvider.Factory { override fun <R : androidx.lifecycle.ViewModel> create(modelClass: Class<R>): R = create() as R }
