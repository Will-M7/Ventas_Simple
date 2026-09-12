require('dotenv/config');
const { db, initDatabase } = require('../dist/config/database');

db.exec('DROP TABLE IF EXISTS inventory_movements; DROP TABLE IF EXISTS sale_items; DROP TABLE IF EXISTS sales; DROP TABLE IF EXISTS products; DROP TABLE IF EXISTS categories; DROP TABLE IF EXISTS settings; DROP TABLE IF EXISTS users;');
initDatabase();
db.close();
console.log('SQLite recreada con usuarios iniciales y configuración mínima.');
