import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
const jwtSecret = process.env.JWT_SECRET ?? '';
if (!jwtSecret) throw new Error('JWT_SECRET es obligatorio. Copia backend/.env.example a backend/.env y configura un secreto local.');
export type TokenPayload = { id: number; username: string; rol: 'admin' | 'vendedor' };
export function hashPassword(password: string): string { return bcrypt.hashSync(password, 10); }
export function verifyPassword(password: string, stored: string): boolean { return bcrypt.compareSync(password, stored); }
export function createToken(payload: TokenPayload): string { return jwt.sign(payload, jwtSecret, { expiresIn: '12h' }); }
export function verifyToken(token: string): TokenPayload { return jwt.verify(token, jwtSecret) as TokenPayload; }
