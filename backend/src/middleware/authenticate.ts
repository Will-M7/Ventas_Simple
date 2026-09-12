import { NextFunction, Request, Response } from 'express';
import { TokenPayload, verifyToken } from '../utils/security';
declare global { namespace Express { interface Request { user?: TokenPayload; } } }
export function authenticate(req: Request, res: Response, next: NextFunction): void {
  const header = req.header('authorization');
  if (!header?.startsWith('Bearer ')) { res.status(401).json({ message: 'Autenticación requerida' }); return; }
  try { req.user = verifyToken(header.slice(7)); next(); } catch { res.status(401).json({ message: 'Token inválido o expirado' }); }
}
export function authorize(...roles: Array<'admin' | 'vendedor'>) {
  return (req: Request, res: Response, next: NextFunction): void => {
    if (!req.user || !roles.includes(req.user.rol)) { res.status(403).json({ message: 'No tienes permisos para esta operación' }); return; }
    next();
  };
}
