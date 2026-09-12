import { db } from '../config/database';
import { createToken, hashPassword, verifyPassword } from '../utils/security';
import { Rol, User } from '../types/models';
type UserRow = User & { password_hash: string };
const publicUser = (row: UserRow): User => ({ id: row.id, username: row.username, nombre: row.nombre, rol: row.rol, activo: row.activo, fecha_creacion: row.fecha_creacion });
export class UsuarioService {
  login(username: string, password: string) {
    const row = db.prepare('SELECT * FROM users WHERE username = ?').get(username) as UserRow | undefined;
    if (!row || !row.activo || !verifyPassword(password, row.password_hash)) return null;
    const user = publicUser(row);
    return { token: createToken({ id: user.id, username: user.username, rol: user.rol }), user };
  }
  list(): User[] { return db.prepare('SELECT id,username,nombre,rol,activo,fecha_creacion FROM users ORDER BY id DESC').all() as User[]; }
  create(input: { username: string; password: string; nombre: string; rol?: Rol }) {
    if (!input.username?.trim() || !input.password || !input.nombre?.trim()) throw Object.assign(new Error('username, password y nombre son obligatorios'), { status: 400 });
    const result = db.prepare('INSERT INTO users (username,password_hash,nombre,rol) VALUES (?,?,?,?)').run(input.username.trim(), hashPassword(input.password), input.nombre.trim(), input.rol ?? 'vendedor');
    return this.byId(Number(result.lastInsertRowid));
  }
  update(id: number, input: Partial<{ username: string; nombre: string; rol: Rol; password: string }>) {
    const fields: string[] = []; const values: unknown[] = [];
    for (const key of ['username', 'nombre', 'rol'] as const) if (input[key] !== undefined) { fields.push(key + ' = ?'); values.push(input[key]); }
    if (input.password) { fields.push('password_hash = ?'); values.push(hashPassword(input.password)); }
    if (fields.length) { fields.push('fecha_actualizacion = CURRENT_TIMESTAMP'); values.push(id); db.prepare('UPDATE users SET ' + fields.join(', ') + ' WHERE id = ?').run(...values); }
    return this.byId(id);
  }
  setActive(id: number, activo: boolean) { db.prepare('UPDATE users SET activo=?,fecha_actualizacion=CURRENT_TIMESTAMP WHERE id=?').run(activo ? 1 : 0, id); return this.byId(id); }
  byId(id: number): User | null { return (db.prepare('SELECT id,username,nombre,rol,activo,fecha_creacion FROM users WHERE id=?').get(id) as User | undefined) ?? null; }
}
