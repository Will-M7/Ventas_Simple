import Database from 'better-sqlite3';
import { initialSchema } from './schema';
type Migration = { version: number; name: string; destructive?: boolean; up: (db: Database.Database) => void };
export const migrations: Migration[] = [
  { version: 1, name: 'baseline_sqlite', up: db => { db.exec(initialSchema); } },
  { version: 2, name: 'sessions_and_sale_operations', up: db => {
    db.exec('ALTER TABLE users ADD COLUMN session_version INTEGER NOT NULL DEFAULT 0');
    db.exec('ALTER TABLE sales ADD COLUMN operation_id TEXT');
    db.exec('ALTER TABLE sales ADD COLUMN operation_hash TEXT');
    db.exec('CREATE UNIQUE INDEX idx_sales_operation ON sales(operation_id) WHERE operation_id IS NOT NULL');
  } },
  { version: 3, name: 'sale_items_sale_index', up: db => { db.exec('CREATE INDEX IF NOT EXISTS idx_sale_items_sale ON sale_items(venta_id)'); } },
];
export function migrate(db: Database.Database, steps: Migration[] = migrations): void {
  db.exec('CREATE TABLE IF NOT EXISTS schema_migrations (version INTEGER PRIMARY KEY, name TEXT NOT NULL, applied_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)');
  const known = db.prepare('SELECT version,name FROM schema_migrations ORDER BY version').all() as Array<{version: number; name: string}>;
  for (const row of known) if (!steps.some(m => m.version === row.version && m.name === row.name)) throw new Error('Versión de esquema no compatible con este servidor');
  for (const step of steps) {
    if (step.destructive) throw new Error('Las migraciones destructivas requieren un procedimiento manual');
    db.transaction(() => {
      if (db.prepare('SELECT 1 FROM schema_migrations WHERE version=?').get(step.version)) return;
      step.up(db);
      db.prepare('INSERT INTO schema_migrations(version,name) VALUES (?,?)').run(step.version, step.name);
    }).immediate();
  }
}
export function schemaVersion(db: Database.Database): number {
  return (db.prepare('SELECT COALESCE(MAX(version),0) version FROM schema_migrations').get() as {version: number}).version;
}
