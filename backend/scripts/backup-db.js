require('dotenv/config');
const path = require('node:path');
const { createBackup } = require('../dist/operations/backup');
const { version } = require('../package.json');
createBackup({
  databaseFile: path.join(path.resolve(process.env.DATA_DIR || path.join(__dirname,'..','data')), 'tienda.db'),
  uploadsDirectory: path.resolve(process.env.UPLOAD_DIR || path.join(__dirname,'..','uploads')),
  outputDirectory: path.resolve(process.env.BACKUP_DIR || path.join(__dirname,'..','backups')),
  retention: Number(process.env.BACKUP_RETENTION_COUNT || 14), applicationVersion: version,
}).then(destination => console.log('Backup verificado: ' + destination)).catch(() => { console.error('Backup incompleto; no se publicó una copia válida. Revisa disco, permisos y archivos referenciados.'); process.exitCode=1; });
