package com.example.sistema_tienda.data.repositories

import com.example.sistema_tienda.data.local.UserPreferencesManager
import com.example.sistema_tienda.data.remote.*
import com.example.sistema_tienda.domain.model.*
import com.example.sistema_tienda.domain.repository.*
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import retrofit2.HttpException

private suspend fun <T> request(preferences: UserPreferencesManager, endpoint: String, block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: Exception) {
    if (e.causedByNetworkFailure()) {
        val url = "http://${preferences.getServerIp()}:3000/api/$endpoint"
        Result.failure(Exception("No se pudo conectar a $url. Cambia la IP del servidor en Configuración.", e))
    } else if (e is HttpException) {
        val detail = e.response()?.errorBody()?.string()?.takeIf { it.isNotBlank() }
            ?.replace(Regex("[\\{\\}\"]"), "")?.replace("message:", "")?.trim()
        val message = when (e.code()) {
            400 -> detail ?: "Datos incompletos o inválidos."
            401 -> if (!endpoint.endsWith("usuarios/login")) { preferences.clear(); "Tu sesión terminó. Inicia sesión nuevamente." } else "Credenciales inválidas."
            403 -> "No tienes permiso para realizar esta acción."
            404 -> if (endpoint.contains("codigo")) "Producto no registrado." else "No se encontró el recurso solicitado."
            409 -> if (endpoint.contains("usuarios")) "Ya existe un usuario con ese nombre de usuario." else "Ya existe un producto con ese código."
            500 -> "Ocurrió un error en el servidor."
            else -> detail ?: "Error HTTP ${e.code()}"
        }
        Result.failure(Exception(message, e))
    } else {
        val message = if (endpoint.startsWith("productos/") && !endpoint.contains("codigo")) {
            "No se pudieron guardar los cambios."
        } else {
            "Ocurrió un error inesperado."
        }
        Result.failure(Exception(message, e))
    }
}

private fun Throwable.causedByNetworkFailure(): Boolean {
    var cause: Throwable? = this
    while (cause != null) {
        if (cause is ConnectException || cause is SocketTimeoutException || cause is UnknownHostException || cause is IOException) return true
        cause = cause.cause
    }
    return false
}
private fun UserDto.toDomain() = User(id, username, nombre, rol.lowercase(), token)
private fun ProductDto.toDomain() = Product(id, codigo ?: codigo_barras.orEmpty(), nombre, precio_compra, precio_venta, stock, descripcion, stock_minimo, categoria_nombre, imagen, activo == 1, categoria_id)

class AuthRepositoryImpl(private val api: StoreApi, private val preferences: UserPreferencesManager) : AuthRepository {
    override suspend fun login(username: String, password: String) = request(preferences, "usuarios/login") {
        val response = api.login(LoginRequest(username, password))
        val user = response.user.copy(token = response.token).toDomain()
        user.also { preferences.save(it) }
    }
    override suspend fun getSession() = preferences.get()
    override suspend fun logout() { preferences.clear() }
}
class ProductRepositoryImpl(private val api: StoreApi, private val preferences: UserPreferencesManager) : ProductRepository {
    override suspend fun getProducts() = request(preferences, "productos") { api.products().map { it.toDomain() } }
    override suspend fun findByBarcode(code: String) = request(preferences, "productos/codigo/$code") { api.productByCode(code).toDomain() }
    override suspend fun addStock(id: Long, quantity: Int) = request(preferences, "productos/$id/stock") { api.addStock(id, StockChangeRequest(quantity)).toDomain() }
    override suspend fun decreaseStock(id: Long, quantity: Int) = request(preferences, "productos/$id/stock") { api.decreaseStock(id, StockChangeRequest(quantity)).toDomain() }
    override suspend fun update(id: Long, payload: UpdateProductRequest) = request(preferences, "productos/$id") { api.updateProduct(id, payload).toDomain() }
    override suspend fun create(product: Product): Result<Product> = Result.failure(UnsupportedOperationException("El endpoint de creación aún no está disponible"))
}
class SaleRepositoryImpl(private val api: StoreApi, private val preferences: UserPreferencesManager) : SaleRepository {
    override suspend fun process(userId: Long, items: List<SaleItem>) = request(preferences, "ventas") { val response = api.createSale(SaleRequest(items.map { SaleItemDto(it.producto_id, it.cantidad) })); Sale(response.id, userId, items, response.total) }
    override suspend fun report(period: String) = request(preferences, "reportes/ventas") { api.salesReport(period).ingresos }
}
