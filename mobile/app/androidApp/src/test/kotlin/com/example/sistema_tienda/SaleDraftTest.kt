package com.example.sistema_tienda

import com.example.sistema_tienda.domain.model.*
import com.example.sistema_tienda.data.remote.ServerAddress
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class SaleDraftTest {
    private val product = Product(1, "TEST", "Producto", 0.10, 0.20, 2)
    @Test fun quantityAndStockStaySeparate() {
        val first = SaleDraft().add(product)
        val second = first.add(product)
        assertEquals(2, second.cart.single().quantity)
        assertEquals(2, second.cart.single().product.stock)
        assertEquals(second, second.add(product))
        assertEquals(1, second.decrease(1).cart.single().quantity)
        assertTrue(second.decrease(1).decrease(1).cart.isEmpty())
        assertEquals(0.4, second.total, 0.0)
    }
    @Test fun uncertainSaleLocksCartAndReusesItsId() {
        val original = SaleDraft().add(product)
        val pending = original.begin { "11111111-1111-4111-8111-111111111111" }
        assertEquals(pending, pending.add(product))
        assertEquals(pending, pending.decrease(1))
        val retried = pending.failed(false).begin { error("Must reuse ID") }
        assertEquals(pending.pending!!.operationId, retried.pending!!.operationId)
        assertEquals(original.cart, retried.cart)
        assertEquals(retried, retried.confirmed("other-operation"))
        assertTrue(retried.confirmed(retried.pending!!.operationId).cart.isEmpty())
    }
    @Test fun pendingSaleSurvivesSerializationAndDefinitiveFailureAllowsEditing() {
        val pending = SaleDraft().add(product).begin { "11111111-1111-4111-8111-111111111111" }
        val restored = Gson().fromJson(Gson().toJson(pending), SaleDraft::class.java).validated()
        assertEquals(pending, restored)
        assertEquals(pending.pending!!.operationId, restored.begin { error("Must reuse ID") }.pending!!.operationId)
        val edited = restored.failed(true).add(product)
        assertNull(edited.pending)
        assertEquals(2, edited.cart.single().quantity)
    }
    @Test fun serverAddressSupportsLegacyHostHttpsAndCustomPort() {
        assertEquals("http://192.168.1.22:3000/", ServerAddress.normalize("192.168.1.22"))
        assertEquals("https://tienda.ejemplo.com/", ServerAddress.normalize("https://tienda.ejemplo.com/"))
        assertEquals("http://192.168.1.22:8080/", ServerAddress.normalize("http://192.168.1.22:8080/"))
        assertEquals("https://tienda.ejemplo.com/api/", ServerAddress.api("https://tienda.ejemplo.com/").toString())
        for (value in listOf("", "ftp://example.com/", "http://user:password@example.com/", "http://example.com/?token=x")) {
            assertThrows(IllegalArgumentException::class.java) { ServerAddress.normalize(value) }
        }
    }
}
