import { Request, Response } from 'express';
import { ProductoService, ProductInput } from '../services/producto.service';
import { storeImage, removeUnreferencedImage } from '../services/image.service';
import { HttpError, id, boolean, numeric, object, queryText, text, queryBoolean } from '../utils/validation';
import { sendList } from '../utils/http';
const service = new ProductoService();
function input(req: Request): ProductInput {
  const body = object(req.body), result = {...body};
  if (body.imagen !== undefined || body.foto_url !== undefined) throw new HttpError(400, 'Usa el endpoint de imagen para adjuntar archivos');
  const code = body.codigo ?? body.codigo_barras;
  if (code !== undefined) result.codigo = code;
  for (const key of ['precio_compra','precio_venta','stock','stock_minimo','categoria_id']) {
    if (body[key] !== undefined) result[key] = body[key] === null && ['stock_minimo','categoria_id'].includes(key) ? null : numeric(body[key], key);
  }
  return result as ProductInput;
}
async function withImage(req: Request, work: (data: ProductInput) => unknown, productId?: number) {
  const data = input(req);
  if (productId !== undefined && !service.byId(productId)) throw new HttpError(404, 'Producto no encontrado');
  const image = req.file ? await storeImage(req.file) : undefined;
  try {
    // Reread immediately before the synchronous DB update to handle concurrent uploads.
    const oldImage = productId === undefined ? null : service.byId(productId)?.imagen;
    const result = work({...data, ...(image ? {imagen: image} : {})});
    if (image) await removeUnreferencedImage(oldImage);
    return result;
  } catch (error) { if (image) await removeUnreferencedImage(image); throw error; }
}
export const list = (req: Request, res: Response) => {
  const inactive = queryBoolean(req.query.incluirInactivos, 'incluirInactivos');
  return sendList(res, service.list({search: queryText(req.query.search, 'search') ?? '',
    ...(req.query.categoria !== undefined ? {categoria: String(id(req.query.categoria))} : {}),
    incluirInactivos: req.user?.rol === 'admin' && inactive}), 1000);
};
export const byId = (req: Request, res: Response) => { const product = service.byId(id(req.params.id)); return product && (product.activo || req.user?.rol === 'admin') ? res.json(product) : res.status(404).json({ message: 'Producto no encontrado' }); };
export const byCode = (req: Request, res: Response) => { const product = service.byCode(text(req.params.codigo, 'codigo', 80)); return product && (product.activo || req.user?.rol === 'admin') ? res.json(product) : res.status(404).json({ message: 'Producto no encontrado' }); };
export const create = async (req: Request, res: Response) => res.status(201).json(await withImage(req, data => service.create(data, req.user!.id)));
export const update = async (req: Request, res: Response) => { const productId = id(req.params.id); return res.json(await withImage(req, data => service.update(productId, data, req.user!.id), productId)); };
export const state = (req: Request, res: Response) => res.json(service.setActive(id(req.params.id), boolean(object(req.body).activo)));
export const image = async (req: Request, res: Response) => {
  const productId = id(req.params.id);
  if (!req.file) throw new HttpError(400, 'Imagen requerida');
  if (!service.byId(productId)) throw new HttpError(404, 'Producto no encontrado');
  const image = await storeImage(req.file);
  try {
    const oldImage = service.byId(productId)?.imagen;
    const result = service.setImage(productId, image);
    await removeUnreferencedImage(oldImage);
    return res.json(result);
  } catch (error) { await removeUnreferencedImage(image); throw error; }
};
