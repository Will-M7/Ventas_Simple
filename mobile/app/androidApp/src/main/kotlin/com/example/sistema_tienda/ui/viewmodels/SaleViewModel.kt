package com.example.sistema_tienda.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sistema_tienda.data.AppContainer
import com.example.sistema_tienda.data.repositories.ApiFailure
import com.example.sistema_tienda.domain.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SaleUiState(
    val draft: SaleDraft = SaleDraft(), val recoveryBlocked: Boolean = false, val results: List<Product> = emptyList(), val busy: Boolean = false,
    val error: String? = null, val message: String? = null, val lastAdded: Product? = null, val scannedCount: Int = 0
) { val editable get() = !busy && !recoveryBlocked && draft.editable }

class SaleViewModel(private val c: AppContainer, private val user: User) : ViewModel() {
    private val origin = c.serverBaseUrl()
    private val mutable = MutableStateFlow(try { SaleUiState(draft = c.loadSaleDraft(user.id, origin)) }
        catch (_: Exception) { SaleUiState(recoveryBlocked = true, error = "No se pudo recuperar el borrador. Consulta al administrador antes de repetir una venta.") })
    val state = mutable.asStateFlow()
    private var lookupGeneration = 0
    private val persistence = Mutex()
    private fun bound() = c.isCurrentSession(user.id, origin)
    private suspend fun store(draft: SaleDraft): Boolean = persistence.withLock {
        if (draft != mutable.value.draft) true
        else withContext(Dispatchers.IO) { c.saveSaleDraft(user.id, origin, draft) }
    }
    private fun persist() {
        val draft = mutable.value.draft
        viewModelScope.launch {
            try { if (!store(draft)) mutable.value = mutable.value.copy(error = "No se pudo guardar el borrador local") }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = mutable.value.copy(error = "No se pudo guardar el borrador local") }
        }
    }
    fun add(product: Product): Boolean {
        val old = mutable.value
        if (!old.editable || !bound()) return false
        val next = old.draft.add(product)
        if (next == old.draft) { mutable.value = old.copy(error = if (!product.activo) "Producto inactivo" else "Producto sin stock disponible"); return false }
        mutable.value = old.copy(draft = next, error = null, results = emptyList())
        persist(); return true
    }
    fun decrease(id: Long) {
        val old = mutable.value
        if (!old.editable || !bound()) return
        mutable.value = old.copy(draft = old.draft.decrease(id)); persist()
    }
    fun lookup(value: String, accepted: () -> Unit) {
        if (!mutable.value.editable || value.isBlank() || !bound()) return
        val generation = lookupGeneration
        viewModelScope.launch {
            val result = c.products.findByBarcode(value)
            if (!bound() || generation != lookupGeneration || !mutable.value.editable) return@launch
            result.onSuccess { product ->
                if (add(product)) { mutable.value = mutable.value.copy(lastAdded = product, scannedCount = mutable.value.scannedCount + 1); accepted() }
            }.onFailure { failure ->
                if (failure is ApiFailure && failure.status == 404) {
                    try {
                        val results = c.searchProducts(value).map { Product(it.id, it.codigo ?: it.codigo_barras.orEmpty(), it.nombre, it.precio_compra, it.precio_venta, it.stock, it.descripcion, it.stock_minimo, it.categoria_nombre, it.imagen, it.activo == 1, it.categoria_id) }
                        if (bound() && generation == lookupGeneration && mutable.value.editable) mutable.value = mutable.value.copy(results = results, error = if (results.isEmpty()) "Producto no registrado" else null)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { if (generation == lookupGeneration) mutable.value = mutable.value.copy(error = error.message) }
                } else mutable.value = mutable.value.copy(error = failure.message)
            }
        }
    }
    fun confirm() {
        val current = mutable.value
        if (current.busy || current.recoveryBlocked || current.draft.cart.isEmpty() || !bound()) return
        val snapshot = current.draft.begin()
        val operation = snapshot.pending!!
        lookupGeneration++
        mutable.value = current.copy(draft = snapshot, busy = true, error = null, message = null)
        viewModelScope.launch {
            try {
                // Durably save the ID and exact payload before any network request.
                check(store(snapshot)) { "No se pudo conservar la venta pendiente; no se envió" }
                check(bound()) { "La sesión o el servidor cambiaron" }
                c.processSale(user.id, operation.cart.map { SaleItem(it.product.id, it.quantity) }, operation.operationId)
                    .onSuccess { sale ->
                        mutable.value = mutable.value.copy(draft = mutable.value.draft.confirmed(operation.operationId), results = emptyList(), lastAdded = null, scannedCount = 0, message = "Venta registrada #${sale.id} · Total: ${String.format(java.util.Locale.US, "S/ %.2f", sale.total)}")
                    }.onFailure { error ->
                        val definitive = error is ApiFailure && error.status in listOf(400, 404)
                        mutable.value = mutable.value.copy(draft = mutable.value.draft.failed(definitive), error = error.message ?: "Resultado de venta incierto. Reintenta la misma operación.")
                    }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutable.value = mutable.value.copy(error = error.message) }
            finally {
                withContext(kotlinx.coroutines.NonCancellable) {
                    try { if (!store(mutable.value.draft)) mutable.value = mutable.value.copy(error = "No se pudo actualizar el borrador local; conserva el ID confirmado") }
                    catch (_: Exception) { mutable.value = mutable.value.copy(error = "No se pudo actualizar el borrador local; conserva el ID confirmado") }
                }
                mutable.value = mutable.value.copy(busy = false)
            }
        }
    }
}
