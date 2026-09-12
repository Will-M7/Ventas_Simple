import { Request, Response } from 'express';
import { CategoriaService } from '../services/categoria.service';
const service = new CategoriaService();
export const list = (req: Request, res: Response) => res.json(service.list(req.user?.rol === 'admin' && req.query.incluirInactivas === 'true'));
export const create = (req: Request, res: Response) => res.status(201).json(service.create(req.body));
export const update = (req: Request, res: Response) => res.json(service.update(Number(req.params.id), req.body));
export const state = (req: Request, res: Response) => res.json(service.state(Number(req.params.id), Boolean(req.body.activo)));
