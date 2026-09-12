package com.example.sistema_tienda

import com.google.gson.*
import java.net.URI
import java.io.File
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.prefs.Preferences

data class DesktopUser(val id: Long, val username: String, val nombre: String, val rol: String)
data class DesktopProduct(val id: Long, val codigo: String, val nombre: String, val precioCompra: Double, val precioVenta: Double, val stock: Int, val stockMinimo: Int = 5)
data class DesktopSale(val id: Long, val total: Double, val fecha: String, val vendedor: String)

class DesktopApi {
    private val http = HttpClient.newHttpClient(); private val gson = Gson(); private val prefs = Preferences.userRoot().node("sistematienda"); var token: String? = prefs.get("token", null); var host: String = normalize(prefs.get("server_ip", "")); private set
    fun saveHost(value: String) { host = normalize(value); prefs.put("server_ip", host) }
    fun base(): String = "http://" + host + ":3000/api/"
    fun asset(path: String): String = "http://" + host + ":3000" + if (path.startsWith("/")) path else "/" + path
    fun health(): Boolean { val response = raw("http://" + host + ":3000/health", "GET", null); return response.statusCode() in 200..299 }
    fun login(username: String, password: String): DesktopUser { val json = request("usuarios/login", "POST", mapOf("username" to username, "password" to password)); token = json.asJsonObject["token"].asString; prefs.put("token", token); return user(json.asJsonObject["user"].asJsonObject).also { saveUser(it) } }
    fun savedUser(): DesktopUser? = if (prefs.getLong("user_id", -1) < 0) null else DesktopUser(prefs.getLong("user_id", -1), prefs.get("user_username", ""), prefs.get("user_name", ""), prefs.get("user_role", "vendedor"))
    fun logout() { token = null; prefs.remove("token"); prefs.remove("user_id"); prefs.remove("user_username"); prefs.remove("user_name"); prefs.remove("user_role") }
    private fun saveUser(value: DesktopUser) { prefs.putLong("user_id", value.id); prefs.put("user_username", value.username); prefs.put("user_name", value.nombre); prefs.put("user_role", value.rol) }
    fun products(search: String = ""): List<DesktopProduct> = request("productos?search=" + java.net.URLEncoder.encode(search, "UTF-8"), "GET", null).asJsonArray.map { product(it.asJsonObject) }
    fun createProduct(name: String, code: String, purchase: Double, sale: Double, stock: Int): DesktopProduct = product(request("productos", "POST", mapOf("nombre" to name, "codigo" to code, "precio_compra" to purchase, "precio_venta" to sale, "stock" to stock)).asJsonObject)
    fun uploadImage(id: Long, file: File): DesktopProduct { val boundary = "----Sistematienda${System.currentTimeMillis()}"; val prefix = "--$boundary\r\nContent-Disposition: form-data; name=\"foto\"; filename=\"${file.name}\"\r\nContent-Type: image/*\r\n\r\n".toByteArray(); val suffix = "\r\n--$boundary--\r\n".toByteArray(); val payload = prefix + file.readBytes() + suffix; val builder = HttpRequest.newBuilder(URI(base() + "productos/$id/imagen")).header("Content-Type", "multipart/form-data; boundary=$boundary"); token?.let { builder.header("Authorization", "Bearer $it") }; val response = http.send(builder.POST(HttpRequest.BodyPublishers.ofByteArray(payload)).build(), HttpResponse.BodyHandlers.ofString()); if (response.statusCode() !in 200..299) throw IllegalStateException("HTTP ${response.statusCode()}: ${response.body()}"); return product(JsonParser.parseString(response.body()).asJsonObject) }
    fun find(code: String): DesktopProduct = product(request("productos/codigo/" + java.net.URLEncoder.encode(code, "UTF-8"), "GET", null).asJsonObject)
    fun increase(id: Long, quantity: Int): DesktopProduct = product(request("inventario/" + id + "/aumentar", "POST", mapOf("cantidad" to quantity)).asJsonObject)
    fun decrease(id: Long, quantity: Int): DesktopProduct = product(request("inventario/" + id + "/disminuir", "POST", mapOf("cantidad" to quantity)).asJsonObject)
    fun sales(mine: Boolean): List<DesktopSale> = request(if (mine) "ventas/mias" else "ventas", "GET", null).asJsonArray.map { val o = it.asJsonObject; DesktopSale(o["id"].asLong, o["total"].asDouble, o["fecha_creacion"].asString, o["vendedor_nombre"]?.asString ?: o["vendedor"]?.asString.orEmpty()) }
    fun sell(items: List<Map<String, Any>>): JsonObject = request("ventas", "POST", mapOf("items" to items)).asJsonObject
    fun report(period: String): JsonObject = request("reportes/ventas?periodo=" + period, "GET", null).asJsonObject
    fun dashboard(): JsonObject = request("reportes/dashboard", "GET", null).asJsonObject
    fun users(): JsonArray = request("usuarios", "GET", null).asJsonArray
    fun categories(): JsonArray = request("categorias", "GET", null).asJsonArray
    fun createUser(name: String, username: String, password: String, role: String): JsonObject = request("usuarios", "POST", mapOf("nombre" to name, "username" to username, "password" to password, "rol" to role)).asJsonObject
    fun createCategory(name: String, description: String): JsonObject = request("categorias", "POST", mapOf("nombre" to name, "descripcion" to description)).asJsonObject
    private fun user(o: JsonObject) = DesktopUser(o["id"].asLong, o["username"].asString, o["nombre"].asString, o["rol"].asString)
    private fun product(o: JsonObject) = DesktopProduct(o["id"].asLong, (o["codigo"] ?: o["codigo_barras"]).asString, o["nombre"].asString, o["precio_compra"].asDouble, o["precio_venta"].asDouble, o["stock"].asInt, o["stock_minimo"]?.asInt ?: 5)
    private fun request(path: String, method: String, body: Any?): JsonElement { val response = raw(base() + path, method, body); if (response.statusCode() !in 200..299) { val message = when (response.statusCode()) { 400 -> "Datos incompletos o inválidos."; 401 -> { if (!path.endsWith("usuarios/login")) { logout(); "Tu sesión terminó. Inicia sesión nuevamente." } else "Credenciales inválidas." }; 403 -> "No tienes permiso para realizar esta acción."; 404 -> if (path.contains("codigo")) "Producto no registrado." else "No se encontró el recurso solicitado."; 409 -> if (path.contains("usuarios")) "Ya existe un usuario con ese nombre de usuario." else "Ya existe un producto con ese código."; 500 -> "Ocurrió un error en el servidor."; else -> "Error HTTP ${response.statusCode()}" }; throw IllegalStateException(message) }; return JsonParser.parseString(response.body()) }
    private fun raw(url: String, method: String, body: Any?): HttpResponse<String> { val builder = HttpRequest.newBuilder(URI(url)).header("Content-Type", "application/json"); token?.let { builder.header("Authorization", "Bearer " + it) }; val publisher = if (body == null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(gson.toJson(body)); return http.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString()) }
    companion object { fun normalize(value: String): String = value.trim().removePrefix("http://").removePrefix("https://").substringBefore('/').substringBefore(':').trim() }
}
