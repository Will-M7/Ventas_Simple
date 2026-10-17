import { db, withTransaction } from '../config/database';
import { createToken, hashPassword, verifyPassword } from '../utils/security';
import { Rol, User } from '../types/models';
import { HttpError, object, text, password, role, id, boolean } from '../utils/validation';
type UserRow = User & { password_hash: string; session_version: number };
const publicUser = (row: UserRow): User => ({ id: row.id, username: row.username, nombre: row.nombre, rol: row.rol, activo: row.activo, fecha_creacion: row.fecha_creacion });
let dummyHash: Promise<string> | undefined;
export class UsuarioService {
  async login(username: unknown, rawPassword: unknown) {
    const name = text(username, 'username', 80);
    if (typeof rawPassword !== 'string' || !rawPassword || Buffer.byteLength(rawPassword) > 72) throw new HttpError(400, 'Contraseña inválida');
    const row = db.prepare('SELECT * FROM users WHERE username=?').get(name) as UserRow | undefined;
    dummyHash ??= hashPassword('invalid-account-dummy-password');
    const matches = await verifyPassword(rawPassword, row?.password_hash ?? await dummyHash);
    if (!row || !row.activo || !matches) return null;
    // Password hashing yields to the event loop; recheck state before issuing a token.
    const current = db.prepare('SELECT * FROM users WHERE id=?').get(row.id) as UserRow | undefined;
    if (!current?.activo || current.session_version !== row.session_version || current.password_hash !== row.password_hash) return null;
    return { token: createToken({ id: current.id, username: current.username, rol: current.rol, session_version: current.session_version }), user: publicUser(current) };
  }
  list(): User[] { return db.prepare('SELECT id,username,nombre,rol,activo,fecha_creacion FROM users ORDER BY id DESC LIMIT 1001').all() as User[]; }
  async create(value: unknown) {
    const input = object(value);
    const username = text(input.username, 'username', 80), nombre = text(input.nombre, 'nombre', 120);
    const rol = role(input.rol === undefined ? 'vendedor' : input.rol), hash = await hashPassword(password(input.password));
    const result = db.prepare('INSERT INTO users(username,password_hash,nombre,rol) VALUES (?,?,?,?)').run(username, hash, nombre, rol);
    return this.byId(Number(result.lastInsertRowid));
  }
  async update(userId: number, value: unknown) {
    id(userId); const input = object(value);
    const username = input.username === undefined ? undefined : text(input.username, 'username', 80);
    const nombre = input.nombre === undefined ? undefined : text(input.nombre, 'nombre', 120);
    const rol = input.rol === undefined ? undefined : role(input.rol);
    const hash = input.password === undefined ? undefined : await hashPassword(password(input.password));
    if (username === undefined && nombre === undefined && rol === undefined && hash === undefined) throw new HttpError(400, 'No hay cambios válidos');
    return withTransaction(() => {
      const current = this.row(userId);
      this.protectLastAdmin(current, rol ?? current.rol, !!current.activo);
      const fields: string[] = [], values: unknown[] = [];
      for (const [key, v] of [['username',username],['nombre',nombre],['rol',rol],['password_hash',hash]]) {
        if (v !== undefined) { fields.push(key + '=?'); values.push(v); }
      }
      if (hash !== undefined || (rol !== undefined && rol !== current.rol)) fields.push('session_version=session_version+1');
      db.prepare('UPDATE users SET ' + fields.join(',') + ',fecha_actualizacion=CURRENT_TIMESTAMP WHERE id=?').run(...values, userId);
      return this.byId(userId);
    });
  }
  setActive(userId: number, value: unknown) {
    id(userId); const active = boolean(value);
    return withTransaction(() => {
      const current = this.row(userId);
      this.protectLastAdmin(current, current.rol, active);
      db.prepare('UPDATE users SET activo=?,session_version=session_version+1,fecha_actualizacion=CURRENT_TIMESTAMP WHERE id=?').run(active ? 1 : 0, userId);
      return this.byId(userId);
    });
  }
  private protectLastAdmin(current: UserRow, nextRole: Rol, nextActive: boolean) {
    if (current.activo && current.rol === 'admin' && (!nextActive || nextRole !== 'admin')) {
      const others = db.prepare("SELECT COUNT(*) n FROM users WHERE rol='admin' AND activo=1 AND id<>?").get(current.id) as {n: number};
      if (!others.n) throw new HttpError(409, 'Debe existir al menos un administrador activo');
    }
  }
  private row(userId: number): UserRow {
    const row = db.prepare('SELECT * FROM users WHERE id=?').get(userId) as UserRow | undefined;
    if (!row) throw new HttpError(404, 'Usuario no encontrado');
    return row;
  }
  byId(userId: number): User { return publicUser(this.row(id(userId))); }
}
