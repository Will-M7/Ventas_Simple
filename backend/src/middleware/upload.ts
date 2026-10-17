import multer from 'multer';
import { RequestHandler } from 'express';
const parser = multer({ storage: multer.memoryStorage(), limits: { fileSize: 5 * 1024 * 1024, files: 1, fields: 20, fieldSize: 8192, parts: 21 } });
let active = 0;
export const upload = {
  single(field: string): RequestHandler {
    const parse = parser.single(field);
    return (req, res, next) => {
      if (active >= 8) { res.status(429).json({message: 'Demasiadas imágenes en proceso; reintenta'}); return; }
      active++;
      let released = false;
      const release = () => { if (!released) { active--; released = true; } };
      res.once('finish', release); res.once('close', release);
      parse(req, res, next);
    };
  },
};
