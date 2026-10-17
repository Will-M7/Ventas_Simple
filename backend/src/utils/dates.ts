import { DateTime, IANAZone } from 'luxon';
import { HttpError, text } from './validation';
export function businessZone(): string {
  const zone = process.env.BUSINESS_TIME_ZONE ?? 'America/Lima';
  if (!IANAZone.isValidZone(zone)) throw new Error('BUSINESS_TIME_ZONE inválida');
  return zone;
}
function calendarDate(value: unknown, zone: string): DateTime {
  const s = text(value, 'Fecha', 10);
  const date = DateTime.fromISO(s, { zone });
  if (!/^\d{4}-\d{2}-\d{2}$/.test(s) || !date.isValid || date.toISODate() !== s) throw new HttpError(400, 'Fecha inválida: usa YYYY-MM-DD');
  return date.startOf('day');
}
export function dateWindow(periodo: string = 'dia', from?: string, to?: string, now = DateTime.utc()) {
  const zone = businessZone(), today = now.setZone(zone).startOf('day');
  let start: DateTime, end: DateTime;
  if (from !== undefined || to !== undefined) {
    start = from !== undefined ? calendarDate(from, zone) : calendarDate('1970-01-01', zone);
    end = (to !== undefined ? calendarDate(to, zone) : today).plus({ days: 1 });
  } else {
    switch (periodo) {
      case 'dia': case 'hoy': start = today; end = today.plus({ days: 1 }); break;
      case 'semana': start = today.minus({ days: 6 }); end = today.plus({ days: 1 }); break;
      case 'mes': start = today.startOf('month'); end = start.plus({ months: 1 }); break;
      case 'anio': start = today.startOf('year'); end = start.plus({ years: 1 }); break;
      default: throw new HttpError(400, 'Período inválido');
    }
  }
  if (start >= end) throw new HttpError(400, 'El rango de fechas está invertido');
  return { start: start.toUTC().toFormat('yyyy-MM-dd HH:mm:ss'), end: end.toUTC().toFormat('yyyy-MM-dd HH:mm:ss'), zone };
}
