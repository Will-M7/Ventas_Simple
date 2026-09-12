package com.example.sistema_tienda.domain.model

data class User(val id: Long, val username: String, val nombre: String, val rol: String, val token: String? = null)
data class Product(val id: Long, val codigo_barras: String, val nombre: String, val precio_compra: Double, val precio_venta: Double, val stock: Int, val descripcion: String? = null, val stock_minimo: Int? = null, val categoria_nombre: String? = null, val imagen: String? = null, val activo: Boolean = true, val categoria_id: Long? = null)
data class SaleItem(val producto_id: Long, val cantidad: Int)
data class Sale(val id: Long, val usuario_id: Long, val items: List<SaleItem>, val total: Double)
