const assert=require('node:assert/strict');
const {test}=require('node:test');
const {randomUUID}=require('node:crypto');
const Database=require('better-sqlite3');
const {DateTime}=require('luxon');
const {fixture,httpFixture}=require('./fixtures.cjs');
const {migrate,migrations,schemaVersion}=require('../dist/config/migrations');
const {initialSchema}=require('../dist/config/schema');
const {bootstrap}=require('../dist/config/database');
const {VentaService}=require('../dist/services/venta.service');
const {UsuarioService}=require('../dist/services/usuario.service');
const {ProductoService}=require('../dist/services/producto.service');
const {InventoryService}=require('../dist/services/inventory.service');
const {ReporteService}=require('../dist/services/reporte.service');
const {dateWindow}=require('../dist/utils/dates');
const {createLoginLimiter}=require('../dist/middleware/loginLimit');
const {assertDestructiveAllowed}=require('../dist/utils/operationGuard');

test('migración de DB estable preserva filas, es repetible y añade columnas/índice',()=>{
 const db=new Database(':memory:');try{
 db.pragma('foreign_keys=ON');db.exec(initialSchema);
 db.prepare('INSERT INTO users(username,password_hash,nombre,rol) VALUES(?,?,?,?)').run('renamed','oldhash','Original','admin');
 db.prepare('INSERT INTO products(nombre,codigo,precio_compra,precio_venta,stock) VALUES(?,?,?,?,?)').run('Original','OLD',1,2,4);
 migrate(db);migrate(db);
 assert.equal(schemaVersion(db),3);assert.equal(db.prepare('SELECT COUNT(*) n FROM users').get().n,1);
 assert.equal(db.prepare('SELECT * FROM users').get().password_hash,'oldhash');
 assert.equal(db.prepare('SELECT * FROM users').get().session_version,0);
 assert.equal(db.prepare('SELECT stock FROM products').get().stock,4);
 assert.ok(db.prepare("SELECT 1 FROM sqlite_master WHERE name='idx_sale_items_sale'").get());
 assert.equal(db.pragma('foreign_keys',{simple:true}),1);
 }finally{db.close();}
});
test('migración fallida revierte DDL y versión; migración destructiva se rechaza',()=>{
 const db=new Database(':memory:');try{
 migrate(db);
 assert.throws(()=>migrate(db,[...migrations,{version:4,name:'broken',up(c){c.exec('CREATE TABLE should_rollback(id)');throw Error('injected');}}]),/injected/);
 assert.equal(schemaVersion(db),3);assert.equal(db.prepare("SELECT 1 FROM sqlite_master WHERE name='should_rollback'").get(),undefined);
 assert.throws(()=>migrate(db,[...migrations,{version:4,name:'destructive',destructive:true,up(){throw Error('never');}}]),/destructivas/);
 }finally{db.close();}
});
test('bootstrap exige configuración segura en base nueva y nunca recrea cuentas',async()=>{
 const db=new Database(':memory:');try{
 migrate(db);await assert.rejects(bootstrap(db,{}));assert.equal(db.prepare('SELECT COUNT(*) n FROM users').get().n,0);
 await bootstrap(db,{BOOTSTRAP_ADMIN_USERNAME:'owner',BOOTSTRAP_ADMIN_PASSWORD:'installation-test-123'});
 db.prepare("UPDATE users SET username='renamed'").run();await bootstrap(db,{});
 assert.equal(db.prepare('SELECT COUNT(*) n FROM users').get().n,1);
 assert.equal(db.prepare("SELECT 1 FROM users WHERE username='admin'").get(),undefined);
 }finally{db.close();}
});
test('login válido, inactivo, contraseña incorrecta y validaciones de usuario',async t=>{
 const f=await httpFixture(t);
 let r=await f.request('/api/usuarios/login',{method:'POST',token:null,body:{username:'admin-test',password:'testpass123'}});
 assert.equal(r.status,200);assert.ok(r.body.token);assert.equal(r.body.user.password_hash,undefined);
 r=await f.request('/api/usuarios/login',{method:'POST',token:null,body:{username:'admin-test',password:'wrong'}});assert.equal(r.status,401);
 new UsuarioService().setActive(2,false);
 r=await f.request('/api/usuarios/login',{method:'POST',token:null,body:{username:'seller-test',password:'testpass123'}});assert.equal(r.status,401);
 for(const body of [{username:'',nombre:'x',password:'testpass123'},{username:'x',nombre:'',password:'testpass123'},{username:'x',nombre:'x',password:'short'},{username:'x',nombre:'x',password:'testpass123',rol:'root'}]){
  assert.equal((await f.request('/api/usuarios',{method:'POST',body})).status,400);
 }
 assert.equal((await f.request('/api/usuarios/2/estado',{method:'PATCH',body:{activo:'false'}})).status,400);
 assert.equal((await f.request('/api/usuarios/2/password',{method:'PATCH',body:{password:''}})).status,400);
 assert.equal((await f.request('/api/usuarios/no-id',{method:'PATCH',body:{nombre:'x'}})).status,400);
});
test('JWT se revoca inmediatamente al cambiar contraseña, rol o estado',async t=>{
 const f=await httpFixture(t),users=new UsuarioService();
 const token=f.token(2);await users.update(2,{password:'replacement123'});
 assert.equal((await f.request('/api/ventas/mias',{token})).status,401);
 assert.equal(await users.login('seller-test','testpass123'),null);
 assert.ok(await users.login('seller-test','replacement123'));
 const next=f.token(2);await users.update(2,{rol:'admin'});
 assert.equal((await f.request('/api/usuarios',{token:next})).status,401);
 const promoted=f.token(2);assert.equal((await f.request('/api/usuarios',{token:promoted})).status,200);
 await users.update(2,{rol:'vendedor'});
 assert.equal((await f.request('/api/usuarios',{token:promoted})).status,401);
 assert.equal((await f.request('/api/usuarios',{token:f.token(2)})).status,403);
 const active=f.token(2);users.setActive(2,false);
 assert.equal((await f.request('/api/ventas/mias',{token:active})).status,401);
 users.setActive(2,true);assert.equal((await f.request('/api/ventas/mias',{token:active})).status,401);
});
test('último admin protegido incluso frente a cambios concurrentes',async t=>{
 const {db}=await fixture(t),users=new UsuarioService();
 assert.throws(()=>users.setActive(1,false),e=>e.status===409);
 await assert.rejects(users.update(1,{rol:'vendedor'}),e=>e.status===409);
 await users.update(2,{rol:'admin'});
 const results=await Promise.allSettled([users.update(1,{rol:'vendedor'}),users.update(2,{rol:'vendedor'})]);
 assert.equal(results.filter(r=>r.status==='fulfilled').length,1);
 assert.equal(db.prepare("SELECT COUNT(*) n FROM users WHERE activo=1 AND rol='admin'").get().n,1);
});
test('limitación de login es por IP/usuario, temporal y acotada',()=>{
 let clock=0;const limiter=createLoginLimiter({now:()=>clock,windowMs:1000,maxIp:3,maxPair:2,maxKeys:10});
 const call=(ip,name)=>{let status=200,next=false,retry;limiter({ip,body:{username:name},socket:{}},{setHeader(k,v){retry=v},status(n){status=n;return this},json(){}},()=>next=true);return{status,next,retry};};
 assert.ok(call('one','u').next);assert.ok(call('one','u').next);assert.equal(call('one','u').status,429);
 assert.ok(call('two','u').next);assert.ok(call('one','other').next);assert.equal(call('one','third').status,429);
 clock=1001;assert.ok(call('one','u').next);
});
test('idempotencia devuelve venta original con stock agotado, sin duplicar registros',async t=>{
 const {db}=await fixture(t),sales=new VentaService(),op=randomUUID();
 const first=sales.create(1,[{producto_id:1,cantidad:10}],op);
 const repeat=sales.create(1,[{producto_id:1,cantidad:4},{productoId:1,cantidad:6}],op);
 assert.equal(first.id,repeat.id);assert.equal(first.total,repeat.total);
 assert.equal(db.prepare('SELECT stock FROM products').get().stock,0);
 for(const table of ['sales','sale_items','inventory_movements'])assert.equal(db.prepare(`SELECT COUNT(*) n FROM ${table}`).get().n,1);
 assert.throws(()=>sales.create(1,[{producto_id:1,cantidad:1}],op),e=>e.status===409);
 assert.throws(()=>sales.create(2,[{producto_id:1,cantidad:10}],op),e=>e.status===409);
});
test('respuesta perdida/repetida y dos POST simultáneos con operation_id',async t=>{
 const f=await httpFixture(t),body={operation_id:randomUUID(),items:[{producto_id:1,cantidad:2}]};
 const responses=await Promise.all([f.request('/api/ventas',{method:'POST',body}),f.request('/api/ventas',{method:'POST',body})]);
 assert.equal(responses[0].status,201);assert.equal(responses[1].status,201);assert.equal(responses[0].body.id,responses[1].body.id);
 const retry=await f.request('/api/ventas',{method:'POST',body});assert.equal(retry.body.id,responses[0].body.id);
 assert.equal(f.db.prepare('SELECT COUNT(*) n FROM sales').get().n,1);assert.equal(f.db.prepare('SELECT stock FROM products').get().stock,8);
});
test('rollback no reserva operation_id: el mismo ID puede reintentarse',async t=>{
 const {db}=await fixture(t),sales=new VentaService(),op=randomUUID();
 db.exec("CREATE TEMP TRIGGER fail_sale BEFORE INSERT ON inventory_movements BEGIN SELECT RAISE(ABORT,'test rollback'); END;");
 assert.throws(()=>sales.create(1,[{producto_id:1,cantidad:2}],op),/test rollback/);
 assert.equal(db.prepare('SELECT COUNT(*) n FROM sales').get().n,0);assert.equal(db.prepare('SELECT stock FROM products').get().stock,10);
 db.exec('DROP TRIGGER fail_sale');assert.ok(sales.create(1,[{producto_id:1,cantidad:2}],op));
});
test('centavos exactos: 0.10+0.20, cantidades múltiples y varias líneas',async t=>{
 const {db}=await fixture(t),products=new ProductoService(),sales=new VentaService();
 products.update(1,{precio_venta:0.1},1);
 const p=products.create({nombre:'P2',codigo:'P2',precio_compra:0,precio_venta:0.2,stock:10},1);
 const first=sales.create(1,[{producto_id:1,cantidad:1},{producto_id:p.id,cantidad:1}],randomUUID());assert.equal(first.total,0.3);
 const second=sales.create(1,[{producto_id:1,cantidad:3},{producto_id:p.id,cantidad:2}],randomUUID());assert.equal(second.total,0.7);
 assert.equal(db.prepare('SELECT SUM(CAST(ROUND(subtotal*100) AS INTEGER)) n FROM sale_items WHERE venta_id=?').get(second.id).n,70);
 assert.throws(()=>products.update(1,{precio_venta:1.005},1),e=>e.status===400);
 assert.throws(()=>products.update(1,{stock_minimo:1.5},1),e=>e.status===400);
});
test('inventario aumenta/disminuye, no acepta negativos ni producto inexistente',async t=>{
 const f=await httpFixture(t),inventory=new InventoryService();
 inventory.change(1,1,3,false,'entrada');inventory.change(1,1,2,true,'salida');
 assert.equal(f.db.prepare('SELECT stock FROM products').get().stock,11);
 assert.throws(()=>inventory.change(1,1,12,true),e=>e.status===400);
 assert.throws(()=>inventory.change(999,1,1,false),e=>e.status===404);
 assert.equal((await f.request('/api/inventario/movimientos?limit=texto')).status,400);
 assert.equal((await f.request('/api/inventario/movimientos?limit=0')).status,400);
 assert.equal((await f.request('/api/productos?categoria=texto')).status,400);
 assert.equal((await f.request('/api/productos?incluirInactivos=texto')).status,400);
 assert.equal((await f.request('/api/categorias?incluirInactivas=1')).status,400);
 const list=await f.request('/api/inventario/movimientos?limit=1');assert.equal(list.body.length,1);assert.equal(list.headers.get('x-results-truncated'),'true');
});
test('Lima: día y medianoche se convierten a límites UTC exclusivos',async t=>{
 const {db}=await fixture(t),now=()=>DateTime.fromISO('2026-10-17T02:00:00Z'),reports=new ReporteService(now);
 const range=dateWindow('dia',undefined,undefined,now());assert.equal(range.start,'2026-10-16 05:00:00');assert.equal(range.end,'2026-10-17 05:00:00');
 const insert=db.prepare('INSERT INTO sales(usuario_id,total,fecha_creacion) VALUES(1,?,?)');
 for(const timestamp of ['2026-10-16 04:59:59','2026-10-16 05:00:00','2026-10-17 04:59:59','2026-10-17 05:00:00'])insert.run(3,timestamp);
 const summary=reports.summary();assert.equal(summary.ventas,2);assert.equal(summary.ingresos,6);
 assert.equal(reports.summary(undefined,'dia','2026-10-17','2026-10-17').ventas,1);
 assert.throws(()=>reports.summary(undefined,'dia','2026-02-30','2026-03-01'),e=>e.status===400);
 assert.throws(()=>reports.summary(undefined,'dia','2026-10-18','2026-10-17'),e=>e.status===400);
});
test('zona configurable respeta DST y rango explícito reemplaza período',()=>{
 const old=process.env.BUSINESS_TIME_ZONE;try{
 process.env.BUSINESS_TIME_ZONE='America/New_York';const r=dateWindow('dia',undefined,undefined,DateTime.fromISO('2026-03-08T12:00:00Z'));
 assert.equal(r.start,'2026-03-08 05:00:00');assert.equal(r.end,'2026-03-09 04:00:00');
 const explicit=dateWindow('dia','2000-01-01','2000-01-01');assert.equal(explicit.start,'2000-01-01 05:00:00');
 }finally{process.env.BUSINESS_TIME_ZONE=old;}
});
test('ranking usa período y propietario; stock mínimo null coincide con dashboard',async t=>{
 const {db}=await fixture(t),reports=new ReporteService(()=>DateTime.fromISO('2026-10-17T02:00:00Z'));
 db.prepare('UPDATE products SET stock=4,stock_minimo=NULL WHERE id=1').run();
 const sale=db.prepare('INSERT INTO sales(usuario_id,total,fecha_creacion) VALUES(?,?,?)'),item=db.prepare('INSERT INTO sale_items(venta_id,producto_id,cantidad,precio_unitario,subtotal) VALUES(?,1,?,3,?)');
 item.run(sale.run(1,3,'2026-10-16 06:00:00').lastInsertRowid,1,3);item.run(sale.run(2,15,'2026-10-15 06:00:00').lastInsertRowid,5,15);
 assert.equal(reports.top(undefined,'dia')[0].unidades,1);assert.equal(reports.top(undefined,'semana')[0].unidades,6);assert.equal(reports.top(2,'dia').length,0);
 assert.equal(reports.dashboard().productosStockBajo,1);assert.equal(reports.lowStock().length,1);
});
test('health comprueba DB y errores 500 no filtran detalles',async t=>{
 const f=await httpFixture(t);assert.equal((await f.request('/health',{token:null})).status,200);
 f.db.exec("CREATE TEMP TRIGGER fail_product BEFORE UPDATE ON products BEGIN SELECT RAISE(ABORT,'sensitive sql internal'); END;");
 const r=await f.request('/api/productos/1',{method:'PATCH',body:{nombre:'nuevo'}});assert.equal(r.status,500);assert.equal(r.body.message,'Error interno del servidor');
 f.db.close();assert.equal((await f.request('/health',{token:null})).status,503);
});
test('guarda destructiva rechaza producción y requiere autorización exacta',()=>{
 assert.throws(()=>assertDestructiveAllowed({}));assert.throws(()=>assertDestructiveAllowed({NODE_ENV:'production',ALLOW_DESTRUCTIVE_DB_RESET:'true'}));
 assert.throws(()=>assertDestructiveAllowed({ALLOW_DESTRUCTIVE_DB_RESET:'1'}));assert.doesNotThrow(()=>assertDestructiveAllowed({NODE_ENV:'test',ALLOW_DESTRUCTIVE_DB_RESET:'true'}));
});

test('HTTP exige operation_id y no acepta cantidades booleanas',async t=>{
 const f=await httpFixture(t);
 for(const body of [{items:[{producto_id:1,cantidad:1}]},{operation_id:'bad',items:[{producto_id:1,cantidad:1}]},{operation_id:randomUUID(),items:[{producto_id:1,cantidad:true}]}])assert.equal((await f.request('/api/ventas',{method:'POST',body})).status,400);
 assert.equal(f.db.prepare('SELECT COUNT(*) n FROM sales').get().n,0);
});
test('rol efectivo viene de SQLite y JWT antiguo sin versión se rechaza',async t=>{
 const f=await httpFixture(t),jwt=require('jsonwebtoken');
 const legacy=jwt.sign({id:1,username:'admin-test',rol:'admin'},process.env.JWT_SECRET,{expiresIn:'1h'});
 assert.equal((await f.request('/api/usuarios',{token:legacy})).status,401);
 const mismatched=jwt.sign({id:2,username:'seller-test',rol:'admin',session_version:0},process.env.JWT_SECRET,{expiresIn:'1h'});
 assert.equal((await f.request('/api/usuarios',{token:mismatched})).status,403);
});

test('editar un producto conserva referencias y precios históricos no modificados', async t => {
 const f = await httpFixture(t);
 f.db.prepare('UPDATE products SET imagen=?,precio_compra=? WHERE id=1').run('https://example.invalid/legacy.gif', 1.005);
 const r = await f.request('/api/productos/1', {method:'PATCH', body:{nombre:'Nombre nuevo'}});
 assert.equal(r.status, 200);
 assert.equal(r.body.nombre, 'Nombre nuevo');
 assert.equal(r.body.imagen, 'https://example.invalid/legacy.gif');
 assert.equal(r.body.precio_compra, 1.005);
 assert.equal(f.db.prepare('SELECT COUNT(*) n FROM inventory_movements').get().n, 0);
 assert.equal((await f.request('/api/productos/1', {method:'PATCH',body:{precio_compra:1.005}})).status, 400);
 assert.equal((await f.request('/api/productos/1', {method:'PATCH',body:{imagen:'https://example.invalid/new.gif'}})).status, 400);
});

test('rechaza importes cuyo almacenamiento REAL perdería un centavo', () => {
 const {safeMoneySum} = require('../dist/utils/validation');
 assert.equal(safeMoneySum(30),30);
 assert.throws(()=>safeMoneySum(9007199254740893),e=>e.status===400);
 assert.throws(()=>safeMoneySum(Number.MAX_SAFE_INTEGER+1),e=>e.status===400);
});

test('cliente pierde el cuerpo de respuesta y recupera la venta con el mismo UUID', async t => {
 const f = await httpFixture(t), http = require('node:http');
 const body = {operation_id:randomUUID(),items:[{producto_id:1,cantidad:3}]};
 const payload = JSON.stringify(body);
 await new Promise((resolve,reject)=>{
  const request = http.request(f.base+'/api/ventas', {
   method:'POST',headers:{Authorization:'Bearer '+f.token(),'Content-Type':'application/json','Content-Length':Buffer.byteLength(payload)}
  }, response => {
   assert.equal(response.statusCode,201);
   // Discard the result instead of parsing/storing the confirmed sale ID.
   response.destroy();resolve();
  });
  request.on('error',reject);request.end(payload);
 });
 const recorded = f.db.prepare('SELECT id,total FROM sales WHERE operation_id=?').get(body.operation_id);
 assert.ok(recorded);
 const retry = await f.request('/api/ventas',{method:'POST',body});
 assert.equal(retry.status,201);assert.equal(retry.body.id,recorded.id);assert.equal(retry.body.total,recorded.total);
 assert.equal(f.db.prepare('SELECT stock FROM products WHERE id=1').get().stock,7);
 for (const table of ['sales','sale_items','inventory_movements']) assert.equal(f.db.prepare(`SELECT COUNT(*) n FROM ${table}`).get().n,1);
});
