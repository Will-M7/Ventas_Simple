package com.example.sistema_tienda.domain.model

import java.util.UUID
import kotlin.math.roundToLong

data class PendingSale(val operationId: String, val cart: List<CartLine>, val uncertain: Boolean = true)
data class SaleDraft(val cart: List<CartLine> = emptyList(), val pending: PendingSale? = null) {
    val editable: Boolean get() = pending?.uncertain != true
    val total: Double get() = cart.sumOf { (it.product.precio_venta * 100).roundToLong() * it.quantity } / 100.0
    fun add(product: Product): SaleDraft {
        if (!editable || !product.activo || product.stock <= 0) return this
        val current = cart.firstOrNull { it.product.id == product.id }
        if (current != null && current.quantity >= product.stock) return this
        val next = if (current == null) cart + CartLine(product, 1) else cart.map {
            if (it.product.id == product.id) it.copy(product = product, quantity = it.quantity + 1) else it
        }
        return SaleDraft(next)
    }
    fun decrease(id: Long): SaleDraft {
        if (!editable) return this
        return SaleDraft(cart.mapNotNull {
            when { it.product.id != id -> it; it.quantity > 1 -> it.copy(quantity = it.quantity - 1); else -> null }
        })
    }
    fun begin(newId: () -> String = { UUID.randomUUID().toString() }): SaleDraft {
        require(cart.isNotEmpty())
        val previous = pending?.takeIf { it.cart == cart }
        return copy(pending = previous?.copy(uncertain = true) ?: PendingSale(newId(), cart.toList()))
    }
    fun failed(definitive: Boolean): SaleDraft = copy(pending = pending?.copy(uncertain = !definitive))
    fun confirmed(operationId: String): SaleDraft = if (pending?.operationId == operationId && pending.cart == cart) SaleDraft() else this
    fun validated(): SaleDraft {
        require(cart.size <= 1000 && cart.map { it.product.id }.distinct().size == cart.size)
        require(cart.all { it.product.id > 0 && it.quantity > 0 && it.product.stock >= it.quantity })
        pending?.let { UUID.fromString(it.operationId); require(it.cart == cart) }
        return this
    }
}
