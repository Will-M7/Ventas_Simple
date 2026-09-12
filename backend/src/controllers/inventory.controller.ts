import { Request, Response } from 'express';
import { InventoryService } from '../services/inventory.service';
const service = new InventoryService();
export const increase = (req: Request, res: Response) => res.json(service.change(Number(req.params.productoId), req.user!.id, Number(req.body.cantidad), false, req.body.observacion));
export const decrease = (req: Request, res: Response) => res.json(service.change(Number(req.params.productoId), req.user!.id, Number(req.body.cantidad), true, req.body.observacion));
export const movements = (req: Request, res: Response) => res.json(service.movements({ ...(req.query.productoId ? { productoId: String(req.query.productoId) } : {}), ...(req.query.limit ? { limit: String(req.query.limit) } : {}) }));
