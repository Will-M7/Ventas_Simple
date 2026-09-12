import { Request, Response } from 'express';
import { VentaService } from '../services/venta.service';
const service = new VentaService();
export const create = (req: Request, res: Response) => res.status(201).json(service.create(req.user!.id, req.body.items));
export const list = (req: Request, res: Response) => res.json(service.list(req.user!.rol === 'admin' ? undefined : req.user!.id, String(req.query.desde ?? ''), String(req.query.hasta ?? '')));
export const mine = (req: Request, res: Response) => res.json(service.list(req.user!.id, String(req.query.desde ?? ''), String(req.query.hasta ?? '')));
export const byId = (req: Request, res: Response) => { const sale = service.byId(Number(req.params.id)); if (!sale) return res.status(404).json({ message: 'Venta no encontrada' }); if (req.user!.rol !== 'admin' && (sale as unknown as { usuario_id: number }).usuario_id !== req.user!.id) return res.status(403).json({ message: 'No tienes acceso a esta venta' }); return res.json(sale); };
