package com.example.sistema_tienda.data.remote

import com.example.sistema_tienda.data.local.UserPreferencesManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiFactory {
    private val shared = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS).build()
    fun create(session: UserPreferencesManager, base: String, token: String? = session.get()?.token): StoreApi {
        val root = ServerAddress.normalize(base)
        val auth = Interceptor { chain ->
            val isLogin = chain.request().url.encodedPath.endsWith("/usuarios/login")
            val request = chain.request().newBuilder().apply {
                if (!isLogin && !token.isNullOrBlank()) header("Authorization", "Bearer $token")
            }.build()
            val response = chain.proceed(request)
            if (response.code == 401 && !isLogin && token != null) session.clearIfCurrent(token, root)
            if (response.header("X-Results-Truncated") == "true") {
                session.showResultNotice("Resultados limitados a ${response.header("X-Result-Limit")}. Usa búsqueda o filtros para consultar otros registros.")
            }
            response
        }
        return Retrofit.Builder().baseUrl(ServerAddress.api(root))
            .client(shared.newBuilder().addInterceptor(auth).build())
            .addConverterFactory(GsonConverterFactory.create()).build().create(StoreApi::class.java)
    }
}
