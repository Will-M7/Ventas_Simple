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

class ApiFailure(val status: Int?, message: String, cause: Throwable? = null) : Exception(message, cause)

internal suspend fun <T> apiCall(preferences: UserPreferencesManager, endpoint: String, block: suspend () -> T): T = try {
    block()
} catch (cancelled: kotlinx.coroutines.CancellationException) {
    throw cancelled
} catch (error: HttpException) {
    val detail = runCatching {
        com.google.gson.JsonParser.parseString(error.response()?.errorBody()?.string().orEmpty())
            .asJsonObject.get("message")?.asString
    }.getOrNull()
    throw ApiFailure(error.code(), detail ?: when (error.code()) {
        401 -> "Tu sesión terminó. Inicia sesión nuevamente."
        403 -> "No tienes permiso para esta acción."
        404 -> if (endpoint.contains("codigo")) "Producto no registrado." else "Recurso no encontrado."
        409 -> "El registro ya existe o la operación está en conflicto."
        429 -> "Demasiados intentos. Espera antes de reintentar."
        else -> "Error HTTP ${error.code()}"
    }, error)
} catch (error: IOException) {
    throw ApiFailure(null, "No se pudo conectar a ${preferences.getServerIp()}. Revisa la conexión y el servidor.", error)
} catch (error: Exception) {
    throw ApiFailure(null, error.message ?: "No se pudo completar la operación", error)
}
private suspend fun <T> request(preferences: UserPreferencesManager, endpoint: String, block: suspend () -> T): Result<T> = try {
    Result.success(apiCall(preferences, endpoint, block))
} catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
catch (error: Exception) { Result.failure(error) }
private fun UserDto.toDomain() = User(id, username, nombre, rol.lowercase(), token)
private fun ProductDto.toDomain() = Product(id, codigo ?: codigo_barras.orEmpty(), nombre, precio_compra, precio_venta, stock, descripcion, stock_minimo, categoria_nombre, imagen, activo == 1, categoria_id)

class AuthRepositoryImpl(private val api: () -> StoreApi, private val preferences: UserPreferencesManager) : AuthRepository {
    override suspend fun login(username: String, password: String) = request(preferences, "usuarios/login") {
        val origin = preferences.getServerIp()
        val response = api().login(LoginRequest(username, password))
        check(preferences.getServerIp() == origin) { "El servidor cambió durante el login" }
        val user = response.user.copy(token = response.token).toDomain()
        user.also { preferences.save(it) }
    }
    override suspend fun getSession() = preferences.get()
    override suspend fun logout() { preferences.clear() }
}
class ProductRepositoryImpl(private val api: () -> StoreApi, private val preferences: UserPreferencesManager) : ProductRepository {
    override suspend fun getProducts() = request(preferences, "productos") { api().products().map { it.toDomain() } }
    override suspend fun findByBarcode(code: String) = request(preferences, "productos/codigo/$code") { api().productByCode(code).toDomain() }
    override suspend fun addStock(id: Long, quantity: Int) = request(preferences, "productos/$id/stock") { api().addStock(id, StockChangeRequest(quantity)).toDomain() }
    override suspend fun decreaseStock(id: Long, quantity: Int) = request(preferences, "productos/$id/stock") { api().decreaseStock(id, StockChangeRequest(quantity)).toDomain() }
    override suspend fun update(id: Long, payload: UpdateProductRequest) = request(preferences, "productos/$id") { api().updateProduct(id, payload).toDomain() }
    override suspend fun create(product: Product): Result<Product> = Result.failure(UnsupportedOperationException("El endpoint de creación aún no está disponible"))
}
class SaleRepositoryImpl(private val api: () -> StoreApi, private val preferences: UserPreferencesManager) : SaleRepository {
    override suspend fun process(userId: Long, items: List<SaleItem>, operationId: String?) = request(preferences, "ventas") { val response = api().createSale(SaleRequest(items.map { SaleItemDto(it.producto_id, it.cantidad) }, operationId)); Sale(response.id, userId, items, response.total) }
    override suspend fun report(period: String) = request(preferences, "reportes/ventas") { api().salesReport(period).ingresos }
}
