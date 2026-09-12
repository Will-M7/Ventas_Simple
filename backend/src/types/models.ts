export type Rol = 'admin' | 'vendedor';
export type User = { id: number; username: string; nombre: string; rol: Rol; activo: number; fecha_creacion: string; };
export type Product = { id: number; nombre: string; descripcion: string | null; categoria_id: number | null; precio_compra: number; precio_venta: number; stock: number; stock_minimo: number | null; codigo: string; imagen: string | null; activo: number; fecha_creacion: string; fecha_actualizacion: string; categoria_nombre?: string | null; };
export type SaleItemInput = { productoId?: number; producto_id?: number; cantidad: number };
