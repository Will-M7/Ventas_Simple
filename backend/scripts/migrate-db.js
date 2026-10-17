// Explicit target only: never defaults to the live/local data directory.
const fs = require('node:fs');
const path = require('node:path');
const { openDatabase } = require('../dist/config/database');
const { schemaVersion } = require('../dist/config/migrations');
if (!process.argv[2]) throw new Error('Uso: node scripts/migrate-db.js DIRECTORIO_DE_DATOS_EXPLICITO');
const filename = path.resolve(process.argv[2], 'tienda.db');
if (!fs.existsSync(filename)) throw new Error('No existe la base indicada; no se creará una base vacía');
const db = openDatabase(filename);
try { console.log('Esquema aplicado: ' + schemaVersion(db)); } finally { db.close(); }
