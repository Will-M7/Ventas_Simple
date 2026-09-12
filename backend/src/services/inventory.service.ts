import { db, withTransaction } from '../config/database';
export class InventoryService {
  change(productId: number, userId: number, amount: number, decrease: boolean, observation?: string) {
    if (!Number.isInteger(amount) || amount <= 0) throw Object.assign(new Error('La cantidad debe ser un entero positivo'), { status: 400 });
    return withTransaction(() => {
      const product = db.prepare('SELECT * FROM products WHERE id=?').get(productId) as { stock: number } | undefined;
      if (!product) throw Object.assign(new Error('Producto no encontrado'), { status: 404 });
      const next = product.stock + (decrease ? -amount : amount); if (next < 0) throw Object.assign(new Error('El stock no puede ser negativo'), { status: 400 });
      db.prepare('UPDATE products SET stock=?,fecha_actualizacion=CURRENT_TIMESTAMP WHERE id=?').run(next, productId);
      db.prepare('INSERT INTO inventory_movements (producto_id,usuario_id,tipo,cantidad,stock_anterior,stock_nuevo,observacion) VALUES (?,?,?,?,?,?,?)').run(productId,userId,decrease?'MANUAL_DECREASE':'MANUAL_INCREASE',amount,product.stock,next,observation ?? null);
      return db.prepare('SELECT * FROM products WHERE id=?').get(productId);
    });
  }
  movements(query: { productoId?: string; limit?: string }) {
    const where = query.productoId ? 'WHERE m.producto_id=?' : ''; const values = query.productoId ? [Number(query.productoId)] : []; const limit = Math.min(Math.max(Number(query.limit ?? 100), 1), 500);
    return db.prepare('SELECT m.*,p.nombre producto,p.codigo,u.username usuario FROM inventory_movements m JOIN products p ON p.id=m.producto_id JOIN users u ON u.id=m.usuario_id ' + where + ' ORDER BY m.id DESC LIMIT ' + limit).all(...values);
  }
}
