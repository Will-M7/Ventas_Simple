package com.example.sistema_tienda.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import android.net.Uri
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sistema_tienda.data.AppContainer
import com.example.sistema_tienda.data.remote.*
import com.example.sistema_tienda.data.repositories.ApiFailure
import com.example.sistema_tienda.domain.model.*
import com.example.sistema_tienda.ui.BarcodeFeedback
import com.example.sistema_tienda.ui.BarcodeScanner
import com.example.sistema_tienda.ui.ScannerMode
import com.example.sistema_tienda.ui.rememberBarcodeFeedback
import com.example.sistema_tienda.ui.components.*
import com.example.sistema_tienda.ui.navigation.SimpleFactory
import com.example.sistema_tienda.ui.viewmodels.LoginViewModel
import com.example.sistema_tienda.ui.viewmodels.SaleViewModel
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

@Composable fun LoginScreen(c: AppContainer, onSuccess: (User) -> Unit) {
    val vm: LoginViewModel = viewModel(factory = SimpleFactory { LoginViewModel(c) })
    val username by vm.username.collectAsState(); val password by vm.password.collectAsState(); val error by vm.error.collectAsState(); val busy by vm.busy.collectAsState()
    var settings by remember { mutableStateOf(false) }; val snack = remember { SnackbarHostState() }
    LaunchedEffect(error) { error?.let { snack.showSnackbar(it) } }
    Scaffold(snackbarHost = { SnackbarHost(snack) }, topBar = { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.End) { TextButton({ settings = true }) { Icon(Icons.Outlined.Settings, "Configuración", tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(6.dp)); Text("Configurar servidor") } } }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(28.dp), verticalArrangement = Arrangement.Center) {
            Text("Sistematienda", style = MaterialTheme.typography.headlineLarge); Text("Control simple para tu tienda", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(24.dp))
            OutlinedTextField(username, { vm.username.value = it }, label = { Text("Usuario") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, { vm.password.value = it }, label = { Text("Contraseña") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            if (c.getServerIp().isBlank()) Text("Configura la IP del servidor antes de iniciar sesión.", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp)); Button({ vm.login { snack.currentSnackbarData?.dismiss(); onSuccess(it) } }, enabled = !busy && username.isNotBlank() && password.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Ingresando…" else "Iniciar sesión") }
        }
    }
    if (settings) ServerSettingsDialog(c, { settings = false }, snack)
}

@Composable fun ServerSettingsScreen(c: AppContainer, onClose: () -> Unit) { val snack = remember { SnackbarHostState() }; Scaffold(snackbarHost = { SnackbarHost(snack) }) { ServerSettingsContent(c, snack, Modifier.padding(it), onClose) } }
@Composable private fun ServerSettingsDialog(c: AppContainer, close: () -> Unit, snack: SnackbarHostState) {
    var address by rememberSaveable { mutableStateOf(c.getServerIp()) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun save() { c.saveServerIp(address); close() }
    AlertDialog(
        onDismissRequest = { if (!busy) close() }, title = { Text("Servidor") },
        text = { Column {
            OutlinedTextField(address, { address = it; failure = null }, label = { Text("IP o URL completa") }, singleLine = true, enabled = !busy)
            Text("Ejemplos: 192.168.1.22 o https://tienda.ejemplo.com/", style = MaterialTheme.typography.bodySmall)
            failure?.let { Text(it, color = MaterialTheme.colorScheme.error); Text("No se guardó la configuración.") }
        } },
        confirmButton = { Button(enabled = !busy && address.isNotBlank(), onClick = {
            busy = true; failure = null
            scope.launch {
                try {
                    c.testServerConnection(address).onSuccess { save() }.onFailure { failure = it.message ?: "No se pudo conectar" }
                } finally { busy = false }
            }
        }) { Text(if (busy) "Probando…" else "Probar y guardar") } },
        dismissButton = { Column {
            if (failure != null && !busy) TextButton(onClick = { try { save() } catch (error: IllegalArgumentException) { failure = error.message } }) { Text("Guardar de todos modos") }
            TextButton(enabled = !busy, onClick = close) { Text("Cancelar") }
        } }
    )
}
@Composable private fun ServerSettingsContent(c: AppContainer, snack: SnackbarHostState, modifier: Modifier, onClose: () -> Unit) {
    Column(modifier.padding(24.dp)) { Text("Configuración del servidor", style = MaterialTheme.typography.headlineSmall); Text("Servidor: " + c.getServerIp()); ServerSettingsDialog(c, onClose, snack) }
}

@Composable fun DashboardScreen(c: AppContainer, user: User, onNavigate: (String) -> Unit) {
    QueryContent(Unit, { c.dashboard() }) { data ->
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text(if (user.rol == "admin") "Dashboard admin" else "Inicio", style = MaterialTheme.typography.headlineSmall); Text("Hola, " + user.nombre) }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { StatCard("Ventas hoy", data.ventasHoy.toString(), Modifier.weight(1f)); StatCard("Ingresos", money(data.ingresosHoy), Modifier.weight(1f)) } }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { StatCard("Unidades", data.unidadesVendidasHoy.toString(), Modifier.weight(1f)); if (user.rol == "admin") StatCard("Stock bajo", data.productosStockBajo.toString(), Modifier.weight(1f)) } }
            item { Row { Button({ onNavigate("sale") }) { Text("Nueva venta") }; if (user.rol == "admin") OutlinedButton({ onNavigate("products") }) { Text("Productos") } } }
            item { Text("Ventas recientes"); if (data.historialTruncado) Text("Se muestran las últimas 100 ventas del período.") }
            if (data.ventasRecientes.isEmpty()) item { EmptyState("No hay ventas en el período.") }
            items(data.ventasRecientes) { SaleRow(it) {} }
        }
    }
}

@Composable fun ProductsScreen(c: AppContainer, user: User) {
    var search by remember { mutableStateOf("") }; var list by remember { mutableStateOf<List<Product>>(emptyList()) }; var loading by remember { mutableStateOf(true) }; var error by remember { mutableStateOf<String?>(null) }; var selected by remember { mutableStateOf<Product?>(null) }; var registeredProduct by remember { mutableStateOf<Product?>(null) }; var create by rememberSaveable { mutableStateOf(false) }; var createCode by rememberSaveable { mutableStateOf<String?>(null) }; var scanning by remember { mutableStateOf(false) }; var unknownCode by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope(); val snack = remember { SnackbarHostState() }
    var refreshTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(search, refreshTick) { loading = true; error = null; kotlinx.coroutines.delay(250); cancellableRunCatching { c.searchProducts(search).map { it.toDomain() } }.onSuccess { list = it; loading = false; error = null }.onFailure { error = it.message; loading = false } }
    if (scanning) {
        BarcodeScanner(onCode = { code -> scanning = false; scope.launch { c.products.findByBarcode(code).onSuccess { registeredProduct = it }.onFailure { failure -> if ((failure as? ApiFailure)?.status == 404) unknownCode = code else error = failure.message } } }, onClose = { scanning = false }, onManualCode = { code -> scanning = false; scope.launch { c.products.findByBarcode(code).onSuccess { registeredProduct = it }.onFailure { failure -> if ((failure as? ApiFailure)?.status == 404) unknownCode = code else error = failure.message } } })
        return
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(search, { search = it }, label = { Text("Buscar nombre o código") }, modifier = Modifier.weight(1f), singleLine = true); TextButton({ scanning = true }) { Text("+ Nuevo") } }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton({ scanning = true }) { Text("Escanear producto") } }; when { loading -> LoadingState(); error != null -> ErrorState(error!!); list.isEmpty() -> EmptyState("No se encontraron productos."); else -> LazyColumn { items(list) { ProductCard(it, c) { selected = it } } } } }
    selected?.let { ProductDetailDialog(c, it, user) { selected = null; refreshTick++ } }; if (create) ProductFormDialog(c, createCode) { create = false; refreshTick++ }; registeredProduct?.let { product -> AlertDialog(onDismissRequest = { registeredProduct = null; selected = product }, title = { Text("Este producto ya está registrado.") }, text = { Text("Se abrirá el detalle del producto existente.") }, confirmButton = { Button({ registeredProduct = null; selected = product }) { Text("Ver detalle") } }) }; unknownCode?.let { code -> AlertDialog(onDismissRequest = { unknownCode = null }, title = { Text("Producto no registrado") }, text = { Text("No existe un producto con el código $code.") }, confirmButton = { Button({ unknownCode = null; createCode = code; create = true }) { Text("Crear producto") } }, dismissButton = { TextButton({ unknownCode = null }) { Text("Cerrar") } }) }; SnackbarHost(snack)
}
private fun ProductDto.toDomain() = Product(id, codigo ?: codigo_barras.orEmpty(), nombre, precio_compra, precio_venta, stock, descripcion, stock_minimo, categoria_nombre, imagen, activo == 1, categoria_id)

@Composable private fun ProductDetailDialog(c: AppContainer, product: Product, user: User, close: () -> Unit) { var stock by remember { mutableIntStateOf(product.stock) }; var stockError by remember { mutableStateOf<String?>(null) }; var edit by remember { mutableStateOf(false) }; var active by remember { mutableStateOf(product.activo) }; var stockDialog by remember { mutableStateOf<Boolean?>(null) }; val scope = rememberCoroutineScope(); if (edit) ProductEditDialog(c, product, close = { edit = false }) { stock = product.stock; active = product.activo; edit = false; close() } else AlertDialog(onDismissRequest = close, title = { Text(product.nombre) }, text = { Column { ProductImage(product.copy(activo = active), c, Modifier.fillMaxWidth().height(140.dp)); Text("Código: " + product.codigo_barras); Text("Descripción: " + (product.descripcion ?: "Sin descripción")); Text("Compra: " + money(product.precio_compra)); Text("Venta: " + money(product.precio_venta)); Text("Stock: " + stock); stockError?.let { Text(it, color = MaterialTheme.colorScheme.error) }; Text("Stock mínimo: " + (product.stock_minimo ?: 5)); Text(product.categoria_nombre ?: "Sin categoría"); Text(if (active) "Activo" else "Inactivo"); Row { Button({ stockDialog = true }) { Text("Aumentar") }; if (user.rol.equals("admin", true)) { Spacer(Modifier.width(8.dp)); OutlinedButton({ stockDialog = false }) { Text("Disminuir") } } } } }, confirmButton = { if (user.rol.equals("admin", true)) Button({ edit = true }) { Text("Editar") } }, dismissButton = { TextButton(close) { Text("Cerrar") } }); stockDialog?.let { increase -> StockQuantityDialog(stock, increase, { stockDialog = null }) { quantity -> scope.launch { val result = if (increase) c.products.addStock(product.id, quantity) else c.products.decreaseStock(product.id, quantity); result.onSuccess { stock = it.stock; stockDialog = null }.onFailure { stockError = it.message; stockDialog = null } } } } }

@Composable private fun StockQuantityDialog(current: Int, increase: Boolean, close: () -> Unit, confirm: (Int) -> Unit) {
    var value by remember { mutableStateOf("1") }
    var busy by remember { mutableStateOf(false) }
    val quantity = value.toIntOrNull()
    val next = current.toLong() + (if (increase) 1 else -1) * (quantity?.toLong() ?: 0)
    val valid = quantity != null && quantity >= 1 && next in 0..Int.MAX_VALUE.toLong()
    AlertDialog(onDismissRequest = { if (!busy) close() }, title = { Text(if (increase) "Aumentar stock" else "Disminuir stock") }, text = {
        Column {
            Text("Stock actual: $current")
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(enabled = !busy, onClick = { value = ((quantity ?: 1) - 1).coerceAtLeast(1).toString() }) { Text("−") }
                OutlinedTextField(value, { value = it.filter(Char::isDigit) }, label = { Text("Cantidad") }, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                TextButton(enabled = !busy && (quantity ?: 0) < Int.MAX_VALUE, onClick = { value = ((quantity ?: 0) + 1).toString() }) { Text("+") }
            }
            Text("Stock resultante: $next")
            if (!valid) Text("Cantidad o stock resultante inválido", color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { Button(enabled = valid && !busy, onClick = { if (!busy) { busy = true; confirm(quantity!!) } }) { Text(if (busy) "Guardando…" else "Confirmar") } }, dismissButton = { TextButton(enabled = !busy, onClick = close) { Text("Cancelar") } })
}
private const val MAX_PRODUCT_PHOTO_BYTES = 5 * 1024 * 1024

private data class PreparedProductPhoto(val bytes: ByteArray, val mimeType: String, val extension: String)

private suspend fun prepareProductPhoto(context: android.content.Context, uri: Uri): PreparedProductPhoto =
    withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                if (output.size() + count > MAX_PRODUCT_PHOTO_BYTES) {
                    throw IllegalArgumentException("La imagen supera el límite de 5 MB.")
                }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: throw IOException("No se pudo abrir la imagen seleccionada.")

        val signature = when {
            bytes.size >= 3 && bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "image/jpeg" to "jpg"
            bytes.size >= 8 && bytes.sliceArray(0..7).contentEquals(
                byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            ) -> "image/png" to "png"
            bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp" to "webp"
            else -> throw IllegalArgumentException("Elige una imagen JPG, PNG o WebP válida.")
        }
        PreparedProductPhoto(bytes, signature.first, signature.second)
    }

private suspend fun uploadPhoto(c: AppContainer, id: Long, photo: PreparedProductPhoto) {
    val body = photo.bytes.toRequestBody(photo.mimeType.toMediaType())
    c.uploadProductImage(
        id,
        MultipartBody.Part.createFormData("foto", "producto-$id.${photo.extension}", body)
    )
}

private fun newProductCameraUri(context: android.content.Context): Uri {
    val directory = File(context.cacheDir, "product_photos")
    if (!directory.exists() && !directory.mkdirs()) throw IOException("No se pudo preparar la foto.")
    val file = File.createTempFile("producto-", ".jpg", directory)
    return try {
        FileProvider.getUriForFile(context, "${context.packageName}.productphotos", file)
    } catch (failure: Exception) {
        file.delete()
        throw failure
    }
}

private fun deleteTemporaryPhoto(context: android.content.Context, uri: Uri?) {
    if (uri?.authority == "${context.packageName}.productphotos") {
        cancellableRunCatching { context.contentResolver.delete(uri, null, null) }
            .onFailure { Log.w("ProductPhoto", "No se pudo limpiar la foto temporal", it) }
    } else if (uri != null) {
        cancellableRunCatching { context.contentResolver.releasePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ImageSourceSheet(onCamera: () -> Unit, onGallery: () -> Unit, onCancel: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onCancel) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            ListItem(headlineContent = { Text("Tomar foto") }, leadingContent = { Icon(Icons.Outlined.PhotoCamera, "Cámara", tint = MaterialTheme.colorScheme.onSurfaceVariant) }, modifier = Modifier.fillMaxWidth().clickable(onClick = onCamera), tonalElevation = 0.dp)
            ListItem(headlineContent = { Text("Elegir de galería") }, leadingContent = { Icon(Icons.Outlined.PhotoLibrary, "Galería", tint = MaterialTheme.colorScheme.onSurfaceVariant) }, modifier = Modifier.fillMaxWidth().clickable(onClick = onGallery))
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
        }
    }
}

@Composable private fun ProductFormDialog(c: AppContainer, code: String?, close: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var productCode by rememberSaveable { mutableStateOf(code ?: "") }
    var purchase by rememberSaveable { mutableStateOf("") }
    var sale by rememberSaveable { mutableStateOf("") }
    var stock by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var scanning by rememberSaveable { mutableStateOf(false) }
    var imageSheet by rememberSaveable { mutableStateOf(false) }
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var createdId by rememberSaveable { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            if (uri != selected) {
                cancellableRunCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                deleteTemporaryPhoto(context, selected)
                selected = uri
            }
            error = null
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        cameraUri = null
        if (ok && uri != null) {
            deleteTemporaryPhoto(context, selected)
            selected = uri
            error = null
        } else deleteTemporaryPhoto(context, uri)
    }
    fun cancel() {
        deleteTemporaryPhoto(context, cameraUri)
        deleteTemporaryPhoto(context, selected)
        close()
    }
    fun takePhoto() {
        try {
            val uri = newProductCameraUri(context)
            cameraUri = uri
            camera.launch(uri)
        } catch (failure: Exception) {
            deleteTemporaryPhoto(context, cameraUri)
            cameraUri = null
            Log.w("ProductPhoto", "No se pudo abrir la cámara", failure)
            error = "No se pudo abrir la cámara para tomar la foto."
        }
    }
    fun pickPhoto() {
        try {
            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (failure: Exception) {
            Log.w("ProductPhoto", "No se pudo abrir el selector", failure)
            error = "No se pudo abrir el selector de imágenes."
        }
    }
    if (scanning) {
        BarcodeScanner({ productCode = it; scanning = false }, { scanning = false })
        return
    }
    AlertDialog(
        onDismissRequest = { if (!busy) cancel() },
        title = { Text("Nuevo producto") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, enabled = !busy && createdId == null)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(productCode, { productCode = it }, label = { Text("Código") }, modifier = Modifier.weight(1f), enabled = !busy && createdId == null)
                    TextButton(enabled = !busy && createdId == null, onClick = { scanning = true }) { Text("Escanear") }
                }
                OutlinedTextField(purchase, { purchase = it }, label = { Text("Precio compra") }, enabled = !busy && createdId == null)
                OutlinedTextField(sale, { sale = it }, label = { Text("Precio venta") }, enabled = !busy && createdId == null)
                OutlinedTextField(stock, { stock = it }, label = { Text("Stock") }, enabled = !busy && createdId == null)
                selected?.let { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = "Vista previa de la imagen seleccionada",
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentScale = ContentScale.Fit,
                        onError = { error = "No se pudo mostrar la imagen. Elige otra foto." }
                    )
                }
                TextButton(enabled = !busy, onClick = { imageSheet = true }) {
                    Icon(Icons.Outlined.PhotoCamera, "Imagen", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text(if (selected == null) "Agregar imagen" else "Cambiar imagen")
                }
                if (createdId != null) Text("Producto creado. Puedes reintentar la subida de la imagen.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = !busy, onClick = {
                val p = purchase.toDoubleOrNull()
                val s = sale.toDoubleOrNull()
                val n = stock.toIntOrNull()
                if (createdId == null && (name.isBlank() || productCode.isBlank() || p == null || s == null ||
                        n == null || p < 0 || s < 0 || n < 0)) {
                    error = "Completa datos válidos"
                } else {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val photo = selected?.let { prepareProductPhoto(context, it) }
                            val id = createdId ?: c.createProduct(name, productCode, p!!, s!!, n!!).id.also { createdId = it }
                            photo?.let { uploadPhoto(c, id, it) }
                            cancel()
                        } catch (failure: CancellationException) {
                            throw failure
                        } catch (failure: Exception) {
                            Log.e("ProductPhoto", "No se pudo guardar la imagen del producto", failure)
                            error = when {
                                failure is IllegalArgumentException -> failure.message
                                createdId != null -> "Producto creado, pero no se pudo subir la imagen. Reintenta."
                                failure is ApiFailure && failure.status == 409 -> "Ya existe un producto con ese código."
                                failure is ApiFailure -> failure.message
                                else -> "No se pudo crear el producto."
                            }
                        } finally {
                            busy = false
                        }
                    }
                }
            }) { Text(if (busy) "Guardando…" else "Guardar") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = ::cancel) { Text("Cancelar") } }
    )
    if (imageSheet) ImageSourceSheet(
        onCamera = { imageSheet = false; takePhoto() },
        onGallery = { imageSheet = false; pickPhoto() },
        onCancel = { imageSheet = false }
    )
}

@Composable private fun ProductEditDialog(c: AppContainer, product: Product, close: () -> Unit, saved: () -> Unit) {
    var name by remember { mutableStateOf(product.nombre) }
    var description by remember { mutableStateOf(product.descripcion.orEmpty()) }
    var purchase by remember { mutableStateOf(product.precio_compra.toString()) }
    var sale by remember { mutableStateOf(product.precio_venta.toString()) }
    var minimum by remember { mutableStateOf((product.stock_minimo ?: 5).toString()) }
    var selected by rememberSaveable { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var imageSheet by remember { mutableStateOf(false) }
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            if (uri != selected) {
                cancellableRunCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                deleteTemporaryPhoto(context, selected)
                selected = uri
            }
            error = null
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        cameraUri = null
        if (ok && uri != null) {
            deleteTemporaryPhoto(context, selected)
            selected = uri
            error = null
        } else deleteTemporaryPhoto(context, uri)
    }
    fun cancel() {
        deleteTemporaryPhoto(context, cameraUri)
        deleteTemporaryPhoto(context, selected)
        close()
    }
    fun takePhoto() {
        try {
            val uri = newProductCameraUri(context)
            cameraUri = uri
            camera.launch(uri)
        } catch (failure: Exception) {
            deleteTemporaryPhoto(context, cameraUri)
            cameraUri = null
            Log.w("ProductPhoto", "No se pudo abrir la cámara", failure)
            error = "No se pudo abrir la cámara para tomar la foto."
        }
    }
    fun pickPhoto() {
        try {
            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (failure: Exception) {
            Log.w("ProductPhoto", "No se pudo abrir el selector", failure)
            error = "No se pudo abrir el selector de imágenes."
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) cancel() },
        title = { Text("Editar producto") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") })
                OutlinedTextField(description, { description = it }, label = { Text("Descripción") })
                OutlinedTextField(purchase, { purchase = it }, label = { Text("Precio compra") })
                OutlinedTextField(sale, { sale = it }, label = { Text("Precio venta") })
                OutlinedTextField(minimum, { minimum = it }, label = { Text("Stock mínimo") })
                selected?.let { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = "Vista previa de la imagen seleccionada",
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentScale = ContentScale.Fit,
                        onError = { error = "No se pudo mostrar la imagen. Elige otra foto." }
                    )
                } ?: ProductImage(product, c, Modifier.fillMaxWidth().height(120.dp))
                TextButton({ imageSheet = true }) {
                    Icon(Icons.Outlined.PhotoCamera, "Imagen", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text("Cambiar imagen")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = !busy, onClick = {
                val p = purchase.toDoubleOrNull()
                val s = sale.toDoubleOrNull()
                val m = minimum.toIntOrNull()
                if (name.isBlank() || p == null || s == null || m == null || p < 0 || s < 0 || m < 0) {
                    error = "Completa datos válidos"
                } else {
                    busy = true
                    error = null
                    scope.launch {
                        var updated = false
                        try {
                            val photo = selected?.let { prepareProductPhoto(context, it) }
                            c.products.update(
                                product.id,
                                UpdateProductRequest(nombre = name, descripcion = description, precioCompra = p,
                                    precioVenta = s, stockMinimo = m)
                            ).getOrThrow()
                            updated = true
                            photo?.let { uploadPhoto(c, product.id, it) }
                            deleteTemporaryPhoto(context, selected)
                            saved()
                        } catch (failure: CancellationException) {
                            throw failure
                        } catch (failure: Exception) {
                            Log.e("ProductPhoto", "No se pudo guardar el producto o su imagen", failure)
                            error = when {
                                failure is IllegalArgumentException -> failure.message
                                updated -> "Los datos se guardaron, pero no se pudo subir la imagen. Reintenta."
                                failure is ApiFailure -> failure.message
                                else -> "No se pudieron guardar los cambios."
                            }
                        } finally {
                            busy = false
                        }
                    }
                }
            }) { Text(if (busy) "Guardando…" else "Guardar cambios") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = ::cancel) { Text("Cancelar") } }
    )
    if (imageSheet) ImageSourceSheet(
        onCamera = { imageSheet = false; takePhoto() },
        onGallery = { imageSheet = false; pickPhoto() },
        onCancel = { imageSheet = false }
    )
}

@Composable fun MerchandiseScreen(c: AppContainer, user: User, onCreate: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var product by remember { mutableStateOf<Product?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var amount by remember { mutableStateOf("1") }
    val scope = rememberCoroutineScope()
    fun find(value: String) { scope.launch { cancellableRunCatching { c.products.findByBarcode(value).getOrThrow() }.onSuccess { product = it; error = null }.onFailure { product = null; error = if ((it as? ApiFailure)?.status == 404) "Producto no registrado" else it.message } } }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Mercadería", style = MaterialTheme.typography.headlineSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(code, { code = it }, label = { Text("Código") }, modifier = Modifier.weight(1f), singleLine = true)
            Button({ find(code) }) { Text("Buscar") }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(150.dp)) { BarcodeScanner({ code = it; find(it) }, { }) }
        error?.let { ErrorState(it) }
        product?.let { p ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    ProductImage(p, c, Modifier.fillMaxWidth().height(100.dp))
                    Text(p.nombre, style = MaterialTheme.typography.titleLarge)
                    Text("Stock actual: " + p.stock)
                    OutlinedTextField(amount, { amount = it }, label = { Text("Cantidad") }, singleLine = true)
                    Row {
                        Button({ scope.launch { c.products.addStock(p.id, amount.toIntOrNull() ?: 0).onSuccess { product = it } } }) { Text("Aumentar") }
                        if (user.rol == "admin") {
                            Spacer(Modifier.width(8.dp))
                            Button({ scope.launch { c.decreaseStock(p.id, amount.toIntOrNull() ?: 0).onSuccess { product = it } } }) { Text("Disminuir") }
                        }
                    }
                }
            }
        }
        if (error == "Producto no registrado") Button({ onCreate() }, Modifier.fillMaxWidth()) { Text("Crear producto") }
    }
}

@Composable fun NewSaleScreen(c: AppContainer, user: User) {
    val vm: SaleViewModel = viewModel(key = "sale:${user.id}:${c.serverBaseUrl()}", factory = SimpleFactory { SaleViewModel(c, user) })
    val state by vm.state.collectAsStateWithLifecycle()
    var code by rememberSaveable { mutableStateOf("") }
    var scanning by rememberSaveable { mutableStateOf(false) }
    val feedback = rememberBarcodeFeedback()
    if (scanning && state.editable) {
        BarcodeScanner(
            onCode = { vm.lookup(it) { feedback.accepted() } },
            onClose = { scanning = false },
            onManualCode = { vm.lookup(it) { feedback.accepted() } },
            mode = ScannerMode.CONTINUOUS_SALE,
            beepOnDecode = false,
            bottomContent = {
                state.error?.let { Text(it, color = Color.White, modifier = Modifier.padding(8.dp)) }
                state.lastAdded?.let { product ->
                    val quantity = state.draft.cart.firstOrNull { it.product.id == product.id }?.quantity ?: 0
                    Text("Último agregado: ${product.nombre}\nCantidad en carrito: $quantity\nProductos escaneados: ${state.scannedCount}", color = Color.White, modifier = Modifier.padding(8.dp))
                }
            }
        )
        return
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Nueva venta", style = MaterialTheme.typography.headlineSmall)
        if (state.draft.pending?.uncertain == true) Text("Venta pendiente: reintenta para recuperar el resultado. Se conservará la misma operación.")
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(code, { code = it }, label = { Text("Código o nombre") }, modifier = Modifier.weight(1f), singleLine = true, enabled = state.editable)
            Button(enabled = state.editable, onClick = { vm.lookup(code) { feedback.accepted() } }) {
                Icon(Icons.Outlined.Search, "Buscar"); Spacer(Modifier.width(4.dp)); Text("Buscar")
            }
            TextButton(enabled = state.editable, onClick = { scanning = true }) { Text("Escanear") }
        }
        state.results.take(5).forEach { product ->
            TextButton(enabled = state.editable, onClick = { if (vm.add(product)) code = "" }) {
                Text(product.nombre + " · " + money(product.precio_venta) + " · stock " + product.stock)
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(state.draft.cart, key = { it.product.id }) { line ->
                SaleLine(line, state.editable, { vm.add(line.product) }, { vm.decrease(line.product.id) })
            }
        }
        Text("Total: " + money(state.draft.total), style = MaterialTheme.typography.headlineMedium)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Button(enabled = state.draft.cart.isNotEmpty() && !state.busy && !state.recoveryBlocked, onClick = { scanning = false; vm.confirm() }, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.busy) "Registrando…" else if (state.draft.pending != null) "REINTENTAR VENTA" else "CONFIRMAR VENTA")
        }
    }
}
@Composable private fun SaleLine(line: CartLine, enabled: Boolean, add: () -> Unit, remove: () -> Unit) {
    val product = line.product
    Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(product.nombre)
            Text(money(product.precio_venta) + " × " + line.quantity + " = " + money(product.precio_venta * line.quantity))
            Text("Disponible: ${product.stock}", style = MaterialTheme.typography.bodySmall)
        }
        TextButton(enabled = enabled, onClick = remove) { Text("−") }
        TextButton(enabled = enabled && line.quantity < product.stock, onClick = add) { Text("+") }
    }
}

@Composable fun SalesScreen(c: AppContainer, admin: Boolean) {
    var detail by remember { mutableStateOf<SaleDetailDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    QueryContent(admin, { if (admin) c.sales() else c.mySales() }) { sales ->
        LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
            item { Text(if (admin) "Ventas" else "Mis ventas", style = MaterialTheme.typography.headlineSmall); error?.let { ErrorState(it) } }
            if (sales.isEmpty()) item { EmptyState("No hay ventas todavía.") }
            items(sales) { sale -> SaleRow(sale) { scope.launch {
                cancellableRunCatching { c.sale(sale.id) }.onSuccess { detail = it; error = null }.onFailure { error = it.message }
            } } }
        }
    }
    detail?.let { SaleDetailDialog(it) { detail = null } }
}
@Composable private fun SaleRow(s: SaleSummaryDto, click: () -> Unit) { Card(onClick = click, modifier = Modifier.fillMaxWidth().padding(4.dp)) { Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Venta #" + s.id); Text(money(s.total)) } } }
@Composable private fun SaleDetailDialog(s: SaleDetailDto, close: () -> Unit) { AlertDialog(onDismissRequest = close, title = { Text("Venta #" + s.id) }, text = { Column { Text(s.fecha_creacion + " UTC"); s.items.forEach { Text((it.producto ?: "Producto") + " × " + it.cantidad + " · " + money(it.subtotal)) }; Text("Total: " + money(s.total)) } }, confirmButton = { Button(close) { Text("Cerrar") } }) }

@Composable fun ReportsScreen(c: AppContainer, admin: Boolean) {
    var period by rememberSaveable { mutableStateOf("dia") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(if (admin) "Reportes" else "Mis reportes", style = MaterialTheme.typography.headlineSmall)
        Row { listOf("dia", "semana", "mes", "anio").forEach { p -> FilterChip(period == p, { period = p }, label = { Text(p) }, modifier = Modifier.padding(2.dp)) } }
        QueryContent(period, { c.report(period) to c.topProducts(period) }) { (report, top) ->
            LazyColumn {
                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { StatCard("Ventas", report.ventas.toString(), Modifier.weight(1f)); StatCard("Ingresos", money(report.ingresos), Modifier.weight(1f)); StatCard("Unidades", report.unidadesVendidas.toString(), Modifier.weight(1f)) } }
                item { Text("Productos más vendidos · $period", style = MaterialTheme.typography.titleMedium) }
                if (top.isEmpty()) item { EmptyState("No hay productos vendidos en este período.") }
                items(top) { Text(it.nombre + " · " + it.unidades + " unidades", Modifier.padding(5.dp)) }
                if (report.historialTruncado) item { Text("El historial detallado está limitado a 100 ventas; los totales incluyen todo el período.") }
            }
        }
    }
}
@Composable fun InventoryScreen(c: AppContainer) {
    QueryContent(Unit, { c.inventory() }) { list ->
        LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
            item { Text("Movimientos de inventario", style = MaterialTheme.typography.headlineSmall) }
            if (list.isEmpty()) item { EmptyState("No hay movimientos todavía.") }
            items(list) { Text(it.fecha_creacion + " UTC · " + it.producto + " · " + it.tipo + " · " + it.stock_anterior + " → " + it.stock_nuevo, Modifier.padding(6.dp)) }
        }
    }
}
@Composable fun CategoriesScreen(c: AppContainer) {
    var editor by remember { mutableStateOf<CategoryDto?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row { Text("Categorías", style = MaterialTheme.typography.headlineSmall); Button({ editor = CategoryDto(0, "") }) { Text("Crear") } }
        QueryContent(tick, { c.categories() }) { list ->
            LazyColumn { if (list.isEmpty()) item { EmptyState("No hay categorías.") }; items(list) { category ->
                Card(Modifier.fillMaxWidth().padding(4.dp)) { Row(Modifier.padding(10.dp)) { Text(category.nombre, Modifier.weight(1f)); TextButton({ editor = category }) { Text("Editar") } } }
            } }
        }
    }
    editor?.let { CategoryEditor(c, it, { editor = null }) { tick++; editor = null } }
}
@Composable private fun CategoryEditor(c: AppContainer, category: CategoryDto, close: () -> Unit, saved: () -> Unit) { var name by remember { mutableStateOf(category.nombre) }; var description by remember { mutableStateOf(category.descripcion.orEmpty()) }; var error by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope(); AlertDialog(onDismissRequest = close, title = { Text(if (category.id == 0L) "Nueva categoría" else "Editar categoría") }, text = { Column { OutlinedTextField(name, { name = it }, label = { Text("Nombre") }); OutlinedTextField(description, { description = it }, label = { Text("Descripción") }); error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } }, confirmButton = { Button({ if (name.isBlank()) error = "El nombre es obligatorio" else scope.launch { cancellableRunCatching { if (category.id == 0L) c.createCategory(name, description) else c.updateCategory(category.id, name, description) }.onSuccess { saved() }.onFailure { error = it.message } } }) { Text("Guardar") } }, dismissButton = { TextButton(close) { Text("Cancelar") } }) }
@Composable fun UsersScreen(c: AppContainer) {
    var create by remember { mutableStateOf(false) }
    var passwordUser by remember { mutableStateOf<AdminUserDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row { Text("Usuarios", style = MaterialTheme.typography.headlineSmall); Button({ create = true }) { Text("Crear usuario") } }
        error?.let { ErrorState(it) }
        QueryContent(tick, { c.users() }) { list ->
            LazyColumn { if (list.isEmpty()) item { EmptyState("No hay usuarios.") }; items(list) { user ->
                Card(Modifier.fillMaxWidth().padding(4.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text(user.nombre + " · " + user.username + " · " + user.rol + if (user.activo == 1) "" else " · Inactivo")
                        Row {
                            TextButton({ passwordUser = user }) { Text("Cambiar clave") }
                            TextButton({ scope.launch { cancellableRunCatching { c.setUserActive(user.id, user.activo != 1) }.onSuccess { error = null; tick++ }.onFailure { error = it.message } } }) { Text(if (user.activo == 1) "Desactivar" else "Activar") }
                        }
                    }
                }
            } }
        }
    }
    if (create) UserEditor(c, { create = false }) { tick++; create = false }
    passwordUser?.let { PasswordEditor(c, it, { passwordUser = null }) { tick++; passwordUser = null } }
}
@Composable private fun UserEditor(c: AppContainer, close: () -> Unit, saved: () -> Unit) { var name by remember { mutableStateOf("") }; var username by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var role by remember { mutableStateOf("vendedor") }; var error by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope(); AlertDialog(onDismissRequest = close, title = { Text("Crear usuario") }, text = { Column { OutlinedTextField(name, { name = it }, label = { Text("Nombre") }); OutlinedTextField(username, { username = it }, label = { Text("Usuario") }); OutlinedTextField(password, { password = it }, label = { Text("Contraseña (mín. 8)") }, visualTransformation = PasswordVisualTransformation()); OutlinedTextField(role, { role = it }, label = { Text("Rol") }); error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } }, confirmButton = { Button({ if (name.isBlank() || username.isBlank() || password.isBlank()) error = "Completa los datos" else scope.launch { cancellableRunCatching { c.createUser(name, username, password, role) }.onSuccess { saved() }.onFailure { error = if (it.message?.contains("409") == true) "Ya existe ese usuario." else it.message } } }) { Text("Guardar") } }, dismissButton = { TextButton(close) { Text("Cancelar") } }) }
@Composable private fun PasswordEditor(c: AppContainer, user: AdminUserDto, close: () -> Unit, saved: () -> Unit) { var password by remember { mutableStateOf("") }; var confirmation by remember { mutableStateOf("") }; var error by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope(); AlertDialog(onDismissRequest = close, title = { Text("Cambiar contraseña de ${user.username}") }, text = { Column { OutlinedTextField(password, { password = it }, label = { Text("Nueva contraseña (mín. 8)") }, visualTransformation = PasswordVisualTransformation()); OutlinedTextField(confirmation, { confirmation = it }, label = { Text("Confirmar contraseña") }, visualTransformation = PasswordVisualTransformation()); error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } }, confirmButton = { Button({ if (password.isBlank() || password != confirmation) error = "Las contraseñas deben coincidir" else scope.launch { cancellableRunCatching { c.setUserPassword(user.id, password) }.onSuccess { saved() }.onFailure { error = it.message } } }) { Text("Guardar") } }, dismissButton = { TextButton(close) { Text("Cancelar") } }) }

private inline fun <T> cancellableRunCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) { throw cancelled }
catch (error: Exception) { Result.failure(error) }

private sealed interface QueryState<out T> {
    data object Loading : QueryState<Nothing>
    data class Data<T>(val value: T) : QueryState<T>
    data class Failed(val message: String) : QueryState<Nothing>
}
@Composable private fun <T> QueryContent(key: Any?, load: suspend () -> T, content: @Composable (T) -> Unit) {
    var state by remember { mutableStateOf<QueryState<T>>(QueryState.Loading) }
    var refresh by remember { mutableIntStateOf(0) }
    val currentLoad by rememberUpdatedState(load)
    LaunchedEffect(key, refresh) {
        state = QueryState.Loading
        try { state = QueryState.Data(currentLoad()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { state = QueryState.Failed(error.message ?: "No se pudo cargar") }
    }
    when (val current = state) {
        QueryState.Loading -> LoadingState()
        is QueryState.Failed -> ErrorState(current.message) { refresh++ }
        is QueryState.Data -> content(current.value)
    }
}
