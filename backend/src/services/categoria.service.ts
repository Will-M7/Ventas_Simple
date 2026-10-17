import { db } from '../config/database';
import { HttpError, id, text, object, boolean } from '../utils/validation';
export class CategoriaService {
  list(all = false) { return db.prepare('SELECT * FROM categories WHERE ? OR activo=1 ORDER BY nombre LIMIT 1001').all(all ? 1 : 0); }
  create(value: unknown) {
    const input = object(value), name = text(input.nombre, 'nombre', 120);
    const description = input.descripcion == null ? null : text(input.descripcion, 'descripcion', 2000, true);
    const r = db.prepare('INSERT INTO categories(nombre,descripcion) VALUES (?,?)').run(name, description);
    return this.byId(Number(r.lastInsertRowid));
  }
  update(categoryId: number, value: unknown) {
    this.byId(id(categoryId)); const input = object(value), fields: string[] = [], values: unknown[] = [];
    if (input.nombre !== undefined) { fields.push('nombre=?'); values.push(text(input.nombre, 'nombre', 120)); }
    if (input.descripcion !== undefined) { fields.push('descripcion=?'); values.push(input.descripcion === null ? null : text(input.descripcion, 'descripcion', 2000, true)); }
    if (!fields.length) throw new HttpError(400, 'No hay cambios válidos');
    db.prepare('UPDATE categories SET ' + fields.join(',') + ' WHERE id=?').run(...values, categoryId);
    return this.byId(categoryId);
  }
  state(categoryId: number, active: boolean) { this.byId(id(categoryId)); boolean(active); db.prepare('UPDATE categories SET activo=? WHERE id=?').run(active ? 1 : 0, categoryId); return this.byId(categoryId); }
  byId(categoryId: number) { const row = db.prepare('SELECT * FROM categories WHERE id=?').get(id(categoryId)); if (!row) throw new HttpError(404, 'Categoría no encontrada'); return row; }
}
