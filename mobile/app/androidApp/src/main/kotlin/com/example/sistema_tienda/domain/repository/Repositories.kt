package com.example.sistema_tienda.domain.repository

import com.example.sistema_tienda.domain.model.*

interface AuthRepository {
    suspend fun login(username: String, password: String): Result<User>
    suspend fun getSession(): User?
    suspend fun logout()
}
interface ProductRepository {
    suspend fun getProducts(): Result<List<Product>>
    suspend fun findByBarcode(code: String): Result<Product>
    suspend fun addStock(id: Long, quantity: Int): Result<Product>
    suspend fun decreaseStock(id: Long, quantity: Int): Result<Product>
    suspend fun update(id: Long, request: com.example.sistema_tienda.data.remote.UpdateProductRequest): Result<Product>
    suspend fun create(product: Product): Result<Product>
}
interface SaleRepository {
    suspend fun process(userId: Long, items: List<SaleItem>): Result<Sale>
    suspend fun report(period: String): Result<Double>
}
