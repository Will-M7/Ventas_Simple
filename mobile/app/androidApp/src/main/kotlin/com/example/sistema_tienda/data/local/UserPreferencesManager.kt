package com.example.sistema_tienda.data.local

import android.content.Context
import com.example.sistema_tienda.domain.model.User
import com.example.sistema_tienda.data.remote.ServerAddress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class UserPreferencesManager(context: Context) {
    private val preferences = context.getSharedPreferences("store_session", Context.MODE_PRIVATE)
    private val invalidated = MutableStateFlow(0)
    val sessionInvalidated = invalidated.asStateFlow()
    private val notice = MutableStateFlow<String?>(null)
    val resultNotice = notice.asStateFlow()
    fun showResultNotice(message: String?) { notice.value = message }
    fun getServerIp(): String = preferences.getString("server_url", null)
        ?: preferences.getString("server_ip", "").orEmpty().let { if (it.isBlank()) "" else runCatching { ServerAddress.normalize(it) }.getOrDefault("") }
    @Synchronized fun saveServerIp(value: String) {
        val normalized = ServerAddress.normalize(value)
        if (normalized != getServerIp()) clear()
        preferences.edit().putString("server_url", normalized).remove("server_ip").apply()
    }
    fun save(user: User) = preferences.edit().putString("token", user.token).putLong("id_usuario", user.id)
        .putString("username", user.username).putString("nombre", user.nombre).putString("rol", user.rol).apply()
    fun get(): User? {
        val token = preferences.getString("token", null)?.takeIf { it.isNotBlank() } ?: return null
        val username = preferences.getString("username", null) ?: return null
        val userId = preferences.getLong("id_usuario", 0).takeIf { it > 0 } ?: return null
        return User(userId, username, preferences.getString("nombre", "").orEmpty(), preferences.getString("rol", "vendedor").orEmpty(), token)
    }
    @Synchronized fun clearIfCurrent(token: String, base: String) {
        if (get()?.token == token && getServerIp() == base) clear()
    }
    @Synchronized fun clear() {
        preferences.edit().remove("token").remove("id_usuario").remove("username").remove("nombre").remove("rol").apply()
        notice.value = null
        invalidated.update { it + 1 }
    }
    fun readDraft(key: String): String? = preferences.getString("sale_$key", null)
    fun writeDraft(key: String, value: String): Boolean = preferences.edit().putString("sale_$key", value).commit()
}
