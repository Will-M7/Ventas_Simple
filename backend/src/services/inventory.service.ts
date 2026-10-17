import { db, withTransaction } from '../config/database';
import { HttpError, id, integer, text } from '../utils/validation';
export class InventoryService {
  change(productId: number, userId: number, amount: number, decrease: boolean, observation?: string) {
    productId = id(productId); userId = id(userId); amount = integer(amount, 'cantidad');
    const note = observation == null ? null : text(observation, 'observacion', 1000, true);
    return withTransaction(() => {
      const product = db.prepare('SELECT * FROM products WHERE id=?').get(productId) as { stock: number } | undefined;
      if (!product) throw new HttpError(404, 'Producto no encontrado');
      const next = product.stock + (decrease ? -amount : amount);
      if (next < 0) throw new HttpError(400, 'El stock no puede ser negativo');
      integer(next, 'stock resultante', 0);
      const updated = db.prepare('UPDATE products SET stock=?,fecha_actualizacion=CURRENT_TIMESTAMP WHERE id=? AND stock=?').run(next, productId, product.stock);
      if (updated.changes !== 1) throw new HttpError(409, 'El stock cambió; vuelve a consultar');
      db.prepare('INSERT INTO inventory_movements(producto_id,usuario_id,tipo,cantidad,stock_anterior,stock_nuevo,observacion) VALUES (?,?,?,?,?,?,?)').run(productId,userId,decrease?'MANUAL_DECREASE':'MANUAL_INCREASE',amount,product.stock,next,note);
      return db.prepare('SELECT * FROM products WHERE id=?').get(productId);
    });
  }
  movements(query: { productoId?: string; limit?: string }) {
    const values: unknown[] = [], where = query.productoId === undefined ? '' : 'WHERE m.producto_id=?';
    if (query.productoId !== undefined) values.push(id(query.productoId));
    const limit = integer(query.limit ?? 100, 'limit', 1, 500);
    return db.prepare('SELECT m.*,p.nombre producto,p.codigo,u.username usuario FROM inventory_movements m JOIN products p ON p.id=m.producto_id JOIN users u ON u.id=m.usuario_id ' + where + ' ORDER BY m.id DESC LIMIT ?').all(...values, limit + 1);
  }
}
