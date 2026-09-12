package com.example.sistema_tienda.data

import android.content.Context
import com.example.sistema_tienda.data.local.UserPreferencesManager
import com.example.sistema_tienda.data.remote.ApiFactory
import com.example.sistema_tienda.data.repositories.*
import com.example.sistema_tienda.domain.usecase.*
import java.io.IOException
import okhttp3.MultipartBody

class AppContainer(context: Context) {
    private val preferences = UserPreferencesManager(context.applicationContext)
    private val api = ApiFactory.create(preferences)
    val auth = AuthRepositoryImpl(api, preferences)
    val products = ProductRepositoryImpl(api, preferences)
    val sales = SaleRepositoryImpl(api, preferences)
    fun getServerIp(): String = preferences.getServerIp()
    val sessionInvalidated = preferences.sessionInvalidated
    fun saveServerIp(ip: String) = preferences.saveServerIp(ip)
    fun serverBaseUrl(): String = "http://${preferences.getServerIp()}:3000/api/"
    fun serverAssetsUrl(path: String): String = "http://${preferences.getServerIp()}:3000" + if (path.startsWith('/')) path else "/$path"
    suspend fun dashboard() = api.dashboard()
    suspend fun report(period: String) = api.report(period)
    suspend fun topProducts() = api.topProducts()
    suspend fun lowStock() = api.lowStock()
    suspend fun inventory() = api.inventory()
    suspend fun sales() = api.sales()
    suspend fun mySales() = api.mySales()
    suspend fun sale(id: Long) = api.sale(id)
    suspend fun categories() = api.categories()
    suspend fun createCategory(name: String, description: String?) = api.createCategory(mapOf("nombre" to name, "descripcion" to description))
    suspend fun updateCategory(id: Long, name: String, description: String?) = api.updateCategory(id, mapOf("nombre" to name, "descripcion" to description))
    suspend fun setCategoryActive(id: Long, active: Boolean) = api.categoryState(id, mapOf("activo" to active))
    suspend fun users() = api.users()
    suspend fun createUser(name: String, username: String, password: String, role: String) = api.createUser(mapOf("nombre" to name, "username" to username, "password" to password, "rol" to role))
    suspend fun setUserActive(id: Long, active: Boolean) = api.userState(id, mapOf("activo" to active))
    suspend fun setUserPassword(id: Long, password: String) = api.userPassword(id, mapOf("password" to password))
    suspend fun createProduct(name: String, code: String, purchase: Double, sale: Double, stock: Int, description: String? = null, minimum: Int? = null, categoryId: Long? = null) = api.createProduct(mapOf("nombre" to name, "codigo" to code, "precio_compra" to purchase, "precio_venta" to sale, "stock" to stock, "descripcion" to description, "stock_minimo" to minimum, "categoria_id" to categoryId))
    suspend fun product(id: Long) = api.product(id)
    suspend fun searchProducts(search: String) = api.products(search = search.ifBlank { null })
    suspend fun decreaseStock(id: Long, quantity: Int) = api.decreaseStock(id, mapOf("cantidad" to quantity))
    suspend fun setProductActive(id: Long, active: Boolean) = api.productState(id, mapOf("activo" to active))
    suspend fun updateProduct(id: Long, body: Map<String, Any?>) = api.updateProduct(id, body)
    suspend fun uploadProductImage(id: Long, photo: MultipartBody.Part) = api.uploadProductImage(id, photo)
    suspend fun testServerConnection(): Result<Unit> = try {
        val response = api.health()
        check(response.isSuccessful) { "HTTP ${response.code()} en ${serverBaseUrl()}" }
        Result.success(Unit)
    } catch (error: IOException) {
        Result.failure(Exception("No se pudo conectar a ${serverBaseUrl()}. Toca ⚙️ para cambiar la IP del servidor.", error))
    } catch (error: Exception) {
        Result.failure(error)
    }
    val login = LoginUseCase(auth)
    val session = GetSessionUseCase(auth)
    val logout = LogoutUseCase(auth)
    val getProducts = GetProductsUseCase(products)
    val processSale = ProcessSaleUseCase(sales)
}
