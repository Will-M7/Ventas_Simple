import { db, withTransaction } from '../config/database';
import { SaleItemInput } from '../types/models';
export class VentaService {
  create(userId: number, items: SaleItemInput[]) {
    if (!Array.isArray(items) || !items.length) throw Object.assign(new Error('La venta debe contener al menos un producto'), { status: 400 });
    return withTransaction(() => {
      const resolved: Array<{ id: number; quantity: number; price: number; subtotal: number; stock: number }> = []; let total = 0;
      for (const item of items) {
        const id = Number(item.productoId ?? item.producto_id); const quantity = Number(item.cantidad);
        if (!Number.isInteger(id) || !Number.isInteger(quantity) || quantity <= 0) throw Object.assign(new Error('Producto y cantidad inválidos'), { status: 400 });
        const product = db.prepare('SELECT id,precio_venta,stock,activo FROM products WHERE id=?').get(id) as { id: number; precio_venta: number; stock: number; activo: number } | undefined;
        if (!product || !product.activo) throw Object.assign(new Error('Producto ' + id + ' no disponible'), { status: 404 });
        if (quantity > product.stock) throw Object.assign(new Error('Stock insuficiente para el producto ' + id), { status: 400 });
        const subtotal = Number((product.precio_venta * quantity).toFixed(2)); total += subtotal;
        resolved.push({ id, quantity, price: product.precio_venta, subtotal, stock: product.stock });
      }
      const sale = db.prepare('INSERT INTO sales (usuario_id,total) VALUES (?,?)').run(userId, Number(total.toFixed(2))); const saleId = Number(sale.lastInsertRowid);
      for (const item of resolved) {
        db.prepare('INSERT INTO sale_items (venta_id,producto_id,cantidad,precio_unitario,subtotal) VALUES (?,?,?,?,?)').run(saleId,item.id,item.quantity,item.price,item.subtotal);
        db.prepare('UPDATE products SET stock=stock-?,fecha_actualizacion=CURRENT_TIMESTAMP WHERE id=? AND stock>=?').run(item.quantity,item.id,item.quantity);
        db.prepare('INSERT INTO inventory_movements (producto_id,usuario_id,tipo,cantidad,stock_anterior,stock_nuevo,referencia) VALUES (?,?,?,?,?,?,?)').run(item.id,userId,'SALE',item.quantity,item.stock,item.stock-item.quantity,String(saleId));
      }
      return this.byId(saleId);
    });
  }
  byId(id: number) {
    const sale = db.prepare('SELECT s.*,u.username vendedor,u.nombre vendedor_nombre FROM sales s JOIN users u ON u.id=s.usuario_id WHERE s.id=?').get(id);
    if (!sale) return null;
    return { ...sale, items: db.prepare('SELECT si.*,p.nombre producto,p.codigo FROM sale_items si JOIN products p ON p.id=si.producto_id WHERE si.venta_id=?').all(id) };
  }
  list(userId?: number, from?: string, to?: string) {
    const where = ['1=1']; const values: unknown[] = [];
    if (userId !== undefined) { where.push('s.usuario_id=?'); values.push(userId); }
    if (from) { where.push('date(s.fecha_creacion)>=date(?)'); values.push(from); }
    if (to) { where.push('date(s.fecha_creacion)<=date(?)'); values.push(to); }
    return db.prepare('SELECT s.*,u.username vendedor,u.nombre vendedor_nombre FROM sales s JOIN users u ON u.id=s.usuario_id WHERE ' + where.join(' AND ') + ' ORDER BY s.id DESC LIMIT 500').all(...values);
  }
}
