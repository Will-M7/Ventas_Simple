import { db } from '../config/database';
export class CategoriaService {
  list(all = false) { return db.prepare('SELECT * FROM categories WHERE ? OR activo=1 ORDER BY nombre').all(all ? 1 : 0); }
  create(input: { nombre?: string; descripcion?: string | null }) { if (!input.nombre?.trim()) throw Object.assign(new Error('El nombre es obligatorio'), { status: 400 }); const r = db.prepare('INSERT INTO categories (nombre,descripcion) VALUES (?,?)').run(input.nombre.trim(), input.descripcion ?? null); return this.byId(Number(r.lastInsertRowid)); }
  update(id: number, input: { nombre?: string; descripcion?: string | null }) { db.prepare('UPDATE categories SET nombre=COALESCE(?,nombre),descripcion=COALESCE(?,descripcion) WHERE id=?').run(input.nombre?.trim() || null, input.descripcion ?? null, id); return this.byId(id); }
  state(id: number, active: boolean) { db.prepare('UPDATE categories SET activo=? WHERE id=?').run(active ? 1 : 0, id); return this.byId(id); }
  byId(id: number) { return db.prepare('SELECT * FROM categories WHERE id=?').get(id); }
}
