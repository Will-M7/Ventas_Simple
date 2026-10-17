package com.example.sistema_tienda.domain.usecase

import com.example.sistema_tienda.domain.model.*
import com.example.sistema_tienda.domain.repository.*

class LoginUseCase(private val repository: AuthRepository) { suspend operator fun invoke(username: String, password: String) = repository.login(username, password) }
class GetSessionUseCase(private val repository: AuthRepository) { suspend operator fun invoke() = repository.getSession() }
class LogoutUseCase(private val repository: AuthRepository) { suspend operator fun invoke() = repository.logout() }
class GetProductsUseCase(private val repository: ProductRepository) { suspend operator fun invoke() = repository.getProducts() }
class ProcessSaleUseCase(private val repository: SaleRepository) { suspend operator fun invoke(userId: Long, items: List<SaleItem>, operationId: String? = null) = repository.process(userId, items, operationId) }
