#!/usr/bin/env node
const baseUrl = (process.env.BASE_URL || 'http://localhost:3000').replace(/\/$/, '');
const username = process.env.TEST_USERNAME || 'admin';
const password = process.env.TEST_PASSWORD || 'admin123';
const code = `QA-OVERSALE-${Date.now()}`;
async function api(path, options = {}) {
  const response = await fetch(`${baseUrl}${path}`, { ...options, headers: { 'Content-Type': 'application/json', ...(options.headers || {}) } });
  const body = await response.json().catch(() => ({}));
  return { response, body };
}
function assert(condition, message) { if (!condition) throw new Error(message); }
const login = await api('/api/usuarios/login', { method: 'POST', body: JSON.stringify({ username, password }) });
assert(login.response.ok, `Login falló: ${JSON.stringify(login.body)}`);
const userId = login.body.user.id;
const created = await api('/api/productos', { method: 'POST', body: JSON.stringify({ codigo_barras: code, nombre: 'Producto integración', precio_compra: 1, precio_venta: 3, stock: 1 }) });
assert(created.response.status === 201, `Creación falló: ${JSON.stringify(created.body)}`);
const productId = created.body.id;
const sale = await api('/api/ventas', { method: 'POST', body: JSON.stringify({ usuario_id: userId, items: [{ producto_id: productId, cantidad: 2 }] }) });
assert(sale.response.status >= 400 && sale.response.status < 500, `La sobreventa fue aceptada: ${JSON.stringify(sale.body)}`);
const byCode = await api(`/api/productos/codigo/${code}`);
assert(byCode.response.ok, `No se pudo consultar el producto: ${JSON.stringify(byCode.body)}`);
assert(byCode.body.stock === 1, `La transacción no conservó el stock: ${JSON.stringify(byCode.body)}`);
console.log(`OK: sobreventa rechazada (${sale.response.status}) y stock conservado en 1. Producto ${productId}.`);
