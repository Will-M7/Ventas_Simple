import { Response } from 'express';
export function sendList(res: Response, rows: unknown[], limit: number): Response {
  res.setHeader('X-Result-Limit', limit);
  res.setHeader('X-Results-Truncated', rows.length > limit ? 'true' : 'false');
  return res.json(rows.slice(0, limit));
}
