import { Request, Response } from 'express';
import { CategoriaService } from '../services/categoria.service';
import { id, boolean, object, queryBoolean } from '../utils/validation';
import { sendList } from '../utils/http';
const service = new CategoriaService();
export const list = (req: Request, res: Response) => {
  const inactive = queryBoolean(req.query.incluirInactivas, 'incluirInactivas');
  return sendList(res, service.list(req.user?.rol === 'admin' && inactive), 1000);
};
export const create = (req: Request, res: Response) => res.status(201).json(service.create(req.body));
export const update = (req: Request, res: Response) => res.json(service.update(id(req.params.id), req.body));
export const state = (req: Request, res: Response) => res.json(service.state(id(req.params.id), boolean(object(req.body).activo)));
