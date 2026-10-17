import { db, withTransaction } from '../config/database';
import { Product } from '../types/models';
import { HttpError, text, cents, integer, id, boolean, object } from '../utils/validation';

export type ProductInput = {
  codigo?: string; codigo_barras?: string; nombre: string; descripcion?: string | null;
  precio_compra: number; precio_venta: number; stock?: number; stock_minimo?: number | null;
  categoria_id?: number | null; imagen?: string | null; foto_url?: string | null;
};
const productSelect = 'p.*, c.nombre AS categoria_nombre';

// Validate supplied fields, not unchanged historical values. An ordinary edit must
// not require rewriting an old image reference or historical monetary precision.
function validate(input: Partial<ProductInput>, creating = false): void {
  if (creating || input.nombre !== undefined) text(input.nombre, 'nombre');
  if (creating || input.codigo !== undefined || input.codigo_barras !== undefined)
    text(input.codigo ?? input.codigo_barras, 'codigo', 80);
  if (creating || input.precio_compra !== undefined) cents(input.precio_compra, 'precio_compra');
  if (creating || input.precio_venta !== undefined) cents(input.precio_venta, 'precio_venta');
  if (input.stock !== undefined) integer(input.stock, 'stock', 0);
  if (input.stock_minimo !== undefined && input.stock_minimo !== null) integer(input.stock_minimo, 'stock_minimo', 0);
  if (input.categoria_id !== undefined && input.categoria_id !== null) {
    id(input.categoria_id);
    if (!db.prepare('SELECT 1 FROM categories WHERE id=? AND activo=1').get(input.categoria_id))
      throw new HttpError(400, 'Categoría no disponible');
  }
  if (input.descripcion !== undefined && input.descripcion !== null) text(input.descripcion, 'descripcion', 2000, true);
  if (input.imagen != null && !/^\/uploads\/products\/[0-9a-f-]+\.(jpg|png|webp)$/.test(input.imagen))
    throw new HttpError(400, 'Imagen no válida');
}

export class ProductoService {
  list(query: { search?: string; categoria?: string; incluirInactivos?: boolean } = {}): Product[] {
    if (query.search !== undefined) text(query.search, 'search', 180, true);
    const where = [query.incluirInactivos ? '1=1' : 'p.activo=1'];
    const values: unknown[] = [];
    if (query.search?.trim()) {
      where.push('(p.nombre LIKE ? OR p.codigo LIKE ?)');
      const q = '%' + query.search.trim() + '%'; values.push(q, q);
    }
    if (query.categoria) { where.push('p.categoria_id=?'); values.push(id(query.categoria)); }
    return db.prepare('SELECT ' + productSelect + ' FROM products p LEFT JOIN categories c ON c.id=p.categoria_id WHERE ' + where.join(' AND ') + ' ORDER BY p.nombre LIMIT 1001').all(...values) as Product[];
  }
  byId(productId: number): Product | null {
    return (db.prepare('SELECT ' + productSelect + ' FROM products p LEFT JOIN categories c ON c.id=p.categoria_id WHERE p.id=?').get(id(productId)) as Product | undefined) ?? null;
  }
  byCode(code: string): Product | null {
    return (db.prepare('SELECT ' + productSelect + ' FROM products p LEFT JOIN categories c ON c.id=p.categoria_id WHERE p.codigo=?').get(text(code, 'codigo', 80)) as Product | undefined) ?? null;
  }
  create(input: ProductInput, userId: number): Product {
    object(input); validate(input, true);
    const codigo = text(input.codigo ?? input.codigo_barras, 'codigo', 80);
    const stock = integer(input.stock ?? 0, 'stock', 0);
    return withTransaction(() => {
      const result = db.prepare('INSERT INTO products (nombre,descripcion,categoria_id,precio_compra,precio_venta,stock,stock_minimo,codigo,imagen) VALUES (?,?,?,?,?,?,?,?,?)')
        .run(text(input.nombre, 'nombre'), input.descripcion ?? null, input.categoria_id ?? null, cents(input.precio_compra) / 100,
          cents(input.precio_venta) / 100, stock, input.stock_minimo ?? 5, codigo, input.imagen ?? input.foto_url ?? null);
      const productId = Number(result.lastInsertRowid);
      if (stock > 0) db.prepare('INSERT INTO inventory_movements (producto_id,usuario_id,tipo,cantidad,stock_anterior,stock_nuevo,observacion) VALUES (?,?,?,?,?,?,?)')
        .run(productId, id(userId), 'PRODUCT_CREATE', stock, 0, stock, 'Stock inicial');
      return this.byId(productId)!;
    });
  }
  update(productId: number, input: Partial<ProductInput>, _userId: number): Product | null {
    productId = id(productId); object(input);
    if (Object.prototype.hasOwnProperty.call(input, 'stock'))
      throw new HttpError(400, 'La edición de producto no acepta stock. Usa las operaciones de inventario.');
    if (!this.byId(productId)) throw new HttpError(404, 'Producto no encontrado');
    validate(input);
    const fields: string[] = [], values: unknown[] = [];
    for (const key of ['nombre','descripcion','categoria_id','precio_compra','precio_venta','stock_minimo','codigo','imagen'] as const) {
      if (input[key] === undefined && !(key === 'codigo' && input.codigo_barras !== undefined)) continue;
      const value = key === 'codigo' ? input.codigo ?? input.codigo_barras : input[key];
      let normalized: unknown = value;
      if (key === 'nombre' || key === 'codigo') normalized = text(value, key, key === 'codigo' ? 80 : 180);
      else if (key === 'descripcion' && value !== null) normalized = text(value, key, 2000, true);
      else if (key === 'precio_compra' || key === 'precio_venta') normalized = cents(value, key) / 100;
      fields.push(key + '=?'); values.push(normalized);
    }
    return withTransaction(() => {
      if (fields.length) {
        fields.push('fecha_actualizacion=CURRENT_TIMESTAMP');
        db.prepare('UPDATE products SET ' + fields.join(',') + ' WHERE id=?').run(...values, productId);
      }
      return this.byId(productId);
    });
  }
  setActive(productId: number, active: boolean): Product | null {
    productId = id(productId); boolean(active);
    if (!this.byId(productId)) throw new HttpError(404, 'Producto no encontrado');
    db.prepare('UPDATE products SET activo=?,fecha_actualizacion=CURRENT_TIMESTAMP WHERE id=?').run(active ? 1 : 0, productId);
    return this.byId(productId);
  }
  setImage(productId: number, image: string): Product | null {
    productId = id(productId);
    if (!this.byId(productId)) throw new HttpError(404, 'Producto no encontrado');
    db.prepare('UPDATE products SET imagen=?,fecha_actualizacion=CURRENT_TIMESTAMP WHERE id=?').run(image, productId);
    return this.byId(productId);
  }
}
