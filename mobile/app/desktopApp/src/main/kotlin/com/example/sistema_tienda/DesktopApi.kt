package com.example.sistema_tienda

import com.google.gson.*
import java.net.URI
import java.io.File
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.prefs.Preferences
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DesktopUser(val id: Long, val username: String, val nombre: String, val rol: String)
data class DesktopProduct(val id: Long, val codigo: String, val nombre: String, val precioCompra: Double, val precioVenta: Double, val stock: Int, val stockMinimo: Int = 5)
data class DesktopSale(val id: Long, val total: Double, val fecha: String, val vendedor: String)
data class DesktopCartLine(val product: DesktopProduct, val quantity: Int)
data class DesktopPendingSale(val operationId: String, val cart: List<DesktopCartLine>, val uncertain: Boolean = true)
class DesktopApiError(val status: Int, message: String) : Exception(message)

class DesktopApi {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    private val gson = Gson()
    private val prefs = Preferences.userRoot().node("sistematienda")
    @Volatile var token: String? = prefs.get("token", null); private set
    @Volatile var host: String = runCatching { normalize(prefs.get("server_url", prefs.get("server_ip", ""))) }.getOrDefault(""); private set
    private val expired = MutableStateFlow(false)
    val sessionExpired = expired.asStateFlow()
    private val notice = MutableStateFlow<String?>(null)
    val resultNotice = notice.asStateFlow()
    fun clearNotice() { notice.value = null }
    fun saveHost(value: String) {
        val normalized = normalize(value)
        if (host != normalized) logout()
        host = normalized; prefs.put("server_url",host); prefs.remove("server_ip")
    }
    fun base(): String = host + "api/"
    fun asset(path: String): String = host + path.removePrefix("/")
    suspend fun testConnection(value: String): Boolean = withContext(Dispatchers.IO) {
        val response = raw(normalize(value) + "health", "GET", null, null)
        val health = runCatching { JsonParser.parseString(response.body()).asJsonObject }.getOrNull()
        check(response.statusCode() in 200..299 && health?.get("status")?.asString == "ok" && health["database"]?.asString == "sqlite") { "Servidor no disponible o incompatible; no se guardó" }
        true
    }
    suspend fun health(): Boolean = testConnection(host)
    suspend fun login(username: String, password: String): DesktopUser {
        val origin = host
        val json = request("usuarios/login", "POST", mapOf("username" to username, "password" to password))
        check(origin == host) { "El servidor cambió durante el login" }
        token = json.asJsonObject["token"].asString; prefs.put("token", token)
        expired.value = false
        return user(json.asJsonObject["user"].asJsonObject).also { saveUser(it) }
    }
    fun savedUser(): DesktopUser? = if (token.isNullOrBlank() || prefs.getLong("user_id", -1) <= 0) null else DesktopUser(prefs.getLong("user_id", -1), prefs.get("user_username", ""), prefs.get("user_name", ""), prefs.get("user_role", "vendedor"))
    fun logout() { token = null; prefs.remove("token"); prefs.remove("user_id"); prefs.remove("user_username"); prefs.remove("user_name"); prefs.remove("user_role"); expired.value = true; notice.value = null }
    private fun saveUser(value: DesktopUser) { prefs.putLong("user_id", value.id); prefs.put("user_username", value.username); prefs.put("user_name", value.nombre); prefs.put("user_role", value.rol) }
    suspend fun products(search: String = ""): List<DesktopProduct> = request("productos?search=" + java.net.URLEncoder.encode(search, "UTF-8"), "GET", null).asJsonArray.map { product(it.asJsonObject) }
    suspend fun createProduct(name: String, code: String, purchase: Double, sale: Double, stock: Int): DesktopProduct = product(request("productos", "POST", mapOf("nombre" to name, "codigo" to code, "precio_compra" to purchase, "precio_venta" to sale, "stock" to stock)).asJsonObject)
    suspend fun uploadImage(id: Long, file: File): DesktopProduct {
        val origin = base(); val authorization = token
        return withContext(Dispatchers.IO) {
            val bytes = file.inputStream().use { it.readNBytes(5 * 1024 * 1024 + 1) }
            require(bytes.size <= 5 * 1024 * 1024) { "La imagen supera 5 MiB" }
            val mime = when {
                bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() -> "image/jpeg"
                bytes.size >= 8 && bytes.copyOfRange(0,8).contentEquals(byteArrayOf(137.toByte(),80,78,71,13,10,26,10)) -> "image/png"
                bytes.size >= 12 && String(bytes,0,4,Charsets.US_ASCII) == "RIFF" && String(bytes,8,4,Charsets.US_ASCII) == "WEBP" -> "image/webp"
                else -> throw IllegalArgumentException("Usa una imagen JPEG, PNG o WebP válida")
            }
            val extension = mime.substringAfter('/').replace("jpeg","jpg")
            val boundary = "Sistematienda-" + UUID.randomUUID()
            val prefix = "--$boundary\r\nContent-Disposition: form-data; name=\"foto\"; filename=\"product-$id.$extension\"\r\nContent-Type: $mime\r\n\r\n".toByteArray()
            val suffix = "\r\n--$boundary--\r\n".toByteArray()
            val builder = HttpRequest.newBuilder(URI(origin + "productos/$id/imagen")).timeout(Duration.ofSeconds(60)).header("Content-Type", "multipart/form-data; boundary=$boundary")
            authorization?.let { builder.header("Authorization", "Bearer $it") }
            val response = http.send(builder.POST(HttpRequest.BodyPublishers.ofByteArray(prefix + bytes + suffix)).build(), HttpResponse.BodyHandlers.ofString())
            ensureSuccess(response, "productos/$id/imagen", authorization, origin)
            product(JsonParser.parseString(response.body()).asJsonObject)
        }
    }
    suspend fun find(code: String): DesktopProduct = product(request("productos/codigo/" + java.net.URLEncoder.encode(code, "UTF-8"), "GET", null).asJsonObject)
    suspend fun increase(id: Long, quantity: Int): DesktopProduct = product(request("inventario/$id/aumentar", "POST", mapOf("cantidad" to quantity)).asJsonObject)
    suspend fun decrease(id: Long, quantity: Int): DesktopProduct = product(request("inventario/$id/disminuir", "POST", mapOf("cantidad" to quantity)).asJsonObject)
    suspend fun sales(mine: Boolean): List<DesktopSale> = request(if (mine) "ventas/mias" else "ventas", "GET", null).asJsonArray.map { val o = it.asJsonObject; DesktopSale(o["id"].asLong, o["total"].asDouble, o["fecha_creacion"].asString, o["vendedor_nombre"]?.asString ?: o["vendedor"]?.asString.orEmpty()) }
    suspend fun sell(items: List<Map<String, Any>>, operationId: String): JsonObject = request("ventas", "POST", mapOf("items" to items, "operation_id" to operationId)).asJsonObject
    suspend fun report(period: String): JsonObject = request("reportes/ventas?periodo=$period", "GET", null).asJsonObject
    suspend fun dashboard(): JsonObject = request("reportes/dashboard", "GET", null).asJsonObject
    suspend fun users(): JsonArray = request("usuarios", "GET", null).asJsonArray
    suspend fun categories(): JsonArray = request("categorias", "GET", null).asJsonArray
    suspend fun createUser(name: String, username: String, password: String, role: String): JsonObject = request("usuarios", "POST", mapOf("nombre" to name, "username" to username, "password" to password, "rol" to role)).asJsonObject
    suspend fun createCategory(name: String, description: String): JsonObject = request("categorias", "POST", mapOf("nombre" to name, "descripcion" to description)).asJsonObject
    private fun pendingKey(userId: Long, origin: String) = "pending_" + MessageDigest.getInstance("SHA-256").digest("$origin|$userId".toByteArray()).joinToString("") { "%02x".format(it) }
    private fun pendingPath(userId: Long, origin: String) = java.nio.file.Path.of(System.getProperty("user.home"), ".sistematienda", "pending-sales", pendingKey(userId,origin) + ".json")
    suspend fun pending(userId: Long): DesktopPendingSale? {
        val filename = pendingPath(userId, host)
        return withContext(Dispatchers.IO) {
            if (!java.nio.file.Files.exists(filename)) null
            else gson.fromJson(java.nio.file.Files.readString(filename), DesktopPendingSale::class.java).also { p ->
                UUID.fromString(p.operationId)
                require(p.cart.size <= 1000 && p.cart.all { it.quantity > 0 && it.quantity <= it.product.stock }) { "Venta pendiente inválida" }
            }
        }
    }
    suspend fun savePending(userId: Long, origin: String, pending: DesktopPendingSale?) = withContext(Dispatchers.IO) {
        val filename = pendingPath(userId, origin)
        java.nio.file.Files.createDirectories(filename.parent)
        if (pending == null) { java.nio.file.Files.deleteIfExists(filename); Unit }
        else {
            val temporary = java.nio.file.Files.createTempFile(filename.parent, "pending-", ".tmp")
            try {
                try { java.nio.file.Files.setPosixFilePermissions(temporary, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")) } catch (_: UnsupportedOperationException) { }
                java.nio.file.Files.writeString(temporary, gson.toJson(pending))
                try { java.nio.file.Files.move(temporary, filename, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
                catch (_: java.nio.file.AtomicMoveNotSupportedException) { java.nio.file.Files.move(temporary, filename, java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
                Unit
            } finally { java.nio.file.Files.deleteIfExists(temporary) }
        }
    }
    private fun user(o: JsonObject) = DesktopUser(o["id"].asLong, o["username"].asString, o["nombre"].asString, o["rol"].asString)
    private fun product(o: JsonObject) = DesktopProduct(o["id"].asLong, (o["codigo"] ?: o["codigo_barras"]).asString, o["nombre"].asString, o["precio_compra"].asDouble, o["precio_venta"].asDouble, o["stock"].asInt, o["stock_minimo"]?.takeUnless { it.isJsonNull }?.asInt ?: 5)
    private suspend fun request(path: String, method: String, body: Any?): JsonElement {
        val origin = base(); val url = origin + path; val authorization = if (path == "usuarios/login") null else token
        return withContext(Dispatchers.IO) {
            val response = raw(url,method,body,authorization)
            ensureSuccess(response,path,authorization,origin)
            if (response.headers().firstValue("X-Results-Truncated").orElse("") == "true") notice.value = "Resultados limitados a ${response.headers().firstValue("X-Result-Limit").orElse("un máximo")}. Usa búsqueda o filtros."
            JsonParser.parseString(response.body())
        }
    }
    private fun ensureSuccess(response: HttpResponse<String>, path: String, authorization: String?, origin: String) {
        if (response.statusCode() in 200..299) return
        if (response.statusCode() == 401 && path != "usuarios/login" && authorization != null && token == authorization && base() == origin) logout()
        val message = runCatching { JsonParser.parseString(response.body()).asJsonObject["message"].asString }.getOrNull()
        throw DesktopApiError(response.statusCode(), message ?: "Error HTTP ${response.statusCode()}")
    }
    private fun raw(url: String, method: String, body: Any?, authorization: String?): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(30)).header("Content-Type","application/json")
        authorization?.let { builder.header("Authorization","Bearer $it") }
        val publisher = if (body == null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(gson.toJson(body))
        return http.send(builder.method(method,publisher).build(),HttpResponse.BodyHandlers.ofString())
    }
    companion object {
        fun normalize(value: String): String {
            val raw = value.trim(); require(raw.isNotEmpty()) { "Configura el servidor" }
            val candidate = if (raw.contains("://")) raw else "http://$raw${if (raw.contains(':')) "" else ":3000"}/"
            val uri = URI(candidate)
            require(uri.scheme in listOf("http","https") && !uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null && (uri.port == -1 || uri.port in 1..65535)) { "URL del servidor inválida" }
            return uri.toString().trimEnd('/') + "/"
        }
    }
}
