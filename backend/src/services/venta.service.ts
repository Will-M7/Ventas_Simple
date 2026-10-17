import { createHash } from 'node:crypto';
import { db, withTransaction } from '../config/database';
import { SaleItemInput } from '../types/models';
import { HttpError, cents, safeMoneySum, id, uuid } from '../utils/validation';
import { dateWindow } from '../utils/dates';
type SaleRow = { id: number; usuario_id: number; total: number; fecha_creacion: string; operation_id: string | null; operation_hash: string | null };
export class VentaService {
  create(userId: number, items: SaleItemInput[], operationId?: string) {
    id(userId);
    if (!Array.isArray(items) || !items.length || items.length > 1000) throw new HttpError(400, 'La venta debe contener entre 1 y 1000 líneas');
    const operation = operationId === undefined ? null : uuid(operationId);
    return withTransaction(() => {
      const quantities = new Map<number, number>();
      for (const item of items) {
        if (!item) throw new HttpError(400, 'Producto y cantidad inválidos');
        const productId = id(item.productoId ?? item.producto_id), quantity = Number(item.cantidad);
        if (!Number.isSafeInteger(productId) || productId <= 0 || !Number.isSafeInteger(quantity) || quantity <= 0 || (typeof item.cantidad !== 'number' && !(typeof item.cantidad === 'string' && /^\d+$/.test(item.cantidad)))) throw new HttpError(400, 'Producto y cantidad inválidos');
        const groupedQuantity = (quantities.get(productId) ?? 0) + quantity;
        if (!Number.isSafeInteger(groupedQuantity)) throw new HttpError(400, 'La cantidad total del producto es inválida');
        quantities.set(productId, groupedQuantity);
      }
      const fingerprint = createHash('sha256').update(JSON.stringify([...quantities].sort((a,b) => a[0]-b[0]))).digest('hex');
      if (operation) {
        const previous = db.prepare('SELECT * FROM sales WHERE operation_id=?').get(operation) as SaleRow | undefined;
        if (previous) {
          if (previous.usuario_id !== userId || previous.operation_hash !== fingerprint) throw new HttpError(409, 'operation_id ya está asociado a otra solicitud');
          return this.byId(previous.id)!;
        }
      }
      const resolved: Array<{ id: number; quantity: number; price: number; subtotal: number; stock: number }> = [];
      let totalCents = 0;
      for (const [productId, quantity] of quantities) {
        const product = db.prepare('SELECT id,precio_venta,stock,activo FROM products WHERE id=?').get(productId) as { id: number; precio_venta: number; stock: number; activo: number } | undefined;
        if (!product?.activo) throw new HttpError(404, 'Producto ' + productId + ' no disponible');
        if (quantity > product.stock) throw new HttpError(400, 'Stock insuficiente para el producto ' + productId);
        const priceCents = cents(product.precio_venta), subtotalCents = safeMoneySum(priceCents * quantity);
        totalCents = safeMoneySum(totalCents + subtotalCents);
        resolved.push({ id: productId, quantity, price: priceCents / 100, subtotal: subtotalCents / 100, stock: product.stock });
      }
      const sale = db.prepare('INSERT INTO sales(usuario_id,total,operation_id,operation_hash) VALUES (?,?,?,?)').run(userId, totalCents / 100, operation, operation ? fingerprint : null);
      const saleId = Number(sale.lastInsertRowid);
      for (const item of resolved) {
        db.prepare('INSERT INTO sale_items(venta_id,producto_id,cantidad,precio_unitario,subtotal) VALUES (?,?,?,?,?)').run(saleId,item.id,item.quantity,item.price,item.subtotal);
        const updated = db.prepare('UPDATE products SET stock=stock-?,fecha_actualizacion=CURRENT_TIMESTAMP WHERE id=? AND stock>=?').run(item.quantity,item.id,item.quantity);
        if (updated.changes !== 1) throw new HttpError(400, 'No se pudo descontar el stock del producto ' + item.id);
        db.prepare('INSERT INTO inventory_movements(producto_id,usuario_id,tipo,cantidad,stock_anterior,stock_nuevo,referencia) VALUES (?,?,?,?,?,?,?)').run(item.id,userId,'SALE',item.quantity,item.stock,item.stock-item.quantity,String(saleId));
      }
      return this.byId(saleId)!;
    });
  }
  byId(saleId: number) {
    const sale = db.prepare('SELECT s.*,u.username vendedor,u.nombre vendedor_nombre FROM sales s JOIN users u ON u.id=s.usuario_id WHERE s.id=?').get(id(saleId)) as SaleRow | undefined;
    if (!sale) return null;
    const { operation_hash: _privateHash, ...publicSale } = sale;
    return { ...publicSale, items: db.prepare('SELECT si.*,p.nombre producto,p.codigo FROM sale_items si JOIN products p ON p.id=si.producto_id WHERE si.venta_id=?').all(saleId) };
  }
  list(userId?: number, from?: string, to?: string) {
    const where = ['1=1'], values: unknown[] = [];
    if (userId !== undefined) { where.push('s.usuario_id=?'); values.push(id(userId)); }
    if (from !== undefined || to !== undefined) {
      const range = dateWindow('dia', from, to);
      where.push('s.fecha_creacion>=? AND s.fecha_creacion<?'); values.push(range.start, range.end);
    }
    return db.prepare('SELECT s.id,s.usuario_id,s.total,s.fecha_creacion,s.operation_id,u.username vendedor,u.nombre vendedor_nombre FROM sales s JOIN users u ON u.id=s.usuario_id WHERE ' + where.join(' AND ') + ' ORDER BY s.id DESC LIMIT 501').all(...values);
  }
}
