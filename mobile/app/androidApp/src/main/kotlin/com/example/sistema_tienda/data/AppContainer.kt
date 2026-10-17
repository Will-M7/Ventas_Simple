package com.example.sistema_tienda.data

import android.content.Context
import com.example.sistema_tienda.data.local.UserPreferencesManager
import com.example.sistema_tienda.data.remote.*
import com.example.sistema_tienda.data.repositories.*
import com.example.sistema_tienda.domain.model.SaleDraft
import com.example.sistema_tienda.domain.usecase.*
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MultipartBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.security.MessageDigest

class AppContainer private constructor(context: Context) {
    private val preferences = UserPreferencesManager(context.applicationContext)
    private val gson = Gson()
    private var clientKey: Pair<String, String?>? = null
    private var client: StoreApi? = null
    private val api: StoreApi
        @Synchronized get() {
            val base = getServerIp()
            val key = base to preferences.get()?.token
            if (key != clientKey || client == null) {
                client = ApiFactory.create(preferences, base, key.second)
                clientKey = key
            }
            return client!!
        }
    val auth = AuthRepositoryImpl({ api }, preferences)
    val products = ProductRepositoryImpl({ api }, preferences)
    val sales = SaleRepositoryImpl({ api }, preferences)
    fun getServerIp(): String = preferences.getServerIp()
    val sessionInvalidated = preferences.sessionInvalidated
    val resultNotice = preferences.resultNotice
    fun clearResultNotice() = preferences.showResultNotice(null)
    fun currentUser() = preferences.get()
    fun isCurrentSession(userId: Long, origin: String) = currentUser()?.id == userId && serverBaseUrl() == origin
    fun saveServerIp(value: String) = preferences.saveServerIp(value)
    fun serverBaseUrl(): String = if (getServerIp().isBlank()) "" else ServerAddress.api(getServerIp()).toString()
    fun serverAssetsUrl(path: String): String = getServerIp().toHttpUrl().resolve(path.removePrefix("/"))!!.toString()
    private suspend fun <T> call(endpoint: String, block: suspend (StoreApi) -> T): T = apiCall(preferences, endpoint) { block(api) }
    suspend fun dashboard() = call("reportes/dashboard") { it.dashboard() }
    suspend fun report(period: String) = call("reportes/ventas") { it.report(period) }
    suspend fun topProducts(period: String = "dia") = call("reportes/productos-mas-vendidos") { it.topProducts(period) }
    suspend fun lowStock() = call("reportes/stock-bajo") { it.lowStock() }
    suspend fun inventory() = call("reportes/inventario") { it.inventory() }
    suspend fun sales() = call("ventas") { it.sales() }
    suspend fun mySales() = call("ventas/mias") { it.mySales() }
    suspend fun sale(id: Long) = call("ventas/$id") { it.sale(id) }
    suspend fun categories() = call("categorias") { it.categories() }
    suspend fun createCategory(name: String, description: String?) = call("categorias") { it.createCategory(CategoryRequest(name, description)) }
    suspend fun updateCategory(id: Long, name: String, description: String?) = call("categorias/$id") { it.updateCategory(id, CategoryRequest(name, description)) }
    suspend fun setCategoryActive(id: Long, active: Boolean) = call("categorias/$id/estado") { it.categoryState(id, ActiveRequest(active)) }
    suspend fun users() = call("usuarios") { it.users() }
    suspend fun createUser(name: String, username: String, password: String, role: String) = call("usuarios") { it.createUser(CreateUserRequest(name, username, password, role)) }
    suspend fun setUserActive(id: Long, active: Boolean) = call("usuarios/$id/estado") { it.userState(id, ActiveRequest(active)) }
    suspend fun setUserPassword(id: Long, password: String) = call("usuarios/$id/password") { it.userPassword(id, PasswordRequest(password)) }
    suspend fun createProduct(name: String, code: String, purchase: Double, sale: Double, stock: Int, description: String? = null, minimum: Int? = null, categoryId: Long? = null) = call("productos") {
        it.createProduct(CreateProductRequest(name, code, purchase, sale, stock, description, minimum, categoryId))
    }
    suspend fun product(id: Long) = call("productos/$id") { it.product(id) }
    suspend fun searchProducts(search: String) = call("productos") { it.products(search = search.ifBlank { null }) }
    suspend fun decreaseStock(id: Long, quantity: Int) = products.decreaseStock(id, quantity)
    suspend fun setProductActive(id: Long, active: Boolean) = call("productos/$id/estado") { it.productState(id, ActiveRequest(active)) }
    suspend fun updateProduct(id: Long, request: UpdateProductRequest) = products.update(id, request)
    suspend fun uploadProductImage(id: Long, photo: MultipartBody.Part) = call("productos/$id/imagen") { it.uploadProductImage(id, photo) }
    suspend fun testServerConnection(candidate: String = getServerIp()): Result<Unit> = try {
        val service = ApiFactory.create(preferences, ServerAddress.normalize(candidate), null)
        val response = withTimeoutOrNull(8_000L) { service.health() }
            ?: throw java.net.SocketTimeoutException("Se agotó el tiempo de conexión; no se guardó el servidor")
        check(response.isSuccessful && response.body()?.status == "ok" && response.body()?.database == "sqlite") { "El servidor no está disponible o no es compatible" }
        Result.success(Unit)
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) { Result.failure(error) }
    private fun draftKey(userId: Long, origin: String): String = MessageDigest.getInstance("SHA-256")
        .digest("$origin|$userId".toByteArray()).joinToString("") { "%02x".format(it) }
    fun loadSaleDraft(userId: Long, origin: String): SaleDraft {
        val raw = preferences.readDraft(draftKey(userId, origin)) ?: return SaleDraft()
        return gson.fromJson(raw, SaleDraft::class.java)?.validated()
            ?: throw IllegalStateException("No se pudo recuperar la venta pendiente. Consulta al administrador antes de repetirla.")
    }
    fun saveSaleDraft(userId: Long, origin: String, draft: SaleDraft): Boolean = preferences.writeDraft(draftKey(userId, origin), gson.toJson(draft))
    val login = LoginUseCase(auth)
    val session = GetSessionUseCase(auth)
    val logout = LogoutUseCase(auth)
    val getProducts = GetProductsUseCase(products)
    val processSale = ProcessSaleUseCase(sales)
    companion object {
        @Volatile private var instance: AppContainer? = null
        fun get(context: Context): AppContainer = instance ?: synchronized(this) {
            instance ?: AppContainer(context.applicationContext).also { instance = it }
        }
    }
}
