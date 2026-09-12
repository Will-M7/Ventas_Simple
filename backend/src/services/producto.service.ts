import { db, withTransaction } from '../config/database';
import { Product } from '../types/models';
export type ProductInput = { codigo?: string; codigo_barras?: string; nombre: string; descripcion?: string | null; precio_compra: number; precio_venta: number; stock?: number; stock_minimo?: number | null; categoria_id?: number | null; imagen?: string | null; foto_url?: string | null };
const productSelect = 'p.*, c.nombre AS categoria_nombre';
function validate(input: ProductInput): void {
  if (!input.nombre?.trim() || !(input.codigo ?? input.codigo_barras)?.trim()) throw Object.assign(new Error('nombre y codigo son obligatorios'), { status: 400 });
  if (!Number.isFinite(input.precio_compra) || input.precio_compra < 0 || !Number.isFinite(input.precio_venta) || input.precio_venta < 0) throw Object.assign(new Error('Los precios no pueden ser negativos'), { status: 400 });
  if (input.stock !== undefined && (!Number.isInteger(input.stock) || input.stock < 0)) throw Object.assign(new Error('El stock debe ser un entero no negativo'), { status: 400 });
}
export class ProductoService {
  list(query: { search?: string; categoria?: string; incluirInactivos?: boolean } = {}): Product[] {
    const where = [query.incluirInactivos ? '1=1' : 'p.activo=1']; const values: unknown[] = [];
    if (query.search?.trim()) { where.push('(p.nombre LIKE ? OR p.codigo LIKE ?)'); const q = '%' + query.search.trim() + '%'; values.push(q, q); }
    if (query.categoria) { where.push('p.categoria_id=?'); values.push(Number(query.categoria)); }
    return db.prepare('SELECT ' + productSelect + ' FROM products p LEFT JOIN categories c ON c.id=p.categoria_id WHERE ' + where.join(' AND ') + ' ORDER BY p.nombre').all(...values) as Product[];
  }
  byId(id: number): Product | null { return (db.prepare('SELECT ' + productSelect + ' FROM products p LEFT JOIN categories c ON c.id=p.categoria_id WHERE p.id=?').get(id) as Product | undefined) ?? null; }
  byCode(code: string): Product | null { return (db.prepare('SELECT ' + productSelect + ' FROM products p LEFT JOIN categories c ON c.id=p.categoria_id WHERE p.codigo=?').get(code) as Product | undefined) ?? null; }
  create(input: ProductInput, userId: number): Product {
    validate(input); const codigo = (input.codigo ?? input.codigo_barras!).trim(); const stock = input.stock ?? 0;
    return withTransaction(() => { const result = db.prepare('INSERT INTO products (nombre,descripcion,categoria_id,precio_compra,precio_venta,stock,stock_minimo,codigo,imagen) VALUES (?,?,?,?,?,?,?,?,?)').run(input.nombre.trim(), input.descripcion ?? null, input.categoria_id ?? null, input.precio_compra, input.precio_venta, stock, input.stock_minimo ?? 5, codigo, input.imagen ?? input.foto_url ?? null); const id = Number(result.lastInsertRowid); if (stock > 0) db.prepare('INSERT INTO inventory_movements (producto_id,usuario_id,tipo,cantidad,stock_anterior,stock_nuevo,observacion) VALUES (?,?,?,?,?,?,?)').run(id,userId,'PRODUCT_CREATE',stock,0,stock,'Stock inicial'); return this.byId(id)!; });
  }
  update(id: number, input: Partial<ProductInput>, userId: number): Product | null {
    const current = this.byId(id); if (!current) return null; validate({ ...current, ...input, codigo: input.codigo ?? input.codigo_barras ?? current.codigo } as ProductInput);
    const fields: string[] = []; const values: unknown[] = [];
    for (const key of ['nombre','descripcion','categoria_id','precio_compra','precio_venta','stock_minimo','codigo','imagen'] as const) if (input[key] !== undefined || (key === 'codigo' && input.codigo_barras !== undefined)) { fields.push(key + '=?'); values.push(key === 'codigo' ? input.codigo ?? input.codigo_barras : input[key]); }
    return withTransaction(() => {
      if (fields.length) { fields.push('fecha_actualizacion=CURRENT_TIMESTAMP'); values.push(id); db.prepare('UPDATE products SET ' + fields.join(',') + ' WHERE id=?').run(...values); }
      if (input.stock !== undefined && input.stock !== current.stock) {
        const amount = Math.abs(input.stock - current.stock); const decrease = input.stock < current.stock;
        db.prepare('INSERT INTO inventory_movements (producto_id,usuario_id,tipo,cantidad,stock_anterior,stock_nuevo,observacion) VALUES (?,?,?,?,?,?,?)').run(id,userId,decrease?'MANUAL_DECREASE':'MANUAL_INCREASE',amount,current.stock,input.stock,'Ajuste desde producto');
      }
      return this.byId(id);
    });
  }
  setActive(id: number, activo: boolean): Product | null { db.prepare('UPDATE products SET activo=?,fecha_actualizacion=CURRENT_TIMESTAMP WHERE id=?').run(activo ? 1 : 0, id); return this.byId(id); }
  setImage(id: number, image: string): Product | null { db.prepare('UPDATE products SET imagen=?,fecha_actualizacion=CURRENT_TIMESTAMP WHERE id=?').run(image,id); return this.byId(id); }
}
