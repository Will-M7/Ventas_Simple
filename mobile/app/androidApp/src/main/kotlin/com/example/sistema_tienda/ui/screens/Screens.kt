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
import com.example.sistema_tienda.domain.model.*
import com.example.sistema_tienda.ui.BarcodeFeedback
import com.example.sistema_tienda.ui.BarcodeScanner
import com.example.sistema_tienda.ui.ScannerMode
import com.example.sistema_tienda.ui.rememberBarcodeFeedback
import com.example.sistema_tienda.ui.components.*
import com.example.sistema_tienda.ui.navigation.SimpleFactory
import com.example.sistema_tienda.ui.viewmodels.LoginViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
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
@Composable private fun ServerSettingsDialog(c: AppContainer, close: () -> Unit, snack: SnackbarHostState) { var ip by remember { mutableStateOf(c.getServerIp()) }; var busy by remember { mutableStateOf(false) }; val scope = rememberCoroutineScope(); AlertDialog(onDismissRequest = { if (!busy) close() }, title = { Text("Servidor") }, text = { Column { OutlinedTextField(ip, { ip = it }, label = { Text("IP o host") }, singleLine = true); Text("API: http://" + ip.trim() + ":3000/api/", style = MaterialTheme.typography.bodySmall) } }, confirmButton = { Button(enabled = !busy && ip.trim().isNotBlank(), onClick = { c.saveServerIp(ip); busy = true; scope.launch { try { c.testServerConnection().onSuccess { snack.showSnackbar("Servidor disponible") }.onFailure { snack.showSnackbar("No se pudo conectar al servidor.") } } finally { busy = false; close() } } }) { Text(if (busy) "Probando…" else "Probar conexión") } }, dismissButton = { TextButton(enabled = !busy, onClick = close) { Text("Cancelar") } }) }
@Composable private fun ServerSettingsContent(c: AppContainer, snack: SnackbarHostState, modifier: Modifier, onClose: () -> Unit) { Column(modifier.padding(24.dp)) { Text("Configuración del servidor", style = MaterialTheme.typography.headlineSmall); Text("Servidor: " + c.getServerIp()); Spacer(Modifier.height(16.dp)); ServerSettingsDialog(c, onClose, snack) } }

@Composable fun DashboardScreen(c: AppContainer, user: User, onNavigate: (String) -> Unit) {
    var data by remember { mutableStateOf<DashboardDto?>(null) }; var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { runCatching { c.dashboard() }.onSuccess { data = it }.onFailure { error = it.message } }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(if (user.rol == "admin") "Dashboard admin" else "Inicio", style = MaterialTheme.typography.headlineSmall); Text("Hola, " + user.nombre, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (error != null) item { ErrorState(error!!) } else if (data == null) item { LoadingState() } else {
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { StatCard("Ventas hoy", data!!.ventasHoy.toString(), Modifier.weight(1f)); StatCard("Ingresos", money(data!!.ingresosHoy), Modifier.weight(1f)) } }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { StatCard("Unidades", data!!.unidadesVendidasHoy.toString(), Modifier.weight(1f)); if (user.rol == "admin") StatCard("Stock bajo", data!!.productosStockBajo.toString(), Modifier.weight(1f)) } }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ onNavigate("sale") }, Modifier.weight(1f)) { Text("Nueva venta") }; if (user.rol.equals("admin", true)) OutlinedButton({ onNavigate("products") }, Modifier.weight(1f)) { Text("Productos") } } }
            item { Text("Ventas recientes", style = MaterialTheme.typography.titleMedium) }; items(data!!.ventasRecientes) { SaleRow(it) {} }
        }
    }
}

@Composable fun ProductsScreen(c: AppContainer, user: User) {
    var search by remember { mutableStateOf("") }; var list by remember { mutableStateOf<List<Product>>(emptyList()) }; var loading by remember { mutableStateOf(true) }; var error by remember { mutableStateOf<String?>(null) }; var selected by remember { mutableStateOf<Product?>(null) }; var registeredProduct by remember { mutableStateOf<Product?>(null) }; var create by remember { mutableStateOf(false) }; var createCode by remember { mutableStateOf<String?>(null) }; var scanning by remember { mutableStateOf(false) }; var unknownCode by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope(); val snack = remember { SnackbarHostState() }
    var refreshTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(search, refreshTick) { kotlinx.coroutines.delay(250); runCatching { c.searchProducts(search).map { it.toDomain() } }.onSuccess { list = it; loading = false; error = null }.onFailure { error = it.message; loading = false } }
    if (scanning) {
        BarcodeScanner(onCode = { code -> scanning = false; scope.launch { c.products.findByBarcode(code).onSuccess { registeredProduct = it }.onFailure { unknownCode = code } } }, onClose = { scanning = false }, onManualCode = { code -> scanning = false; scope.launch { c.products.findByBarcode(code).onSuccess { registeredProduct = it }.onFailure { unknownCode = code } } })
        return
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(search, { search = it }, label = { Text("Buscar nombre o código") }, modifier = Modifier.weight(1f), singleLine = true); TextButton({ scanning = true }) { Text("+ Nuevo") } }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton({ scanning = true }) { Text("Escanear producto") } }; when { loading -> LoadingState(); error != null -> ErrorState(error!!); list.isEmpty() -> EmptyState("No se encontraron productos."); else -> LazyColumn { items(list) { ProductCard(it, c) { selected = it } } } } }
    selected?.let { ProductDetailDialog(c, it, user) { selected = null; refreshTick++ } }; if (create) ProductFormDialog(c, createCode) { create = false; refreshTick++ }; registeredProduct?.let { product -> AlertDialog(onDismissRequest = { registeredProduct = null; selected = product }, title = { Text("Este producto ya está registrado.") }, text = { Text("Se abrirá el detalle del producto existente.") }, confirmButton = { Button({ registeredProduct = null; selected = product }) { Text("Ver detalle") } }) }; unknownCode?.let { code -> AlertDialog(onDismissRequest = { unknownCode = null }, title = { Text("Producto no registrado") }, text = { Text("No existe un producto con el código $code.") }, confirmButton = { Button({ unknownCode = null; createCode = code; create = true }) { Text("Crear producto") } }, dismissButton = { TextButton({ unknownCode = null }) { Text("Cerrar") } }) }; SnackbarHost(snack)
}
private fun ProductDto.toDomain() = Product(id, codigo ?: codigo_barras.orEmpty(), nombre, precio_compra, precio_venta, stock, descripcion, stock_minimo, categoria_nombre, imagen, activo == 1, categoria_id)

@Composable private fun ProductDetailDialog(c: AppContainer, product: Product, user: User, close: () -> Unit) { var stock by remember { mutableIntStateOf(product.stock) }; var edit by remember { mutableStateOf(false) }; var active by remember { mutableStateOf(product.activo) }; var stockDialog by remember { mutableStateOf<Boolean?>(null) }; val scope = rememberCoroutineScope(); if (edit) ProductEditDialog(c, product, close = { edit = false }) { stock = product.stock; active = product.activo; edit = false; close() } else AlertDialog(onDismissRequest = close, title = { Text(product.nombre) }, text = { Column { ProductImage(product.copy(activo = active), c, Modifier.fillMaxWidth().height(140.dp)); Text("Código: " + product.codigo_barras); Text("Descripción: " + (product.descripcion ?: "Sin descripción")); Text("Compra: " + money(product.precio_compra)); Text("Venta: " + money(product.precio_venta)); Text("Stock: " + stock); Text("Stock mínimo: " + (product.stock_minimo ?: 5)); Text(product.categoria_nombre ?: "Sin categoría"); Text(if (active) "Activo" else "Inactivo"); Row { Button({ stockDialog = true }) { Text("Aumentar") }; if (user.rol.equals("admin", true)) { Spacer(Modifier.width(8.dp)); OutlinedButton({ stockDialog = false }) { Text("Disminuir") } } } } }, confirmButton = { if (user.rol.equals("admin", true)) Button({ edit = true }) { Text("Editar") } }, dismissButton = { TextButton(close) { Text("Cerrar") } }); stockDialog?.let { increase -> StockQuantityDialog(stock, increase, { stockDialog = null }) { quantity -> scope.launch { val result = if (increase) c.products.addStock(product.id, quantity) else c.products.decreaseStock(product.id, quantity); result.onSuccess { stock = it.stock; stockDialog = null }.onFailure { stockDialog = null } } } } }

@Composable private fun StockQuantityDialog(current: Int, increase: Boolean, close: () -> Unit, confirm: (Int) -> Unit) { var value by remember { mutableStateOf("1") }; val quantity = value.toIntOrNull(); val valid = quantity != null && quantity >= 1 && (increase || quantity <= current); val error = if (!increase && quantity != null && quantity > current) "No hay suficiente stock." else null; AlertDialog(onDismissRequest = close, title = { Text(if (increase) "Aumentar stock" else "Disminuir stock") }, text = { Column { Text("Stock actual: $current"); Row(verticalAlignment = Alignment.CenterVertically) { TextButton({ value = ((quantity ?: 1) - 1).coerceAtLeast(1).toString() }) { Text("−") }; OutlinedTextField(value, { value = it.filter(Char::isDigit) }, label = { Text("Cantidad") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f)); TextButton({ value = ((quantity ?: 0) + 1).toString() }) { Text("+") } }; Text("Stock resultante: ${if (increase) current + (quantity ?: 0) else current - (quantity ?: 0)}"); error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } }, confirmButton = { Button(enabled = valid, onClick = { confirm(quantity!!) }) { Text("Confirmar") } }, dismissButton = { TextButton(onClick = close) { Text("Cancelar") } }) }
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
        runCatching { context.contentResolver.delete(uri, null, null) }
            .onFailure { Log.w("ProductPhoto", "No se pudo limpiar la foto temporal", it) }
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
    var name by remember { mutableStateOf("") }
    var productCode by remember { mutableStateOf(code ?: "") }
    var purchase by remember { mutableStateOf("") }
    var sale by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var imageSheet by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var createdId by remember { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            deleteTemporaryPhoto(context, selected)
            selected = uri
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
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(productCode, { productCode = it }, label = { Text("Código") }, modifier = Modifier.weight(1f))
                    TextButton({ scanning = true }) { Text("Escanear") }
                }
                OutlinedTextField(purchase, { purchase = it }, label = { Text("Precio compra") })
                OutlinedTextField(sale, { sale = it }, label = { Text("Precio venta") })
                OutlinedTextField(stock, { stock = it }, label = { Text("Stock") })
                selected?.let { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = "Vista previa de la imagen seleccionada",
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentScale = ContentScale.Fit,
                        onError = { error = "No se pudo mostrar la imagen. Elige otra foto." }
                    )
                }
                TextButton({ imageSheet = true }) {
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
                                failure.message?.contains("409") == true -> "Ya existe un producto con ese código."
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
    var selected by remember { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var imageSheet by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            deleteTemporaryPhoto(context, selected)
            selected = uri
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
    fun find(value: String) { scope.launch { runCatching { c.products.findByBarcode(value).getOrThrow() }.onSuccess { product = it; error = null }.onFailure { product = null; error = "Producto no registrado" } } }
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
    var code by remember { mutableStateOf("") }; var cart by remember { mutableStateOf<List<Product>>(emptyList()) }; var results by remember { mutableStateOf<List<Product>>(emptyList()) }; var scanning by remember { mutableStateOf(false) }; var busy by remember { mutableStateOf(false) }; var message by remember { mutableStateOf<String?>(null) }; var error by remember { mutableStateOf<String?>(null) }; var lastAdded by remember { mutableStateOf<String?>(null) }; var scannedCount by remember { mutableIntStateOf(0) }; val scope = rememberCoroutineScope(); val feedback = rememberBarcodeFeedback(); val scanQueue = remember { Channel<String>(Channel.UNLIMITED) }
    fun lookup(value: String) { if (value.isBlank()) return; scope.launch { c.products.findByBarcode(value).onSuccess { product -> val inCart = cart.firstOrNull { it.id == product.id }?.stock ?: 0; when { !product.activo -> error = "Producto inactivo"; product.stock <= 0 || inCart >= product.stock -> error = "Producto sin stock"; else -> { cart = addProduct(cart, product); lastAdded = product.nombre; scannedCount++; error = null; feedback.accepted() } } }.onFailure { failure -> if (failure.message?.contains("Producto no registrado") == true) { results = runCatching { c.searchProducts(value).map { it.toDomain() } }.getOrDefault(emptyList()); error = if (results.isEmpty()) "Producto no registrado" else null } else { error = "No se pudo consultar el producto" } } } }
    LaunchedEffect(scanQueue) { scanQueue.consumeEach { lookup(it) } }
    if (scanning) { BarcodeScanner(onCode = { scanQueue.trySend(it) }, onClose = { scanning = false }, onManualCode = { scanQueue.trySend(it) }, mode = ScannerMode.CONTINUOUS_SALE, beepOnDecode = false, bottomContent = { error?.let { Text(it, color = Color.White, modifier = Modifier.padding(8.dp)) }; lastAdded?.let { Text("Último agregado: $it\nCantidad en carrito: ${cart.firstOrNull { p -> p.nombre == it }?.stock ?: 0}\nProductos escaneados: $scannedCount", color = Color.White, modifier = Modifier.padding(8.dp)) } }); return }
    Column(Modifier.fillMaxSize().padding(16.dp)) { Text("Nueva venta", style = MaterialTheme.typography.headlineSmall); Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(code, { code = it }, label = { Text("Código o nombre") }, modifier = Modifier.weight(1f), singleLine = true); Button({ lookup(code) }) { Icon(Icons.Outlined.Search, "Buscar"); Spacer(Modifier.width(4.dp)); Text("Buscar") }; TextButton({ scanning = true }) { Text("Escanear") } }; results.take(5).forEach { p -> TextButton({ if (p.activo && p.stock > 0) { cart = addProduct(cart, p); results = emptyList(); code = "" } else error = if (!p.activo) "Producto inactivo" else "Producto sin stock" }) { Text(p.nombre + " · " + money(p.precio_venta) + " · stock " + p.stock) } }; LazyColumn(Modifier.weight(1f)) { items(cart) { p -> SaleLine(p, { cart = cart.map { if (it.id == p.id && it.stock < p.stock) it.copy(stock = it.stock + 1) else it } }, { cart = cart.filterNot { it.id == p.id } }) } }; Text("Total: " + money(cart.sumOf { it.precio_venta * it.stock }), style = MaterialTheme.typography.headlineMedium); error?.let { Text(it, color = MaterialTheme.colorScheme.error) }; message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }; Button(enabled = cart.isNotEmpty() && !busy, onClick = { busy = true; scope.launch { c.processSale(user.id, cart.map { SaleItem(it.id, it.stock) }).onSuccess { message = "Venta registrada #" + it.id; cart = emptyList() }.onFailure { error = "No se pudo registrar la venta." }; busy = false } }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Registrando…" else "CONFIRMAR VENTA") } }
}
private fun addProduct(cart: List<Product>, p: Product): List<Product> { val current = cart.firstOrNull { it.id == p.id }; return if (current == null) cart + p.copy(stock = 1) else cart.map { if (it.id == p.id && it.stock < p.stock) it.copy(stock = it.stock + 1) else it } }
@Composable private fun SaleLine(p: Product, add: () -> Unit, remove: () -> Unit) { Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(p.nombre); Text(money(p.precio_venta) + " × " + p.stock + " = " + money(p.precio_venta * p.stock)) }; TextButton(remove) { Text("−") }; TextButton(add) { Text("+") } } }

@Composable fun SalesScreen(c: AppContainer, admin: Boolean) { var sales by remember { mutableStateOf<List<SaleSummaryDto>>(emptyList()) }; var detail by remember { mutableStateOf<SaleDetailDto?>(null) }; val scope = rememberCoroutineScope(); LaunchedEffect(Unit) { sales = runCatching { if (admin) c.sales() else c.mySales() }.getOrDefault(emptyList()) }; LazyColumn(Modifier.fillMaxSize().padding(16.dp)) { item { Text(if (admin) "Ventas" else "Mis ventas", style = MaterialTheme.typography.headlineSmall) }; if (sales.isEmpty()) item { EmptyState("No hay ventas todavía.") }; items(sales) { sale -> SaleRow(sale) { scope.launch { detail = runCatching { c.sale(sale.id) }.getOrNull() } } } }; detail?.let { SaleDetailDialog(it) { detail = null } } }
@Composable private fun SaleRow(s: SaleSummaryDto, click: () -> Unit) { Card(onClick = click, modifier = Modifier.fillMaxWidth().padding(4.dp)) { Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Venta #" + s.id); Text(money(s.total)) } } }
@Composable private fun SaleDetailDialog(s: SaleDetailDto, close: () -> Unit) { AlertDialog(onDismissRequest = close, title = { Text("Venta #" + s.id) }, text = { Column { Text(s.fecha_creacion); s.items.forEach { Text((it.producto ?: "Producto") + " × " + it.cantidad + " · " + money(it.subtotal)) }; Text("Total: " + money(s.total)) } }, confirmButton = { Button(close) { Text("Cerrar") } }) }

@Composable fun ReportsScreen(c: AppContainer, admin: Boolean) { var period by remember { mutableStateOf("dia") }; var report by remember { mutableStateOf<DashboardReportDto?>(null) }; var top by remember { mutableStateOf<List<TopProductDto>>(emptyList()) }; LaunchedEffect(period) { report = runCatching { c.report(period) }.getOrNull(); top = runCatching { c.topProducts() }.getOrDefault(emptyList()) }; LazyColumn(Modifier.fillMaxSize().padding(16.dp)) { item { Text(if (admin) "Reportes" else "Mis reportes", style = MaterialTheme.typography.headlineSmall); Row { listOf("dia", "semana", "mes", "anio").forEach { p -> FilterChip(period == p, { period = p }, label = { Text(p) }, modifier = Modifier.padding(2.dp)) } } }; report?.let { item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { StatCard("Ventas", it.ventas.toString(), Modifier.weight(1f)); StatCard("Ingresos", money(it.ingresos), Modifier.weight(1f)); StatCard("Unidades", it.unidadesVendidas.toString(), Modifier.weight(1f)) } } }; item { Text("Productos más vendidos", style = MaterialTheme.typography.titleMedium) }; items(top) { Text(it.nombre + " · " + it.unidades + " unidades", Modifier.padding(5.dp)) } } }
@Composable fun InventoryScreen(c: AppContainer) { var list by remember { mutableStateOf<List<MovementDto>>(emptyList()) }; LaunchedEffect(Unit) { list = runCatching { c.inventory() }.getOrDefault(emptyList()) }; LazyColumn(Modifier.fillMaxSize().padding(16.dp)) { item { Text("Movimientos de inventario", style = MaterialTheme.typography.headlineSmall) }; if (list.isEmpty()) item { EmptyState("No hay movimientos todavía.") }; items(list) { Text(it.fecha_creacion + " · " + it.producto + " · " + it.tipo + " · " + it.stock_anterior + " → " + it.stock_nuevo, Modifier.padding(6.dp)) } } }
@Composable fun CategoriesScreen(c: AppContainer) { var list by remember { mutableStateOf<List<CategoryDto>>(emptyList()) }; var editor by remember { mutableStateOf<CategoryDto?>(null) }; val scope = rememberCoroutineScope(); fun reload() { scope.launch { list = runCatching { c.categories() }.getOrDefault(emptyList()) } }; LaunchedEffect(Unit) { reload() }; Column(Modifier.fillMaxSize().padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text("Categorías", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.width(12.dp)); Button({ editor = CategoryDto(0, "") }) { Text("Crear") } }; LazyColumn { items(list) { category -> Card(Modifier.fillMaxWidth().padding(4.dp)) { Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Text(category.nombre + if (category.activo == 1) "" else " · Inactiva", Modifier.weight(1f)); TextButton({ editor = category }) { Text("Editar") } } } } } }; editor?.let { CategoryEditor(c, it, { editor = null }) { reload(); editor = null } } }
@Composable private fun CategoryEditor(c: AppContainer, category: CategoryDto, close: () -> Unit, saved: () -> Unit) { var name by remember { mutableStateOf(category.nombre) }; var description by remember { mutableStateOf(category.descripcion.orEmpty()) }; var error by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope(); AlertDialog(onDismissRequest = close, title = { Text(if (category.id == 0L) "Nueva categoría" else "Editar categoría") }, text = { Column { OutlinedTextField(name, { name = it }, label = { Text("Nombre") }); OutlinedTextField(description, { description = it }, label = { Text("Descripción") }); error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } }, confirmButton = { Button({ if (name.isBlank()) error = "El nombre es obligatorio" else scope.launch { runCatching { if (category.id == 0L) c.createCategory(name, description) else c.updateCategory(category.id, name, description) }.onSuccess { saved() }.onFailure { error = it.message } } }) { Text("Guardar") } }, dismissButton = { TextButton(close) { Text("Cancelar") } }) }
@Composable fun UsersScreen(c: AppContainer) { var list by remember { mutableStateOf<List<AdminUserDto>>(emptyList()) }; var create by remember { mutableStateOf(false) }; var passwordUser by remember { mutableStateOf<AdminUserDto?>(null) }; val scope = rememberCoroutineScope(); fun reload() { scope.launch { list = runCatching { c.users() }.getOrDefault(emptyList()) } }; LaunchedEffect(Unit) { reload() }; Column(Modifier.fillMaxSize().padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text("Usuarios", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.width(12.dp)); Button({ create = true }) { Text("Crear usuario") } }; LazyColumn { items(list) { user -> Card(Modifier.fillMaxWidth().padding(4.dp)) { Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Text(user.nombre + " · " + user.username + " · " + user.rol + if (user.activo == 1) "" else " · Inactivo", Modifier.weight(1f)); TextButton({ passwordUser = user }) { Text("Cambiar clave") }; TextButton({ scope.launch { c.setUserActive(user.id, user.activo != 1); reload() } }) { Text(if (user.activo == 1) "Desactivar" else "Activar") } } } } } }; if (create) UserEditor(c, { create = false }) { reload(); create = false }; passwordUser?.let { PasswordEditor(c, it, { passwordUser = null }) { reload(); passwordUser = null } } }
@Composable private fun UserEditor(c: AppContainer, close: () -> Unit, saved: () -> Unit) { var name by remember { mutableStateOf("") }; var username by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var role by remember { mutableStateOf("vendedor") }; var error by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope(); AlertDialog(onDismissRequest = close, title = { Text("Crear usuario") }, text = { Column { OutlinedTextField(name, { name = it }, label = { Text("Nombre") }); OutlinedTextField(username, { username = it }, label = { Text("Usuario") }); OutlinedTextField(password, { password = it }, label = { Text("Contraseña") }); OutlinedTextField(role, { role = it }, label = { Text("Rol") }); error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } }, confirmButton = { Button({ if (name.isBlank() || username.isBlank() || password.isBlank()) error = "Completa los datos" else scope.launch { runCatching { c.createUser(name, username, password, role) }.onSuccess { saved() }.onFailure { error = if (it.message?.contains("409") == true) "Ya existe ese usuario." else it.message } } }) { Text("Guardar") } }, dismissButton = { TextButton(close) { Text("Cancelar") } }) }
@Composable private fun PasswordEditor(c: AppContainer, user: AdminUserDto, close: () -> Unit, saved: () -> Unit) { var password by remember { mutableStateOf("") }; var confirmation by remember { mutableStateOf("") }; var error by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope(); AlertDialog(onDismissRequest = close, title = { Text("Cambiar contraseña de ${user.username}") }, text = { Column { OutlinedTextField(password, { password = it }, label = { Text("Nueva contraseña") }); OutlinedTextField(confirmation, { confirmation = it }, label = { Text("Confirmar contraseña") }); error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } }, confirmButton = { Button({ if (password.isBlank() || password != confirmation) error = "Las contraseñas deben coincidir" else scope.launch { runCatching { c.setUserPassword(user.id, password) }.onSuccess { saved() }.onFailure { error = it.message } } }) { Text("Guardar") } }, dismissButton = { TextButton(close) { Text("Cancelar") } }) }
