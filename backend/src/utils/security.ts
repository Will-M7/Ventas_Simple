import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
function secret(): string {
  const value = process.env.JWT_SECRET;
  if (!value || value.trim().length < 32) throw new Error('JWT_SECRET requiere al menos 32 caracteres');
  return value;
}
export type TokenPayload = { id: number; username: string; rol: 'admin' | 'vendedor'; session_version: number };
export function hashPassword(password: string): Promise<string> { return bcrypt.hash(password, 10); }
export function verifyPassword(password: string, stored: string): Promise<boolean> { return bcrypt.compare(password, stored); }
export function createToken(payload: TokenPayload): string { return jwt.sign(payload, secret(), { expiresIn: '12h', algorithm: 'HS256' }); }
export function verifyToken(token: string): TokenPayload {
  const payload = jwt.verify(token, secret(), { algorithms: ['HS256'] });
  if (typeof payload === 'string' || !Number.isSafeInteger(payload.id) || !Number.isSafeInteger(payload.session_version)) throw new Error('Sesión no vigente');
  return payload as TokenPayload;
}
