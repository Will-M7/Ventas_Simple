import { Request, Response } from 'express';
import { ReporteService } from '../services/reporte.service';
const service = new ReporteService();
const owner = (req: Request) => req.user!.rol === 'admin' ? undefined : req.user!.id;
export const dashboard = (req: Request, res: Response) => res.json(service.dashboard(owner(req)));
export const sales = (req: Request, res: Response) => res.json(service.summary(owner(req), String(req.query.periodo ?? 'dia'), req.query.desde ? String(req.query.desde) : undefined, req.query.hasta ? String(req.query.hasta) : undefined));
export const top = (req: Request, res: Response) => res.json(service.top(owner(req)));
export const lowStock = (_req: Request, res: Response) => res.json(service.lowStock());
export const movements = (_req: Request, res: Response) => res.json(service.movements());
