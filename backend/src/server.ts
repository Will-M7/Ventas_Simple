import 'dotenv/config';
import { Server } from 'node:http';
import Database from 'better-sqlite3';
import { app } from './app';
import { db, initDatabase } from './config/database';
import { businessZone } from './utils/dates';
import { integer } from './utils/validation';
export function shutdown(server: Server, connection: Database.Database): Promise<void> {
  return new Promise((resolve, reject) => {
    server.close(error => {
      if (error) { reject(error); return; }
      try { if (connection.open) connection.close(); resolve(); } catch (failure) { reject(failure); }
    });
    server.closeIdleConnections();
  });
}
async function start() {
  if (!process.env.JWT_SECRET || process.env.JWT_SECRET.trim().length < 32) throw new Error('JWT_SECRET requiere al menos 32 caracteres');
  businessZone();
  const port = integer(process.env.PORT ?? 3000, 'PORT', 1, 65535);
  await initDatabase();
  const server = app.listen(port, '0.0.0.0', () => console.log('API escuchando en puerto ' + port));
  let stopping = false;
  const stop = () => {
    if (stopping) return;
    stopping = true;
    shutdown(server, db).catch(() => { console.error('Error al cerrar el servidor'); process.exitCode = 1; });
  };
  process.on('SIGTERM', stop); process.on('SIGINT', stop);
  server.on('error', error => { console.error('No se pudo escuchar', {code: (error as NodeJS.ErrnoException).code}); if (db.open) db.close(); process.exitCode = 1; });
}
if (require.main === module) start().catch(() => { console.error('No se pudo iniciar: revisa configuración, permisos y migraciones'); process.exitCode = 1; });
