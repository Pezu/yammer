import { Pipe, PipeTransform } from '@angular/core';

/**
 * Single source of truth for displaying timestamps.
 *
 * Every timestamp the backend returns is a naive wall-clock string with no zone
 * (Java `LocalDateTime`, e.g. `"2026-06-29T13:40:00"`) recorded in UTC. The whole
 * app renders these in Romanian local time (EET/EEST) — using the IANA zone so the
 * summer/winter offset is handled automatically.
 */
export const DISPLAY_TZ = 'Europe/Bucharest';

/** Parse a backend timestamp, interpreting a zone-less string as UTC. */
export function parseUtc(value: string | number | Date | null | undefined): Date | null {
  if (value == null || value === '') return null;
  if (value instanceof Date) return Number.isNaN(value.getTime()) ? null : value;
  if (typeof value === 'number') return new Date(value);
  const hasZone = /[zZ]$|[+-]\d{2}:?\d{2}$/.test(value);
  const d = new Date(hasZone ? value : `${value}Z`);
  return Number.isNaN(d.getTime()) ? null : d;
}

interface TzParts {
  day: string;
  month: string;
  year: string;
  hour: string;
  minute: string;
}

const PARTS_FMT = new Intl.DateTimeFormat('en-GB', {
  timeZone: DISPLAY_TZ,
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});

function partsInTz(d: Date): TzParts {
  const acc: Record<string, string> = {};
  for (const part of PARTS_FMT.formatToParts(d)) {
    if (part.type !== 'literal') acc[part.type] = part.value;
  }
  // some engines render midnight as "24" with hour12:false — normalise to "00"
  if (acc['hour'] === '24') acc['hour'] = '00';
  return {
    day: acc['day'] ?? '',
    month: acc['month'] ?? '',
    year: acc['year'] ?? '',
    hour: acc['hour'] ?? '',
    minute: acc['minute'] ?? '',
  };
}

/** "HH:mm" in Romanian local time. */
export function roTime(value: string | number | Date | null | undefined): string {
  const d = parseUtc(value);
  if (!d) return '';
  const p = partsInTz(d);
  return `${p.hour}:${p.minute}`;
}

/** "dd.MM.yyyy" in Romanian local time. */
export function roDate(value: string | number | Date | null | undefined): string {
  const d = parseUtc(value);
  if (!d) return '';
  const p = partsInTz(d);
  return `${p.day}.${p.month}.${p.year}`;
}

/** "dd.MM HH:mm" in Romanian local time. */
export function roDateTime(value: string | number | Date | null | undefined): string {
  const d = parseUtc(value);
  if (!d) return '';
  const p = partsInTz(d);
  return `${p.day}.${p.month} ${p.hour}:${p.minute}`;
}

/** Template pipe: `{{ value | roDate }}` (datetime), or `| roDate: 'time'` / `'date'`. */
@Pipe({ name: 'roDate' })
export class RoDatePipe implements PipeTransform {
  transform(
    value: string | number | Date | null | undefined,
    mode: 'time' | 'date' | 'datetime' = 'datetime',
  ): string {
    return mode === 'time' ? roTime(value) : mode === 'date' ? roDate(value) : roDateTime(value);
  }
}
