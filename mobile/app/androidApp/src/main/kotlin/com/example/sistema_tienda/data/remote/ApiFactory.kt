package com.example.sistema_tienda.data.remote

import com.example.sistema_tienda.data.local.UserPreferencesManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiFactory {
    fun create(session: UserPreferencesManager): StoreApi {
        val auth = Interceptor { chain ->
            val token = session.get()?.token
            val request = chain.request().newBuilder().apply { if (!token.isNullOrBlank()) addHeader("Authorization", "Bearer $token") }.build()
            chain.proceed(request)
        }
        val dynamicBaseUrl = Interceptor { chain ->
            val serverIp = session.getServerIp()
            require(serverIp.isNotBlank()) { "Configura la IP del servidor desde ⚙️ antes de continuar" }
            val serverBase = "http://$serverIp:3000/api/".toHttpUrl()
            val originalUrl = chain.request().url
            val endpointPath = originalUrl.encodedPath.removePrefix("/api/")
            val targetUrl = serverBase.newBuilder()
                .encodedPath(if (originalUrl.encodedPath.startsWith("/api/")) serverBase.encodedPath + endpointPath else originalUrl.encodedPath)
                .encodedQuery(originalUrl.encodedQuery)
                .build()
            chain.proceed(chain.request().newBuilder().url(targetUrl).build())
        }
        return Retrofit.Builder().baseUrl("http://dynamic.invalid/api/")
            .client(OkHttpClient.Builder().addInterceptor(dynamicBaseUrl).addInterceptor(auth).build())
            .addConverterFactory(GsonConverterFactory.create()).build().create(StoreApi::class.java)
    }
}
