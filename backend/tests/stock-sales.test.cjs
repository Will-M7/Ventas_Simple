const assert = require('node:assert/strict');
const { test } = require('node:test');
const { openDatabase, configureDatabase } = require('../dist/config/database');
function loadService(name) { return require(`../dist/services/${name}.service`); }

function fixture(t, stock = 10) {
  const db = openDatabase(':memory:');
  configureDatabase(db, require('node:os').tmpdir());
  t.after(() => db.close());
  db.pragma('foreign_keys = ON');
  db.prepare('INSERT INTO users (id,username,password_hash,nombre,rol) VALUES (1,?,?,?,?)')
    .run('test', 'unused', 'Test', 'admin');
  db.prepare('INSERT INTO products (id,nombre,codigo,precio_compra,precio_venta,stock) VALUES (1,?,?,?,?,?)')
    .run('Producto 1', 'TEST-1', 1, 3, stock);
  const { VentaService } = loadService('venta', db);
  const { ProductoService } = loadService('producto', db);
  return { db, sales: new VentaService(), products: new ProductoService() };
}

function assertNoSale(db, expectedStock) {
  assert.equal(db.prepare('SELECT stock FROM products WHERE id=1').get().stock, expectedStock);
  for (const table of ['sales', 'sale_items', 'inventory_movements']) {
    assert.equal(db.prepare(`SELECT COUNT(*) AS count FROM ${table}`).get().count, 0, table);
  }
}

function assertConsistentSale(db, saleId, productId, before, quantity) {
  const items = db.prepare('SELECT * FROM sale_items WHERE venta_id=? AND producto_id=?').all(saleId, productId);
  const movements = db.prepare("SELECT * FROM inventory_movements WHERE referencia=? AND producto_id=? AND tipo='SALE'")
    .all(String(saleId), productId);
  assert.equal(items.length, 1);
  assert.equal(movements.length, 1);
  assert.equal(items[0].cantidad, quantity);
  assert.equal(movements[0].cantidad, quantity);
  assert.equal(movements[0].stock_anterior, before);
  assert.equal(movements[0].stock_nuevo, before - quantity);
  assert.equal(db.prepare('SELECT stock FROM products WHERE id=?').get(productId).stock, before - quantity);
}

test('línea simple: usa el precio del servidor y registra stock/detalle/movimiento', t => {
  const { db, sales } = fixture(t, 5);
  const sale = sales.create(1, [{ producto_id: 1, cantidad: 2, precio_unitario: 0.01 }]);
  assert.equal(sale.total, 6);
  assert.equal(sale.items[0].precio_unitario, 3);
  assert.equal(sale.items[0].subtotal, 6);
  assertConsistentSale(db, sale.id, 1, 5, 2);
});

test('duplicados que caben: stock 10, cantidades 4+3, una línea y movimiento 10 -> 3', t => {
  const { db, sales } = fixture(t, 10);
  const sale = sales.create(1, [{ producto_id: 1, cantidad: 4 }, { productoId: 1, cantidad: 3 }]);
  assert.equal(sale.total, 21);
  assert.equal(sale.items.length, 1);
  assert.equal(db.prepare('SELECT COUNT(*) AS count FROM sales').get().count, 1);
  assertConsistentSale(db, sale.id, 1, 10, 7);
});

test('duplicados que exceden: stock 5, cantidades 4+4, rechazo completo con 400', t => {
  const { db, sales } = fixture(t, 5);
  assert.throws(() => sales.create(1, [{ producto_id: 1, cantidad: 4 }, { producto_id: 1, cantidad: 4 }]),
    error => error.status === 400 && /Stock insuficiente/.test(error.message));
  assertNoSale(db, 5);
});

test('varios productos: cada detalle y movimiento coincide con el descuento agrupado', t => {
  const { db, sales } = fixture(t, 10);
  db.prepare('INSERT INTO products (id,nombre,codigo,precio_compra,precio_venta,stock) VALUES (2,?,?,?,?,?)')
    .run('Producto 2', 'TEST-2', 2, 5, 8);
  const sale = sales.create(1, [
    { producto_id: 1, cantidad: 4 },
    { producto_id: 2, cantidad: 2 },
    { producto_id: 1, cantidad: 3 },
  ]);
  assert.equal(sale.total, 31);
  assert.equal(sale.items.length, 2);
  assertConsistentSale(db, sale.id, 1, 10, 7);
  assertConsistentSale(db, sale.id, 2, 8, 2);
});

test('fallo después de escribir stock: rollback de toda la venta y todos los productos', t => {
  const { db, sales } = fixture(t, 10);
  db.prepare('INSERT INTO products (id,nombre,codigo,precio_compra,precio_venta,stock) VALUES (2,?,?,?,?,?)')
    .run('Producto 2', 'TEST-2', 2, 5, 8);
  db.exec(`CREATE TEMP TRIGGER fail_second_movement BEFORE INSERT ON inventory_movements
    WHEN NEW.tipo='SALE' AND NEW.producto_id=2
    BEGIN SELECT RAISE(ABORT, 'Fallo de prueba después del descuento'); END;`);
  assert.throws(() => sales.create(1, [{ producto_id: 1, cantidad: 2 }, { producto_id: 2, cantidad: 3 }]),
    /Fallo de prueba/);
  assertNoSale(db, 10);
  assert.equal(db.prepare('SELECT stock FROM products WHERE id=2').get().stock, 8);
});

test('UPDATE sin fila modificada: rechaza y revierte incluso sin excepción SQLite', t => {
  const { db, sales } = fixture(t, 5);
  db.exec(`CREATE TEMP TRIGGER skip_stock_update BEFORE UPDATE OF stock ON products
    BEGIN SELECT RAISE(IGNORE); END;`);
  assert.throws(() => sales.create(1, [{ producto_id: 1, cantidad: 2 }]),
    error => error.status === 400);
  assertNoSale(db, 5);
});

test('cantidades duplicadas inválidas no se compensan mediante agrupación', t => {
  const { db, sales } = fixture(t, 5);
  assert.throws(() => sales.create(1, [{ producto_id: 1, cantidad: 4 }, { producto_id: 1, cantidad: -2 }]),
    error => error.status === 400);
  assertNoSale(db, 5);
});

test('agrupación rechaza una suma fuera del rango entero seguro', t => {
  const { db, sales } = fixture(t, 5);
  assert.throws(() => sales.create(1, [
    { producto_id: 1, cantidad: Number.MAX_SAFE_INTEGER },
    { producto_id: 1, cantidad: 1 },
  ]), error => error.status === 400 && /cantidad total/.test(error.message));
  assertNoSale(db, 5);
});

for (const stock of [12, 5, 0, null, undefined]) {
  test(`update con stock=${String(stock)}: 400 sin modificar producto ni movimientos`, t => {
    const { db, products } = fixture(t, 5);
    assert.throws(() => products.update(1, { stock, nombre: 'No guardar' }, 1),
      error => error.status === 400 && /inventario/i.test(error.message));
    assertNoSale(db, 5);
    assert.equal(products.byId(1).nombre, 'Producto 1');
  });
}

test('update ordinario sin stock: mantiene existencias y no crea movimientos', t => {
  const { db, products } = fixture(t, 5);
  const result = products.update(1, { nombre: 'Editado', precio_venta: 4 }, 1);
  assert.equal(result.nombre, 'Editado');
  assert.equal(result.precio_venta, 4);
  assertNoSale(db, 5);
});
