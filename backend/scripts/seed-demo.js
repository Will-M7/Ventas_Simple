require('dotenv/config');
if (process.env.NODE_ENV === 'production' || process.env.ALLOW_DEMO_SEED !== 'true') throw new Error('Seed permitido solo con ALLOW_DEMO_SEED=true fuera de producción');
async function main() {
const config = require('../dist/config/database');
await config.initDatabase();
const { db } = config;
const category = db.prepare('INSERT OR IGNORE INTO categories (nombre, descripcion) VALUES (?, ?)');
for (const [name, description] of [['Bebidas', 'Bebidas frías y calientes'], ['Abarrotes', 'Productos de despensa'], ['Limpieza', 'Limpieza del hogar']]) category.run(name, description);
const categoryId = db.prepare('SELECT id FROM categories WHERE nombre=?');
const product = db.prepare('INSERT OR IGNORE INTO products (nombre, descripcion, categoria_id, precio_compra, precio_venta, stock, stock_minimo, codigo) VALUES (?,?,?,?,?,?,?,?)');
[['Agua mineral 625ml', 'Botella individual', 'Bebidas', 1.2, 2.0, 20, 5, 'DEMO-AGUA-625'], ['Arroz extra 1kg', 'Arroz embolsado', 'Abarrotes', 3.2, 4.5, 15, 4, 'DEMO-ARROZ-1K'], ['Lavavajillas 500ml', 'Producto de limpieza', 'Limpieza', 3.5, 5.2, 10, 3, 'DEMO-LAVA-500']].forEach(([name, description, categoryName, purchase, sale, stock, minimum, code]) => product.run(name, description, categoryId.get(categoryName).id, purchase, sale, stock, minimum, code));
db.close();
console.log('Datos demo mínimos agregados.');

}
main().catch(() => { console.error("Seed falló"); process.exitCode=1; });
