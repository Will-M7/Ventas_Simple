import { NextFunction, Request, Response } from 'express';
import { db } from '../config/database';
import { TokenPayload, verifyToken } from '../utils/security';
declare global { namespace Express { interface Request { user?: TokenPayload; } } }
export function authenticate(req: Request, res: Response, next: NextFunction): void {
  const header = req.header('authorization');
  if (!header?.startsWith('Bearer ')) { res.status(401).json({ message: 'Autenticación requerida' }); return; }
  let payload: TokenPayload;
  try { payload = verifyToken(header.slice(7)); }
  catch { res.status(401).json({ message: 'Token inválido o expirado' }); return; }
  try {
    const user = db.prepare('SELECT id,username,rol,activo,session_version FROM users WHERE id=?').get(payload.id) as (TokenPayload & {activo: number}) | undefined;
    if (!user?.activo || user.session_version !== payload.session_version) { res.status(401).json({ message: 'Sesión revocada' }); return; }
    req.user = { id: user.id, username: user.username, rol: user.rol, session_version: user.session_version };
  } catch (error) { next(error); return; }
  next();
}
export function authorize(...roles: Array<'admin' | 'vendedor'>) {
  return (req: Request, res: Response, next: NextFunction): void => {
    if (!req.user || !roles.includes(req.user.rol)) { res.status(403).json({ message: 'No tienes permisos para esta operación' }); return; }
    next();
  };
}
