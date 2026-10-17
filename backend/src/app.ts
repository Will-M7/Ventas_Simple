import express from 'express';
import cors from 'cors';
import path from 'node:path';
import fs from 'node:fs/promises';
import usuarios from './routes/usuarios.routes';
import productos from './routes/productos.routes';
import ventas from './routes/ventas.routes';
import reportes from './routes/reportes.routes';
import categorias from './routes/categorias.routes';
import inventario from './routes/inventario.routes';
import { db, dataDirectory } from './config/database';
import { uploadsRoot } from './services/image.service';
import { errorHandler } from './middleware/errors';
export function createApp() {
  const app = express();
  app.disable('x-powered-by');
  app.use(cors({exposedHeaders: ['X-Result-Limit','X-Results-Truncated','Retry-After']}));
  app.use((_req, res, next) => { res.setHeader('X-Content-Type-Options','nosniff'); next(); });
  app.use(express.json({limit: '100kb'}));
  app.use(express.urlencoded({extended: false, limit: '100kb'}));
  app.use('/uploads/products', (req, res, next) => {
    if (!/^\/[0-9a-f-]+\.(jpg|jpeg|png|webp|gif)$/i.test(req.path)) { res.status(404).json({message: 'Imagen no encontrada'}); return; }
    res.setHeader('Content-Security-Policy', "default-src 'none'; sandbox"); next();
  }, express.static(path.join(uploadsRoot(), 'products'), {maxAge: '1d', dotfiles: 'deny'}));
  app.get('/health', async (_req, res) => {
    try {
      db.prepare('SELECT 1').get();
      await fs.access(dataDirectory, fs.constants.R_OK | fs.constants.W_OK);
      res.json({status: 'ok', database: 'sqlite'});
    } catch { res.status(503).json({status: 'unavailable', database: 'sqlite'}); }
  });
  app.use('/api/usuarios', usuarios); app.use('/api/productos', productos);
  app.use('/api/ventas', ventas); app.use('/api/reportes', reportes);
  app.use('/api/categorias', categorias); app.use('/api/inventario', inventario);
  app.use((_req, res) => { res.status(404).json({message: 'Ruta no encontrada'}); });
  app.use(errorHandler);
  return app;
}
export const app = createApp();
