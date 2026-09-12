package com.example.sistema_tienda.data.local

import android.content.Context
import com.example.sistema_tienda.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow

class UserPreferencesManager(context: Context) {
    private val preferences = context.getSharedPreferences("store_session", Context.MODE_PRIVATE)
    private val _sessionInvalidated = MutableStateFlow(0)
    val sessionInvalidated = _sessionInvalidated
    fun getServerIp(): String = preferences.getString(KEY_SERVER_IP, "") ?: ""
    fun saveServerIp(ip: String) = preferences.edit().putString(KEY_SERVER_IP, ip.normalizeServerIp()).apply()
    fun save(user: User) = preferences.edit().putString("token", user.token).putLong("id_usuario", user.id)
        .putString("username", user.username).putString("nombre", user.nombre).putString("rol", user.rol).apply()
    fun get(): User? = preferences.getString("username", null)?.let { username ->
        User(preferences.getLong("id_usuario", 0), username, preferences.getString("nombre", "") ?: "", preferences.getString("rol", "vendedor") ?: "vendedor", preferences.getString("token", null))
    }
    fun clear() { preferences.edit()
        .remove("token").remove("id_usuario").remove("username").remove("nombre").remove("rol")
        .apply(); _sessionInvalidated.value++ }

    private fun String.normalizeServerIp(): String = trim()
        .removePrefix("http://")
        .removePrefix("https://")
        .substringBefore('/')
        .substringBefore(':')
        .trim()

    private companion object {
        const val KEY_SERVER_IP = "server_ip"
    }
}
