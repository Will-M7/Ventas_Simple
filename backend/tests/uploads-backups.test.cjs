const assert=require('node:assert/strict');
const {test}=require('node:test');
const fs=require('node:fs/promises');
const path=require('node:path');
const os=require('node:os');
const http=require('node:http');
const {once}=require('node:events');
const sharp=require('sharp');
const {httpFixture}=require('./fixtures.cjs');
const {openDatabase}=require('../dist/config/database');
const {createBackup,verifyBackup}=require('../dist/operations/backup');
const {shutdown}=require('../dist/server');
async function photo(format='png') {return sharp({create:{width:8,height:8,channels:3,background:'#ffcc00'}}).toFormat(format).toBuffer();}
async function upload(f,bytes,mime='image/png',name='photo.png',product=1) {
 const body=new FormData();body.append('foto',new Blob([bytes],{type:mime}),name);
 return f.request(`/api/productos/${product}/imagen`,{method:'POST',body});
}
const fileList=f=>fs.readdir(path.join(f.uploads,'products'));
for(const format of ['jpeg','png','webp'])test(`upload ${format} válido se decodifica y se sirve con tipo real`,async t=>{
 const f=await httpFixture(t),r=await upload(f,await photo(format),'image/'+format,'image.'+format);
 assert.equal(r.status,200);const files=await fileList(f);assert.equal(files.length,1);
 const stored=await sharp(path.join(f.uploads,'products',files[0])).metadata();assert.equal(stored.format,format);
 const fetched=await fetch(f.base+r.body.imagen);assert.equal(fetched.status,200);assert.equal(fetched.headers.get('content-type'),'image/'+format);await fetched.arrayBuffer();
});
test('MIME falso, contenido inválido y exceso de bytes no dejan archivos',async t=>{
 const f=await httpFixture(t),png=await photo();
 assert.equal((await upload(f,png,'image/jpeg')).status,400);
 assert.equal((await upload(f,Buffer.from('<html>bad</html>'),'image/png')).status,400);
 assert.equal((await upload(f,Buffer.alloc(5*1024*1024+1),'image/png')).status,413);
 assert.deepEqual(await fileList(f),[]);
});
test('extensión falsa se sustituye por la del formato verificado',async t=>{
 const f=await httpFixture(t),r=await upload(f,await photo('jpeg'),'image/jpeg','payload.html');
 assert.equal(r.status,200);assert.match(r.body.imagen,/\.jpg$/);assert.equal((await fileList(f)).some(n=>n.endsWith('.html')),false);
 assert.equal((await fetch(f.base+'/uploads/products/payload.html')).status,404);
});
test('reemplazo conserva una imagen compartida y elimina la anterior solo sin referencias',async t=>{
 const f=await httpFixture(t),first=await upload(f,await photo());const old=path.basename(first.body.imagen);
 f.db.prepare('INSERT INTO products(id,nombre,codigo,precio_compra,precio_venta,stock,imagen) VALUES(2,?,?,?,?,?,?)').run('P2','P2',1,2,0,first.body.imagen);
 const second=await upload(f,await photo('jpeg'),'image/jpeg');assert.equal(second.status,200);assert.ok((await fileList(f)).includes(old));
 const third=await upload(f,await photo('webp'),'image/webp','new.webp',2);assert.equal(third.status,200);assert.equal((await fileList(f)).includes(old),false);
 assert.equal((await fileList(f)).length,2);
});
test('producto inexistente y fallo posterior a almacenar no dejan huérfanos',async t=>{
 const f=await httpFixture(t),bytes=await photo();assert.equal((await upload(f,bytes,'image/png','photo.png',999)).status,404);assert.deepEqual(await fileList(f),[]);
 const first=await upload(f,bytes);assert.equal(first.status,200);const previous=first.body.imagen;
 f.db.exec("CREATE TEMP TRIGGER fail_image BEFORE UPDATE OF imagen ON products BEGIN SELECT RAISE(ABORT,'image failure'); END;");
 assert.equal((await upload(f,bytes)).status,500);assert.deepEqual(await fileList(f),[path.basename(previous)]);assert.equal(f.db.prepare('SELECT imagen FROM products WHERE id=1').get().imagen,previous);
 const body=new FormData();body.append('nombre','Duplicado');body.append('codigo','TEST-1');body.append('precio_compra','1');body.append('precio_venta','2');body.append('stock','0');body.append('foto',new Blob([bytes],{type:'image/png'}),'new.png');
 assert.equal((await f.request('/api/productos',{method:'POST',body})).status,409);assert.deepEqual(await fileList(f),[path.basename(previous)]);
});
test('backup integral desde WAL activo: DB, imágenes, hashes, retención y detección de alteración',async t=>{
 const root=await fs.mkdtemp(path.join(os.tmpdir(),'sistematienda-backup-test-')),source=path.join(root,'source'),uploads=path.join(root,'uploads'),output=path.join(root,'backups');
 await fs.mkdir(source);await fs.mkdir(path.join(uploads,'products'),{recursive:true});
 const db=openDatabase(path.join(source,'tienda.db'));t.after(async()=>{if(db.open)db.close();await fs.rm(root,{recursive:true,force:true});});
 assert.equal(db.pragma('journal_mode',{simple:true}),'wal');assert.equal(db.pragma('foreign_keys',{simple:true}),1);assert.equal(db.pragma('busy_timeout',{simple:true}),5000);
 db.prepare('INSERT INTO users(username,password_hash,nombre,rol) VALUES(?,?,?,?)').run('test','x','Test','admin');
 const image='/uploads/products/11111111-1111-4111-8111-111111111111.png';await fs.writeFile(path.join(uploads,'products',path.basename(image)),await photo());
 db.prepare('INSERT INTO products(nombre,codigo,precio_compra,precio_venta,stock,imagen) VALUES(?,?,?,?,?,?)').run('P','P',1,2,7,image);
 assert.ok((await fs.stat(path.join(source,'tienda.db-wal'))).size>0);
 const options={databaseFile:path.join(source,'tienda.db'),uploadsDirectory:uploads,outputDirectory:output,retention:2,applicationVersion:'test'};
 const first=await createBackup(options),manifest=await verifyBackup(first);assert.equal(manifest.schemaVersion,3);assert.equal(manifest.files.length,2);
 assert.ok(manifest.files.every(f=>f.sha256.length===64));
 const copy=require('better-sqlite3')(path.join(first,'tienda.db'),{readonly:true});assert.equal(copy.prepare('SELECT stock FROM products').get().stock,7);copy.close();
 const restored=path.join(root,'restored');await fs.cp(first,restored,{recursive:true});
 const restoredDb=openDatabase(path.join(restored,'tienda.db'));
 try{assert.equal(restoredDb.prepare('SELECT stock FROM products').get().stock,7);assert.equal(restoredDb.prepare('SELECT COUNT(*) n FROM schema_migrations').get().n,3);}finally{restoredDb.close();}
 assert.equal((await sharp(path.join(restored,image.slice(1))).metadata()).format,'png');
 await fs.writeFile(path.join(output,'legacy.db'),'do not remove');
 const second=await createBackup(options),third=await createBackup(options);assert.notEqual(second,third);assert.equal(await fs.readFile(path.join(output,'legacy.db'),'utf8'),'do not remove');
 assert.equal((await fs.readdir(output)).filter(n=>n.startsWith('backup-')).length,2);
 await fs.appendFile(path.join(third,'uploads/products',path.basename(image)),'tamper');await assert.rejects(verifyBackup(third),/alterado|faltante/);
 assert.equal(db.prepare('SELECT stock FROM products').get().stock,7);
});
test('backup rechaza referencias faltantes y nunca publica una copia incompleta',async t=>{
 const root=await fs.mkdtemp(path.join(os.tmpdir(),'sistematienda-missing-backup-'));t.after(()=>fs.rm(root,{recursive:true,force:true}));
 await fs.mkdir(path.join(root,'data'));await fs.mkdir(path.join(root,'uploads'));
 const db=openDatabase(path.join(root,'data/tienda.db'));t.after(()=>{if(db.open)db.close();});
 db.prepare('INSERT INTO products(nombre,codigo,precio_compra,precio_venta,stock,imagen) VALUES(?,?,?,?,?,?)').run('P','P',1,2,0,'/uploads/products/11111111-1111-4111-8111-111111111111.png');
 await assert.rejects(createBackup({databaseFile:path.join(root,'data/tienda.db'),uploadsDirectory:path.join(root,'uploads'),outputDirectory:path.join(root,'backups')}));
 assert.deepEqual(await fs.readdir(path.join(root,'backups')),[]);
});
test('shutdown espera petición activa antes de cerrar SQLite',async()=>{
 const db=openDatabase(':memory:');let enteredResolve;const entered=new Promise(r=>enteredResolve=r);let completed=false;
 const server=http.createServer(async(_req,res)=>{enteredResolve();await new Promise(r=>setTimeout(r,30));db.prepare('SELECT 1').get();completed=true;res.end('ok');});
 server.listen(0,'127.0.0.1');await once(server,'listening');
 try{
 const response=fetch(`http://127.0.0.1:${server.address().port}/`);await entered;const closing=shutdown(server,db);assert.equal(db.open,true);
 assert.equal(await (await response).text(),'ok');await closing;assert.equal(completed,true);assert.equal(db.open,false);
 }finally{if(server.listening)server.close();if(db.open)db.close();}
});

test('upload abortado no deja archivos ni ocupa permanentemente un slot',async t=>{
 const f=await httpFixture(t),bytes=await photo();
 const endpoint=new URL(f.base+'/api/productos/1/imagen');
 await new Promise(resolve=>{
  const request=http.request(endpoint,{method:'POST',headers:{Authorization:'Bearer '+f.token(),'Content-Type':'multipart/form-data; boundary=abort-test','Content-Length':100000}});
  request.on('error',()=>resolve());request.on('close',resolve);
  request.write('--abort-test\r\nContent-Disposition: form-data; name="foto"; filename="x.png"\r\nContent-Type: image/png\r\n\r\n');request.write(bytes.subarray(0,10));
  setTimeout(()=>request.destroy(),20);
 });
 await new Promise(r=>setTimeout(r,20));assert.deepEqual(await fileList(f),[]);
 assert.equal((await upload(f,bytes)).status,200);
});

test('la concurrencia de decodificación está acotada y libera capacidad al terminar', async t => {
 const f = await httpFixture(t), bytes = await photo();
 const {storeImage} = require('../dist/services/image.service');
 const results = await Promise.allSettled(Array.from({length:3}, () => storeImage({buffer:bytes,mimetype:'image/png'})));
 assert.equal(results.filter(r=>r.status==='fulfilled').length,2);
 const rejected = results.filter(r=>r.status==='rejected');
 assert.equal(rejected.length,1); assert.equal(rejected[0].reason.status,429);
 assert.equal((await upload(f,bytes)).status,200);
});
