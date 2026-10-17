import 'dotenv/config';
import Database from 'better-sqlite3';
import fs from 'node:fs';
import path from 'node:path';
import { migrate } from './migrations';
import { hashPassword } from '../utils/security';
import { password, text } from '../utils/validation';

// Importing services/app never opens or initializes a real database.
export let db: Database.Database;
export let dataDirectory = '';
export function configureDatabase(connection: Database.Database, directory: string): void {
  db = connection;
  dataDirectory = directory;
}
export function openDatabase(filename: string): Database.Database {
  const connection = new Database(filename);
  try {
    connection.pragma('journal_mode = WAL');
    connection.pragma('foreign_keys = ON');
    connection.pragma('busy_timeout = 5000');
    migrate(connection);
    return connection;
  } catch (error) { connection.close(); throw error; }
}
export async function bootstrap(connection: Database.Database, env: NodeJS.ProcessEnv = process.env): Promise<void> {
  if ((connection.prepare('SELECT COUNT(*) n FROM users').get() as {n: number}).n !== 0) return;
  const username = text(env.BOOTSTRAP_ADMIN_USERNAME, 'BOOTSTRAP_ADMIN_USERNAME', 80);
  const initialPassword = password(env.BOOTSTRAP_ADMIN_PASSWORD, 12);
  const name = text(env.BOOTSTRAP_ADMIN_NAME ?? 'Administrador', 'BOOTSTRAP_ADMIN_NAME', 120);
  const hash = await hashPassword(initialPassword);
  connection.transaction(() => {
    if ((connection.prepare('SELECT COUNT(*) n FROM users').get() as {n: number}).n === 0)
      connection.prepare('INSERT INTO users(username,password_hash,nombre,rol) VALUES (?,?,?,?)').run(username, hash, name, 'admin');
  }).immediate();
}
export async function initDatabase(): Promise<void> {
  if (db?.open) return;
  const directory = path.resolve(process.env.DATA_DIR ?? path.join(__dirname, '..', '..', 'data'));
  fs.mkdirSync(directory, { recursive: true, mode: 0o750 });
  const connection = openDatabase(path.join(directory, 'tienda.db'));
  try {
    await bootstrap(connection);
    connection.prepare('INSERT OR IGNORE INTO settings(clave,valor) VALUES (?,?)').run('nombre_tienda', 'Sistematienda');
    configureDatabase(connection, directory);
  } catch (error) { connection.close(); throw error; }
}
export function withTransaction<T>(work: () => T): T { return db.transaction(work).immediate(); }
