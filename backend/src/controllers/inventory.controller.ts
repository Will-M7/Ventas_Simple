import { Request, Response } from 'express';
import { InventoryService } from '../services/inventory.service';
import { id, integer, object, queryText } from '../utils/validation';
import { sendList } from '../utils/http';
const service = new InventoryService();
export const increase = (req: Request, res: Response) => { const b = object(req.body); return res.json(service.change(id(req.params.productoId), req.user!.id, integer(b.cantidad, 'cantidad'), false, b.observacion as string | undefined)); };
export const decrease = (req: Request, res: Response) => { const b = object(req.body); return res.json(service.change(id(req.params.productoId), req.user!.id, integer(b.cantidad, 'cantidad'), true, b.observacion as string | undefined)); };
export const movements = (req: Request, res: Response) => {
  const limit = integer(req.query.limit ?? 100, 'limit', 1, 500), product = queryText(req.query.productoId, 'productoId', 20);
  return sendList(res, service.movements({ ...(product !== undefined ? {productoId: product} : {}), limit: String(limit) }), limit);
};
