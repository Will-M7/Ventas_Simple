const { verifyBackup } = require('../dist/operations/backup');
if (!process.argv[2]) throw new Error('Uso: node scripts/verify-backup.js DIRECTORIO');
verifyBackup(process.argv[2]).then(m => console.log(`Backup válido: esquema ${m.schemaVersion}, ${m.files.length} archivos, ${m.totalBytes} bytes`)).catch(() => { console.error('Backup inválido'); process.exitCode=1; });
