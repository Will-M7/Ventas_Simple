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
    init { viewModelScope.launch { _state.value = try { c.session()?.let { SessionState.LoggedIn(it) } ?: SessionState.LoggedOut } catch (_: Exception) { SessionState.LoggedOut } } }
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
        viewModelScope.launch { busy.value=true; error.value=null; c.login(username.value.trim(), password.value).onSuccess { onSuccess(it) }.onFailure { error.value=it.message ?: "Credenciales inválidas" }; busy.value=false }
    }
}
data class CartLine(val product: Product, val quantity: Int)
data class PosState(val loading:Boolean=false, val products:List<Product> = emptyList(), val cart:List<CartLine> = emptyList(), val scanned:Product?=null, val error:String?=null, val message:String?=null)
class PosViewModel(private val c: AppContainer, private val user: User) : ViewModel() {
    private val _state=MutableStateFlow(PosState()); val state=_state.asStateFlow()
    init { loadProducts() }
    fun loadProducts() { viewModelScope.launch { _state.value=_state.value.copy(loading=true,error=null); c.getProducts().onSuccess { _state.value=_state.value.copy(loading=false,products=it) }.onFailure { _state.value=_state.value.copy(loading=false,error=it.message) } } }
    fun scan(code:String) { viewModelScope.launch { c.products.findByBarcode(code).onSuccess { _state.value=_state.value.copy(scanned=it,error=null) }.onFailure { _state.value=_state.value.copy(error="Producto no encontrado: $code") } } }
    fun add(product:Product) { val old=_state.value.cart.firstOrNull { it.product.id==product.id }; _state.value=_state.value.copy(scanned=null,cart=if(old==null) _state.value.cart+CartLine(product,1) else _state.value.cart.map { if(it.product.id==product.id) it.copy(quantity=it.quantity+1) else it }) }
    fun quantity(id:Long, value:Int) { _state.value=_state.value.copy(cart=_state.value.cart.mapNotNull { if(it.product.id!=id) it else if(value<1)null else it.copy(quantity=value) }) }
    fun sell() { val cart=_state.value.cart; if(cart.isEmpty()) return; viewModelScope.launch { _state.value=_state.value.copy(loading=true); c.processSale(user.id,cart.map { SaleItem(it.product.id,it.quantity) }).onSuccess { _state.value=_state.value.copy(loading=false,cart=emptyList(),message="Venta #${it.id} registrada") }.onFailure { _state.value=_state.value.copy(loading=false,error=it.message) } } }
    fun clear() { _state.value=_state.value.copy(scanned=null,error=null,message=null) }
}
class InventoryViewModel(private val c: AppContainer) : ViewModel() {
    val products=MutableStateFlow<List<Product>>(emptyList()); val error=MutableStateFlow<String?>(null)
    init { refresh() }
    fun refresh() { viewModelScope.launch { c.getProducts().onSuccess { products.value=it }.onFailure { error.value=it.message } } }
    fun addStock(product:Product, quantity:Int) { viewModelScope.launch { c.products.addStock(product.id,quantity).onSuccess { refresh() }.onFailure { error.value=it.message } } }
}
class ReportsViewModel(private val c: AppContainer) : ViewModel() {
    val today=MutableStateFlow(0.0); val week=MutableStateFlow(0.0); val error=MutableStateFlow<String?>(null)
    init { refresh() }
    fun refresh() { viewModelScope.launch { c.sales.report("dia").onSuccess { today.value=it }.onFailure { error.value=it.message }; c.sales.report("semana").onSuccess { week.value=it }.onFailure { error.value=it.message } } }
}
