import { RequestHandler } from 'express';
type Entry = { count: number; until: number };
export function createLoginLimiter(options: { now?: () => number; windowMs?: number; maxIp?: number; maxPair?: number; maxKeys?: number } = {}): RequestHandler {
  const now = options.now ?? Date.now, windowMs = options.windowMs ?? 15 * 60_000;
  const entries = new Map<string, Entry>();
  const maxKeys = options.maxKeys ?? 10_000;
  return (req, res, next) => {
    const time = now(), ip = req.ip ?? req.socket.remoteAddress ?? 'unknown';
    const username = typeof req.body?.username === 'string' ? req.body.username.trim().toLowerCase().slice(0,80) : '';
    for (const [key, entry] of entries) if (entry.until <= time) entries.delete(key);
    const keys: Array<[string,number]> = [['ip:' + ip, options.maxIp ?? 100], ['pair:' + ip + ':' + username, options.maxPair ?? 10]];
    for (const [key, max] of keys) {
      const entry = entries.get(key);
      if (entry && entry.count >= max) {
        res.setHeader('Retry-After', Math.max(1, Math.ceil((entry.until - time) / 1000)));
        res.status(429).json({ message: 'Demasiados intentos. Espera antes de reintentar.' }); return;
      }
    }
    for (const [key] of keys) {
      if (!entries.has(key)) {
        if (entries.size >= maxKeys) {
          res.setHeader('Retry-After', Math.ceil(windowMs / 1000));
          res.status(429).json({ message: 'Límite temporal de autenticación alcanzado' }); return;
        }
        entries.set(key, { count: 0, until: time + windowMs });
      }
      entries.get(key)!.count++;
    }
    next();
  };
}
