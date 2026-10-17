export class HttpError extends Error {
  constructor(public readonly status: number, message: string) { super(message); }
}
export function object(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new HttpError(400, 'Se requiere un objeto JSON');
  return value as Record<string, unknown>;
}
export function text(value: unknown, name: string, max = 180, allowEmpty = false): string {
  if (typeof value !== 'string' || value.length > max || (!allowEmpty && !value.trim()) || /[\u0000-\u0008\u000b\u000c\u000e-\u001f]/.test(value)) throw new HttpError(400, `${name} inválido (máximo ${max} caracteres)`);
  return value.trim();
}
export function numeric(value: unknown, name: string): number {
  if (typeof value !== 'number' && !(typeof value === 'string' && /^-?\d+(?:\.\d+)?$/.test(value))) throw new HttpError(400, `${name} inválido`);
  const n = Number(value);
  if (!Number.isFinite(n)) throw new HttpError(400, `${name} inválido`);
  return n;
}
export function integer(value: unknown, name: string, min = 1, max = 2_147_483_647): number {
  const n = numeric(value, name);
  if (!Number.isSafeInteger(n) || n < min || n > max) throw new HttpError(400, `${name} debe ser entero entre ${min} y ${max}`);
  return n;
}
export const id = (value: unknown): number => integer(value, 'ID');
export function boolean(value: unknown): boolean {
  if (typeof value !== 'boolean') throw new HttpError(400, 'activo debe ser booleano');
  return value;
}
export function role(value: unknown): 'admin' | 'vendedor' {
  if (value !== 'admin' && value !== 'vendedor') throw new HttpError(400, 'Rol inválido');
  return value;
}
export function password(value: unknown, minimum = 8): string {
  if (typeof value !== 'string' || value.trim().length < minimum || Buffer.byteLength(value, 'utf8') > 72) throw new HttpError(400, `Contraseña: mínimo ${minimum} caracteres y máximo 72 bytes`);
  return value;
}
export function cents(value: unknown, name = 'Precio'): number {
  const n = numeric(value, name), rounded = Math.round(n * 100);
  if (n < 0 || n > 9_999_999.99 || !/^\d+(?:\.\d{1,2})?$/.test(String(n))) throw new HttpError(400, `${name}: importe no negativo con máximo 2 decimales`);
  return rounded;
}
export function safeMoneySum(value: number): number {
  // The compatible REAL representation must round back to the same cents.
  if (!Number.isSafeInteger(value) || value < 0 || Math.round(value / 100 * 100) !== value)
    throw new HttpError(400, 'Total monetario fuera de rango');
  return value;
}
export function queryText(value: unknown, name: string, max = 180): string | undefined {
  return value === undefined ? undefined : text(value, name, max, true);
}
export function queryBoolean(value: unknown, name: string): boolean {
  if (value === undefined || value === 'false') return false;
  if (value === 'true') return true;
  throw new HttpError(400, `${name} debe ser true o false`);
}
export function uuid(value: unknown): string {
  const s = text(value, 'operation_id', 36);
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(s)) throw new HttpError(400, 'operation_id debe ser UUID');
  return s.toLowerCase();
}
