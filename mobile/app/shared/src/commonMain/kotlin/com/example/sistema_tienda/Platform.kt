package com.example.sistema_tienda

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform