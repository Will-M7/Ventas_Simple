import { ErrorRequestHandler } from 'express';
import { HttpError } from '../utils/validation';
export const errorHandler: ErrorRequestHandler = (error, _req, res, _next) => {
  let status = 500, message = 'Error interno del servidor';
  if (error instanceof HttpError) { status = error.status; message = error.message; }
  else if (error?.code === 'SQLITE_CONSTRAINT_UNIQUE') { status = 409; message = 'El registro ya existe'; }
  else if (error?.code === 'SQLITE_CONSTRAINT_FOREIGNKEY' || error?.code === 'SQLITE_CONSTRAINT_CHECK') { status = 400; message = 'Datos incompatibles con el registro'; }
  else if (error?.code === 'LIMIT_FILE_SIZE') { status = 413; message = 'La imagen supera el límite de 5 MiB'; }
  else if (typeof error?.code === 'string' && error.code.startsWith('LIMIT_')) { status = 400; message = 'Multipart inválido o excesivo'; }
  else if (error?.type === 'entity.parse.failed') { status = 400; message = 'JSON inválido'; }
  else if (error?.type === 'entity.too.large') { status = 413; message = 'Solicitud demasiado grande'; }
  else if (error?.code === 'SQLITE_BUSY' || error?.code === 'SQLITE_BUSY_SNAPSHOT') { status = 503; message = 'Base ocupada; consulta el resultado de la operación antes de reintentar'; }
  if (status === 500) console.error('Error interno de API', { code: typeof error?.code === 'string' ? error.code : 'UNEXPECTED' });
  if (!res.headersSent) res.status(status).json({message});
};
