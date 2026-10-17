package com.example.sistema_tienda.data.remote

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object ServerAddress {
    fun normalize(value: String): String {
        val raw = value.trim()
        require(raw.isNotEmpty()) { "Escribe una IP o URL del servidor" }
        val candidate = if (raw.contains("://")) raw else "http://$raw${if (raw.contains(':')) "" else ":3000"}/"
        val url = candidate.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("URL inválida. Usa http:// o https://")
        require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) {
            "La URL no debe contener credenciales, parámetros ni fragmentos"
        }
        return url.newBuilder().encodedPath(url.encodedPath.trimEnd('/') + "/").build().toString()
    }
    fun api(base: String): HttpUrl = normalize(base).toHttpUrlOrNull()!!.resolve("api/")!!
}
