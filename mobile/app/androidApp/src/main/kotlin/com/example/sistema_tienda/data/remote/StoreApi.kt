package com.example.sistema_tienda.data.remote

import retrofit2.http.*
import retrofit2.Response
import okhttp3.MultipartBody

data class LoginRequest(val username: String, val password: String)
data class UserDto(val id: Long, val username: String, val nombre: String, val rol: String, val token: String? = null)
data class LoginResponse(val user: UserDto)
data class ProductDto(val id: Long, val codigo: String? = null, val codigo_barras: String? = null, val nombre: String, val precio_compra: Double, val precio_venta: Double, val stock: Int, val descripcion: String? = null, val stock_minimo: Int? = null, val categoria_id: Long? = null, val categoria_nombre: String? = null, val imagen: String? = null, val activo: Int = 1)
data class SaleItemDto(val producto_id: Long, val cantidad: Int)
data class SaleRequest(val items: List<SaleItemDto>)
data class SaleDto(val id: Long, val total: Double)
data class ReportDto(val ingresos: Double = 0.0, val total_vendido: Double = 0.0)
data class DashboardDto(val ventasHoy: Int = 0, val ingresosHoy: Double = 0.0, val productosActivos: Int = 0, val productosStockBajo: Int = 0, val unidadesVendidasHoy: Int = 0, val ventasRecientes: List<SaleSummaryDto> = emptyList())
data class SaleSummaryDto(val id: Long, val total: Double, val fecha_creacion: String, val vendedor: String? = null, val vendedor_nombre: String? = null)
data class SaleDetailDto(val id: Long, val usuario_id: Long, val total: Double, val fecha_creacion: String, val vendedor: String? = null, val vendedor_nombre: String? = null, val items: List<SaleLineDto> = emptyList())
data class SaleLineDto(val producto_id: Long, val producto: String? = null, val codigo: String? = null, val cantidad: Int, val precio_unitario: Double, val subtotal: Double)
data class CategoryDto(val id: Long, val nombre: String, val descripcion: String? = null, val activo: Int = 1)
data class MovementDto(val id: Long, val producto_id: Long, val producto: String, val codigo: String, val usuario: String, val tipo: String, val cantidad: Int, val stock_anterior: Int, val stock_nuevo: Int, val fecha_creacion: String)
data class AdminUserDto(val id: Long, val username: String, val nombre: String, val rol: String, val activo: Int)
data class TopProductDto(val id: Long, val codigo: String, val nombre: String, val unidades: Int, val ingresos: Double)

interface StoreApi {
    @GET("../health") suspend fun health(): Response<Unit>
    @POST("usuarios/login") suspend fun login(@Body request: LoginRequest): LoginResponse
    @GET("productos") suspend fun products(@Query("search") search: String? = null, @Query("categoria") category: Long? = null): List<ProductDto>
    @GET("productos/{id}") suspend fun product(@Path("id") id: Long): ProductDto
    @POST("productos") suspend fun createProduct(@Body body: Map<String, Any?>): ProductDto
    @PATCH("productos/{id}") suspend fun updateProduct(@Path("id") id: Long, @Body body: Map<String, Any?>): ProductDto
    @Multipart @POST("productos/{id}/imagen") suspend fun uploadProductImage(@Path("id") id: Long, @Part foto: MultipartBody.Part): ProductDto
    @PATCH("productos/{id}/estado") suspend fun productState(@Path("id") id: Long, @Body body: Map<String, Boolean>): ProductDto
    @GET("productos/codigo/{code}") suspend fun productByCode(@Path("code") code: String): ProductDto
    @PATCH("productos/{id}/stock") suspend fun addStock(@Path("id") id: Long, @Body quantity: Map<String, Int>): ProductDto
    @POST("../inventario/{id}/disminuir") suspend fun decreaseStock(@Path("id") id: Long, @Body quantity: Map<String, Int>): ProductDto
    @POST("ventas") suspend fun createSale(@Body request: SaleRequest): SaleDto
    @GET("reportes/ventas") suspend fun salesReport(@Query("periodo") period: String): ReportDto
    @GET("reportes/dashboard") suspend fun dashboard(): DashboardDto
    @GET("reportes/ventas") suspend fun report(@Query("periodo") period: String): DashboardReportDto
    @GET("reportes/productos-mas-vendidos") suspend fun topProducts(): List<TopProductDto>
    @GET("reportes/stock-bajo") suspend fun lowStock(): List<ProductDto>
    @GET("reportes/inventario") suspend fun inventory(): List<MovementDto>
    @GET("ventas") suspend fun sales(): List<SaleSummaryDto>
    @GET("ventas/mias") suspend fun mySales(): List<SaleSummaryDto>
    @GET("ventas/{id}") suspend fun sale(@Path("id") id: Long): SaleDetailDto
    @GET("categorias") suspend fun categories(): List<CategoryDto>
    @POST("categorias") suspend fun createCategory(@Body body: Map<String, String?>): CategoryDto
    @PATCH("categorias/{id}") suspend fun updateCategory(@Path("id") id: Long, @Body body: Map<String, String?>): CategoryDto
    @PATCH("categorias/{id}/estado") suspend fun categoryState(@Path("id") id: Long, @Body body: Map<String, Boolean>): CategoryDto
    @GET("usuarios") suspend fun users(): List<AdminUserDto>
    @POST("usuarios") suspend fun createUser(@Body body: Map<String, String>): AdminUserDto
    @PATCH("usuarios/{id}/estado") suspend fun userState(@Path("id") id: Long, @Body body: Map<String, Boolean>): AdminUserDto
    @PATCH("usuarios/{id}/password") suspend fun userPassword(@Path("id") id: Long, @Body body: Map<String, String>): AdminUserDto
}
data class DashboardReportDto(val periodo: String = "dia", val ventas: Int = 0, val ingresos: Double = 0.0, val unidadesVendidas: Int = 0, val historial: List<SaleSummaryDto> = emptyList())
