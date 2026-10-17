import { Request, Response } from 'express';
import { VentaService } from '../services/venta.service';
import { id, object, queryText, uuid } from '../utils/validation';
import { sendList } from '../utils/http';
const service = new VentaService();
export const create = (req: Request, res: Response) => { const body = object(req.body); return res.status(201).json(service.create(req.user!.id, body.items as never, uuid(body.operation_id))); };
export const list = (req: Request, res: Response) => sendList(res, service.list(req.user!.rol === 'admin' ? undefined : req.user!.id, queryText(req.query.desde, 'desde', 10), queryText(req.query.hasta, 'hasta', 10)), 500);
export const mine = (req: Request, res: Response) => sendList(res, service.list(req.user!.id, queryText(req.query.desde, 'desde', 10), queryText(req.query.hasta, 'hasta', 10)), 500);
export const byId = (req: Request, res: Response) => { const sale = service.byId(id(req.params.id)); if (!sale) return res.status(404).json({ message: 'Venta no encontrada' }); if (req.user!.rol !== 'admin' && sale.usuario_id !== req.user!.id) return res.status(403).json({ message: 'No tienes acceso a esta venta' }); return res.json(sale); };
