package com.example.sistema_tienda.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sistema_tienda.data.AppContainer
import com.example.sistema_tienda.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SessionState { data object Loading : SessionState; data object LoggedOut : SessionState; data class LoggedIn(val user: User) : SessionState; data class Error(val message: String) : SessionState }
class SessionViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading); val state = _state.asStateFlow()
    init {
        viewModelScope.launch {
            _state.value = c.session()?.let { SessionState.LoggedIn(it) } ?: SessionState.LoggedOut
            c.sessionInvalidated.collect { if (c.currentUser() == null) _state.value = SessionState.LoggedOut }
        }
    }
    fun logout() { viewModelScope.launch { c.logout(); _state.value = SessionState.LoggedOut } }
    fun forceLogout() { _state.value = SessionState.LoggedOut }
    fun accept(user: User) { _state.value = SessionState.LoggedIn(user) }
}
class LoginViewModel(private val c: AppContainer) : ViewModel() {
    val username = MutableStateFlow(""); val password = MutableStateFlow(""); val busy = MutableStateFlow(false); val error = MutableStateFlow<String?>(null)
    fun login(onSuccess: (User) -> Unit) {
        if (c.getServerIp().isBlank()) {
            error.value = "Configura la IP del servidor antes de iniciar sesión"
            return
        }
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true; error.value = null
            try {
                c.login(username.value.trim(), password.value)
                    .onSuccess { password.value = ""; onSuccess(it) }
                    .onFailure { error.value = it.message ?: "Credenciales inválidas" }
            } finally { busy.value = false }
        }
    }
}
