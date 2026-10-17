import { Request, Response } from 'express';
import { UsuarioService } from '../services/usuario.service';
import { id, object } from '../utils/validation';
import { sendList } from '../utils/http';
const service = new UsuarioService();
export const login = async (req: Request, res: Response) => {
  const body = object(req.body), result = await service.login(body.username, body.password);
  return result ? res.json(result) : res.status(401).json({ message: 'Credenciales inválidas o usuario inactivo' });
};
export const list = (_req: Request, res: Response) => sendList(res, service.list(), 1000);
export const create = async (req: Request, res: Response) => res.status(201).json(await service.create(req.body));
export const update = async (req: Request, res: Response) => res.json(await service.update(id(req.params.id), req.body));
export const state = (req: Request, res: Response) => res.json(service.setActive(id(req.params.id), object(req.body).activo));
export const password = async (req: Request, res: Response) => res.json(await service.update(id(req.params.id), { password: object(req.body).password ?? '' }));
