const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const { once } = require('node:events');
const config = require('../dist/config/database');
const security = require('../dist/utils/security');
let hash;
async function fixture(t) {
  const root = await fs.mkdtemp(path.join(os.tmpdir(),'sistematienda-test-'));
  const uploads = path.join(root,'uploads');
  await fs.mkdir(path.join(uploads,'products'),{recursive:true});
  process.env.UPLOAD_DIR = uploads;
  const db = config.openDatabase(':memory:'); config.configureDatabase(db,root);
  hash ??= security.hashPassword('testpass123');
  const passwordHash = await hash;
  const insert = db.prepare('INSERT INTO users(id,username,password_hash,nombre,rol) VALUES (?,?,?,?,?)');
  insert.run(1,'admin-test',passwordHash,'Admin test','admin');
  insert.run(2,'seller-test',passwordHash,'Seller test','vendedor');
  db.prepare('INSERT INTO products(id,nombre,codigo,precio_compra,precio_venta,stock,stock_minimo) VALUES(1,?,?,?,?,?,?)').run('Producto 1','TEST-1',1,3,10,5);
  const servers=[];
  t.after(async()=>{
    for(const server of servers) if(server.listening) await new Promise((resolve,reject)=>{server.close(e=>e?reject(e):resolve());server.closeIdleConnections();});
    if(db.open)db.close(); await fs.rm(root,{recursive:true,force:true});
  });
  const token=(userId=1)=>{const u=db.prepare('SELECT * FROM users WHERE id=?').get(userId);return security.createToken({id:u.id,username:u.username,rol:u.rol,session_version:u.session_version});};
  return {db,root,uploads,token,servers};
}
async function httpFixture(t) {
  const f=await fixture(t), {createApp}=require('../dist/app');
  const server=createApp().listen(0,'127.0.0.1');f.servers.push(server);await once(server,'listening');
  const base=`http://127.0.0.1:${server.address().port}`;
  async function request(endpoint,{method='GET',body,token=f.token(),headers={}}={}){
    const response=await fetch(base+endpoint,{method,headers:{...(token?{Authorization:'Bearer '+token}:{}),...(body!==undefined && !(body instanceof FormData)?{'Content-Type':'application/json'}:{}),...headers},...(body===undefined?{}:{body:body instanceof FormData?body:JSON.stringify(body)})});
    const result=await response.json().catch(()=>null);return {status:response.status,body:result,headers:response.headers};
  }
  return {...f,base,request};
}
module.exports={fixture,httpFixture};
