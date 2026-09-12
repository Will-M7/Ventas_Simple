import 'dotenv/config';
import Database from 'better-sqlite3';
import fs from 'node:fs';
import path from 'node:path';
import { hashPassword } from '../utils/security';

const dataDir = path.resolve(process.env.DATA_DIR ?? path.join(__dirname, '..', '..', 'data'));
fs.mkdirSync(dataDir, { recursive: true });
export const db: Database.Database = new Database(path.join(dataDir, 'tienda.db'));
db.pragma('journal_mode = WAL');
db.pragma('foreign_keys = ON');
db.pragma('busy_timeout = 5000');

export function initDatabase(): void {
  db.exec(`
    CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL, nombre TEXT NOT NULL, rol TEXT NOT NULL CHECK (rol IN ('admin','vendedor')) DEFAULT 'vendedor', activo INTEGER NOT NULL DEFAULT 1 CHECK (activo IN (0,1)), fecha_creacion TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, fecha_actualizacion TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP);
    CREATE TABLE IF NOT EXISTS categories (id INTEGER PRIMARY KEY AUTOINCREMENT, nombre TEXT NOT NULL UNIQUE, descripcion TEXT, activo INTEGER NOT NULL DEFAULT 1 CHECK (activo IN (0,1)), fecha_creacion TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP);
    CREATE TABLE IF NOT EXISTS products (id INTEGER PRIMARY KEY AUTOINCREMENT, nombre TEXT NOT NULL, descripcion TEXT, categoria_id INTEGER REFERENCES categories(id) ON DELETE SET NULL, precio_compra REAL NOT NULL CHECK (precio_compra >= 0), precio_venta REAL NOT NULL CHECK (precio_venta >= 0), stock INTEGER NOT NULL DEFAULT 0 CHECK (stock >= 0), stock_minimo INTEGER CHECK (stock_minimo IS NULL OR stock_minimo >= 0), codigo TEXT NOT NULL UNIQUE, imagen TEXT, activo INTEGER NOT NULL DEFAULT 1 CHECK (activo IN (0,1)), fecha_creacion TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, fecha_actualizacion TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP);
    CREATE TABLE IF NOT EXISTS sales (id INTEGER PRIMARY KEY AUTOINCREMENT, usuario_id INTEGER NOT NULL REFERENCES users(id), total REAL NOT NULL CHECK (total >= 0), fecha_creacion TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP);
    CREATE TABLE IF NOT EXISTS sale_items (id INTEGER PRIMARY KEY AUTOINCREMENT, venta_id INTEGER NOT NULL REFERENCES sales(id) ON DELETE CASCADE, producto_id INTEGER NOT NULL REFERENCES products(id), cantidad INTEGER NOT NULL CHECK (cantidad > 0), precio_unitario REAL NOT NULL CHECK (precio_unitario >= 0), subtotal REAL NOT NULL CHECK (subtotal >= 0));
    CREATE TABLE IF NOT EXISTS inventory_movements (id INTEGER PRIMARY KEY AUTOINCREMENT, producto_id INTEGER NOT NULL REFERENCES products(id), usuario_id INTEGER NOT NULL REFERENCES users(id), tipo TEXT NOT NULL CHECK (tipo IN ('PRODUCT_CREATE','RESTOCK','MANUAL_INCREASE','MANUAL_DECREASE','SALE')), cantidad INTEGER NOT NULL CHECK (cantidad > 0), stock_anterior INTEGER NOT NULL CHECK (stock_anterior >= 0), stock_nuevo INTEGER NOT NULL CHECK (stock_nuevo >= 0), referencia TEXT, observacion TEXT, fecha_creacion TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP);
    CREATE TABLE IF NOT EXISTS settings (clave TEXT PRIMARY KEY, valor TEXT NOT NULL);
    CREATE INDEX IF NOT EXISTS idx_products_name ON products(nombre);
    CREATE INDEX IF NOT EXISTS idx_products_category ON products(categoria_id);
    CREATE INDEX IF NOT EXISTS idx_products_active_stock ON products(activo, stock);
    CREATE INDEX IF NOT EXISTS idx_sales_date_user ON sales(fecha_creacion, usuario_id);
    CREATE INDEX IF NOT EXISTS idx_sale_items_product ON sale_items(producto_id);
    CREATE INDEX IF NOT EXISTS idx_movements_date ON inventory_movements(fecha_creacion);
    CREATE INDEX IF NOT EXISTS idx_movements_product ON inventory_movements(producto_id);
  `);
  const insertUser = db.prepare('INSERT OR IGNORE INTO users (username,password_hash,nombre,rol) VALUES (?,?,?,?)');
  insertUser.run('admin', hashPassword('admin123'), 'Administrador', 'admin');
  insertUser.run('vendedor1', hashPassword('123'), 'Vendedor 1', 'vendedor');
  db.prepare('INSERT OR IGNORE INTO settings (clave,valor) VALUES (?,?)').run('nombre_tienda', 'Sistematienda');
}

export function withTransaction<T>(work: () => T): T { return db.transaction(work)(); }
