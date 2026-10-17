package com.example.sistema_tienda

import com.google.gson.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.io.File
import java.util.UUID
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.CancellationException

private fun money(value: Double) = "S/ %.2f".format(value)

@Composable fun DesktopApp() {
    val api = remember { DesktopApi() }
    var user by remember { mutableStateOf(api.savedUser()) }
    var screen by remember { mutableStateOf("dashboard") }
    val expired by api.sessionExpired.collectAsState()
    LaunchedEffect(expired) { if (expired) { user = null; screen = "dashboard" } }
    if (user == null) DesktopLogin(api) { screen = "dashboard"; user = it }
    else DesktopShell(api, user!!, screen) { if (it == "logout") { api.logout(); user = null; screen = "dashboard" } else screen = it }
}

@Composable private fun DesktopLogin(api: DesktopApi, onLogin: (DesktopUser) -> Unit) {
    var username by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    var host by remember { mutableStateOf(api.host) }; var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }; val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(80.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Sistematienda", style = MaterialTheme.typography.displaySmall)
        OutlinedTextField(username, { username = it }, label = { Text("Usuario") }, singleLine = true)
        OutlinedTextField(password, { password = it }, label = { Text("Contraseña") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        OutlinedTextField(host, { host = it }, label = { Text("IP o URL del servidor") }, singleLine = true)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row {
            OutlinedButton(enabled = !busy, onClick = { scope.launch { safeRunCatching { api.testConnection(host) }.onSuccess { error = "Servidor disponible" }.onFailure { error = it.message } } }) { Text("Probar conexión") }
            Button(enabled = !busy && username.isNotBlank() && password.isNotBlank() && host.isNotBlank(), onClick = {
                busy = true; error = null
                scope.launch { try { api.testConnection(host); api.saveHost(host); val result = api.login(username.trim(),password); password = ""; onLogin(result) } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { error = failure.message } finally { busy = false } }
            }) { Text(if (busy) "Ingresando…" else "Iniciar sesión") }
        }
    }
}

@Composable private fun DesktopShell(api: DesktopApi, user: DesktopUser, screen: String, navigate: (String) -> Unit) {
    val notice by api.resultNotice.collectAsState()
    LaunchedEffect(screen) { api.clearNotice() }
    Row(Modifier.fillMaxSize()) {
        NavigationRail {
            Text("S", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(18.dp))
            listOf("dashboard" to "Inicio", "sale" to "Nueva venta", "products" to "Productos", "merch" to "Mercadería", "sales" to if (user.rol == "admin") "Ventas" else "Mis ventas", "reports" to if (user.rol == "admin") "Reportes" else "Mis reportes").forEach { (key,label) -> NavigationRailItem(screen == key,{ navigate(key) },icon = {},label = { Text(label) }) }
            if (user.rol == "admin") {
                NavigationRailItem(screen == "users", { navigate("users") }, icon = {}, label = { Text("Usuarios") })
                NavigationRailItem(screen == "categories", { navigate("categories") }, icon = {}, label = { Text("Categorías") })
            }
            Spacer(Modifier.weight(1f)); NavigationRailItem(screen == "settings",{ navigate("settings") },icon = {},label = { Text("Servidor") }); TextButton({ navigate("logout") }) { Text("Cerrar sesión") }
        }
        Column(Modifier.fillMaxSize().padding(26.dp)) {
            notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Box(Modifier.weight(1f)) { when (screen) {
                "dashboard" -> DesktopDashboard(api,user,navigate); "sale" -> DesktopSale(api,user)
                "products" -> DesktopProducts(api); "merch" -> DesktopMerchandise(api,user)
                "sales" -> DesktopSales(api,user.rol == "admin"); "reports" -> DesktopReports(api)
                "users" -> DesktopUsers(api); "categories" -> DesktopCategories(api); "settings" -> DesktopSettings(api)
            } }
        }
    }
}

@Composable private fun DesktopDashboard(api: DesktopApi, user: DesktopUser, navigate: (String) -> Unit) { var data by remember { mutableStateOf<JsonObject?>(null) }; var error by remember { mutableStateOf<String?>(null) }; LaunchedEffect(Unit) { safeRunCatching { api.dashboard() }.onSuccess { data = it }.onFailure { error = it.message } }; Column { Text(if (user.rol == "admin") "Dashboard" else "Mi inicio", style = MaterialTheme.typography.headlineMedium); Text("Hola, " + user.nombre); if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error); if (data == null && error == null) CircularProgressIndicator(); data?.let { d -> Row(Modifier.padding(vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { DesktopStat("Ventas hoy", d["ventasHoy"].asString); DesktopStat("Ingresos", money(d["ingresosHoy"].asDouble)); DesktopStat("Unidades", d["unidadesVendidasHoy"].asString); if (user.rol == "admin") DesktopStat("Stock bajo", d["productosStockBajo"].asString) }; Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Button({ navigate("sale") }) { Text("Nueva venta") }; OutlinedButton({ navigate("merch") }) { Text("Mercadería") }; OutlinedButton({ navigate("products") }) { Text("Productos") } } } } }
@Composable private fun DesktopStat(title: String, value: String) { Card(Modifier.width(170.dp)) { Column(Modifier.padding(18.dp)) { Text(title); Text(value, style = MaterialTheme.typography.headlineSmall) } } }

@Composable private fun DesktopProducts(api: DesktopApi) { var search by remember { mutableStateOf("") }; var list by remember { mutableStateOf<List<DesktopProduct>>(emptyList()) }; var showCreate by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope(); fun load() { scope.launch { safeRunCatching { api.products(search) }.onSuccess { list = it }.onFailure { error = it.message } } }; LaunchedEffect(Unit) { load() }; Column { Row(verticalAlignment = Alignment.CenterVertically) { Text("Productos", style = MaterialTheme.typography.headlineMedium); Spacer(Modifier.width(24.dp)); OutlinedTextField(search, { search = it }, label = { Text("Buscar") }); Button({ load() }) { Text("Buscar") }; Spacer(Modifier.weight(1f)); Button({ showCreate = true }) { Text("Nuevo producto") } }; error?.let { Text(it, color = MaterialTheme.colorScheme.error) }; LazyColumn { items(list) { p -> Card(Modifier.fillMaxWidth().padding(4.dp)) { Row(Modifier.padding(14.dp)) { Column(Modifier.weight(1f)) { Text(p.nombre, style = MaterialTheme.typography.titleMedium); Text(p.codigo + " · stock " + p.stock) }; Text(money(p.precioVenta)) } } } } }; if (showCreate) DesktopCreateProduct(api) { showCreate = false; load() } }
@Composable private fun DesktopCreateProduct(api: DesktopApi, close: () -> Unit) {
    var name by remember { mutableStateOf("") }; var code by remember { mutableStateOf("") }
    var purchase by remember { mutableStateOf("") }; var sale by remember { mutableStateOf("") }; var stock by remember { mutableStateOf("") }
    var image by remember { mutableStateOf<File?>(null) }; var error by remember { mutableStateOf<String?>(null) }
    var createdId by remember { mutableStateOf<Long?>(null) }; var busy by remember { mutableStateOf(false) }; val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!busy) close() },title = { Text("Nuevo producto") },text = { Column {
        OutlinedTextField(name,{name=it},label={Text("Nombre")},enabled=createdId==null&&!busy)
        OutlinedTextField(code,{code=it},label={Text("Código")},enabled=createdId==null&&!busy)
        OutlinedTextField(purchase,{purchase=it},label={Text("Precio compra")},enabled=createdId==null&&!busy)
        OutlinedTextField(sale,{sale=it},label={Text("Precio venta")},enabled=createdId==null&&!busy)
        OutlinedTextField(stock,{stock=it},label={Text("Stock")},enabled=createdId==null&&!busy)
        TextButton(enabled=!busy,onClick={
            val dialog=FileDialog(null as java.awt.Frame?,"Seleccionar imagen",FileDialog.LOAD)
            dialog.setFilenameFilter { _, filename -> listOf(".jpg",".jpeg",".png",".webp").any { filename.lowercase().endsWith(it) } }
            dialog.isVisible=true
            image=dialog.directory?.let { directory -> dialog.file?.let { File(directory,it) } }
        }) { Text(image?.name ?: "Seleccionar imagen") }
        if(createdId!=null)Text("Producto creado. Puedes reintentar únicamente la imagen.")
        error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    } },confirmButton={ Button(enabled=!busy,onClick={
        val p=purchase.toDoubleOrNull();val s=sale.toDoubleOrNull();val n=stock.toIntOrNull()
        if(createdId==null&&(name.isBlank()||code.isBlank()||p==null||s==null||n==null||p<0||s<0||n<0))error="Completa datos válidos"
        else { busy=true;error=null;scope.launch {
            try { val id=createdId ?: api.createProduct(name,code,p!!,s!!,n!!).id.also { createdId=it };image?.let { api.uploadImage(id,it) };close() }
            catch(cancelled:CancellationException){throw cancelled}
            catch(failure:Exception){error=if(createdId!=null)"Producto creado; falló la imagen: ${failure.message}" else failure.message}
            finally{busy=false}
        } }
    }) { Text(if(busy)"Guardando…" else if(createdId!=null)"Reintentar imagen" else "Guardar") } },dismissButton={TextButton(enabled=!busy,onClick=close){Text("Cancelar")}})
}

@Composable private fun DesktopMerchandise(api: DesktopApi, user: DesktopUser) { var code by remember { mutableStateOf("") }; var product by remember { mutableStateOf<DesktopProduct?>(null) }; var amount by remember { mutableStateOf("1") }; var error by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope(); Column { Text("Mercadería", style = MaterialTheme.typography.headlineMedium); Row { OutlinedTextField(code, { code = it }, label = { Text("Código / lector USB") }); Button({ scope.launch { safeRunCatching { api.find(code) }.onSuccess { product = it; error = null }.onFailure { error = "Producto no registrado" } } }) { Text("Buscar") } }; Text("Enfoca el campo y presiona Enter con un lector USB."); error?.let { Text(it, color = MaterialTheme.colorScheme.error) }; product?.let { p -> Card(Modifier.padding(top = 20.dp).width(500.dp)) { Column(Modifier.padding(20.dp)) { Text(p.nombre, style = MaterialTheme.typography.titleLarge); Text("Código: " + p.codigo); Text("Stock: " + p.stock); OutlinedTextField(amount, { amount = it }, label = { Text("Cantidad") }); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ scope.launch { safeRunCatching { api.increase(p.id, amount.toInt()) }.onSuccess { product = it }.onFailure { error = it.message } } }) { Text("Aumentar stock") }; if (user.rol == "admin") OutlinedButton({ scope.launch { safeRunCatching { api.decrease(p.id, amount.toInt()) }.onSuccess { product = it }.onFailure { error = it.message } } }) { Text("Disminuir") } } } } } } }

@Composable private fun DesktopSale(api: DesktopApi, user: DesktopUser) {
    var code by remember { mutableStateOf("") }; var cart by remember { mutableStateOf<List<DesktopCartLine>>(emptyList()) }
    var pending by remember { mutableStateOf<DesktopPendingSale?>(null) }; var loading by remember { mutableStateOf(true) }; var recoveryFailed by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }; var message by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    val scope=rememberCoroutineScope(); val origin=remember { api.host };var generation by remember { mutableIntStateOf(0) }
    val editable=!busy&&!loading&&!recoveryFailed&&pending?.uncertain!=true
    LaunchedEffect(user.id,origin) { try { pending=api.pending(user.id);cart=pending?.cart.orEmpty() } catch(cancelled:CancellationException){throw cancelled} catch(failure:Exception){error="No se pudo recuperar la venta pendiente. Consulta al administrador antes de repetirla.";recoveryFailed=true} finally{loading=false} }
    fun add(product:DesktopProduct) {
        if(busy||loading||recoveryFailed||pending?.uncertain==true||api.host!=origin)return
        val old=cart.firstOrNull { it.product.id==product.id }
        if(product.stock<=0||(old?.quantity ?: 0)>=product.stock){error="Producto sin stock disponible";return}
        cart=if(old==null)cart+DesktopCartLine(product,1) else cart.map{if(it.product.id==product.id)it.copy(product=product,quantity=it.quantity+1) else it};pending=null;error=null
    }
    Column {
        Text("Nueva venta",style=MaterialTheme.typography.headlineMedium)
        if(loading)CircularProgressIndicator()
        if(pending?.uncertain==true)Text("Venta pendiente: reintenta para recuperar el resultado.")
        Row {
            OutlinedTextField(code,{code=it},label={Text("Código / lector USB")},enabled=editable)
            Button(enabled=editable,onClick={val current=generation;scope.launch{safeRunCatching{api.find(code)}.onSuccess{if(current==generation&&!busy){add(it);code=""}}.onFailure{if(current==generation)error=it.message}}}){Text("Agregar")}
        }
        LazyColumn(Modifier.weight(1f)){items(cart){line->Row(Modifier.fillMaxWidth().padding(8.dp)){
            Text(line.product.nombre+" × "+line.quantity,Modifier.weight(1f));Text(money(line.product.precioVenta*line.quantity))
            TextButton(enabled=editable,onClick={if(!busy&&pending?.uncertain!=true){cart=cart.mapNotNull{if(it.product.id!=line.product.id)it else if(it.quantity>1)it.copy(quantity=it.quantity-1)else null};pending=null}}){Text("−")}
            TextButton(enabled=editable&&line.quantity<line.product.stock,onClick={add(line.product)}){Text("+")}
        }}}
        Text("Total: "+money(cart.sumOf{it.product.precioVenta*it.quantity}),style=MaterialTheme.typography.headlineSmall)
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)};message?.let{Text(it)}
        Button(enabled=cart.isNotEmpty()&&!busy&&!loading&&!recoveryFailed,onClick={
            if(!busy&&cart.isNotEmpty()&&api.host==origin){
                val snapshot=pending?.takeIf{it.cart==cart}?.copy(uncertain=true) ?: DesktopPendingSale(UUID.randomUUID().toString(),cart.toList())
                busy=true;generation++;pending=snapshot;error=null;message=null
                scope.launch {
                    try {
                        api.savePending(user.id,origin,snapshot)
                        check(api.host==origin){"El servidor cambió"}
                        val sale=api.sell(snapshot.cart.map{mapOf("producto_id" to it.product.id,"cantidad" to it.quantity)},snapshot.operationId)
                        api.savePending(user.id,origin,null)
                        if(cart==snapshot.cart){cart=emptyList();pending=null}
                        message="Venta registrada #${sale["id"].asLong} · Total: ${money(sale["total"].asDouble)}"
                    }catch(cancelled:CancellationException){throw cancelled}
                    catch(failure:Exception){pending=snapshot.copy(uncertain=!(failure is DesktopApiError&&failure.status in listOf(400,404)));error=failure.message}
                    finally{busy=false}
                }
            }
        }){Text(if(busy)"Registrando…" else if(pending!=null)"REINTENTAR VENTA" else "CONFIRMAR VENTA")}
    }
}

@Composable private fun DesktopSales(api: DesktopApi, admin: Boolean) {
    DesktopQuery(admin,{api.sales(!admin)}) { list -> Column {
        Text(if(admin) "Ventas" else "Mis ventas",style=MaterialTheme.typography.headlineMedium)
        if(list.isEmpty())Text("No hay ventas.")
        LazyColumn { items(list) { Text("Venta #${it.id} · ${it.vendedor} · ${money(it.total)}",Modifier.padding(10.dp)) } }
    } }
}
@Composable private fun DesktopReports(api: DesktopApi) {
    DesktopQuery(Unit,{api.report("dia")}) { report -> Column {
        Text("Reportes · día comercial",style=MaterialTheme.typography.headlineMedium)
        Text("Ventas: "+report["ventas"].asString);Text("Ingresos: "+money(report["ingresos"].asDouble));Text("Unidades: "+report["unidadesVendidas"].asString)
        if(report["historialTruncado"]?.asBoolean==true)Text("Historial limitado a 100; totales del período completos.")
    } }
}
@Composable private fun DesktopUsers(api: DesktopApi) {
    var show by remember{mutableStateOf(false)};var tick by remember{mutableIntStateOf(0)}
    Column {
        Row { Text("Usuarios",style=MaterialTheme.typography.headlineMedium);Button({show=true}){Text("Nuevo usuario")} }
        DesktopQuery(tick,{api.users()}) { list -> if(list.size()==0)Text("No hay usuarios.");list.forEach{Text(it.asJsonObject["nombre"].asString+" · "+it.asJsonObject["username"].asString+" · "+it.asJsonObject["rol"].asString,Modifier.padding(8.dp))} }
    }
    if(show)DesktopCreateUser(api){show=false;tick++}
}
@Composable private fun DesktopCreateUser(api: DesktopApi, close: () -> Unit) { var name by remember { mutableStateOf("") }; var username by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var role by remember { mutableStateOf("vendedor") }; var error by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope(); AlertDialog(onDismissRequest = close, title = { Text("Nuevo usuario") }, text = { Column { OutlinedTextField(name, { name = it }, label = { Text("Nombre") }); OutlinedTextField(username, { username = it }, label = { Text("Usuario") }); OutlinedTextField(password, { password = it }, label = { Text("Contraseña (mín. 8)") }, visualTransformation = PasswordVisualTransformation()); OutlinedTextField(role, { role = it }, label = { Text("Rol: admin o vendedor") }); error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } }, confirmButton = { Button({ if (name.isBlank() || username.isBlank() || password.isBlank()) error = "Completa los datos" else scope.launch { safeRunCatching { api.createUser(name, username, password, role) }.onSuccess { close() }.onFailure { error = it.message } } }) { Text("Guardar") } }, dismissButton = { TextButton(close) { Text("Cancelar") } }) }
@Composable private fun DesktopCategories(api: DesktopApi) {
    var show by remember{mutableStateOf(false)};var tick by remember{mutableIntStateOf(0)}
    Column {
        Row { Text("Categorías",style=MaterialTheme.typography.headlineMedium);Button({show=true}){Text("Nueva categoría")} }
        DesktopQuery(tick,{api.categories()}) { list -> if(list.size()==0)Text("No hay categorías.");list.forEach{Text(it.asJsonObject["nombre"].asString,Modifier.padding(8.dp))} }
    }
    if(show)DesktopCreateCategory(api){show=false;tick++}
}
@Composable private fun DesktopCreateCategory(api: DesktopApi, close: () -> Unit) { var name by remember { mutableStateOf("") }; var description by remember { mutableStateOf("") }; var error by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope(); AlertDialog(onDismissRequest = close, title = { Text("Nueva categoría") }, text = { Column { OutlinedTextField(name, { name = it }, label = { Text("Nombre") }); OutlinedTextField(description, { description = it }, label = { Text("Descripción") }); error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } }, confirmButton = { Button({ if (name.isBlank()) error = "El nombre es obligatorio" else scope.launch { safeRunCatching { api.createCategory(name, description) }.onSuccess { close() }.onFailure { error = it.message } } }) { Text("Guardar") } }, dismissButton = { TextButton(close) { Text("Cancelar") } }) }
@Composable private fun DesktopSettings(api: DesktopApi) {
    var host by remember{mutableStateOf(api.host)};var message by remember{mutableStateOf("")};var failed by remember{mutableStateOf(false)};var busy by remember{mutableStateOf(false)};val scope=rememberCoroutineScope()
    Column(Modifier.width(520.dp)){
        Text("Configuración del servidor",style=MaterialTheme.typography.headlineMedium)
        OutlinedTextField(host,{host=it;failed=false},label={Text("IP o URL completa")},enabled=!busy)
        Button(enabled=!busy,onClick={busy=true;scope.launch{try{api.testConnection(host);api.saveHost(host);message="Servidor guardado"}catch(cancelled:CancellationException){throw cancelled}catch(error:Exception){message=error.message.orEmpty();failed=true}finally{busy=false}}}){Text("Probar y guardar")}
        if(failed)TextButton(enabled=!busy,onClick={safeRunCatching{api.saveHost(host)}.onFailure{message=it.message.orEmpty()}}){Text("Guardar de todos modos")}
        Text(message)
    }
}

private inline fun <T> safeRunCatching(block: () -> T): Result<T> = try { Result.success(block()) }
catch(cancelled: CancellationException) { throw cancelled }
catch(error: Exception) { Result.failure(error) }

@Composable private fun <T> DesktopQuery(key: Any?, load: suspend () -> T, content: @Composable (T) -> Unit) {
    var value by remember { mutableStateOf<T?>(null) };var loading by remember{mutableStateOf(true)}
    var error by remember{mutableStateOf<String?>(null)};var tick by remember{mutableIntStateOf(0)}
    val currentLoad by rememberUpdatedState(load)
    LaunchedEffect(key,tick){loading=true;error=null;try{value=currentLoad()}catch(cancelled:CancellationException){throw cancelled}catch(failure:Exception){error=failure.message ?: "No se pudo cargar"}finally{loading=false}}
    when { loading -> CircularProgressIndicator();error!=null -> Column{Text(error!!,color=MaterialTheme.colorScheme.error);TextButton({tick++}){Text("Reintentar")}};else -> value?.let{content(it)} }
}
