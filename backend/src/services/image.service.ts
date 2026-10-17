import sharp from 'sharp';
import path from 'node:path';
import fs from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import { db } from '../config/database';
import { HttpError } from '../utils/validation';
export const MAX_PHOTO_BYTES = 5 * 1024 * 1024;
let normalizing = 0;
export const uploadsRoot = () => path.resolve(process.env.UPLOAD_DIR ?? path.join(__dirname, '..', '..', 'uploads'));
function formatOf(bytes: Buffer): 'jpeg' | 'png' | 'webp' {
  if (bytes.length >= 3 && bytes[0] === 255 && bytes[1] === 216 && bytes[2] === 255) return 'jpeg';
  if (bytes.length >= 8 && bytes.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10]))) return 'png';
  if (bytes.length >= 12 && bytes.toString('ascii',0,4) === 'RIFF' && bytes.toString('ascii',8,12) === 'WEBP') return 'webp';
  throw new HttpError(400, 'Imagen inválida: usa JPEG, PNG o WebP');
}
export async function storeImage(file: Express.Multer.File): Promise<string> {
  if (file.buffer.length > MAX_PHOTO_BYTES) throw new HttpError(413, 'La imagen supera 5 MiB');
  const format = formatOf(file.buffer);
  if (file.mimetype !== 'image/' + format) throw new HttpError(400, 'MIME de imagen no coincide con el contenido');
  // Keep the CPU/pixel-memory limit until decoding actually finishes, even if
  // the requesting HTTP connection closes while Sharp is still working.
  if (normalizing >= 2) throw new HttpError(429, 'Demasiadas imágenes en proceso; reintenta');
  normalizing++;
  let output: Buffer;
  try {
    const processor = sharp(file.buffer, { limitInputPixels: 40_000_000, failOn: 'warning' }).rotate();
    const metadata = await processor.metadata();
    if (metadata.format !== format || (metadata.pages ?? 1) > 1) throw new Error('Formato no soportado');
    output = await (format === 'jpeg' ? processor.jpeg({quality: 90}) : format === 'webp' ? processor.webp({quality: 90}) : processor.png()).toBuffer();
  } catch { throw new HttpError(400, 'Imagen inválida o dimensiones excesivas (máximo 40 megapíxeles)'); }
  finally { normalizing--; }
  if (output.length > MAX_PHOTO_BYTES) throw new HttpError(413, 'La imagen normalizada supera 5 MiB');
  const name = randomUUID() + (format === 'jpeg' ? '.jpg' : '.' + format);
  const directory = path.join(uploadsRoot(), 'products');
  await fs.mkdir(directory, {recursive: true, mode: 0o750});
  const filename = path.join(directory, name);
  let created = false;
  try {
    const handle = await fs.open(filename, 'wx', 0o640);
    created = true;
    try { await handle.writeFile(output); } finally { await handle.close(); }
  } catch (error) {
    if (created) await fs.unlink(filename).catch(() => undefined);
    throw error;
  }
  return '/uploads/products/' + name;
}
export async function removeUnreferencedImage(image: string | null | undefined): Promise<void> {
  if (!image || !/^\/uploads\/products\/[0-9a-f-]+\.(jpg|jpeg|png|webp|gif)$/.test(image)) return;
  const referenced = db.prepare('SELECT 1 FROM products WHERE imagen=? LIMIT 1').get(image);
  if (referenced) return;
  try { await fs.unlink(path.join(uploadsRoot(), 'products', path.basename(image))); }
  catch (error) { if ((error as NodeJS.ErrnoException).code !== 'ENOENT') console.warn('No se pudo limpiar una imagen huérfana'); }
}
