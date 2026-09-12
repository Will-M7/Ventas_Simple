import { db } from '../config/database';
function period(periodo: string): string {
  const map: Record<string,string> = { dia: "date(s.fecha_creacion)=date('now','localtime')", hoy: "date(s.fecha_creacion)=date('now','localtime')", semana: "date(s.fecha_creacion)>=date('now','-6 days','localtime')", mes: "date(s.fecha_creacion)>=date('now','start of month','localtime')", anio: "date(s.fecha_creacion)>=date('now','start of year','localtime')" };
  return map[periodo] ?? map.dia!;
}
export class ReporteService {
  summary(userId?: number, periodo = 'dia', from?: string, to?: string) {
    const where = [period(periodo)]; const values: unknown[] = [];
    if (userId !== undefined) { where.push('s.usuario_id=?'); values.push(userId); }
    if (from) { where.push('date(s.fecha_creacion)>=date(?)'); values.push(from); }
    if (to) { where.push('date(s.fecha_creacion)<=date(?)'); values.push(to); }
    const filter = where.join(' AND ');
    const saleTotals = db.prepare('SELECT COUNT(*) ventas,COALESCE(SUM(s.total),0) ingresos FROM sales s WHERE ' + filter).get(...values) as { ventas: number; ingresos: number };
    const itemTotals = db.prepare('SELECT COALESCE(SUM(si.cantidad),0) unidades FROM sale_items si JOIN sales s ON s.id=si.venta_id WHERE ' + filter).get(...values) as { unidades: number };
    const ventas = db.prepare('SELECT s.id,s.total,s.fecha_creacion,u.username vendedor FROM sales s JOIN users u ON u.id=s.usuario_id WHERE ' + filter + ' GROUP BY s.id ORDER BY s.id DESC LIMIT 100').all(...values);
    return { periodo, ventas: saleTotals.ventas, ingresos: Number(saleTotals.ingresos), unidadesVendidas: itemTotals.unidades, historial: ventas };
  }
  dashboard(userId?: number) {
    const base = this.summary(userId,'dia');
    const products = userId === undefined ? db.prepare("SELECT COUNT(*) total,COALESCE(SUM(stock<=stock_minimo),0) bajo FROM products WHERE activo=1").get() as { total:number; bajo:number } : undefined;
    return { ventasHoy: base.ventas, ingresosHoy: base.ingresos, unidadesVendidasHoy: base.unidadesVendidas, ...(products ? { productosActivos: products.total, productosStockBajo: products.bajo } : {}), ventasRecientes: base.historial };
  }
  top(userId?: number) { const filter = userId === undefined ? '' : ' WHERE s.usuario_id=' + Number(userId); return db.prepare('SELECT p.id,p.codigo,p.nombre,SUM(si.cantidad) unidades,SUM(si.subtotal) ingresos FROM sale_items si JOIN sales s ON s.id=si.venta_id JOIN products p ON p.id=si.producto_id' + filter + ' GROUP BY p.id ORDER BY unidades DESC LIMIT 20').all(); }
  lowStock() { return db.prepare('SELECT * FROM products WHERE activo=1 AND stock<=COALESCE(stock_minimo,5) ORDER BY stock').all(); }
  movements() { return db.prepare('SELECT m.*,p.nombre producto,p.codigo,u.username usuario FROM inventory_movements m JOIN products p ON p.id=m.producto_id JOIN users u ON u.id=m.usuario_id ORDER BY m.id DESC LIMIT 200').all(); }
}
