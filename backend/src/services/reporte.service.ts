import { db } from '../config/database';
import { DateTime } from 'luxon';
import { dateWindow } from '../utils/dates';
export class ReporteService {
  constructor(private readonly now = () => DateTime.utc()) {}
  private filter(userId?: number, periodo = 'dia', from?: string, to?: string) {
    const range = dateWindow(periodo, from, to, this.now()), values: unknown[] = [range.start, range.end];
    let where = 's.fecha_creacion>=? AND s.fecha_creacion<?';
    if (userId !== undefined) { where += ' AND s.usuario_id=?'; values.push(userId); }
    return { where, values, range };
  }
  summary(userId?: number, periodo = 'dia', from?: string, to?: string) {
    const { where, values, range } = this.filter(userId, periodo, from, to);
    const saleTotals = db.prepare('SELECT COUNT(*) ventas,COALESCE(SUM(CAST(ROUND(s.total*100) AS INTEGER)),0) centavos FROM sales s WHERE ' + where).get(...values) as { ventas: number; centavos: number };
    const itemTotals = db.prepare('SELECT COALESCE(SUM(si.cantidad),0) unidades FROM sale_items si JOIN sales s ON s.id=si.venta_id WHERE ' + where).get(...values) as { unidades: number };
    const history = db.prepare('SELECT s.id,s.total,s.fecha_creacion,u.username vendedor FROM sales s JOIN users u ON u.id=s.usuario_id WHERE ' + where + ' ORDER BY s.id DESC LIMIT 101').all(...values);
    return { periodo: from !== undefined || to !== undefined ? 'rango' : periodo, ventas: saleTotals.ventas, ingresos: saleTotals.centavos / 100, unidadesVendidas: itemTotals.unidades, historial: history.slice(0,100), historialTruncado: history.length > 100, limiteHistorial: 100, zonaHoraria: range.zone, desdeUTC: range.start, hastaUTCExclusivo: range.end };
  }
  dashboard(userId?: number) {
    const base = this.summary(userId, 'dia');
    const products = userId === undefined ? db.prepare('SELECT COUNT(*) total,COALESCE(SUM(stock<=COALESCE(stock_minimo,5)),0) bajo FROM products WHERE activo=1').get() as { total:number; bajo:number } : undefined;
    return { ventasHoy: base.ventas, ingresosHoy: base.ingresos, unidadesVendidasHoy: base.unidadesVendidas, ...(products ? { productosActivos: products.total, productosStockBajo: products.bajo } : {}), ventasRecientes: base.historial, historialTruncado: base.historialTruncado, zonaHoraria: base.zonaHoraria };
  }
  top(userId?: number, periodo = 'dia', from?: string, to?: string) {
    const {where, values} = this.filter(userId, periodo, from, to);
    return db.prepare('SELECT p.id,p.codigo,p.nombre,SUM(si.cantidad) unidades,SUM(CAST(ROUND(si.subtotal*100) AS INTEGER))/100.0 ingresos FROM sale_items si JOIN sales s ON s.id=si.venta_id JOIN products p ON p.id=si.producto_id WHERE ' + where + ' GROUP BY p.id ORDER BY unidades DESC,p.id LIMIT 21').all(...values);
  }
  lowStock() { return db.prepare('SELECT * FROM products WHERE activo=1 AND stock<=COALESCE(stock_minimo,5) ORDER BY stock LIMIT 1001').all(); }
  movements() { return db.prepare('SELECT m.*,p.nombre producto,p.codigo,u.username usuario FROM inventory_movements m JOIN products p ON p.id=m.producto_id JOIN users u ON u.id=m.usuario_id ORDER BY m.id DESC LIMIT 201').all(); }
}
