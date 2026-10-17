import Database from 'better-sqlite3';
import fs from 'node:fs/promises';
import path from 'node:path';
import { createHash, randomUUID } from 'node:crypto';
import { integer } from '../utils/validation';
type FileEntry = { path: string; size: number; sha256: string };
type Manifest = { managedBy: string; formatVersion: number; createdAt: string; applicationVersion: string; schemaVersion: number; files: FileEntry[]; totalBytes: number; externalImageReferences: string[] };
const owner = 'sistematienda-integral-backup';
async function filesUnder(directory: string, prefix = ''): Promise<string[]> {
  const result: string[] = [];
  for (const entry of await fs.readdir(path.join(directory, prefix), {withFileTypes: true})) {
    const relative = path.join(prefix, entry.name);
    if (entry.isSymbolicLink()) throw new Error('No se admiten enlaces simbólicos en backups');
    if (entry.isDirectory()) result.push(...await filesUnder(directory, relative));
    else if (entry.isFile()) result.push(relative);
    else throw new Error('Tipo de archivo no admitido');
  }
  return result.sort();
}
async function hashFile(filename: string): Promise<string> {
  const hash = createHash('sha256'), handle = await fs.open(filename, 'r');
  try { for await (const chunk of handle.createReadStream({autoClose: false})) hash.update(chunk); }
  finally { await handle.close(); }
  return hash.digest('hex');
}
function checkDatabase(connection: Database.Database): void {
  const integrity = connection.pragma('integrity_check') as Array<{integrity_check: string}>;
  if (integrity.length !== 1 || integrity[0]?.integrity_check !== 'ok' || (connection.pragma('foreign_key_check') as unknown[]).length) throw new Error('La copia SQLite no supera integridad');
}
function localImages(connection: Database.Database): string[] {
  return (connection.prepare('SELECT DISTINCT imagen FROM products WHERE imagen IS NOT NULL').all() as Array<{imagen: string}>).map(r => r.imagen);
}
export async function verifyBackup(directory: string): Promise<Manifest> {
  const root = path.resolve(directory), manifest = JSON.parse(await fs.readFile(path.join(root, 'manifest.json'), 'utf8')) as Manifest;
  if (manifest.managedBy !== owner || manifest.formatVersion !== 1 || !Array.isArray(manifest.files)) throw new Error('Manifest de backup inválido');
  const entries = new Set<string>(); let bytes = 0;
  for (const entry of manifest.files) {
    const filename = path.resolve(root, entry.path);
    if (!entry.path || path.isAbsolute(entry.path) || !filename.startsWith(root + path.sep) || entries.has(entry.path)) throw new Error('Ruta inválida en manifest');
    entries.add(entry.path);
    const real = await fs.realpath(filename), stat = await fs.lstat(filename);
    if (real !== filename || !stat.isFile() || stat.isSymbolicLink() || stat.size !== entry.size || await hashFile(filename) !== entry.sha256) throw new Error('Archivo de backup alterado o faltante: ' + entry.path);
    bytes += stat.size;
  }
  const actual = (await filesUnder(root)).filter(f => f !== 'manifest.json');
  if (actual.length !== entries.size || actual.some(f => !entries.has(f)) || bytes !== manifest.totalBytes || !entries.has('tienda.db')) throw new Error('Inventario de backup inconsistente');
  const copy = new Database(path.join(root,'tienda.db'), {readonly: true, fileMustExist: true});
  try {
    checkDatabase(copy);
    for (const image of localImages(copy)) {
      if (image.startsWith('/uploads/')) {
        if (!/^\/uploads\/products\/[0-9a-f-]+\.(jpg|jpeg|png|webp|gif)$/.test(image) || !entries.has(image.slice(1))) throw new Error('Falta una imagen referenciada por SQLite');
      }
    }
  } finally { copy.close(); }
  return manifest;
}
export async function createBackup(options: { databaseFile: string; uploadsDirectory: string; outputDirectory: string; retention?: number; applicationVersion?: string }): Promise<string> {
  const retention = integer(options.retention ?? 14, 'retention', 1, 10000);
  const output = path.resolve(options.outputDirectory), source = path.resolve(options.databaseFile), uploads = path.resolve(options.uploadsDirectory);
  if (output === uploads || output.startsWith(uploads + path.sep) || output === path.dirname(source)) throw new Error('Destino de backup no debe estar dentro de uploads ni ser el directorio de datos');
  await fs.mkdir(output, {recursive: true, mode: 0o750});
  const name = 'backup-' + new Date().toISOString().replace(/[:.]/g,'-') + '-' + randomUUID();
  const staging = path.join(output, '.incomplete-' + randomUUID()), destination = path.join(output, name);
  await fs.mkdir(staging, {mode: 0o750});
  try {
    const uploadCopy = path.join(staging, 'uploads');
    await fs.mkdir(uploadCopy, {mode: 0o750});
    // Copy uploads first; the final reference check rejects a concurrent missing image.
    try {
      for (const relative of await filesUnder(uploads)) {
        const target = path.join(uploadCopy, relative);
        await fs.mkdir(path.dirname(target), {recursive: true, mode: 0o750});
        await fs.copyFile(path.join(uploads, relative), target);
      }
    } catch (error) { if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error; }
    const connection = new Database(source, {readonly: true, fileMustExist: true});
    try { await connection.backup(path.join(staging, 'tienda.db')); } finally { connection.close(); }
    // Only the new copy is normalized; the source is never checkpointed or rewritten.
    const copy = new Database(path.join(staging,'tienda.db'), {fileMustExist: true});
    let version = 0, external: string[] = [];
    try {
      copy.pragma('journal_mode = DELETE'); checkDatabase(copy);
      if (copy.prepare("SELECT 1 FROM sqlite_master WHERE type='table' AND name='schema_migrations'").get()) version = (copy.prepare('SELECT COALESCE(MAX(version),0) n FROM schema_migrations').get() as {n: number}).n;
      for (const image of localImages(copy)) {
        if (image.startsWith('/uploads/')) {
          if (!/^\/uploads\/products\/[0-9a-f-]+\.(jpg|jpeg|png|webp|gif)$/.test(image)) throw new Error('Referencia local de imagen inválida');
          const target = path.join(staging,image.slice(1));
          try { await fs.access(target); } catch {
            await fs.mkdir(path.dirname(target), {recursive: true});
            await fs.copyFile(path.join(uploads, image.slice('/uploads/'.length)), target);
          }
        } else external.push(image);
      }
    } finally { copy.close(); }
    const entries: FileEntry[] = [];
    for (const relative of await filesUnder(staging)) {
      const filename = path.join(staging,relative), stat = await fs.stat(filename);
      entries.push({path: relative, size: stat.size, sha256: await hashFile(filename)});
    }
    const manifest: Manifest = {managedBy: owner, formatVersion: 1, createdAt: new Date().toISOString(), applicationVersion: options.applicationVersion ?? 'unknown', schemaVersion: version, files: entries, totalBytes: entries.reduce((n,f) => n+f.size,0), externalImageReferences: external};
    await fs.writeFile(path.join(staging,'manifest.json'), JSON.stringify(manifest,null,2), {flag: 'wx', mode: 0o640});
    await verifyBackup(staging);
    await fs.rename(staging, destination);
    // Retain only complete backups created by this tool. Legacy .db copies are untouched.
    try {
    const managed: Array<{directory: string; createdAt: string}> = [];
    for (const entry of await fs.readdir(output, {withFileTypes: true})) {
      if (!entry.isDirectory() || !entry.name.startsWith('backup-')) continue;
      const directory = path.join(output,entry.name);
      try { const m = await verifyBackup(directory); managed.push({directory,createdAt: m.createdAt}); } catch { /* Never delete an unverified directory. */ }
    }
    managed.sort((a,b) => b.createdAt.localeCompare(a.createdAt) || b.directory.localeCompare(a.directory));
    for (const old of managed.slice(retention)) if (old.directory !== destination) await fs.rm(old.directory, {recursive: true});
    } catch { console.warn('Backup válido; no se pudo completar la retención'); }
    return destination;
  } catch (error) { await fs.rm(staging,{recursive: true, force: true}); throw error; }
}
