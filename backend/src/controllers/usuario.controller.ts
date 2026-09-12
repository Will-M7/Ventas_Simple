import { Request, Response } from 'express';
import { UsuarioService } from '../services/usuario.service';
const service = new UsuarioService();
export const login = (req: Request, res: Response) => { const result = service.login(String(req.body.username ?? ''), String(req.body.password ?? '')); if (!result) return res.status(401).json({ message: 'Credenciales inválidas o usuario inactivo' }); return res.json(result); };
export const list = (_req: Request, res: Response) => res.json(service.list());
export const create = (req: Request, res: Response) => res.status(201).json(service.create(req.body));
export const update = (req: Request, res: Response) => { const user = service.update(Number(req.params.id), req.body); return user ? res.json(user) : res.status(404).json({ message: 'Usuario no encontrado' }); };
export const state = (req: Request, res: Response) => { const user = service.setActive(Number(req.params.id), Boolean(req.body.activo)); return user ? res.json(user) : res.status(404).json({ message: 'Usuario no encontrado' }); };
export const password = (req: Request, res: Response) => { const user = service.update(Number(req.params.id), { password: String(req.body.password ?? '') }); return user ? res.json(user) : res.status(404).json({ message: 'Usuario no encontrado' }); };
